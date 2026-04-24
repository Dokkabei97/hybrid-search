package com.hl.hybridsearch.search

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import co.elastic.clients.elasticsearch.core.SearchRequest as EsSearchRequest
import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.search.model.SearchFilters
import com.hl.hybridsearch.search.model.SearchHit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 렉시컬 검색 어댑터 (ES).
 * 3-way RRF 전략에 맞춰 두 가지 경로를 제공한다:
 *   - [searchTitle] : title 단일 필드, 정확 매칭·희귀어에 강함
 *   - [searchMulti] : title/brand/description 멀티필드 + tie_breaker + msm
 *
 * 공통 최적화: `track_total_hits=false` (WAND 조기 종료), `_source includes`.
 */
@Component
class LexicalSearcher(
    private val client: ElasticsearchClient,
    private val properties: SearchProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val sourceIncludes = listOf(
        "id", "title", "brand", "category", "price", "rating", "reviewCount", "tags",
    )

    fun searchTitle(
        query: String,
        topK: Int,
        filters: SearchFilters = SearchFilters.EMPTY,
    ): List<SearchHit> = execute(
        query = buildTextQuery(query, listOf("title"), tieBreaker = null, msm = null),
        topK = topK,
        filters = filters,
    ).also { log.debug("Lexical(title) returned {} hits", it.size) }

    fun searchMulti(
        query: String,
        topK: Int,
        filters: SearchFilters = SearchFilters.EMPTY,
    ): List<SearchHit> = execute(
        query = buildTextQuery(
            query,
            fields = listOf("title^3", "brand.text^2", "description"),
            tieBreaker = 0.3,
            msm = "2<75%",
        ),
        topK = topK,
        filters = filters,
    ).also { log.debug("Lexical(multi) returned {} hits", it.size) }

    private fun execute(
        query: Query,
        topK: Int,
        filters: SearchFilters,
    ): List<SearchHit> {
        val finalQuery = if (filters.hasAny) wrapWithFilters(query, filters) else query

        val request = EsSearchRequest.Builder()
            .index(properties.indexName)
            .size(topK)
            .trackTotalHits { t -> t.enabled(false) }
            .source { s -> s.filter { f -> f.includes(sourceIncludes) } }
            .query(finalQuery)
            .build()

        val response = client.search(request, Map::class.java)
        return response.hits().hits().map { hit ->
            @Suppress("UNCHECKED_CAST")
            val payload = (hit.source() as? Map<String, Any?>).orEmpty()
            SearchHit(
                docId = hit.id() ?: error("ES hit without id"),
                score = hit.score() ?: 0.0,
                source = SearchHit.Source.LEXICAL,
                payload = payload,
            )
        }
    }

    private fun buildTextQuery(
        query: String,
        fields: List<String>,
        tieBreaker: Double?,
        msm: String?,
    ): Query = Query.Builder().multiMatch { mm ->
        mm.query(query).fields(fields).type(TextQueryType.BestFields)
        if (tieBreaker != null) mm.tieBreaker(tieBreaker)
        if (msm != null) mm.minimumShouldMatch(msm)
        mm
    }.build()

    private fun wrapWithFilters(inner: Query, filters: SearchFilters): Query =
        Query.Builder().bool { b -> applyFilters(b.must(inner), filters) }.build()

    private fun applyFilters(b: BoolQuery.Builder, filters: SearchFilters): BoolQuery.Builder {
        filters.categoryL1?.let { l1 ->
            b.filter { f -> f.term { t -> t.field("category.l1").value(l1) } }
        }
        filters.brand?.let { brand ->
            // SearchFilters.of 에서 이미 lowercase canonical form 으로 정규화됨
            b.filter { f -> f.term { t -> t.field("brand").value(brand) } }
        }
        if (filters.priceMin != null || filters.priceMax != null) {
            b.filter { f ->
                f.range { r ->
                    r.number { n ->
                        n.field("price")
                        filters.priceMin?.let { n.gte(it.toDouble()) }
                        filters.priceMax?.let { n.lte(it.toDouble()) }
                        n
                    }
                }
            }
        }
        return b
    }
}
