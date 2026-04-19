package com.hl.hybridsearch.eval

import com.hl.hybridsearch.analyzer.QueryType
import com.hl.hybridsearch.indexing.IndexingService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 평가 CLI 러너.
 * 활성화: `-Dspring.profiles.active=eval`
 *
 * CLI 옵션:
 *   --eval.corpus=10000    생성할 카탈로그 크기
 *   --eval.queries=10      카테고리당 쿼리 수 (KEYWORD/SENTENCE 반반)
 *   --eval.seed=42
 *   --eval.skipIndexing=false   ES/Qdrant에 이미 데이터가 있으면 true
 *
 * 흐름:
 *   1) GoldSet 생성
 *   2) (skipIndexing=false면) bulkIndex 로 ES+Qdrant 적재
 *   3) 세 가지 전략으로 평가 → 리포트 출력
 */
@Component
@Profile("eval")
class CliEvaluationRunner(
    private val builder: SyntheticGoldSetBuilder,
    private val indexingService: IndexingService,
    private val evaluator: EvaluationRunner,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val corpusSize = args.single("eval.corpus")?.toInt() ?: 10_000
        val queriesPerCategory = args.single("eval.queries")?.toInt() ?: 10
        val seed = args.single("eval.seed")?.toLong() ?: 42L
        val skipIndexing = args.single("eval.skipIndexing")?.toBoolean() ?: false

        log.info(
            "[eval] building gold set corpus={}, queries/category={}, seed={}",
            corpusSize, queriesPerCategory, seed
        )
        val gold = builder.build(corpusSize, queriesPerCategory, seed)
        log.info("[eval] gold built: corpus={}, queries={}", gold.corpusSize, gold.querySize)

        if (!skipIndexing) {
            log.info("[eval] indexing corpus ...")
            val result = indexingService.bulkIndex(gold.corpus)
            log.info(
                "[eval] indexed: consistent={}, lex_fail={}, orphans={}",
                result.consistent.size, result.lexical.failed.size,
                result.orphanedInLexical.size,
            )
        } else {
            log.info("[eval] skipIndexing=true — assuming ES/Qdrant are already populated")
        }

        val classifier = evaluator.run(gold, "classifier", forceType = null)
        val alwaysSentence = evaluator.run(gold, "always-sentence", forceType = QueryType.SENTENCE)
        val alwaysLexical = evaluator.run(gold, "always-lexical", forceType = QueryType.KEYWORD)

        printReports(listOf(classifier, alwaysSentence, alwaysLexical))
    }

    private fun printReports(reports: List<EvaluationReport>) {
        log.info("\n{}", buildString {
            appendLine("=================================================")
            appendLine("Evaluation summary")
            appendLine("=================================================")
            reports.forEach { append(it.summary()).appendLine() }

            appendLine("-- split by expectedType --")
            reports.forEach { r ->
                val (kw, sn) = r.splitByExpectedType()
                append(kw.summary()).appendLine()
                append(sn.summary()).appendLine()
            }
        })
    }

    private fun ApplicationArguments.single(name: String): String? =
        getOptionValues(name)?.firstOrNull()
}
