package com.hl.hybridsearch.search

import com.hl.hybridsearch.analyzer.MorphologyAnalyzer
import com.hl.hybridsearch.analyzer.QueryClassifier
import com.hl.hybridsearch.analyzer.QueryType
import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.search.model.SearchRequest
import com.hl.hybridsearch.search.model.SearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
        val hits = lexical.search(request.query, properties.topK.lexical)
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
        val lexDeferred = async(Dispatchers.IO) {
            runCatching { lexical.search(request.query, properties.topK.lexical) }
        }
        val vecDeferred = async(Dispatchers.IO) {
            runCatching { vector.search(request.query, properties.topK.vector) }
        }

        val lexResult = lexDeferred.await()
        val vecResult = vecDeferred.await()

        val lexHits = lexResult.getOrElse {
            log.error("Lexical search failed", it)
            emptyList()
        }
        val vecHits = vecResult.getOrElse {
            log.warn("Vector search failed, continuing with lexical only", it)
            emptyList()
        }

        val degraded = vecResult.isFailure
        val degradeReason = vecResult.exceptionOrNull()?.message

        val fused = when {
            degraded && properties.fallback.lexicalOnVectorFailure -> lexHits
            lexHits.isEmpty() && vecHits.isEmpty() -> emptyList()
            else -> rrf.fuse(listOf(lexHits, vecHits))
        }
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
}
