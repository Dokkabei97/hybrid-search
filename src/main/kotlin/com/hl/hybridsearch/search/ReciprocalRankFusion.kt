package com.hl.hybridsearch.search

import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.search.model.SearchHit
import org.springframework.stereotype.Component

/**
 * Weighted Reciprocal Rank Fusion.
 *
 * 공식: score(d) = Σ_i  w_i · 1 / (k + rank_i(d))
 *   - rank는 1-based (리스트 0번째 원소 → rank=1)
 *   - k는 상수(보통 60)
 *   - w_i 는 채널 가중치 (title/body/vector 등)
 *
 * 엣지 케이스:
 *   - 한쪽에만 있는 docId: 해당 채널 기여만 반영
 *   - 모든 리스트가 비어 있으면 emptyList
 *   - 동일 리스트 내 중복 docId: 최상위 rank만 반영
 *   - payload는 "첫 등장" 채널의 것을 보존 (호출자가 채널 순서를 정함)
 */
@Component
class ReciprocalRankFusion(
    private val properties: SearchProperties,
) {

    /** 균등 가중 버전 — 2-way/n-way 어느 쪽이든 쓸 수 있음. */
    fun fuse(rankedLists: List<List<SearchHit>>): List<SearchHit> =
        fuse(rankedLists, List(rankedLists.size) { 1.0 })

    fun fuse(
        rankedLists: List<List<SearchHit>>,
        weights: List<Double>,
    ): List<SearchHit> {
        require(rankedLists.size == weights.size) {
            "rankedLists(${rankedLists.size}) / weights(${weights.size}) size mismatch"
        }
        if (rankedLists.isEmpty() || rankedLists.all { it.isEmpty() }) return emptyList()

        val k = properties.rrf.k
        val scores = LinkedHashMap<String, Double>()
        val firstSeen = LinkedHashMap<String, SearchHit>()
        val seenInChannel = Array(rankedLists.size) { HashSet<String>() }

        rankedLists.forEachIndexed { channelIdx, list ->
            val w = weights[channelIdx]
            list.forEachIndexed { rankIdx, hit ->
                if (!seenInChannel[channelIdx].add(hit.docId)) return@forEachIndexed
                val rank = rankIdx + 1
                val contribution = w / (k + rank).toDouble()
                scores.merge(hit.docId, contribution, Double::plus)
                firstSeen.putIfAbsent(hit.docId, hit)
            }
        }

        return scores.entries
            .sortedByDescending { it.value }
            .map { (docId, fused) ->
                val original = firstSeen[docId]!!
                original.copy(score = fused, source = SearchHit.Source.FUSED)
            }
    }
}
