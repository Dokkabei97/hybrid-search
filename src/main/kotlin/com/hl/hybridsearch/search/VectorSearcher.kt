package com.hl.hybridsearch.search

import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.search.model.SearchHit
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.SearchRequest as AiSearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Component

@Component
class VectorSearcher(
    private val vectorStore: VectorStore,
    private val properties: SearchProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun search(query: String, topK: Int): List<SearchHit> {
        val wrapped = wrapQuery(query)
        val request = AiSearchRequest.builder()
            .query(wrapped)
            .topK(topK)
            .build()

        val docs = vectorStore.similaritySearch(request) ?: emptyList()

        return docs.map { doc ->
            SearchHit(
                docId = doc.id,
                score = doc.score ?: 0.0,
                source = SearchHit.Source.VECTOR,
                payload = doc.metadata,
            )
        }.also { log.debug("Vector search returned {} hits", it.size) }
    }

    /**
     * 모델별 query instruction wrapping. 빈 문자열이면 off.
     * EmbeddingGemma, Qwen3, Nomic 처럼 텍스트에 prefix 를 요구하는 모델만 설정값을 채우고,
     * OpenAI v3 / Gemini / Cohere / Voyage 는 `queryInstruction=""` 로 두어 raw query 가 그대로 전달되게 한다.
     */
    private fun wrapQuery(query: String): String {
        val template = properties.embedding.queryInstruction
        return if (template.isBlank()) query else template.replace("{query}", query)
    }
}
