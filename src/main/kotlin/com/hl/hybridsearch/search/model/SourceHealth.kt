package com.hl.hybridsearch.search.model

/**
 * 검색 응답의 소스별 상태.
 * KEYWORD 경로는 vector 채널을 호출하지 않으므로 `vector=DISABLED`로 나간다.
 *
 * 관측 가능성: Prometheus 레이블 `source={lexical|vector}, status={ok|failed|disabled}` 로
 * 직접 집계 가능하도록 설계.
 */
data class SourceHealth(
    val lexical: Status,
    val vector: Status,
) {
    enum class Status { OK, FAILED, DISABLED }

    companion object {
        val LEXICAL_ONLY = SourceHealth(lexical = Status.OK, vector = Status.DISABLED)
    }
}
