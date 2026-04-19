package com.hl.hybridsearch.eval

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsTest {

    @Test
    fun `ndcg perfect when all top-k are relevant in ideal order`() {
        val ranked = listOf("a", "b", "c")
        val relevant = setOf("a", "b", "c")
        assertEquals(1.0, Metrics.ndcgAtK(ranked, relevant, 3), 1e-9)
    }

    @Test
    fun `ndcg zero when no overlap`() {
        val ranked = listOf("x", "y")
        val relevant = setOf("a", "b")
        assertEquals(0.0, Metrics.ndcgAtK(ranked, relevant, 10), 1e-9)
    }

    @Test
    fun `ndcg penalises later positions`() {
        val good = Metrics.ndcgAtK(listOf("a"), setOf("a"), 10)
        val worse = Metrics.ndcgAtK(listOf("x", "x", "x", "a"), setOf("a"), 10)
        assertTrue(good > worse, "earlier rank should produce higher NDCG")
    }

    @Test
    fun `mrr uses reciprocal of first relevant rank`() {
        assertEquals(1.0, Metrics.mrr(listOf("a"), setOf("a")), 1e-9)
        assertEquals(0.5, Metrics.mrr(listOf("x", "a"), setOf("a")), 1e-9)
        val third = Metrics.mrr(listOf("x", "x", "a"), setOf("a"))
        assertTrue(abs(third - (1.0 / 3.0)) < 1e-9)
    }

    @Test
    fun `mrr zero when no relevant`() {
        assertEquals(0.0, Metrics.mrr(listOf("x", "y"), setOf("a")), 1e-9)
    }

    @Test
    fun `recall at k counts unique relevant hits`() {
        val ranked = listOf("a", "b", "x", "c")
        val relevant = setOf("a", "b", "c", "d")
        // 3 of 4 found → 0.75
        assertEquals(0.75, Metrics.recallAtK(ranked, relevant, 50), 1e-9)
    }

    @Test
    fun `recall at k truncates to top-k window`() {
        val ranked = listOf("a", "b", "x", "c")
        val relevant = setOf("a", "b", "c")
        // top-2 = [a,b] → 2/3
        assertEquals(2.0 / 3.0, Metrics.recallAtK(ranked, relevant, 2), 1e-9)
    }

    @Test
    fun `empty relevant returns zero for all metrics`() {
        assertEquals(0.0, Metrics.ndcgAtK(listOf("a"), emptySet(), 10))
        assertEquals(0.0, Metrics.mrr(listOf("a"), emptySet()))
        assertEquals(0.0, Metrics.recallAtK(listOf("a"), emptySet(), 10))
    }
}
