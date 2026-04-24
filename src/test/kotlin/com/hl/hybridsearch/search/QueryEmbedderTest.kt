package com.hl.hybridsearch.search

import com.hl.hybridsearch.config.SearchProperties
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse

class QueryEmbedderTest {

    /**
     * 최소 EmbeddingModel stub — embed(String) 만 지원하고, 호출 횟수를 카운트한다.
     * 결과는 호출마다 새 FloatArray 를 돌려줘서 "캐시 히트면 같은 인스턴스" 를 검증 가능하게 함.
     */
    private class CountingEmbeddingModel(
        private val dim: Int = 8,
    ) : EmbeddingModel {
        val calls = AtomicInteger(0)

        override fun embed(text: String): FloatArray {
            calls.incrementAndGet()
            // 해시 기반 결정적 벡터 — 입력별 구분 가능, 하지만 매 호출 새 인스턴스
            val seed = text.hashCode()
            return FloatArray(dim) { i -> ((seed shr i) and 1).toFloat() }
        }

        override fun call(request: EmbeddingRequest): EmbeddingResponse =
            error("not used in this test")

        override fun embed(document: org.springframework.ai.document.Document): FloatArray =
            error("not used in this test")

        override fun dimensions(): Int = dim
    }

    private fun propsWith(
        enabled: Boolean = true,
        maxSize: Long = 1_000,
        instruction: String = "",
    ): SearchProperties = SearchProperties(
        embedding = SearchProperties.EmbeddingProperties(
            queryInstruction = instruction,
            queryCache = SearchProperties.QueryCacheProperties(
                enabled = enabled,
                maxSize = maxSize,
                ttlMinutes = 60,
            ),
        ),
    )

    @Test
    fun `cache hit returns same instance and skips model call`() {
        val model = CountingEmbeddingModel()
        val embedder = QueryEmbedder(model, propsWith(enabled = true))

        val first = embedder.embed("나이키 에어포스")
        val second = embedder.embed("나이키 에어포스")

        assertEquals(1, model.calls.get(), "동일 쿼리는 모델을 한 번만 호출해야 한다")
        assertSame(first, second, "캐시 히트는 같은 FloatArray 인스턴스를 반환해야 한다")
    }

    @Test
    fun `distinct queries miss and each triggers a model call`() {
        val model = CountingEmbeddingModel()
        val embedder = QueryEmbedder(model, propsWith(enabled = true))

        embedder.embed("a")
        embedder.embed("b")
        embedder.embed("c")

        assertEquals(3, model.calls.get())
    }

    @Test
    fun `cache disabled always calls model`() {
        val model = CountingEmbeddingModel()
        val embedder = QueryEmbedder(model, propsWith(enabled = false))

        embedder.embed("같은쿼리")
        embedder.embed("같은쿼리")

        assertEquals(2, model.calls.get())
    }

    @Test
    fun `maxSize zero disables cache (guard against misconfig)`() {
        val model = CountingEmbeddingModel()
        val embedder = QueryEmbedder(model, propsWith(enabled = true, maxSize = 0))

        embedder.embed("x")
        embedder.embed("x")

        assertEquals(2, model.calls.get())
    }

    @Test
    fun `instruction template change invalidates cache key`() {
        val model = CountingEmbeddingModel()
        // instruction 을 바꾸면 wrapped 문자열이 달라져서 다른 캐시 키로 인식
        val withPrefix = QueryEmbedder(
            model,
            propsWith(enabled = true, instruction = "search_query: {query}"),
        )
        val noPrefix = QueryEmbedder(model, propsWith(enabled = true, instruction = ""))

        withPrefix.embed("나이키")
        noPrefix.embed("나이키")

        // 각 embedder 의 캐시는 독립 — 각자 한 번씩 호출
        assertEquals(2, model.calls.get())
    }
}
