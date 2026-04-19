package com.hl.hybridsearch.search

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import co.elastic.clients.elasticsearch.core.SearchRequest
import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.search.model.SearchHit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LexicalSearcher(
    private val client: ElasticsearchClient,
    private val properties: SearchProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun search(query: String, topK: Int): List<SearchHit> {
        val request = SearchRequest.Builder()
            .index(properties.indexName)
            .size(topK)
            .query { q ->
                q.multiMatch { mm ->
                    mm.query(query)
                        .fields("title^3", "brand.text^2", "description")
                        .type(TextQueryType.BestFields)
                        .tieBreaker(0.3)
                        .minimumShouldMatch("2<75%")
                }
            }
            .build()

        val response = client.search(request, Map::class.java)

        return response.hits().hits().map { hit ->
            @Suppress("UNCHECKED_CAST")
            val payload = (hit.source() as? Map<String, Any?>).orEmpty()
            SearchHit(
                docId = hit.id() ?: error("ES hit without id"),
                score = hit.score() ?: 0.0,
                source = SearchHit.Source.LEXICAL,
                payload = payload,
            )
        }.also { log.debug("Lexical search returned {} hits", it.size) }
    }
}
