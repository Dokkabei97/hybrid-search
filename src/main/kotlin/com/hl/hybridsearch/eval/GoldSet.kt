package com.hl.hybridsearch.eval

import com.hl.hybridsearch.analyzer.QueryType
import com.hl.hybridsearch.indexing.Product

data class GoldQuery(
    val query: String,
    val expectedType: QueryType,
    val relevant: Set<String>,
    val categoryL1: String,
    val tag: String,
)

/**
 * 합성 평가 세트: 카탈로그 코퍼스 + 쿼리 및 정답 문서 매핑.
 * 평가 러너가 이 객체 하나만 받으면 실험 전체를 재현할 수 있다.
 */
data class GoldSet(
    val corpus: List<Product>,
    val queries: List<GoldQuery>,
) {
    val corpusSize: Int get() = corpus.size
    val querySize: Int get() = queries.size
}
