package com.hl.hybridsearch.indexing

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [Product] 의 저장 표현 계약 테스트.
 *
 * Single Source of Truth 원칙: `Product` 가 `toEsFields` / `toVectorPayload` 로
 * 자기 표현을 책임진다. 이 테스트들은 필드 추가/삭제 시 양쪽 매핑을 빠뜨리지 않도록
 * 키 집합과 정규화 정책을 고정한다.
 *
 * ES 스키마는 `src/main/resources/elasticsearch/index-template.json` 을 single source 로 본다.
 */
class ProductSchemaTest {

    private fun sample(
        id: String = "p1",
        title: String = "에어포스 1",
        brand: String = "Nike",
        tags: List<String> = listOf("sneaker", "classic"),
    ) = Product(
        id = id,
        title = title,
        brand = brand,
        category = Product.Category("패션/신발/스니커즈", "패션", "신발", "스니커즈"),
        description = "화이트 가죽 로우탑.",
        price = 139_000L,
        tags = tags,
        rating = 4.7,
        reviewCount = 1234,
        attributes = mapOf("color" to "white", "size_range" to "220-290"),
    )

    // ---------------- toEsFields ----------------

    @Test
    fun `toEsFields produces all keys required by index-template`() {
        val fields = sample().toEsFields(Instant.parse("2026-01-01T00:00:00Z"))
        val expected = setOf(
            "id", "title", "brand", "category", "description",
            "tags", "price", "rating", "reviewCount", "attributes",
            "createdAt", "updatedAt",
        )
        assertEquals(expected, fields.keys)
    }

    @Test
    fun `toEsFields preserves brand original case (ES normalizer handles lowercase)`() {
        // ES brand 필드는 keyword_lower normalizer 로 색인 시 자동 소문자화 →
        // 도메인에서 미리 lowercase 할 필요 없음
        val fields = sample(brand = "Nike").toEsFields(Instant.now())
        assertEquals("Nike", fields["brand"])
    }

    @Test
    fun `toEsFields category is nested object`() {
        val fields = sample().toEsFields(Instant.now())
        @Suppress("UNCHECKED_CAST")
        val category = fields["category"] as Map<String, Any?>
        assertEquals(setOf("path", "l1", "l2", "l3"), category.keys)
        assertEquals("패션", category["l1"])
    }

    @Test
    fun `toEsFields uses injected clock (deterministic)`() {
        val clock = Instant.parse("2026-01-01T12:34:56Z")
        val fields = sample().toEsFields(clock)
        assertEquals("2026-01-01T12:34:56Z", fields["createdAt"])
        assertEquals("2026-01-01T12:34:56Z", fields["updatedAt"])
    }

    // ---------------- toVectorPayload ----------------

    @Test
    fun `toVectorPayload keys match Qdrant filter expectations`() {
        // VectorFilter.from 은 category_l1, brand, price 를 참조 — payload 에 반드시 있어야 함
        val payload = sample().toVectorPayload()
        assertTrue("doc_id" in payload)
        assertTrue("title" in payload)
        assertTrue("brand" in payload)
        assertTrue("category_l1" in payload)
        assertTrue("category_path" in payload)
        assertTrue("price" in payload)
    }

    @Test
    fun `toVectorPayload lowercases brand (matches SearchFilters_of canonical form)`() {
        val payload = sample(brand = "Nike").toVectorPayload()
        assertEquals("nike", payload["brand"])
    }

    @Test
    fun `toVectorPayload omits tags when empty`() {
        assertFalse("tags" in sample(tags = emptyList()).toVectorPayload())
        assertTrue("tags" in sample(tags = listOf("a")).toVectorPayload())
    }

    @Test
    fun `toVectorPayload keeps price as Long (Qdrant gRPC integer)`() {
        val payload = sample().toVectorPayload()
        assertEquals(139_000L, payload["price"])
    }
}
