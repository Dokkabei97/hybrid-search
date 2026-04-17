package com.hl.hybridsearch.analyzer

import com.hl.hybridsearch.config.SearchProperties
import kotlin.test.Test
import kotlin.test.assertEquals

class QueryClassifierTest {

    private val properties = SearchProperties()
    private val classifier = QueryClassifier(properties)

    @Test
    fun `empty query is KEYWORD`() {
        val features = QueryFeatures(raw = "", tokens = emptyList())
        assertEquals(QueryType.KEYWORD, classifier.classify(features))
    }

    @Test
    fun `short noun-only query is KEYWORD`() {
        val features = features(
            noun("삼성전자"),
            noun("주가"),
        )
        assertEquals(QueryType.KEYWORD, classifier.classify(features))
    }

    @Test
    fun `mid-length noun-dominant query is KEYWORD`() {
        val features = features(
            noun("아이폰"),
            noun("15"),
            noun("프로"),
            noun("맥스"),
        )
        assertEquals(QueryType.KEYWORD, classifier.classify(features))
    }

    @Test
    fun `short query with a josa is still KEYWORD (aggressive)`() {
        val features = features(
            noun("좋은"),
            noun("계곡"),
            noun("추천"),
        )
        assertEquals(QueryType.KEYWORD, classifier.classify(features))
    }

    @Test
    fun `long query with clear predicate and many particles is SENTENCE`() {
        val features = features(
            noun("주말"), josa("에"),
            noun("아이"), josa("랑"),
            verb("가기"), adj("좋은"),
            adj("한적한"), noun("계곡"),
            verb("추천"), eomi("해줘"),
        )
        assertEquals(QueryType.SENTENCE, classifier.classify(features))
    }

    @Test
    fun `long but noun-dominant query is KEYWORD`() {
        val features = features(
            noun("2025"), noun("한국"), noun("반도체"),
            noun("수출"), noun("통계"), noun("월별"), noun("추이"),
        )
        assertEquals(QueryType.KEYWORD, classifier.classify(features))
    }

    @Test
    fun `long query with predicate but low particles is KEYWORD`() {
        val features = features(
            noun("서울"), noun("강남"), noun("맛집"), noun("리스트"),
            noun("추천"), verb("가기"),
        )
        assertEquals(QueryType.KEYWORD, classifier.classify(features))
    }

    @Test
    fun `question with many particles and verb is SENTENCE`() {
        val features = features(
            noun("오늘"), noun("저녁"), josa("에"),
            noun("뭐"), josa("를"),
            verb("먹으면"), adj("좋을지"),
            eomi("까요"),
        )
        assertEquals(QueryType.SENTENCE, classifier.classify(features))
    }

    @Test
    fun `exactly at maxKeywordTokens boundary is KEYWORD`() {
        val cfg = properties.classifier
        val tokens = List(cfg.maxKeywordTokens) { noun("토큰$it") }
        val features = features(*tokens.toTypedArray())
        assertEquals(QueryType.KEYWORD, classifier.classify(features))
    }

    @Test
    fun `just past max tokens but high noun ratio is KEYWORD`() {
        val features = features(
            noun("아"), noun("이"), noun("폰"), noun("15"), noun("프로"), noun("맥스"),
        )
        assertEquals(QueryType.KEYWORD, classifier.classify(features))
    }

    private fun features(vararg tokens: QueryFeatures.Token) =
        QueryFeatures(raw = tokens.joinToString(" ") { it.surface }, tokens = tokens.toList())

    private fun noun(s: String) = QueryFeatures.Token(s, QueryFeatures.Pos.NOUN)
    private fun verb(s: String) = QueryFeatures.Token(s, QueryFeatures.Pos.VERB)
    private fun adj(s: String) = QueryFeatures.Token(s, QueryFeatures.Pos.ADJECTIVE)
    private fun josa(s: String) = QueryFeatures.Token(s, QueryFeatures.Pos.JOSA)
    private fun eomi(s: String) = QueryFeatures.Token(s, QueryFeatures.Pos.EOMI)
}
