package com.hl.hybridsearch.search.model

data class SearchRequest(
    val query: String,
    val page: Int = 0,
    val size: Int = 10,
    val filters: SearchFilters = SearchFilters.EMPTY,
) {
    init {
        require(page >= 0) { "page must be >= 0" }
        require(size in 1..100) { "size must be between 1 and 100" }
        require(query.isNotBlank()) { "query must not be blank" }
        require(query.length <= MAX_QUERY_LEN) { "query too long (max $MAX_QUERY_LEN)" }
    }

    companion object {
        const val MAX_QUERY_LEN = 512
    }
}
