package com.hl.hybridsearch.api

import com.hl.hybridsearch.indexing.BulkIndexResult
import com.hl.hybridsearch.indexing.IndexingService
import com.hl.hybridsearch.indexing.Product
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/products")
class IndexingController(
    private val indexingService: IndexingService,
) {
    @PostMapping
    fun index(@RequestBody product: Product): ResponseEntity<BulkIndexResult> {
        val result = indexingService.index(product)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/bulk")
    fun bulkIndex(@RequestBody products: List<Product>): ResponseEntity<BulkIndexResult> {
        val result = indexingService.bulkIndex(products)
        val status = if (result.isAllSuccess) 200 else 207 // Multi-Status for partial success
        return ResponseEntity.status(status).body(result)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        indexingService.bulkDelete(listOf(id))
        return ResponseEntity.noContent().build()
    }
}
