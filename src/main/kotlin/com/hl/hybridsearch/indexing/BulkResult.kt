package com.hl.hybridsearch.indexing

/**
 * 한 번의 bulk upsert 결과. 스토어 어댑터 공용.
 */
data class BulkWriteResult(
    val succeeded: List<String>,
    val failed: List<FailedItem>,
    val tookMs: Long,
) {
    data class FailedItem(val id: String, val reason: String)

    val isAllSuccess: Boolean get() = failed.isEmpty()
    val total: Int get() = succeeded.size + failed.size

    companion object {
        fun empty() = BulkWriteResult(emptyList(), emptyList(), 0)
    }
}

data class BulkDeleteResult(
    val succeeded: List<String>,
    val failed: List<BulkWriteResult.FailedItem>,
    val tookMs: Long,
) {
    val isAllSuccess: Boolean get() = failed.isEmpty()

    companion object {
        fun empty() = BulkDeleteResult(emptyList(), emptyList(), 0)
    }
}

/**
 * 두 스토어(렉시컬 + 벡터)의 dual-write 결과를 묶은 배치 결과.
 */
data class BulkIndexResult(
    val lexical: BulkWriteResult,
    val vector: BulkWriteResult,
    val tookMs: Long,
) {
    /** 두 스토어 모두에 성공적으로 써진 id 목록. */
    val consistent: List<String>
        get() = lexical.succeeded.toSet().intersect(vector.succeeded.toSet()).toList()

    /** 렉시컬만 성공하고 벡터가 실패해 정합성이 깨진 id (reconciliation 대상). */
    val orphanedInLexical: List<String>
        get() = lexical.succeeded.toSet().minus(vector.succeeded.toSet()).toList()

    /** 벡터만 성공하고 렉시컬이 실패한 id (드물지만 로그 대상). */
    val orphanedInVector: List<String>
        get() = vector.succeeded.toSet().minus(lexical.succeeded.toSet()).toList()

    val isAllSuccess: Boolean
        get() = lexical.isAllSuccess && vector.isAllSuccess

    companion object {
        fun merge(batches: List<BulkIndexResult>): BulkIndexResult {
            val lex = BulkWriteResult(
                succeeded = batches.flatMap { it.lexical.succeeded },
                failed = batches.flatMap { it.lexical.failed },
                tookMs = batches.sumOf { it.lexical.tookMs },
            )
            val vec = BulkWriteResult(
                succeeded = batches.flatMap { it.vector.succeeded },
                failed = batches.flatMap { it.vector.failed },
                tookMs = batches.sumOf { it.vector.tookMs },
            )
            return BulkIndexResult(lex, vec, batches.sumOf { it.tookMs })
        }
    }
}
