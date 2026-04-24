package com.hl.hybridsearch.indexing

import java.time.Instant

/**
 * 이커머스 상품 도메인 모델.
 *
 * 스키마 설계 원칙:
 *  - title/brand/description 은 nori 분석 + BM25 관련성 검색 대상
 *  - brand, category.*, tags 는 keyword 기반 필터/패싯 대상
 *  - attributes 는 카테고리별 가변 속성 (flattened)
 *  - price 는 scaled_float(100) 저장 → 원 단위
 *
 * 저장 표현(Single Source of Truth):
 *  - [toEsFields] : ES `products` 인덱스 스키마와 1:1 매핑 (index-template.json)
 *  - [toVectorPayload] : Qdrant `products` 컬렉션 payload 와 매칭
 *  - [embeddingBody] : 임베딩 "body" 텍스트 구성 (title 제외)
 *  어댑터는 이 세 메서드를 호출하기만 한다 — 스키마 해석을 어댑터 쪽으로 퍼뜨리지 않는다.
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

    /**
     * ES `products` 인덱스용 document.
     *
     * 시각(`createdAt`/`updatedAt`)은 외부에서 주입한다 (clock injection) —
     * 도메인 모델이 `Instant.now()` 를 호출하면 테스트가 비결정적이 된다.
     * brand 는 원본 케이스 그대로 보낸다 — ES `keyword_lower` normalizer 가 색인 시 소문자화.
     */
    fun toEsFields(now: Instant): Map<String, Any?> {
        val isoNow = now.toString()
        return mapOf(
            "id" to id,
            "title" to title,
            "brand" to brand,
            "category" to mapOf(
                "path" to category.path,
                "l1" to category.l1,
                "l2" to category.l2,
                "l3" to category.l3,
            ),
            "description" to description,
            "tags" to tags,
            "price" to price,
            "rating" to rating,
            "reviewCount" to reviewCount,
            "attributes" to attributes,
            "createdAt" to isoNow,
            "updatedAt" to isoNow,
        )
    }

    /**
     * Qdrant `products` 컬렉션 payload (필터/패싯 및 응답 메타).
     *
     * brand 는 lowercase canonical form 으로 저장 —
     * [com.hl.hybridsearch.search.model.SearchFilters.of] 의 정규화 정책과 반드시 일치해야 한다
     * (불일치 시 필터 매칭 실패). ES 쪽은 `keyword_lower` normalizer 가 동일 효과를 낸다.
     *
     * empty collection 은 payload 에서 제외 — Qdrant 는 empty list 저장을 허용하지만
     * 쓸모없는 key 를 줄여 payload 크기를 낮춘다.
     */
    fun toVectorPayload(): Map<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "doc_id" to id,
            "title" to title,
            "brand" to brand.lowercase(),
            "category_path" to category.path,
            "category_l1" to category.l1,
            "price" to price,
        )
        if (tags.isNotEmpty()) payload["tags"] = tags
        return payload
    }
}
