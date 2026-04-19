package com.hl.hybridsearch.search

import com.hl.hybridsearch.analyzer.MorphologyAnalyzer
import com.hl.hybridsearch.analyzer.QueryClassifier
import com.hl.hybridsearch.analyzer.QueryType
import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.observability.SearchMetrics
import com.hl.hybridsearch.search.model.SearchHit
import com.hl.hybridsearch.search.model.SearchRequest
import com.hl.hybridsearch.search.model.SearchResponse
import com.hl.hybridsearch.search.model.SourceHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SearchService(
    private val analyzer: MorphologyAnalyzer,
    private val classifier: QueryClassifier,
    private val lexical: LexicalSearcher,
    private val vector: VectorSearcher,
    private val rrf: ReciprocalRankFusion,
    private val pager: Pager,
    private val properties: SearchProperties,
    private val metrics: SearchMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun search(request: SearchRequest): SearchResponse {
        val features = timedAnalyze { analyzer.analyze(request.query) }
        val queryType = classifier.classify(features)
        metrics.recordRouting(queryType)
        log.debug("Query '{}' classified as {}", request.query, queryType)

        return when (queryType) {
            QueryType.KEYWORD -> lexicalOnly(request, queryType)
            QueryType.SENTENCE -> hybrid(request, queryType)
        }
    }

    private fun timedAnalyze(block: () -> com.hl.hybridsearch.analyzer.QueryFeatures)
        : com.hl.hybridsearch.analyzer.QueryFeatures {
        val start = System.nanoTime()
        return try {
            val r = block()
            metrics.recordAnalyze(SearchMetrics.Status.OK, System.nanoTime() - start)
            r
        } catch (e: Throwable) {
            metrics.recordAnalyze(SearchMetrics.Status.FAILED, System.nanoTime() - start)
            throw e
        }
    }

    private fun lexicalOnly(request: SearchRequest, queryType: QueryType): SearchResponse {
        val (hits, status, reason) = runCatching {
            metrics.timeChannel(SearchMetrics.Source.LEXICAL_ONLY) {
                lexical.searchMulti(request.query, properties.topK.lexical, request.filters)
            }
        }.fold(
            onSuccess = { Triple(it, SourceHealth.Status.OK, null) },
            onFailure = { t ->
                log.error("Lexical-only search failed", t)
                Triple(emptyList<SearchHit>(), SourceHealth.Status.FAILED, t.message)
            },
        )

        val paged = pager.slice(hits, request.page, request.size)
        return SearchResponse(
            queryType = queryType,
            total = hits.size,
            page = request.page,
            size = request.size,
            hits = paged,
            sources = SourceHealth(lexical = status, vector = SourceHealth.Status.DISABLED),
            degradeReason = reason,
        )
    }

    private fun hybrid(request: SearchRequest, queryType: QueryType): SearchResponse = runBlocking {
        val topKLex = properties.topK.lexical
        val topKVec = properties.topK.vector

        val titleDeferred = async(Dispatchers.IO) {
            runCatching {
                metrics.timeChannel(SearchMetrics.Source.LEXICAL_TITLE) {
                    lexical.searchTitle(request.query, topKLex, request.filters)
                }
            }
        }
        val multiDeferred = async(Dispatchers.IO) {
            runCatching {
                metrics.timeChannel(SearchMetrics.Source.LEXICAL_MULTI) {
                    lexical.searchMulti(request.query, topKLex, request.filters)
                }
            }
        }
        val vectorDeferred = async(Dispatchers.IO) {
            runCatching {
                metrics.timeChannel(SearchMetrics.Source.VECTOR) {
                    vector.search(request.query, topKVec)
                }
            }
        }

        val (titleRes, multiRes, vectorRes) = awaitAll(titleDeferred, multiDeferred, vectorDeferred)

        val titleHits = titleRes.getOrElse { logAndEmpty("title", it) }
        val multiHits = multiRes.getOrElse { logAndEmpty("multi", it) }
        val vectorHits = vectorRes.getOrElse { logAndEmpty("vector", it) }

        val lexicalStatus = if (titleRes.isSuccess || multiRes.isSuccess) {
            SourceHealth.Status.OK
        } else {
            SourceHealth.Status.FAILED
        }
        val vectorStatus = if (vectorRes.isSuccess) SourceHealth.Status.OK else SourceHealth.Status.FAILED

        val degradeReason = listOfNotNull(
            titleRes.exceptionOrNull()?.let { "title:${it.message}" },
            multiRes.exceptionOrNull()?.let { "multi:${it.message}" },
            vectorRes.exceptionOrNull()?.let { "vector:${it.message}" },
        ).joinToString("; ").ifBlank { null }

        val fused = fuseChannels(titleHits, multiHits, vectorHits)
        val paged = pager.slice(fused, request.page, request.size)

        SearchResponse(
            queryType = queryType,
            total = fused.size,
            page = request.page,
            size = request.size,
            hits = paged,
            sources = SourceHealth(lexical = lexicalStatus, vector = vectorStatus),
            degradeReason = degradeReason,
        )
    }

    private fun fuseChannels(
        title: List<SearchHit>,
        multi: List<SearchHit>,
        vector: List<SearchHit>,
    ): List<SearchHit> {
        val rrfCfg = properties.rrf
        val lists = listOf(title, multi, vector)
        if (lists.all { it.isEmpty() }) return emptyList()
        val weights = listOf(rrfCfg.titleWeight, rrfCfg.bodyWeight, rrfCfg.vectorWeight)
        return rrf.fuse(lists, weights)
    }

    private fun logAndEmpty(channel: String, t: Throwable): List<SearchHit> {
        log.warn("Search channel '{}' failed: {}", channel, t.message)
        return emptyList()
    }
}
