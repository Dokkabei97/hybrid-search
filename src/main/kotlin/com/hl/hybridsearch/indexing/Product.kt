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
     * 임베딩 "body" 구성 (Lean, docs/embedding-research.md §8.1 A안).
     *
     * title 은 이 문자열에 포함하지 않는다 — 문서 템플릿(`title:{title}|text:{text}`)에서
     * 별도 치환되므로 body 쪽은 title 을 제외한 나머지 시그널만 압축한다.
     * description 은 앞 400자 pre-truncate 로 mean-pool 희석 방지.
     *
     * 템플릿을 쓰지 않는 OpenAI/Gemini/Cohere 계열은 상위에서 `title + body` 로 직접 연결한다.
     */
    fun embeddingBody(maxChars: Int = 1000, descriptionCap: Int = 400): String = buildString {
        if (brand.isNotBlank()) append(brand)
        if (category.l2.isNotBlank()) append(' ').append(category.l2)
        if (category.l3.isNotBlank()) append(' ').append(category.l3)
        val attrValues = attributes.values.filterNotNull().joinToString(" ")
        if (attrValues.isNotBlank()) append(' ').append(attrValues)
        if (tags.isNotEmpty()) append(' ').append(tags.joinToString(" "))
        if (description.isNotBlank()) {
            append(". ")
            append(description.take(descriptionCap))
        }
    }.trim().take(maxChars)
}
