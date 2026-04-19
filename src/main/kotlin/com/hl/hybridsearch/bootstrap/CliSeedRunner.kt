package com.hl.hybridsearch.bootstrap

import com.hl.hybridsearch.indexing.IndexingService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 로컬/테스트 환경에서 합성 카탈로그를 적재하는 시드 러너.
 * Phase 3에서 SyntheticCatalogGenerator가 추가되면 이 러너가 소비한다.
 *
 * 활성화: `-Dspring.profiles.active=seed` 또는 `SPRING_PROFILES_ACTIVE=seed`.
 * 엔터프라이즈에서는 동일한 IndexingService가 Kafka 컨슈머(별 프로파일)에서도 호출된다.
 */
@Component
@Profile("seed")
class CliSeedRunner(
    private val indexingService: IndexingService,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        log.info(
            "[seed] CliSeedRunner active. IndexingService bulk API is ready; " +
                "SyntheticCatalogGenerator (Phase 3) will stream Product batches through it."
        )
        // Phase 3에서 generator 주입 후 아래 호출로 이어진다.
        // val products: Sequence<Product> = generator.generate(total = 100_000)
        // products.chunked(10_000).forEach { indexingService.bulkIndex(it) }
    }
}
