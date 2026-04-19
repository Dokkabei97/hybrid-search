package com.hl.hybridsearch.eval

import com.hl.hybridsearch.analyzer.QueryType
import com.hl.hybridsearch.catalog.SyntheticCatalogGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntheticGoldSetBuilderTest {

    private val builder = SyntheticGoldSetBuilder(SyntheticCatalogGenerator())

    @Test
    fun `produces both KEYWORD and SENTENCE queries`() {
        val gold = builder.build(corpusSize = 5_000, queriesPerCategory = 10, seed = 1L)
        val types = gold.queries.groupingBy { it.expectedType }.eachCount()
        assertTrue((types[QueryType.KEYWORD] ?: 0) > 0)
        assertTrue((types[QueryType.SENTENCE] ?: 0) > 0)
    }

    @Test
    fun `relevant sets are non-empty and reference corpus ids`() {
        val gold = builder.build(corpusSize = 5_000, queriesPerCategory = 6, seed = 2L)
        val allIds = gold.corpus.mapTo(HashSet()) { it.id }
        for (q in gold.queries) {
            assertTrue(q.relevant.isNotEmpty(), "query '${q.query}' has empty relevant")
            assertTrue(q.relevant.all { it in allIds }, "query '${q.query}' references unknown ids")
        }
    }

    @Test
    fun `same seed reproduces identical queries`() {
        val a = builder.build(corpusSize = 2_000, queriesPerCategory = 4, seed = 7L).queries
            .map { it.query to it.relevant }
        val b = builder.build(corpusSize = 2_000, queriesPerCategory = 4, seed = 7L).queries
            .map { it.query to it.relevant }
        assertEquals(a, b)
    }

    @Test
    fun `each relevance set stays within its own L1 category`() {
        // KEYWORD와 SENTENCE 모두 categoryL1 안의 문서만 정답이어야 한다 (합성 gold의 기본 불변식).
        val gold = builder.build(corpusSize = 5_000, queriesPerCategory = 10, seed = 3L)
        val byL1 = gold.corpus.groupBy { it.category.l1 }.mapValues { (_, v) -> v.mapTo(HashSet()) { it.id } }
        for (q in gold.queries) {
            val idsInCategory = byL1[q.categoryL1] ?: emptySet()
            val outOfScope = q.relevant - idsInCategory
            assertTrue(outOfScope.isEmpty(), "q='${q.query}' leaks into other categories: $outOfScope")
        }
    }
}
