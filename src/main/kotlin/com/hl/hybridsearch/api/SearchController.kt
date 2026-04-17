package com.hl.hybridsearch.api

import com.hl.hybridsearch.search.SearchService
import com.hl.hybridsearch.search.model.SearchRequest
import com.hl.hybridsearch.search.model.SearchResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/search")
class SearchController(
    private val searchService: SearchService,
) {
    @GetMapping
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): SearchResponse = searchService.search(SearchRequest(q, page, size))
}
