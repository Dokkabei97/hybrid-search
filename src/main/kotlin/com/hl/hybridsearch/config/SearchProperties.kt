package com.hl.hybridsearch.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "search")
data class SearchProperties(
    val indexName: String = "documents",
    val classifier: ClassifierProperties = ClassifierProperties(),
    val rrf: RrfProperties = RrfProperties(),
    val topK: TopKProperties = TopKProperties(),
    val embedding: EmbeddingProperties = EmbeddingProperties(),
    val fallback: FallbackProperties = FallbackProperties(),
) {
    data class ClassifierProperties(
        val maxKeywordTokens: Int = 5,
        val nounRatioThreshold: Double = 0.5,
        val maxParticlesForKeyword: Int = 2,
    )

    data class RrfProperties(
        val k: Int = 60,
    )

    data class TopKProperties(
        val lexical: Int = 50,
        val vector: Int = 50,
    )

    data class EmbeddingProperties(
        val dimension: Int = 1024,
    )

    data class FallbackProperties(
        val lexicalOnVectorFailure: Boolean = true,
    )
}
