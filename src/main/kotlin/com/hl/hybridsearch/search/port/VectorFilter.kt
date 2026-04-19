package com.hl.hybridsearch.search.port

import com.hl.hybridsearch.search.model.SearchFilters

/**
 * 벡터 스토어 필터 AST — 벤더 중립 도메인 모델.
 * Qdrant/Milvus 공통 서브셋만 포함한다: Term, Terms, Range, And.
 * Or/Not 는 실제 요구가 생기면 확장. 지금은 YAGNI.
 *
 * 어댑터는 각자 네이티브 표현으로 변환한다 (Qdrant: Filter.must, Milvus: boolean expression string).
 */
sealed interface VectorFilter {
    data class Term(val field: String, val value: Any) : VectorFilter
    data class Terms(val field: String, val values: List<Any>) : VectorFilter
    data class Range(
        val field: String,
        val gte: Number? = null,
        val lte: Number? = null,
    ) : VectorFilter {
        init {
            require(gte != null || lte != null) { "Range must have at least one bound" }
        }
    }

    data class And(val clauses: List<VectorFilter>) : VectorFilter {
        init {
            require(clauses.isNotEmpty()) { "And requires at least one clause" }
        }
    }

    companion object {
        /**
         * 도메인 [SearchFilters] → 벡터 필터 AST.
         *
         * payload 키 네이밍은 [com.hl.hybridsearch.indexing.adapter.QdrantBulkWriter.toAiDocument]
         * 와 일치시킨다. 현재 Qdrant 는 원본 케이스로 값을 저장하므로 brand 를 lowercasing 하지 않는다
         * (ES adapter 는 `keyword_lower` normalizer 때문에 반대로 동작) —
         * 두 스토어 간 payload 케이스 정책 통일은 별도 작업.
         */
        fun from(filters: SearchFilters): VectorFilter? {
            if (!filters.hasAny) return null
            val clauses = listOfNotNull(
                filters.categoryL1?.let { Term("category_l1", it) },
                filters.brand?.let { Term("brand", it) },
                priceRange(filters.priceMin, filters.priceMax),
            )
            return when (clauses.size) {
                0 -> null
                1 -> clauses.single()
                else -> And(clauses)
            }
        }

        private fun priceRange(min: Long?, max: Long?): Range? =
            if (min == null && max == null) null else Range("price", min, max)
    }
}
