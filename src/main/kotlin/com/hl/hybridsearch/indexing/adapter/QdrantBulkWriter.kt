package com.hl.hybridsearch.indexing.adapter

import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.indexing.BulkDeleteResult
import com.hl.hybridsearch.indexing.BulkWriteResult
import com.hl.hybridsearch.indexing.Product
import com.hl.hybridsearch.indexing.port.VectorWriter
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document as AiDocument
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Component

@Component
class QdrantBulkWriter(
    private val vectorStore: VectorStore,
    private val properties: SearchProperties,
) : VectorWriter {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun upsert(products: List<Product>): BulkWriteResult {
        if (products.isEmpty()) return BulkWriteResult.empty()
        val start = System.currentTimeMillis()

        val docs = products.map { toAiDocument(it) }
        return try {
            vectorStore.add(docs)
            BulkWriteResult(
                succeeded = products.map { it.id },
                failed = emptyList(),
                tookMs = System.currentTimeMillis() - start,
            )
        } catch (e: Exception) {
            // Spring AI VectorStore는 부분 성공 정보를 반환하지 않아 all-or-nothing 처리
            log.error("Qdrant bulk upsert failed for {} docs", products.size, e)
            BulkWriteResult(
                succeeded = emptyList(),
                failed = products.map {
                    BulkWriteResult.FailedItem(it.id, e.message ?: "qdrant upsert error")
                },
                tookMs = System.currentTimeMillis() - start,
            )
        }
    }

    override fun delete(ids: List<String>): BulkDeleteResult {
        if (ids.isEmpty()) return BulkDeleteResult.empty()
        val start = System.currentTimeMillis()
        return try {
            vectorStore.delete(ids)
            BulkDeleteResult(ids, emptyList(), System.currentTimeMillis() - start)
        } catch (e: Exception) {
            log.warn("Qdrant bulk delete failed for {} ids", ids.size, e)
            BulkDeleteResult(
                succeeded = emptyList(),
                failed = ids.map {
                    BulkWriteResult.FailedItem(it, e.message ?: "qdrant delete error")
                },
                tookMs = System.currentTimeMillis() - start,
            )
        }
    }

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
