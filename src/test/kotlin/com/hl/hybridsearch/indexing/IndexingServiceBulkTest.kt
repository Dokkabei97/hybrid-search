package com.hl.hybridsearch.indexing

import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.indexing.port.LexicalWriter
import com.hl.hybridsearch.indexing.port.VectorWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexingServiceBulkTest {

    @Test
    fun `all succeed in first attempt`() {
        val service = service(FakeLex(), FakeVec())
        val result = service.bulkIndex(products("1", "2", "3"))
        assertTrue(result.isAllSuccess)
        assertEquals(listOf("1", "2", "3"), result.consistent.sorted())
        assertEquals(emptyList(), result.orphanedInLexical)
    }

    @Test
    fun `transient lexical failure recovers after retry`() {
        val lex = FakeLex(transientFailures = mutableMapOf("2" to 1))
        val service = service(lex, FakeVec(), retries = 3)
        val result = service.bulkIndex(products("1", "2", "3"))
        assertTrue(result.isAllSuccess, "expected recovery: failed=${result.lexical.failed}")
        assertEquals(listOf("1", "2", "3"), result.consistent.sorted())
    }

    @Test
    fun `persistent lexical failure is reported after max retries`() {
        val lex = FakeLex(permanentFail = setOf("2"))
        val service = service(lex, FakeVec(), retries = 1)
        val result = service.bulkIndex(products("1", "2"))
        assertFalse(result.isAllSuccess)
        assertEquals(listOf("2"), result.lexical.failed.map { it.id })
        assertEquals(listOf("1"), result.consistent)
    }

    @Test
    fun `vector failure creates orphan in lexical`() {
        val vec = FakeVec(alwaysFail = true)
        val service = service(FakeLex(), vec, retries = 0)
        val result = service.bulkIndex(products("1", "2"))
        assertFalse(result.isAllSuccess)
        assertEquals(listOf("1", "2"), result.orphanedInLexical.sorted())
        assertEquals(emptyList(), result.consistent)
    }

    @Test
    fun `empty input returns empty result`() {
        val result = service(FakeLex(), FakeVec()).bulkIndex(emptyList())
        assertTrue(result.isAllSuccess)
        assertEquals(0, result.lexical.succeeded.size)
    }

    @Test
    fun `batching splits large input by batchSize`() {
        val lex = FakeLex()
        val service = service(lex, FakeVec(), batchSize = 2, retries = 0)
        service.bulkIndex(products("1", "2", "3", "4", "5"))
        assertEquals(3, lex.invocationCount, "expected 3 batches for 5 items with batchSize=2")
    }

    private fun service(
        lex: LexicalWriter,
        vec: VectorWriter,
        batchSize: Int = 10,
        retries: Int = 3,
    ): IndexingService {
        val props = SearchProperties(
            indexing = SearchProperties.IndexingProperties(
                batchSize = batchSize,
                concurrency = 2,
                maxRetries = retries,
                initialBackoffMs = 1,
                maxBackoffMs = 10,
            )
        )
        return IndexingService(lex, vec, props)
    }

    private fun products(vararg ids: String): List<Product> =
        ids.map { Product(id = it, title = "p$it") }

    private class FakeLex(
        val permanentFail: Set<String> = emptySet(),
        val transientFailures: MutableMap<String, Int> = mutableMapOf(),
    ) : LexicalWriter {
        var invocationCount = 0

        override fun upsert(products: List<Product>): BulkWriteResult {
            invocationCount++
            val succeeded = mutableListOf<String>()
            val failed = mutableListOf<BulkWriteResult.FailedItem>()
            for (p in products) {
                val remaining = transientFailures[p.id] ?: 0
                when {
                    p.id in permanentFail -> failed.add(BulkWriteResult.FailedItem(p.id, "permanent"))
                    remaining > 0 -> {
                        transientFailures[p.id] = remaining - 1
                        failed.add(BulkWriteResult.FailedItem(p.id, "transient"))
                    }
                    else -> succeeded.add(p.id)
                }
            }
            return BulkWriteResult(succeeded, failed, 1)
        }

        override fun delete(ids: List<String>) = BulkDeleteResult(ids, emptyList(), 1)
    }

    private class FakeVec(
        val alwaysFail: Boolean = false,
    ) : VectorWriter {
        override fun upsert(products: List<Product>): BulkWriteResult =
            if (alwaysFail) {
                BulkWriteResult(
                    succeeded = emptyList(),
                    failed = products.map { BulkWriteResult.FailedItem(it.id, "vector down") },
                    tookMs = 1,
                )
            } else {
                BulkWriteResult(products.map { it.id }, emptyList(), 1)
            }

        override fun delete(ids: List<String>) = BulkDeleteResult(ids, emptyList(), 1)
    }
}
