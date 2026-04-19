package com.hl.hybridsearch.search.adapter

import com.hl.hybridsearch.config.SearchProperties
import com.hl.hybridsearch.search.model.SearchHit
import com.hl.hybridsearch.search.port.VectorFilter
import com.hl.hybridsearch.search.port.VectorQueryPort
import com.hl.hybridsearch.search.port.VectorSearchMode
import io.qdrant.client.ConditionFactory
import io.qdrant.client.QdrantClient
import io.qdrant.client.WithPayloadSelectorFactory
import io.qdrant.client.grpc.JsonWithInt
import io.qdrant.client.grpc.Points
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Qdrant 벡터 조회 어댑터. [VectorQueryPort] 의 Qdrant 구현체.
 *
 * Spring AI [org.springframework.ai.vectorstore.VectorStore] 를 쓰지 않고 `io.qdrant:client` gRPC
 * 클라이언트를 직접 사용한다 — SearchParams(hnsw_ef, exact) 같은 네이티브 튜닝이 필요해서.
 * Spring AI auto-config 이 생성한 [QdrantClient] 빈을 재사용하므로 커넥션은 쓰기 경로와 공유된다.
 *
 * 컬렉션 스키마(dimension, distance metric) 는 Spring AI `initialize-schema=true` 가 만들고,
 * 이 어댑터는 그 스키마에 순응만 한다 (역방향 스키마 관리 금지).
 */
@Component
class QdrantVectorAdapter(
    private val client: QdrantClient,
    private val properties: SearchProperties,
) : VectorQueryPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun search(
        queryVector: FloatArray,
        topK: Int,
        filter: VectorFilter?,
        mode: VectorSearchMode,
    ): List<SearchHit> {
        val request = Points.SearchPoints.newBuilder()
            .setCollectionName(properties.vector.collectionName)
            .addAllVector(queryVector.toList())
            .setLimit(topK.toLong())
            .setWithPayload(WithPayloadSelectorFactory.enable(true))
            .setParams(toSearchParams(mode))
            .apply { if (filter != null) setFilter(toQdrantFilter(filter)) }
            .build()

        val points = client.searchAsync(request).get()
        return points.map { toSearchHit(it) }
            .also { log.debug("Qdrant search returned {} hits (mode={}, filter={})", it.size, mode, filter != null) }
    }

    private fun toSearchParams(mode: VectorSearchMode): Points.SearchParams {
        val b = Points.SearchParams.newBuilder()
        return when (mode) {
            VectorSearchMode.FAST -> b.setHnswEf(properties.vector.fastHnswEf.toLong()).build()
            VectorSearchMode.ACCURATE -> b.setHnswEf(properties.vector.accurateHnswEf.toLong()).build()
            VectorSearchMode.EXACT -> b.setExact(true).build()
        }
    }

    /**
     * [VectorFilter] AST → Qdrant [Points.Filter].
     * And 는 must 절로 펼치고, 단일 조건은 must 한 개로 감싼다.
     */
    private fun toQdrantFilter(filter: VectorFilter): Points.Filter {
        val builder = Points.Filter.newBuilder()
        flattenAnd(filter).forEach { builder.addMust(toCondition(it)) }
        return builder.build()
    }

    private fun flattenAnd(f: VectorFilter): List<VectorFilter> = when (f) {
        is VectorFilter.And -> f.clauses.flatMap { flattenAnd(it) }
        else -> listOf(f)
    }

    private fun toCondition(f: VectorFilter): Points.Condition = when (f) {
        is VectorFilter.Term -> when (val v = f.value) {
            is String -> ConditionFactory.matchKeyword(f.field, v)
            is Boolean -> ConditionFactory.match(f.field, v)
            is Number -> ConditionFactory.match(f.field, v.toLong())
            else -> error("Unsupported Term value type: ${v::class.simpleName} for field ${f.field}")
        }
        is VectorFilter.Terms -> when (val sample = f.values.firstOrNull()) {
            null -> error("Terms filter requires at least one value (field=${f.field})")
            is String -> ConditionFactory.matchKeywords(f.field, f.values.map { it as String })
            is Number -> ConditionFactory.matchValues(f.field, f.values.map { (it as Number).toLong() })
            else -> error("Unsupported Terms value type: ${sample::class.simpleName} for field ${f.field}")
        }
        is VectorFilter.Range -> {
            val range = Points.Range.newBuilder().apply {
                f.gte?.let { setGte(it.toDouble()) }
                f.lte?.let { setLte(it.toDouble()) }
            }.build()
            ConditionFactory.range(f.field, range)
        }
        is VectorFilter.And -> ConditionFactory.filter(toQdrantFilter(f))
    }

    private fun toSearchHit(point: Points.ScoredPoint): SearchHit {
        val payload = point.payloadMap.mapValues { (_, v) -> convertValue(v) }
        return SearchHit(
            docId = extractId(point.id),
            score = point.score.toDouble(),
            source = SearchHit.Source.VECTOR,
            payload = payload,
        )
    }

    private fun extractId(pointId: Points.PointId): String = when (pointId.pointIdOptionsCase) {
        Points.PointId.PointIdOptionsCase.UUID -> pointId.uuid
        Points.PointId.PointIdOptionsCase.NUM -> pointId.num.toString()
        else -> error("Qdrant ScoredPoint has no id set")
    }

    private fun convertValue(value: JsonWithInt.Value): Any? = when (value.kindCase) {
        JsonWithInt.Value.KindCase.NULL_VALUE -> null
        JsonWithInt.Value.KindCase.DOUBLE_VALUE -> value.doubleValue
        JsonWithInt.Value.KindCase.INTEGER_VALUE -> value.integerValue
        JsonWithInt.Value.KindCase.STRING_VALUE -> value.stringValue
        JsonWithInt.Value.KindCase.BOOL_VALUE -> value.boolValue
        JsonWithInt.Value.KindCase.LIST_VALUE -> value.listValue.valuesList.map { convertValue(it) }
        JsonWithInt.Value.KindCase.STRUCT_VALUE ->
            value.structValue.fieldsMap.mapValues { (_, nested) -> convertValue(nested) }
        JsonWithInt.Value.KindCase.KIND_NOT_SET -> null
        else -> null
    }
}
