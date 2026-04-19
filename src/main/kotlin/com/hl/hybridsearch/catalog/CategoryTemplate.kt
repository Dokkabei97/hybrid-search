package com.hl.hybridsearch.catalog

/**
 * 카테고리별 합성 상품 생성 템플릿.
 * - brands: Pareto 가중치용 (앞쪽 일수록 자주 등장)
 * - subCategories: L2 → L3 매핑 (L3 중 하나가 각 상품에 배정)
 * - titlePatterns / descriptionPatterns: `{key}` 치환 문자열. 사용 가능한 key는 code에서 정의.
 * - attributes: key → 가능한 값 목록. 생성 시 랜덤 선택되어 Product.attributes에 기록됨.
 * - priceRange: 원 단위 [min, max] — log-normal 로 뽑음.
 * - commonTags: 카테고리 공통 태그(부분 샘플링).
 */
data class CategoryTemplate(
    val l1: String,
    val brands: List<String>,
    val subCategories: Map<String, List<String>>,
    val titlePatterns: List<String>,
    val descriptionPatterns: List<String>,
    val attributes: Map<String, List<String>>,
    val priceRange: LongRange,
    val commonTags: List<String>,
)
