package com.hl.hybridsearch.bootstrap

import com.hl.hybridsearch.catalog.SyntheticCatalogGenerator
import com.hl.hybridsearch.indexing.IndexingService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 로컬/벤치 환경에서 합성 카탈로그를 적재하는 시드 러너.
 *
 * 활성화: `-Dspring.profiles.active=seed` (또는 `SPRING_PROFILES_ACTIVE=seed`).
 *
 * CLI 옵션 (모두 선택):
 *   --seed.total=100000       전체 상품 수 (기본 100000)
 *   --seed.seed=42            난수 seed — 동일 seed → 동일 카탈로그
 *   --seed.chunk=10000        단일 bulkIndex 호출당 문서 수 (기본 10000)
 *
 * 엔터프라이즈에서는 동일한 IndexingService를 Kafka 컨슈머가 호출하면 된다.
 */
@Component
@Profile("seed")
class CliSeedRunner(
    private val indexingService: IndexingService,
    private val generator: SyntheticCatalogGenerator,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val total = args.single("seed.total")?.toInt() ?: DEFAULT_TOTAL
        val seed = args.single("seed.seed")?.toLong() ?: DEFAULT_SEED
        val chunk = args.single("seed.chunk")?.toInt() ?: DEFAULT_CHUNK

        log.info("[seed] generating catalog total={}, seed={}, chunkSize={}", total, seed, chunk)
        val start = System.currentTimeMillis()

        val sequence = generator.generate(total = total, seed = seed)
        var indexed = 0
        var failed = 0

        sequence.chunked(chunk).forEachIndexed { idx, batch ->
            val result = indexingService.bulkIndex(batch)
            indexed += result.consistent.size
            failed += result.lexical.failed.size + result.orphanedInLexical.size
            log.info(
                "[seed] chunk {} done: consistent={}, lex_fail={}, orphans={}, tookMs={}",
                idx + 1, result.consistent.size,
                result.lexical.failed.size, result.orphanedInLexical.size, result.tookMs,
            )
        }

        val elapsed = System.currentTimeMillis() - start
        log.info(
            "[seed] finished in {}ms — indexed(consistent)={}, failed/orphaned={}",
            elapsed, indexed, failed,
        )
    }

    private fun ApplicationArguments.single(name: String): String? =
        getOptionValues(name)?.firstOrNull()

    companion object {
        private const val DEFAULT_TOTAL = 100_000
        private const val DEFAULT_SEED = 42L
        private const val DEFAULT_CHUNK = 10_000
    }
}
