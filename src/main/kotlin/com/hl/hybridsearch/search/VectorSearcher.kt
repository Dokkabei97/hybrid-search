package com.hl.hybridsearch.search

import com.hl.hybridsearch.search.model.SearchFilters
import com.hl.hybridsearch.search.model.SearchHit
import com.hl.hybridsearch.search.port.VectorFilter
import com.hl.hybridsearch.search.port.VectorQueryPort
import com.hl.hybridsearch.search.port.VectorSearchMode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 벡터 검색 채널 파사드. 아래 세 책임을 얇게 결합한다:
 *   1) 쿼리 텍스트 → 임베딩 벡터 ([QueryEmbedder])
 *   2) 도메인 [SearchFilters] → 벤더 중립 [VectorFilter] AST
 *   3) [VectorQueryPort] 호출 (벤더 어댑터 선택은 DI)
 *
 * 이 클래스는 Spring AI `VectorStore` 에 더 이상 의존하지 않는다. Qdrant → Milvus/Weaviate 교체 시
 * [VectorQueryPort] 구현체만 바꾸면 된다.
 */
@Component
class VectorSearcher(
    private val embedder: QueryEmbedder,
    private val port: VectorQueryPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun search(
        query: String,
        topK: Int,
        filters: SearchFilters = SearchFilters.EMPTY,
        mode: VectorSearchMode = VectorSearchMode.FAST,
    ): List<SearchHit> {
        val vector = embedder.embed(query)
        val filter = VectorFilter.from(filters)
        return port.search(vector, topK, filter, mode)
            .also { log.debug("Vector channel returned {} hits (filter={})", it.size, filter != null) }
    }
}
