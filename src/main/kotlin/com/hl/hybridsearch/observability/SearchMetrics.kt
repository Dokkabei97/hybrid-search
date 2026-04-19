package com.hl.hybridsearch.observability

import com.hl.hybridsearch.analyzer.QueryType
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Component

/**
 * 하이브리드 검색 전용 메트릭 파사드.
 *
 * 설계 원칙:
 *  - 라벨 카디널리티 고정: `source`와 `status`는 유한 enum만 허용
 *  - Timer 하나로 latency + count 동시 집계 (별도 counter 중복 지양)
 *  - 쿼리 문자열·docId는 절대 라벨이 되면 안 됨 (cardinality 폭발)
 *
 * Prometheus 예:
 *   hybrid_search_latency_seconds_bucket{source="lexical_title", status="ok"}
 *   hybrid_search_routing_total{query_type="KEYWORD"}
 */
@Component
class SearchMetrics(
    private val registry: MeterRegistry,
) {
    enum class Source(val label: String) {
        LEXICAL_TITLE("lexical_title"),
        LEXICAL_MULTI("lexical_multi"),
        LEXICAL_ONLY("lexical_only"),
        VECTOR("vector"),
        ANALYZE("analyze"),
        ES_BULK("es_bulk"),
        QDRANT_BULK("qdrant_bulk"),
    }

    enum class Status(val label: String) {
        OK("ok"),
        FAILED("failed"),
    }

    fun recordSearchChannel(source: Source, status: Status, elapsedNs: Long) {
        Timer.builder("hybrid_search_latency")
            .description("Per-channel latency of the hybrid search pipeline")
            .tag("source", source.label)
            .tag("status", status.label)
            .publishPercentileHistogram()
            .register(registry)
            .record(elapsedNs, TimeUnit.NANOSECONDS)
    }

    fun recordIndexingChannel(source: Source, status: Status, elapsedMs: Long) {
        Timer.builder("hybrid_indexing_latency")
            .description("Per-store latency of bulk indexing")
            .tag("source", source.label)
            .tag("status", status.label)
            .publishPercentileHistogram()
            .register(registry)
            .record(elapsedMs, TimeUnit.MILLISECONDS)
    }

    fun recordAnalyze(status: Status, elapsedNs: Long) {
        Timer.builder("hybrid_analyze_latency")
            .description("Nori _analyze latency (query classification path)")
            .tag("status", status.label)
            .publishPercentileHistogram()
            .register(registry)
            .record(elapsedNs, TimeUnit.NANOSECONDS)
    }

    fun recordRouting(queryType: QueryType) {
        registry.counter(
            "hybrid_search_routing",
            "query_type", queryType.name,
        ).increment()
    }

    fun recordOrphan(count: Int) {
        if (count <= 0) return
        registry.counter("hybrid_indexing_orphans_total").increment(count.toDouble())
    }

    fun <T> timeChannel(source: Source, block: () -> T): T {
        val start = System.nanoTime()
        return try {
            val result = block()
            recordSearchChannel(source, Status.OK, System.nanoTime() - start)
            result
        } catch (e: Throwable) {
            recordSearchChannel(source, Status.FAILED, System.nanoTime() - start)
            throw e
        }
    }
}
