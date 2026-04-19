package com.hl.hybridsearch.indexing

/**
 * 이커머스 상품 도메인 모델.
 *
 * 스키마 설계 원칙:
 *  - title/brand/description 은 nori 분석 + BM25 관련성 검색 대상
 *  - brand, category.*, tags 는 keyword 기반 필터/패싯 대상
 *  - attributes 는 카테고리별 가변 속성 (flattened)
 *  - price 는 scaled_float(100) 저장 → 원 단위
 */
data class Product(
    val id: String,
    val title: String,
    val brand: String = "",
    val category: Category = Category(),
    val description: String = "",
    val price: Long = 0L,
    val tags: List<String> = emptyList(),
    val rating: Double = 0.0,
    val reviewCount: Long = 0L,
    val attributes: Map<String, Any?> = emptyMap(),
) {
    data class Category(
        val path: String = "",
        val l1: String = "",
        val l2: String = "",
        val l3: String = "",
    )

    /**
     * 벡터 임베딩용 텍스트.
     * 제목 가중은 title-only BM25 채널(Phase 4)에서 처리하므로
     * 여기서는 `body` 의미에 해당하는 description 을 중심으로 구성한다.
     * 임베딩 모델 컨텍스트 초과 방지를 위해 길이 상한을 둔다.
     */
    fun embeddingText(maxChars: Int = 2000): String = buildString {
        append(title)
        if (brand.isNotBlank()) append("\n브랜드: ").append(brand)
        if (category.path.isNotBlank()) append("\n카테고리: ").append(category.path)
        if (tags.isNotEmpty()) append("\n태그: ").append(tags.joinToString(", "))
        if (description.isNotBlank()) append("\n\n").append(description)
    }.take(maxChars)
}
