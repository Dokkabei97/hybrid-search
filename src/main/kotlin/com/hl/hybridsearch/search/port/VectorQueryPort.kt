package com.hl.hybridsearch.search.port

import com.hl.hybridsearch.search.model.SearchHit

/**
 * 벡터 스토어 조회 포트.
 *
 * 설계 의도:
 *  - **벤더 중립**: Qdrant/Milvus/Weaviate 등 어떤 어댑터든 이 시그니처에 맞춰 구현한다.
 *    벤더 특화 파라미터(hnsw_ef, nprobe, oversampling)는 [VectorSearchMode] 로 감춘다.
 *  - **얇은 포트 (Option A)**: 임베딩 생성은 호출자(SearchService 또는 평가 러너)가 책임지고,
 *    포트는 순수 벡터 I/O 만 담당한다 → 평가 루프에서 같은 벡터를 FAST/EXACT 로 재사용 가능.
 *  - **필터는 도메인 AST**: [VectorFilter] 로 받아 어댑터가 네이티브로 변환.
 *    payload index 가 만들어져 있으면 선택도가 높은 필터도 성능 손실 최소화 (Qdrant 기준).
 */
interface VectorQueryPort {

    /**
     * @param queryVector 쿼리 임베딩. 차원은 컬렉션 스키마와 일치해야 한다.
     * @param topK 반환 개수 상한.
     * @param filter 선택적 payload 필터. null 이면 전체 벡터 공간에서 검색.
     * @param mode 탐색 모드. 어댑터가 벤더 특화 파라미터로 매핑한다.
     */
    fun search(
        queryVector: FloatArray,
        topK: Int,
        filter: VectorFilter? = null,
        mode: VectorSearchMode = VectorSearchMode.FAST,
    ): List<SearchHit>
}
