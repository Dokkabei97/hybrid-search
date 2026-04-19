package com.hl.hybridsearch.indexing.port

import com.hl.hybridsearch.indexing.BulkDeleteResult
import com.hl.hybridsearch.indexing.BulkWriteResult
import com.hl.hybridsearch.indexing.Product

/**
 * 렉시컬 스토어(ES) 쓰기 포트.
 * 테스트·Kafka 컨슈머 등 어떤 호출자든 이 포트만 알면 된다.
 */
interface LexicalWriter {
    fun upsert(products: List<Product>): BulkWriteResult
    fun delete(ids: List<String>): BulkDeleteResult
}
