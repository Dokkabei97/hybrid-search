package com.hl.hybridsearch.eval

import com.hl.hybridsearch.analyzer.QueryType
import com.hl.hybridsearch.search.SearchService
import com.hl.hybridsearch.search.model.SearchRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * GoldSet을 SearchService에 통과시키고 지표를 집계하는 평가 러너.
 *
 * strategy:
 *   - `null`           → 분류기 기본 경로 (production)
 *   - QueryType.KEYWORD→ 모든 쿼리를 lexical-only 강제
 *   - QueryType.SENTENCE→ 모든 쿼리를 3-way RRF 강제
 * 이 세 가지를 비교하면 "분류기 유무·항상 hybrid 의 비용/품질 트레이드오프"가 드러난다.
 */
@Component
class EvaluationRunner(
    private val searchService: SearchService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun run(
        gold: GoldSet,
        strategyName: String,
        forceType: QueryType? = null,
        topK: Int = 50,
    ): EvaluationReport {
        log.info("Evaluating strategy='{}' on {} queries (forceType={})",
            strategyName, gold.queries.size, forceType)

        val results = gold.queries.map { gq ->
            val response = searchService.search(
                SearchRequest(query = gq.query, page = 0, size = topK),
                forceType = forceType,
            )
            val ranked = response.hits.map { it.docId }
            QueryResult(
                query = gq,
                actualType = response.queryType,
                ndcg10 = Metrics.ndcgAtK(ranked, gq.relevant, 10),
                mrr = Metrics.mrr(ranked, gq.relevant),
                recall50 = Metrics.recallAtK(ranked, gq.relevant, 50),
                degraded = response.degraded,
            )
        }

        return EvaluationReport.aggregate(strategyName, results)
    }
}
