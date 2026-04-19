package com.hl.hybridsearch.search.model

import com.hl.hybridsearch.analyzer.QueryType

data class SearchResponse(
    val queryType: QueryType,
    val total: Int,
    val page: Int,
    val size: Int,
    val hits: List<SearchHit>,
    val sources: SourceHealth,
    val degradeReason: String? = null,
) {
    val degraded: Boolean
        get() = sources.lexical == SourceHealth.Status.FAILED ||
            sources.vector == SourceHealth.Status.FAILED
}
