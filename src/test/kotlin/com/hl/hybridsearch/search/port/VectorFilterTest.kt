package com.hl.hybridsearch.search.port

import com.hl.hybridsearch.search.model.SearchFilters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VectorFilterTest {

    @Test
    fun `empty filters produce null`() {
        assertNull(VectorFilter.from(SearchFilters.EMPTY))
    }

    @Test
    fun `single category produces single Term (no And wrapper)`() {
        val f = VectorFilter.from(SearchFilters(categoryL1 = "패션의류"))
        assertEquals(VectorFilter.Term("category_l1", "패션의류"), f)
    }

    @Test
    fun `brand preserves original case`() {
        // Qdrant payload 는 원본 케이스로 저장됨 — ES keyword_lower 와 비대칭
        val f = VectorFilter.from(SearchFilters(brand = "H&M"))
        assertEquals(VectorFilter.Term("brand", "H&M"), f)
    }

    @Test
    fun `price min only produces Range with only gte`() {
        val f = VectorFilter.from(SearchFilters(priceMin = 10_000L))
        assertEquals(VectorFilter.Range("price", gte = 10_000L, lte = null), f)
    }

    @Test
    fun `multiple clauses wrap with And`() {
        val f = VectorFilter.from(SearchFilters(categoryL1 = "뷰티", brand = "코스", priceMax = 50_000))
        assertTrue(f is VectorFilter.And)
        assertEquals(3, f.clauses.size)
    }

    @Test
    fun `range with no bounds rejected`() {
        assertFailsWith<IllegalArgumentException> {
            VectorFilter.Range("price")
        }
    }

    @Test
    fun `and with no clauses rejected`() {
        assertFailsWith<IllegalArgumentException> {
            VectorFilter.And(emptyList())
        }
    }
}
