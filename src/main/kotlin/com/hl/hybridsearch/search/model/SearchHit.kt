package com.hl.hybridsearch.search.model

data class SearchHit(
    val docId: String,
    val score: Double,
    val source: Source,
    val payload: Map<String, Any?> = emptyMap(),
) {
    enum class Source { LEXICAL, VECTOR, FUSED }
}
