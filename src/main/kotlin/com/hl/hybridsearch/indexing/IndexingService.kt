package com.hl.hybridsearch.indexing

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.hl.hybridsearch.config.SearchProperties
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document as AiDocument
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

@Service
class IndexingService(
    private val esClient: ElasticsearchClient,
    private val vectorStore: VectorStore,
    private val properties: SearchProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun index(doc: Document) {
        val esDoc = mapOf(
            "id" to doc.id,
            "title" to doc.title,
            "body" to doc.body,
            "tags" to doc.tags,
        )
        esClient.index { r ->
            r.index(properties.indexName)
                .id(doc.id)
                .document(esDoc)
        }
        log.debug("Indexed '{}' into ES", doc.id)

        try {
            val aiDoc = AiDocument.builder()
                .id(doc.id)
                .text(doc.embeddingText())
                .metadata(
                    mapOf(
                        "doc_id" to doc.id,
                        "title" to doc.title,
                        "tags" to doc.tags,
                    )
                )
                .build()
            vectorStore.add(listOf(aiDoc))
            log.debug("Upserted '{}' into Qdrant", doc.id)
        } catch (e: Exception) {
            log.error("Qdrant upsert failed for '{}', rolling back ES", doc.id, e)
            runCatching {
                esClient.delete { r -> r.index(properties.indexName).id(doc.id) }
            }
            throw IndexingException("Dual-write failed for ${doc.id}", e)
        }
    }

    fun delete(id: String) {
        runCatching { esClient.delete { r -> r.index(properties.indexName).id(id) } }
            .onFailure { log.warn("ES delete failed for {}", id, it) }
        runCatching { vectorStore.delete(listOf(id)) }
            .onFailure { log.warn("Qdrant delete failed for {}", id, it) }
    }
}

class IndexingException(message: String, cause: Throwable) : RuntimeException(message, cause)
