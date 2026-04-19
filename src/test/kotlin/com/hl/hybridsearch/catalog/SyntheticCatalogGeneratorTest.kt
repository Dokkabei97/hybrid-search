package com.hl.hybridsearch.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SyntheticCatalogGeneratorTest {

    private val generator = SyntheticCatalogGenerator()

    @Test
    fun `generates requested total and distributes across categories`() {
        val total = 1_000
        val products = generator.generate(total).toList()
        assertEquals(total, products.size)
        val byL1 = products.groupingBy { it.category.l1 }.eachCount()
        assertEquals(CatalogTemplates.all.size, byL1.size)
        // 각 카테고리가 최소 90건 이상 (1000/10=100 기준 근사)
        assertTrue(byL1.values.all { it >= 90 }, "uneven distribution: $byL1")
    }

    @Test
    fun `same seed produces identical catalog`() {
        val a = generator.generate(200, seed = 123L).map { it.id to it.title }.toList()
        val b = generator.generate(200, seed = 123L).map { it.id to it.title }.toList()
        assertEquals(a, b)
    }

    @Test
    fun `different seeds produce different catalogs`() {
        val a = generator.generate(200, seed = 1L).take(10).map { it.id }.toList()
        val b = generator.generate(200, seed = 2L).take(10).map { it.id }.toList()
        assertNotEquals(a, b)
    }

    @Test
    fun `brand distribution is skewed Pareto-style`() {
        val products = generator.generate(5_000, seed = 42L).toList()
        val fashion = products.filter { it.category.l1 == "패션의류" }
        val byBrand = fashion.groupingBy { it.brand }.eachCount()
        val sorted = byBrand.values.sortedDescending()
        val top2 = sorted.take(2).sum()
        val total = sorted.sum()
        // 상위 2개 브랜드가 전체의 40% 이상을 차지 (alpha=1.3 기준)
        assertTrue(top2.toDouble() / total >= 0.40, "top2=$top2 total=$total")
    }

    @Test
    fun `prices fall within category range`() {
        val products = generator.generate(1_000, seed = 7L).toList()
        val templatesByL1 = CatalogTemplates.all.associateBy { it.l1 }
        for (p in products) {
            val tpl = templatesByL1[p.category.l1]!!
            assertTrue(
                p.price in tpl.priceRange,
                "price ${p.price} out of range ${tpl.priceRange} for ${p.category.l1}"
            )
        }
    }

    @Test
    fun `title and description are non-empty and have no unsubstituted tokens`() {
        val products = generator.generate(500, seed = 99L).toList()
        for (p in products) {
            assertTrue(p.title.isNotBlank(), "blank title for ${p.id}")
            assertTrue(p.description.isNotBlank(), "blank description for ${p.id}")
            assertTrue(!p.title.contains("{"), "unsubstituted token in title: ${p.title}")
            assertTrue(!p.description.contains("{"), "unsubstituted token in description: ${p.description}")
        }
    }

    @Test
    fun `sequence is lazy — iteration stops early without materialising all`() {
        var peek = 0
        val seq = generator.generate(100_000, seed = 42L).onEach { peek++ }
        seq.take(5).toList()
        assertTrue(peek <= 10, "expected lazy eval, peeked=$peek")
    }
}
