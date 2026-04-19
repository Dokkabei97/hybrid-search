package com.hl.hybridsearch.search

import com.hl.hybridsearch.search.model.SearchHit
import org.springframework.stereotype.Component

@Component
class Pager {

    fun slice(hits: List<SearchHit>, page: Int, size: Int): List<SearchHit> {
        val from = page * size
        if (from >= hits.size) return emptyList()
        val to = (from + size).coerceAtMost(hits.size)
        return hits.subList(from, to)
    }
}
