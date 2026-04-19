package com.hl.hybridsearch.catalog

import com.hl.hybridsearch.indexing.Product
import java.util.UUID
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random
import org.springframework.stereotype.Component

/**
 * 결정론적 합성 상품 카탈로그 생성기.
 *
 * 특성:
 *  - seed가 같으면 동일한 카탈로그 (재현성 → A/B 실험 용이)
 *  - 카테고리별 동등 배분 (default) 또는 커스텀 weight
 *  - 브랜드는 Pareto 가중치(상위 20%가 약 80%를 차지)로 선택
 *  - 가격은 카테고리 priceRange 내 log-uniform 분포
 *  - 속성 값은 카테고리 템플릿에서 랜덤 선택
 *  - 타이틀/설명 `{key}` 치환
 *
 * 메모리: `Sequence<Product>` 로 lazy 생성, 10만+ 규모도 heap 친화적.
 */
@Component
class SyntheticCatalogGenerator(
    private val templates: List<CategoryTemplate> = CatalogTemplates.all,
) {

    fun generate(total: Int, seed: Long = 42L): Sequence<Product> {
        require(total > 0) { "total must be positive" }
        val random = Random(seed)
        val perCategory = total / templates.size
        val remainder = total % templates.size

        return sequence {
            templates.forEachIndexed { idx, template ->
                val count = perCategory + if (idx < remainder) 1 else 0
                repeat(count) {
                    yield(buildProduct(template, random))
                }
            }
        }
    }

    private fun buildProduct(template: CategoryTemplate, random: Random): Product {
        val brand = paretoPick(template.brands, random)
        val (l2, l3) = pickL2L3(template, random)
        val attrValues = template.attributes.mapValues { (_, values) -> values.random(random) }
        val tokens = buildTokenMap(template, brand, l2, l3, attrValues)

        val title = template.titlePatterns.random(random).substitute(tokens)
        val description = template.descriptionPatterns.random(random).substitute(tokens)
        val price = logUniformPrice(template.priceRange, random)
        val rating = (3.5 + random.nextDouble() * 1.5).round(1)
        val reviewCount = paretoLong(5L, 10_000L, random)
        val tags = buildTags(template, attrValues, random)

        return Product(
            id = UUID(random.nextLong(), random.nextLong()).toString(),
            title = title,
            brand = brand,
            category = Product.Category(
                l1 = template.l1,
                l2 = l2,
                l3 = l3,
                path = "${template.l1}/$l2/$l3",
            ),
            description = description,
            price = price,
            tags = tags,
            rating = rating,
            reviewCount = reviewCount,
            attributes = attrValues,
        )
    }

    private fun buildTokenMap(
        template: CategoryTemplate,
        brand: String,
        l2: String,
        l3: String,
        attrValues: Map<String, String>,
    ): Map<String, String> = buildMap {
        put("brand", brand)
        put("l1", template.l1)
        put("l2", l2)
        put("l3", l3)
        putAll(attrValues)
    }

    private fun pickL2L3(template: CategoryTemplate, random: Random): Pair<String, String> {
        val l2 = template.subCategories.keys.random(random)
        val l3 = template.subCategories[l2]!!.random(random)
        return l2 to l3
    }

    /**
     * Pareto 가중치: 앞쪽 원소가 뒤쪽보다 훨씬 자주 선택되도록.
     * i번째 원소의 가중치 = 1 / (i+1)^alpha (기본 alpha=1.3 → 대략 80/20).
     */
    private fun <T> paretoPick(items: List<T>, random: Random, alpha: Double = 1.3): T {
        val weights = items.indices.map { 1.0 / (it + 1).toDouble().pow(alpha) }
        val sum = weights.sum()
        var r = random.nextDouble() * sum
        for ((i, w) in weights.withIndex()) {
            r -= w
            if (r <= 0) return items[i]
        }
        return items.last()
    }

    private fun paretoLong(min: Long, max: Long, random: Random, alpha: Double = 1.2): Long {
        val u = random.nextDouble().coerceAtLeast(1e-9)
        val scaled = min + ((max - min) * u.pow(alpha)).toLong()
        return scaled.coerceIn(min, max)
    }

    private fun logUniformPrice(range: LongRange, random: Random): Long {
        val lnLow = ln(range.first.toDouble())
        val lnHigh = ln(range.last.toDouble())
        val x = lnLow + random.nextDouble() * (lnHigh - lnLow)
        return kotlin.math.exp(x).toLong().coerceIn(range.first, range.last)
    }

    private fun buildTags(
        template: CategoryTemplate,
        attrValues: Map<String, String>,
        random: Random,
    ): List<String> {
        val picked = template.commonTags.shuffled(random).take(random.nextInt(1, 4))
        // 특정 속성값이 태그화할 가치가 있는 경우
        val attrTags = attrValues.values.filter { it.length <= 6 }.shuffled(random).take(1)
        return (picked + attrTags).distinct()
    }

    private fun String.substitute(tokens: Map<String, String>): String {
        var result = this
        for ((k, v) in tokens) {
            result = result.replace("{$k}", v)
        }
        return result
    }

    private fun Double.round(digits: Int): Double {
        val factor = Math.pow(10.0, digits.toDouble())
        return Math.round(this * factor) / factor
    }
}
