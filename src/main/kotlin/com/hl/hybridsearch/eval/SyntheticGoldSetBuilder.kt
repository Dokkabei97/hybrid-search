package com.hl.hybridsearch.eval

import com.hl.hybridsearch.analyzer.QueryType
import com.hl.hybridsearch.catalog.SyntheticCatalogGenerator
import com.hl.hybridsearch.indexing.Product
import kotlin.random.Random
import org.springframework.stereotype.Component

/**
 * 합성 GoldSet 빌더.
 *
 * KEYWORD 쿼리: "{brand} {l3}" — relevant = 같은 brand+l3 문서 전부
 * SENTENCE 쿼리: 자연어 템플릿 (예 "가성비 좋은 {l3} 추천해줘") — relevant = 같은 l3 문서 전부
 *
 * 주의: 합성 gold는 생성 규칙을 평가하는 것이지 절대 진리는 아님.
 * 스터디 가치는 "같은 규칙 하에서 분류기 on/off, RRF 가중치 변화가 NDCG에 어떤 영향을 주는지" 비교에 있다.
 */
@Component
class SyntheticGoldSetBuilder(
    private val generator: SyntheticCatalogGenerator,
) {
    private val sentenceTemplates = listOf(
        "가벼운 {l3} 추천해줘",
        "{l3} 중에 가성비 좋은 거 뭐 있어",
        "인기 있는 {l3} 베스트 상품",
        "집에 두기 좋은 {l3}",
        "요즘 많이 사는 {l3} 알려줘",
    )

    fun build(
        corpusSize: Int = 10_000,
        queriesPerCategory: Int = 10,
        seed: Long = 42L,
        minBrandL3Docs: Int = 3,
        minL3Docs: Int = 5,
    ): GoldSet {
        require(queriesPerCategory >= 2) { "queriesPerCategory must be >= 2" }
        val corpus = generator.generate(corpusSize, seed).toList()
        val random = Random(seed + 1)
        val queries = mutableListOf<GoldQuery>()

        corpus.groupBy { it.category.l1 }.forEach { (l1, products) ->
            val byBrandL3 = products
                .groupBy { it.brand to it.category.l3 }
                .filter { it.value.size >= minBrandL3Docs }
            val byL3 = products
                .groupBy { it.category.l3 }
                .filter { it.value.size >= minL3Docs }
            if (byBrandL3.isEmpty() || byL3.isEmpty()) return@forEach

            val keywordCount = queriesPerCategory / 2
            val sentenceCount = queriesPerCategory - keywordCount

            repeat(keywordCount) {
                val (brand, l3) = byBrandL3.keys.random(random)
                queries += GoldQuery(
                    query = "$brand $l3",
                    expectedType = QueryType.KEYWORD,
                    relevant = byBrandL3.getValue(brand to l3).map { it.id }.toSet(),
                    categoryL1 = l1,
                    tag = "keyword_brand_l3",
                )
            }

            repeat(sentenceCount) {
                val l3 = byL3.keys.random(random)
                val text = sentenceTemplates.random(random).replace("{l3}", l3)
                queries += GoldQuery(
                    query = text,
                    expectedType = QueryType.SENTENCE,
                    relevant = byL3.getValue(l3).map { it.id }.toSet(),
                    categoryL1 = l1,
                    tag = "sentence_l3_natural",
                )
            }
        }

        return GoldSet(corpus = corpus, queries = queries)
    }
}
