package com.hl.hybridsearch.api

import com.hl.hybridsearch.indexing.Document
import com.hl.hybridsearch.indexing.IndexingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/index")
class IndexingController(
    private val indexingService: IndexingService,
) {
    @PostMapping
    fun index(@RequestBody doc: Document): ResponseEntity<Map<String, String>> {
        indexingService.index(doc)
        return ResponseEntity.ok(mapOf("id" to doc.id, "status" to "indexed"))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        indexingService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
