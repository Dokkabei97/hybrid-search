package com.hl.hybridsearch.search

import com.hl.hybridsearch.analyzer.MorphologyAnalyzer
import com.hl.hybridsearch.analyzer.QueryClassifier
import com.hl.hybridsearch.analyzer.QueryType
import com.hl.hybridsearch.config.SearchProperties
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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 프로덕션 검색 진입점. 분류기가 경로(KEYWORD/SENTENCE)를 결정한다.
     *
     * 평가용 경로 강제는 [evaluate] 를 사용하라 — eval 전용 파라미터가 prod 시그니처에
     * 새지 않도록 의도적으로 분리했다.
     */
    fun search(request: SearchRequest): SearchResponse = route(request, forceType = null)

    /**
     * 평가 전용 검색 진입점 (module-internal).
     *
     * @param forceType `null` 이면 분류기 사용 (= prod 경로). KEYWORD/SENTENCE 지정 시 분기 강제.
     * A/B 비교 (classifier vs always-keyword vs always-sentence) 목적으로만 사용한다.
     * 프로덕션 코드에서 호출하지 말 것 — `internal` 가시성이 이를 컴파일 수준에서 차단한다.
     */
    internal fun evaluate(request: SearchRequest, forceType: QueryType? = null): SearchResponse =
        route(request, forceType)

    private fun route(request: SearchRequest, forceType: QueryType?): SearchResponse {
        val queryType = forceType ?: classifier.classify(analyzer.analyze(request.query))
        log.debug(
            "Query '{}' routed as {} (forced={})",
            request.query, queryType, forceType != null,
        )

        return when (queryType) {
            QueryType.KEYWORD -> lexicalOnly(request, queryType)
            QueryType.SENTENCE -> hybrid(request, queryType)
        }
    }

    private fun lexicalOnly(request: SearchRequest, queryType: QueryType): SearchResponse {
        val (hits, status, reason) = runCatching {
            lexical.searchMulti(request.query, properties.topK.lexical, request.filters)
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
            runCatching { lexical.searchTitle(request.query, topKLex, request.filters) }
        }
        val multiDeferred = async(Dispatchers.IO) {
            runCatching { lexical.searchMulti(request.query, topKLex, request.filters) }
        }
        val vectorDeferred = async(Dispatchers.IO) {
            runCatching { vector.search(request.query, topKVec, request.filters) }
        }

        val (titleRes, multiRes, vectorRes) = awaitAll(titleDeferred, multiDeferred, vectorDeferred)

        val titleHits = titleRes.getOrElse { logAndEmpty("title", it) }
        val multiHits = multiRes.getOrElse { logAndEmpty("multi", it) }
        val vectorHits = vectorRes.getOrElse { logAndEmpty("vector", it) }

        // lexical 채널 상태: title/multi 중 하나라도 성공이면 OK
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
