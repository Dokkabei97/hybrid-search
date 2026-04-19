package com.hl.hybridsearch.indexing

import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.indexing.port.LexicalWriter
import com.hl.hybridsearch.indexing.port.VectorWriter
import com.hl.hybridsearch.observability.SearchMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Dual-store 인덱싱 코디네이터.
 *
 * 동작:
 *  1) 입력 Product 리스트를 batchSize로 청킹
 *  2) 청크를 Semaphore(concurrency)로 제한된 병렬 실행
 *  3) 각 청크: ES bulk → 성공 id만 Qdrant bulk (불일치 최소화)
 *  4) 배치 실패 시 지수 백오프 재시도 (maxRetries)
 *  5) 결과는 BulkIndexResult로 집계 (orphan id 노출)
 *
 * Kafka-ready: `bulkIndex(products)` 포트를 컨슈머에서도 동일하게 호출하면 된다.
 */
@Service
class IndexingService(
    private val lexical: LexicalWriter,
    private val vector: VectorWriter,
    private val properties: SearchProperties,
    private val metrics: SearchMetrics? = null,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun index(product: Product): BulkIndexResult = bulkIndex(listOf(product))

    fun bulkIndex(products: List<Product>): BulkIndexResult {
        if (products.isEmpty()) {
            return BulkIndexResult(BulkWriteResult.empty(), BulkWriteResult.empty(), 0)
        }

        val start = System.currentTimeMillis()
        val cfg = properties.indexing
        val batches = products.chunked(cfg.batchSize)
        log.info(
            "Bulk indexing {} products in {} batches (batchSize={}, concurrency={})",
            products.size, batches.size, cfg.batchSize, cfg.concurrency
        )

        val results = runBlocking {
            val sem = Semaphore(cfg.concurrency)
            batches.map { batch ->
                async(Dispatchers.IO) {
                    sem.withPermit { processBatchWithRetry(batch) }
                }
            }.awaitAll()
        }

        val merged = BulkIndexResult.merge(results)
        val total = System.currentTimeMillis() - start
        log.info(
            "Bulk indexing done in {}ms: lex_ok={}, lex_fail={}, vec_ok={}, vec_fail={}, orphans_in_lex={}",
            total,
            merged.lexical.succeeded.size, merged.lexical.failed.size,
            merged.vector.succeeded.size, merged.vector.failed.size,
            merged.orphanedInLexical.size,
        )
        return merged.copy(tookMs = total)
    }

    fun bulkDelete(ids: List<String>): Pair<BulkDeleteResult, BulkDeleteResult> {
        if (ids.isEmpty()) return BulkDeleteResult.empty() to BulkDeleteResult.empty()
        val lex = lexical.delete(ids)
        val vec = vector.delete(ids)
        if (lex.failed.isNotEmpty() || vec.failed.isNotEmpty()) {
            log.warn(
                "Bulk delete partial failure: lex_failed={}, vec_failed={}",
                lex.failed.size, vec.failed.size,
            )
        }
        return lex to vec
    }

    private suspend fun processBatchWithRetry(batch: List<Product>): BulkIndexResult {
        val cfg = properties.indexing
        var attempt = 0
        var backoffMs = cfg.initialBackoffMs

        while (true) {
            val result = processBatch(batch)
            if (result.isAllSuccess || attempt >= cfg.maxRetries) return result

            val retryIds = (result.lexical.failed + result.vector.failed).map { it.id }.toSet()
            val retryBatch = batch.filter { it.id in retryIds }
            if (retryBatch.isEmpty()) return result

            log.warn(
                "Batch attempt {}/{} failed for {} items, backing off {}ms",
                attempt + 1, cfg.maxRetries, retryBatch.size, backoffMs,
            )
            delay(backoffMs)
            attempt++
            backoffMs = (backoffMs * 2).coerceAtMost(cfg.maxBackoffMs)
        }
    }

    private fun processBatch(batch: List<Product>): BulkIndexResult {
        val start = System.currentTimeMillis()
        val lexRes = lexical.upsert(batch)
        recordBulk(SearchMetrics.Source.ES_BULK, lexRes.isAllSuccess, lexRes.tookMs)

        // ES에 성공한 항목만 벡터에 올려 inconsistency 최소화
        val vectorCandidates = batch.filter { it.id in lexRes.succeeded }
        val vecRes = vector.upsert(vectorCandidates)
        recordBulk(SearchMetrics.Source.QDRANT_BULK, vecRes.isAllSuccess, vecRes.tookMs)

        val result = BulkIndexResult(lexRes, vecRes, System.currentTimeMillis() - start)
        metrics?.recordOrphan(result.orphanedInLexical.size)
        return result
    }

    private fun recordBulk(source: SearchMetrics.Source, ok: Boolean, tookMs: Long) {
        val status = if (ok) SearchMetrics.Status.OK else SearchMetrics.Status.FAILED
        metrics?.recordIndexingChannel(source, status, tookMs)
    }
}
