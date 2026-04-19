package com.hl.hybridsearch.indexing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductEmbeddingTest {

    @Test
    fun `embeddingBody excludes title to let document template handle it`() {
        val p = Product(
            id = "1",
            title = "삼성 갤럭시 S24 울트라",
            brand = "삼성",
            description = "AMOLED 디스플레이",
        )
        val body = p.embeddingBody()
        assertFalse(body.contains("갤럭시"), "title must not be inlined into body: '$body'")
        assertTrue(body.contains("삼성"))
    }

    @Test
    fun `embeddingBody composes brand + category + attributes + tags + description`() {
        val p = Product(
            id = "1",
            title = "아이폰 15 프로 맥스",
            brand = "Apple",
            category = Product.Category(l1 = "디지털", l2 = "스마트폰", l3 = "플래그십", path = "디지털/스마트폰/플래그십"),
            description = "티타늄 프레임에 A17 Pro 칩 탑재",
            tags = listOf("신상", "프리미엄"),
            attributes = mapOf("color" to "블랙", "storage" to "256GB"),
        )
        val body = p.embeddingBody()
        assertTrue(body.contains("Apple"))
        assertTrue(body.contains("스마트폰"))
        assertTrue(body.contains("플래그십"))
        assertTrue(body.contains("블랙"))
        assertTrue(body.contains("256GB"))
        assertTrue(body.contains("신상"))
        assertTrue(body.contains("티타늄"))
    }

    @Test
    fun `description is pre-truncated to descriptionCap before maxChars applies`() {
        val longDesc = "가".repeat(2_000)
        val p = Product(id = "1", title = "t", description = longDesc)
        val body = p.embeddingBody(maxChars = 1_000, descriptionCap = 400)
        val gaCount = body.count { it == '가' }
        assertEquals(400, gaCount, "description should be capped at descriptionCap")
    }

    @Test
    fun `maxChars caps the composed body length`() {
        val p = Product(
            id = "1",
            title = "t",
            brand = "B".repeat(500),
            description = "D".repeat(3_000),
        )
        val body = p.embeddingBody(maxChars = 800)
        assertTrue(body.length <= 800, "body length ${body.length} exceeds maxChars=800")
    }

    @Test
    fun `blank optional fields do not leave dangling separators`() {
        val p = Product(id = "1", title = "t")
        val body = p.embeddingBody()
        assertFalse(body.startsWith(" "))
        assertFalse(body.endsWith(" "))
        assertFalse(body.contains("  "), "no double spaces in '$body'")
    }
}
