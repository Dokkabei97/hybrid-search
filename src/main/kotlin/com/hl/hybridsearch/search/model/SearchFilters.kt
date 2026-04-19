package com.hl.hybridsearch.search.model

/**
 * 검색 필터. ES `bool.filter` 절로 내려간다 — 점수 영향 없음.
 */
data class SearchFilters(
    val categoryL1: String? = null,
    val brand: String? = null,
    val priceMin: Long? = null,
    val priceMax: Long? = null,
) {
    val hasAny: Boolean
        get() = categoryL1 != null || brand != null || priceMin != null || priceMax != null

    companion object {
        val EMPTY = SearchFilters()
    }
}
