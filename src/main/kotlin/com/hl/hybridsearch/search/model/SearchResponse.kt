package com.hl.hybridsearch.search.model

import com.hl.hybridsearch.analyzer.QueryType

data class SearchResponse(
    val queryType: QueryType,
    val total: Int,
    val page: Int,
    val size: Int,
    val hits: List<SearchHit>,
    val degraded: Boolean = false,
    val degradeReason: String? = null,
)
