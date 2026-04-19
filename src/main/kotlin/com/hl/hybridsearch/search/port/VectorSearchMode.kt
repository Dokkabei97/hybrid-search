package com.hl.hybridsearch.search.port

/**
 * 벡터 검색 모드 — 벤더 특화 파라미터(hnsw_ef, exact, nprobe 등)를 감추는 추상 레이어.
 *
 * 컨트롤러/외부 API 는 이 enum 을 노출받지 않는다. SearchService 가 쿼리 특성
 * (평가 모드, 필터 선택도 등)을 보고 결정한다.
 *
 * - [FAST]      : 프로덕션 기본. 낮은 latency, HNSW ef 를 작게.
 * - [ACCURATE]  : recall 우선. HNSW ef 를 크게 (Qdrant: 256, Milvus: nprobe 확대).
 * - [EXACT]     : brute-force. 평가 루프에서 recall 상한 측정용.
 */
enum class VectorSearchMode {
    FAST,
    ACCURATE,
    EXACT,
}
