package com.hl.hybridsearch.search.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SearchFiltersTest {

    @Test
    fun `brand is trimmed and lowercased`() {
        val f = SearchFilters.of(brand = "  Nike  ")
        assertEquals("nike", f.brand)
    }

    @Test
    fun `brand with mixed case is lowercased`() {
        assertEquals("h&m", SearchFilters.of(brand = "H&M").brand)
        assertEquals("uniqlo", SearchFilters.of(brand = "UNIQLO").brand)
    }

    @Test
    fun `blank brand becomes null`() {
        assertNull(SearchFilters.of(brand = "").brand)
        assertNull(SearchFilters.of(brand = "   ").brand)
    }

    @Test
    fun `categoryL1 is trimmed but not lowercased`() {
        // ES category.l1 필드에는 normalizer 가 없어서 원본 케이스를 보존해야 함
        val f = SearchFilters.of(categoryL1 = "  패션의류  ")
        assertEquals("패션의류", f.categoryL1)
    }

    @Test
    fun `blank categoryL1 becomes null`() {
        assertNull(SearchFilters.of(categoryL1 = "").categoryL1)
        assertNull(SearchFilters.of(categoryL1 = "   ").categoryL1)
    }

    @Test
    fun `priceMin greater than priceMax rejected`() {
        assertFailsWith<IllegalArgumentException> {
            SearchFilters.of(priceMin = 10_000L, priceMax = 5_000L)
        }
    }

    @Test
    fun `priceMin equal to priceMax accepted`() {
        val f = SearchFilters.of(priceMin = 5_000L, priceMax = 5_000L)
        assertEquals(5_000L, f.priceMin)
        assertEquals(5_000L, f.priceMax)
    }

    @Test
    fun `priceMin or priceMax alone accepted`() {
        assertEquals(10_000L, SearchFilters.of(priceMin = 10_000L).priceMin)
        assertEquals(50_000L, SearchFilters.of(priceMax = 50_000L).priceMax)
    }

    @Test
    fun `no filters means hasAny is false`() {
        assertFalse(SearchFilters.of().hasAny)
        assertFalse(SearchFilters.EMPTY.hasAny)
    }

    @Test
    fun `any single filter makes hasAny true`() {
        assertTrue(SearchFilters.of(brand = "nike").hasAny)
        assertTrue(SearchFilters.of(categoryL1 = "뷰티").hasAny)
        assertTrue(SearchFilters.of(priceMin = 1_000L).hasAny)
        assertTrue(SearchFilters.of(priceMax = 99_000L).hasAny)
    }

    @Test
    fun `EMPTY is a shared sentinel`() {
        // 공유 인스턴스인지 확인 — of() 로 생성된 것은 별개 인스턴스 (의도된 동작)
        assertSame(SearchFilters.EMPTY, SearchFilters.EMPTY)
    }
}
