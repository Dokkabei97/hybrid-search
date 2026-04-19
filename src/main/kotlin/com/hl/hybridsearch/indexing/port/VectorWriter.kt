package com.hl.hybridsearch.indexing.port

import com.hl.hybridsearch.indexing.BulkDeleteResult
import com.hl.hybridsearch.indexing.BulkWriteResult
import com.hl.hybridsearch.indexing.Product

/**
 * 벡터 스토어(Qdrant) 쓰기 포트.
 */
interface VectorWriter {
    fun upsert(products: List<Product>): BulkWriteResult
    fun delete(ids: List<String>): BulkDeleteResult
}
