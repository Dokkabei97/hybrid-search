package com.hl.hybridsearch.indexing.adapter

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.BulkRequest
import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.indexing.BulkDeleteResult
import com.hl.hybridsearch.indexing.BulkWriteResult
import com.hl.hybridsearch.indexing.Product
import com.hl.hybridsearch.indexing.port.LexicalWriter
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ElasticsearchBulkWriter(
    private val client: ElasticsearchClient,
    private val properties: SearchProperties,
) : LexicalWriter {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun upsert(products: List<Product>): BulkWriteResult {
        if (products.isEmpty()) return BulkWriteResult.empty()
        val start = System.currentTimeMillis()

        val request = BulkRequest.Builder().apply {
            products.forEach { p ->
                operations { op ->
                    op.index { idx ->
                        idx.index(properties.indexName)
                            .id(p.id)
                            .document(toEsDocument(p))
                    }
                }
            }
        }.build()

        val response = client.bulk(request)
        val took = System.currentTimeMillis() - start

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<BulkWriteResult.FailedItem>()
        for (item in response.items()) {
            val id = item.id() ?: ""
            val err = item.error()
            if (err != null) {
                failed.add(BulkWriteResult.FailedItem(id, err.reason() ?: "unknown"))
            } else {
                succeeded.add(id)
            }
        }
        if (failed.isNotEmpty()) {
            log.warn("ES bulk upsert partial failure: {}/{} failed", failed.size, products.size)
        }
        return BulkWriteResult(succeeded, failed, took)
    }

    override fun delete(ids: List<String>): BulkDeleteResult {
        if (ids.isEmpty()) return BulkDeleteResult.empty()
        val start = System.currentTimeMillis()

        val request = BulkRequest.Builder().apply {
            ids.forEach { id ->
                operations { op ->
                    op.delete { d -> d.index(properties.indexName).id(id) }
                }
            }
        }.build()

        val response = client.bulk(request)
        val took = System.currentTimeMillis() - start

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<BulkWriteResult.FailedItem>()
        for (item in response.items()) {
            val id = item.id() ?: ""
            val err = item.error()
            // 404(not found)는 실패로 처리하지 않음 — 이미 지워진 문서
            when {
                err == null -> succeeded.add(id)
                item.status() == 404 -> succeeded.add(id)
                else -> failed.add(BulkWriteResult.FailedItem(id, err.reason() ?: "unknown"))
            }
        }
        return BulkDeleteResult(succeeded, failed, took)
    }

    private fun toEsDocument(p: Product): Map<String, Any?> {
        val now = Instant.now().toString()
        return mapOf(
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
            "createdAt" to now,
            "updatedAt" to now,
        )
    }
}
