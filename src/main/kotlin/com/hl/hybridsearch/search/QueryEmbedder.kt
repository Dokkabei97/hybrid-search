package com.hl.hybridsearch.search

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.hl.hybridsearch.config.SearchProperties
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Component

/**
 * 쿼리 텍스트 → 임베딩 벡터 변환 책임을 [VectorSearcher] 로부터 분리.
 *
 * - 모델별 query instruction prefix 적용 (EmbeddingGemma, Qwen3, Nomic 등)
 * - `queryInstruction` 템플릿이 빈 문자열이면 raw query 그대로 (OpenAI v3, Cohere, Voyage)
 * - in-process 캐시(Caffeine)로 반복 쿼리의 임베딩 호출을 제거
 *
 * 이 컴포넌트는 Spring AI 의 [EmbeddingModel] 에만 의존한다. 벡터 스토어와 독립적이어서
 * Qdrant/Milvus 어느 쪽으로 백엔드를 바꿔도 재사용 가능.
 *
 * 캐시 키는 **wrapped query** (템플릿 적용 후 문자열) 를 사용한다 —
 * 같은 raw query 라도 `queryInstruction` 설정이 바뀌면 별도 엔트리로 인식된다.
 */
@Component
class QueryEmbedder(
    private val embeddingModel: EmbeddingModel,
    private val properties: SearchProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val cache: Cache<String, FloatArray>? = buildCache()

    fun embed(query: String): FloatArray {
        val wrapped = wrapQuery(query)
        val c = cache ?: return embeddingModel.embed(wrapped)
        return c.get(wrapped) { key ->
            log.debug("Query embedding cache miss (key hash={})", key.hashCode())
            embeddingModel.embed(key)
        }!!
    }

    private fun wrapQuery(query: String): String {
        val template = properties.embedding.queryInstruction
        return if (template.isBlank()) query else template.replace("{query}", query)
    }

    private fun buildCache(): Cache<String, FloatArray>? {
        val cfg = properties.embedding.queryCache
        if (!cfg.enabled || cfg.maxSize <= 0) {
            log.info("Query embedding cache disabled (enabled={}, maxSize={})", cfg.enabled, cfg.maxSize)
            return null
        }
        log.info(
            "Query embedding cache enabled (maxSize={}, ttl={}min)",
            cfg.maxSize, cfg.ttlMinutes,
        )
        return Caffeine.newBuilder()
            .maximumSize(cfg.maxSize)
            .expireAfterWrite(Duration.ofMinutes(cfg.ttlMinutes))
            .recordStats()
            .build()
    }
}
