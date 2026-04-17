package com.hl.hybridsearch.search

import com.hl.hybridsearch.search.model.SearchHit
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.SearchRequest as AiSearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Component

@Component
class VectorSearcher(
    private val vectorStore: VectorStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun search(query: String, topK: Int): List<SearchHit> {
        val request = AiSearchRequest.builder()
            .query(query)
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
}
