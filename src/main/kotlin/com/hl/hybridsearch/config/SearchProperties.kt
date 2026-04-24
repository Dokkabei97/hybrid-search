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
    val vector: VectorProperties = VectorProperties(),
) {
    /**
     * 벡터 어댑터 튜닝 파라미터. 벤더 특화 값이지만 포트 시그니처엔 안 드러내고
     * 어댑터가 [com.hl.hybridsearch.search.port.VectorSearchMode] 를 매핑할 때 참조한다.
     *
     * Qdrant 매핑:
     *   FAST     → SearchParams(hnsw_ef = [fastHnswEf])
     *   ACCURATE → SearchParams(hnsw_ef = [accurateHnswEf])
     *   EXACT    → SearchParams(exact = true)  — brute-force, 평가 전용
     */
    data class VectorProperties(
        val collectionName: String = "products",
        val fastHnswEf: Int = 64,
        val accurateHnswEf: Int = 256,
    )

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
        val queryCache: QueryCacheProperties = QueryCacheProperties(),
    )

    /**
     * 쿼리 임베딩 캐시 설정 (Caffeine 기반, in-process).
     *
     * 이커머스 쿼리는 소수 핫 쿼리가 트래픽의 대부분 → 임베딩 호출 p95 급감 기대.
     * 메모리 비용: FloatArray(dimension) × maxSize. Gemma 768d × 10k ≈ 30KB × 10k = 30MB
     * 만 아니라, 실제로는 FloatArray 4 bytes × 768 × 10k ≈ 30MB 수준 (float 배열 오버헤드 무시).
     *
     * 주의: FloatArray 는 가변 배열이지만 이 프로젝트의 소비 경로 (VectorSearcher → Qdrant adapter)
     * 는 `.toList()` 로 복사 후 gRPC 에 전달하므로 캐시 공유 인스턴스 mutation 없음.
     */
    data class QueryCacheProperties(
        val enabled: Boolean = true,
        val maxSize: Long = 10_000,
        val ttlMinutes: Long = 60,
    )

    data class FallbackProperties(
        val lexicalOnVectorFailure: Boolean = true,
    )
}
