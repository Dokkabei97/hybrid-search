package com.hl.hybridsearch.search.model

/**
 * 검색 필터. ES `bool.filter` 절로 내려간다 — 점수 영향 없음.
 *
 * 생성 경로는 [of] factory 한 곳으로 고정된다 — brand 같은 텍스트 필드는
 * canonical form (trim + lowercase) 으로 통일해서 어댑터별 정규화 이중 처리를 제거한다.
 * Primary constructor 는 private 이므로 외부에서 직접 인스턴스화할 수 없다.
 *
 * Why canonical form here:
 *  - ES `brand` 필드는 `normalizer: keyword_lower` 로 색인 시 자동 소문자화
 *  - Qdrant payload 는 원본 케이스 저장 → 필터 시 채널 간 매칭 결과가 갈라짐 (silent bug)
 *  - 도메인 모델이 canonical form 을 강제하면 하위 어댑터는 "이미 정규화됐다" 는 것을 신뢰
 */
@ConsistentCopyVisibility
data class SearchFilters private constructor(
    val categoryL1: String? = null,
    val brand: String? = null,
    val priceMin: Long? = null,
    val priceMax: Long? = null,
) {
    val hasAny: Boolean
        get() = categoryL1 != null || brand != null || priceMin != null || priceMax != null

    init {
        if (priceMin != null && priceMax != null) {
            require(priceMin <= priceMax) {
                "priceMin($priceMin) must be <= priceMax($priceMax)"
            }
        }
    }

    companion object {
        /** 필터 없음. 생성 비용을 줄이기 위해 공유 인스턴스. */
        val EMPTY = SearchFilters()

        /**
         * [SearchFilters] 의 유일한 합법 생성 경로 (primary constructor 가 private).
         *
         * 정규화 정책:
         *  - [brand] : trim 후 blank 면 null, 아니면 lowercase (ES keyword_lower 와 일치)
         *  - [categoryL1] : trim 후 blank 면 null — 케이스는 보존 (ES category.l1 필드는 normalizer 없음)
         *  - [priceMin]/[priceMax] : 그대로 통과 — 검증은 [init] 블록에서
         */
        fun of(
            categoryL1: String? = null,
            brand: String? = null,
            priceMin: Long? = null,
            priceMax: Long? = null,
        ): SearchFilters = SearchFilters(
            categoryL1 = categoryL1?.trim()?.takeIf { it.isNotBlank() },
            brand = brand?.trim()?.takeIf { it.isNotBlank() }?.lowercase(),
            priceMin = priceMin,
            priceMax = priceMax,
        )
    }
}
