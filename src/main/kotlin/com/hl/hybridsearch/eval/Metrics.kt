package com.hl.hybridsearch.eval

import kotlin.math.ln
import kotlin.math.min

/**
 * 정보 검색 평가 지표 (binary relevance 기준).
 *
 * - NDCG@K : 랭킹 품질 — 상위에 정답이 몰릴수록 1에 가까움
 * - MRR    : 첫 정답까지의 역수 rank — 단일 의도 쿼리에 민감
 * - Recall@K : 상위 K 내 정답 비율
 *
 * relevant docId 리스트가 비어 있으면 모든 지표는 0 반환.
 */
object Metrics {

    fun ndcgAtK(ranked: List<String>, relevant: Set<String>, k: Int = 10): Double {
        if (relevant.isEmpty()) return 0.0
        val top = ranked.take(k)
        val dcg = top.foldIndexed(0.0) { idx, acc, docId ->
            acc + if (docId in relevant) 1.0 / log2((idx + 2).toDouble()) else 0.0
        }
        val idealCount = min(k, relevant.size)
        val idcg = (1..idealCount).sumOf { 1.0 / log2((it + 1).toDouble()) }
        return if (idcg == 0.0) 0.0 else dcg / idcg
    }

    fun mrr(ranked: List<String>, relevant: Set<String>): Double {
        if (relevant.isEmpty()) return 0.0
        val rank = ranked.indexOfFirst { it in relevant } + 1
        return if (rank == 0) 0.0 else 1.0 / rank
    }

    fun recallAtK(ranked: List<String>, relevant: Set<String>, k: Int = 50): Double {
        if (relevant.isEmpty()) return 0.0
        val hits = ranked.take(k).count { it in relevant }
        return hits.toDouble() / relevant.size
    }

    private fun log2(x: Double): Double = ln(x) / ln(2.0)
}
