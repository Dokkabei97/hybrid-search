package com.hl.hybridsearch.eval

import com.hl.hybridsearch.analyzer.QueryType

data class QueryResult(
    val query: GoldQuery,
    val actualType: QueryType,
    val ndcg10: Double,
    val mrr: Double,
    val recall50: Double,
    val degraded: Boolean,
)

data class EvaluationReport(
    val strategyName: String,
    val perQuery: List<QueryResult>,
    val meanNdcg10: Double,
    val meanMrr: Double,
    val meanRecall50: Double,
    val degradedCount: Int,
) {
    fun summary(): String = buildString {
        appendLine("=== $strategyName ===")
        appendLine("queries=${perQuery.size}, degraded=$degradedCount")
        appendLine("NDCG@10 = %.4f".format(meanNdcg10))
        appendLine("MRR     = %.4f".format(meanMrr))
        appendLine("Recall@50 = %.4f".format(meanRecall50))
    }

    /** KEYWORD 쿼리만 / SENTENCE 쿼리만 으로 갈라 본 서브 리포트. */
    fun splitByExpectedType(): Pair<EvaluationReport, EvaluationReport> {
        val keywordOnly = perQuery.filter { it.query.expectedType == QueryType.KEYWORD }
        val sentenceOnly = perQuery.filter { it.query.expectedType == QueryType.SENTENCE }
        return aggregate("$strategyName/KEYWORD", keywordOnly) to
            aggregate("$strategyName/SENTENCE", sentenceOnly)
    }

    companion object {
        fun aggregate(name: String, results: List<QueryResult>): EvaluationReport {
            if (results.isEmpty()) {
                return EvaluationReport(name, emptyList(), 0.0, 0.0, 0.0, 0)
            }
            return EvaluationReport(
                strategyName = name,
                perQuery = results,
                meanNdcg10 = results.map { it.ndcg10 }.average(),
                meanMrr = results.map { it.mrr }.average(),
                meanRecall50 = results.map { it.recall50 }.average(),
                degradedCount = results.count { it.degraded },
            )
        }
    }
}
