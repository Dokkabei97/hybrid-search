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
        val f = VectorFilter.from(SearchFilters.of(categoryL1 = "패션의류"))
        assertEquals(VectorFilter.Term("category_l1", "패션의류"), f)
    }

    @Test
    fun `brand is canonicalized to lowercase (matches ES keyword_lower)`() {
        // SearchFilters.of 가 "H&M" → "h&m" 으로 정규화 → Qdrant 필터도 동일값
        val f = VectorFilter.from(SearchFilters.of(brand = "H&M"))
        assertEquals(VectorFilter.Term("brand", "h&m"), f)
    }

    @Test
    fun `price min only produces Range with only gte`() {
        val f = VectorFilter.from(SearchFilters.of(priceMin = 10_000L))
        assertEquals(VectorFilter.Range("price", gte = 10_000L, lte = null), f)
    }

    @Test
    fun `multiple clauses wrap with And`() {
        val f = VectorFilter.from(
            SearchFilters.of(categoryL1 = "뷰티", brand = "코스", priceMax = 50_000)
        )
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
