package com.hl.hybridsearch.indexing

data class Document(
    val id: String,
    val title: String,
    val body: String,
    val tags: List<String> = emptyList(),
) {
    fun embeddingText(): String = "$title\n\n$body"
}
