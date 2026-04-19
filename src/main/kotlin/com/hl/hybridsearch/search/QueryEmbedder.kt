package com.hl.hybridsearch.search

import com.hl.hybridsearch.config.SearchProperties
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Component

/**
 * 쿼리 텍스트 → 임베딩 벡터 변환 책임을 [VectorSearcher] 로부터 분리.
 *
 * - 모델별 query instruction prefix 적용 (EmbeddingGemma, Qwen3, Nomic 등)
 * - `queryInstruction` 템플릿이 빈 문자열이면 raw query 그대로 (OpenAI v3, Cohere, Voyage)
 *
 * 이 컴포넌트는 Spring AI 의 [EmbeddingModel] 에만 의존한다. 벡터 스토어와 독립적이어서
 * Qdrant/Milvus 어느 쪽으로 백엔드를 바꿔도 재사용 가능.
 */
@Component
class QueryEmbedder(
    private val embeddingModel: EmbeddingModel,
    private val properties: SearchProperties,
) {

    fun embed(query: String): FloatArray {
        val wrapped = wrapQuery(query)
        return embeddingModel.embed(wrapped)
    }

    private fun wrapQuery(query: String): String {
        val template = properties.embedding.queryInstruction
        return if (template.isBlank()) query else template.replace("{query}", query)
    }
}
