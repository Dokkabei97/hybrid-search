package com.hl.hybridsearch.indexing

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.hl.hybridsearch.config.SearchProperties
import java.time.Instant
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

    fun index(product: Product) {
        val esDoc = toEsDocument(product)
        esClient.index { r ->
            r.index(properties.indexName)
                .id(product.id)
                .document(esDoc)
        }
        log.debug("Indexed '{}' into ES index='{}'", product.id, properties.indexName)

        try {
            vectorStore.add(listOf(toAiDocument(product)))
            log.debug("Upserted '{}' into Qdrant", product.id)
        } catch (e: Exception) {
            log.error("Qdrant upsert failed for '{}', rolling back ES", product.id, e)
            runCatching {
                esClient.delete { r -> r.index(properties.indexName).id(product.id) }
            }.onFailure {
                log.error("ES rollback delete also failed for '{}'. Orphan ES doc!", product.id, it)
            }
            throw IndexingException("Dual-write failed for ${product.id}", e)
        }
    }

    fun delete(id: String) {
        runCatching { esClient.delete { r -> r.index(properties.indexName).id(id) } }
            .onFailure { log.warn("ES delete failed for {}", id, it) }
        runCatching { vectorStore.delete(listOf(id)) }
            .onFailure { log.warn("Qdrant delete failed for {}", id, it) }
    }

    private fun toEsDocument(p: Product): Map<String, Any?> = mapOf(
        "id" to p.id,
        "title" to p.title,
        "brand" to p.brand,
        "category" to mapOf(
            "path" to p.category.path,
            "l1" to p.category.l1,
            "l2" to p.category.l2,
            "l3" to p.category.l3,
        ),
        "description" to p.description,
        "tags" to p.tags,
        "price" to p.price,
        "rating" to p.rating,
        "reviewCount" to p.reviewCount,
        "attributes" to p.attributes,
        "createdAt" to Instant.now().toString(),
        "updatedAt" to Instant.now().toString(),
    )

    private fun toAiDocument(p: Product): AiDocument {
        val payload = mutableMapOf<String, Any>(
            "doc_id" to p.id,
            "title" to p.title,
            "brand" to p.brand,
            "category_path" to p.category.path,
            "category_l1" to p.category.l1,
            "price" to p.price,
        )
        if (p.tags.isNotEmpty()) payload["tags"] = p.tags
        return AiDocument.builder()
            .id(p.id)
            .text(p.embeddingText(properties.embedding.maxChars))
            .metadata(payload)
            .build()
    }
}

class IndexingException(message: String, cause: Throwable) : RuntimeException(message, cause)
