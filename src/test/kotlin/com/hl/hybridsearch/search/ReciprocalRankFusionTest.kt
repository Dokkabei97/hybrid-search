package com.hl.hybridsearch.search

import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.search.model.SearchHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReciprocalRankFusionTest {

    private val properties = SearchProperties()
    private val rrf = ReciprocalRankFusion(properties)

    @Test
    fun `all empty returns empty`() {
        val result = rrf.fuse(listOf(emptyList(), emptyList(), emptyList()))
        assertEquals(emptyList(), result)
    }

    @Test
    fun `single list passes through with FUSED source`() {
        val hits = listOf(hit("a", 5.0, SearchHit.Source.LEXICAL), hit("b", 3.0, SearchHit.Source.LEXICAL))
        val result = rrf.fuse(listOf(hits))
        assertEquals(listOf("a", "b"), result.map { it.docId })
        assertTrue(result.all { it.source == SearchHit.Source.FUSED })
    }

    @Test
    fun `intersection docs score higher than singletons`() {
        // docA는 두 리스트 모두 rank=1 → 2 * 1/(60+1) = 0.0328
        // docB/docC는 각 리스트 rank=2 → 1/(60+2) = 0.0161
        val listA = listOf(hit("docA"), hit("docB"))
        val listB = listOf(hit("docA"), hit("docC"))
        val result = rrf.fuse(listOf(listA, listB))
        assertEquals("docA", result.first().docId)
        assertTrue(result.first().score > result[1].score)
    }

    @Test
    fun `rank contributes reciprocally`() {
        val listA = listOf(hit("x"), hit("y"), hit("z"))
        val listB = listOf(hit("z"))
        // x: 1/(60+1) = 0.01639
        // y: 1/(60+2) = 0.01613
        // z: 1/(60+3) + 1/(60+1) = 0.01587 + 0.01639 = 0.03226  ← winner
        val result = rrf.fuse(listOf(listA, listB))
        assertEquals("z", result.first().docId)
    }

    @Test
    fun `weights scale channel contribution`() {
        val listA = listOf(hit("a"))
        val listB = listOf(hit("b"))
        val result = rrf.fuse(listOf(listA, listB), weights = listOf(2.0, 1.0))
        assertEquals("a", result.first().docId, "channel A has higher weight")
        assertTrue(result.first().score > result[1].score)
    }

    @Test
    fun `one empty channel does not break fusion`() {
        val listA = listOf(hit("a"), hit("b"))
        val emptyList = emptyList<SearchHit>()
        val result = rrf.fuse(listOf(listA, emptyList))
        assertEquals(listOf("a", "b"), result.map { it.docId })
    }

    @Test
    fun `duplicate docs within same list count once at best rank`() {
        val listA = listOf(hit("a"), hit("b"), hit("a"))  // a 중복
        val result = rrf.fuse(listOf(listA))
        assertEquals(listOf("a", "b"), result.map { it.docId })
        assertTrue(result[0].score > result[1].score)
    }

    @Test
    fun `first seen payload is preserved`() {
        val listA = listOf(hit("a", payload = mapOf("from" to "lex")))
        val listB = listOf(hit("a", payload = mapOf("from" to "vec")))
        val result = rrf.fuse(listOf(listA, listB))
        assertEquals("lex", result.first().payload["from"])
    }

    @Test
    fun `size mismatch throws`() {
        val thrown = kotlin.runCatching {
            rrf.fuse(listOf(emptyList(), emptyList()), weights = listOf(1.0))
        }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }

    private fun hit(
        id: String,
        score: Double = 1.0,
        source: SearchHit.Source = SearchHit.Source.LEXICAL,
        payload: Map<String, Any?> = emptyMap(),
    ) = SearchHit(docId = id, score = score, source = source, payload = payload)
}
