package com.hl.hybridsearch.search

import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.search.model.SearchHit
import org.springframework.stereotype.Component

/**
 * Reciprocal Rank Fusion.
 *
 * 공식: score(d) = Σ 1 / (k + rank_i(d))
 *   - rank는 1부터 시작 (0-based 인덱스라면 +1)
 *   - k는 하이퍼파라미터, 보통 60 (Cormack 등 2009 논문)
 *   - 리스트별 원 score는 사용하지 않는다 → 스케일 차이(BM25 vs cosine) 무시
 *
 * 엣지 케이스:
 *   - 양쪽 모두 포함된 docId: 각 rank 기여를 더한다
 *   - 한쪽에만 있는 docId: 해당 rank만 반영
 *   - 동일 리스트에 중복 docId: 최상위 rank만 사용 (이론적으로는 발생 불가)
 *   - 빈 리스트/빈 입력: 빈 리스트 반환
 */
@Component
class ReciprocalRankFusion(
    private val properties: SearchProperties,
) {

    fun fuse(rankedLists: List<List<SearchHit>>): List<SearchHit> {
        val k = properties.rrf.k
        // TODO(user): RRF 융합 로직을 구현해 주세요.
        //  1) rankedLists의 각 리스트에서 docId의 rank(1-based)를 구한다
        //  2) docId별 융합 점수를 합산한다: sum(1.0 / (k + rank))
        //  3) 같은 docId가 여러 리스트에 있으면 payload는 첫 등장(렉시컬 우선) 쪽을 유지한다
        //  4) 융합 점수 내림차순으로 List<SearchHit> 반환 (Source=FUSED)
        //  힌트: k 파라미터는 위에서 가져왔으니 그대로 사용
        TODO("ReciprocalRankFusion.fuse() 구현 필요")
    }
}
