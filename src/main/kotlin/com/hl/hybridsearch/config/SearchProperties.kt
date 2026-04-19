package com.hl.hybridsearch.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "search")
data class SearchProperties(
    val indexName: String = "products",
    val indexTemplateName: String = "products-template",
    val classifier: ClassifierProperties = ClassifierProperties(),
    val rrf: RrfProperties = RrfProperties(),
    val topK: TopKProperties = TopKProperties(),
    val embedding: EmbeddingProperties = EmbeddingProperties(),
    val fallback: FallbackProperties = FallbackProperties(),
    val indexing: IndexingProperties = IndexingProperties(),
) {
    data class IndexingProperties(
        val batchSize: Int = 500,
        val concurrency: Int = 4,
        val maxRetries: Int = 3,
        val initialBackoffMs: Long = 200,
        val maxBackoffMs: Long = 5_000,
    )

    data class ClassifierProperties(
        val maxKeywordTokens: Int = 5,
        val nounRatioThreshold: Double = 0.5,
        val maxParticlesForKeyword: Int = 2,
    )

    data class RrfProperties(
        val k: Int = 60,
        val titleWeight: Double = 1.0,
        val bodyWeight: Double = 1.0,
        val vectorWeight: Double = 1.0,
    )

    data class TopKProperties(
        val lexical: Int = 50,
        val vector: Int = 50,
    )

    data class EmbeddingProperties(
        val dimension: Int = 768,
        val maxChars: Int = 1000,
        /**
         * 쿼리 쪽 텍스트 wrapping 템플릿. `{query}` 치환.
         * 빈 문자열이면 off (텍스트 그대로 전달 — OpenAI v3, Gemini, Cohere 등).
         * EmbeddingGemma: "task: search result | query: {query}"
         * Qwen3:         "Instruct: Given a Korean e-commerce query, retrieve relevant product listings.\nQuery: {query}"
         * Nomic v1.5:    "search_query: {query}"
         */
        val queryInstruction: String = "",
        /**
         * 문서 쪽 텍스트 wrapping 템플릿. `{title}`, `{text}` 치환.
         * 빈 문자열이면 off (title + text를 개행으로 연결).
         * EmbeddingGemma: "title: {title} | text: {text}"
         * Nomic v1.5:    "search_document: {text}"
         */
        val documentInstruction: String = "",
    )

    data class FallbackProperties(
        val lexicalOnVectorFailure: Boolean = true,
    )
}
