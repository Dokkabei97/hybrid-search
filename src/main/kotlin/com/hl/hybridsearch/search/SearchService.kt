package com.hl.hybridsearch.search

import com.hl.hybridsearch.analyzer.MorphologyAnalyzer
import com.hl.hybridsearch.analyzer.QueryClassifier
import com.hl.hybridsearch.analyzer.QueryType
import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.search.model.SearchHit
import com.hl.hybridsearch.search.model.SearchRequest
import com.hl.hybridsearch.search.model.SearchResponse
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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun search(request: SearchRequest): SearchResponse {
        val features = analyzer.analyze(request.query)
        val queryType = classifier.classify(features)
        log.debug("Query '{}' classified as {}", request.query, queryType)

        return when (queryType) {
            QueryType.KEYWORD -> lexicalOnly(request, queryType)
            QueryType.SENTENCE -> hybrid(request, queryType)
        }
    }

    private fun lexicalOnly(request: SearchRequest, queryType: QueryType): SearchResponse {
        val hits = lexical.searchMulti(request.query, properties.topK.lexical, request.filters)
        val paged = pager.slice(hits, request.page, request.size)
        return SearchResponse(
            queryType = queryType,
            total = hits.size,
            page = request.page,
            size = request.size,
            hits = paged,
        )
    }

    private fun hybrid(request: SearchRequest, queryType: QueryType): SearchResponse = runBlocking {
        val topKLex = properties.topK.lexical
        val topKVec = properties.topK.vector

        val titleDeferred = async(Dispatchers.IO) {
            runCatching { lexical.searchTitle(request.query, topKLex, request.filters) }
        }
        val multiDeferred = async(Dispatchers.IO) {
            runCatching { lexical.searchMulti(request.query, topKLex, request.filters) }
        }
        val vectorDeferred = async(Dispatchers.IO) {
            runCatching { vector.search(request.query, topKVec) }
        }

        val (titleRes, multiRes, vectorRes) = awaitAll(titleDeferred, multiDeferred, vectorDeferred)

        val titleHits = titleRes.getOrElse { logAndEmpty("title", it) }
        val multiHits = multiRes.getOrElse { logAndEmpty("multi", it) }
        val vectorHits = vectorRes.getOrElse { logAndEmpty("vector", it) }

        val degraded = titleRes.isFailure || multiRes.isFailure || vectorRes.isFailure
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
            degraded = degraded,
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
