package com.hl.hybridsearch

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@Disabled(
    "Requires running Elasticsearch, Qdrant, and Ollama. Will be replaced by a " +
        "Testcontainers-based integration test (Phase 6) that spins these up in-process."
)
class HybridSearchApplicationTests {

    @Test
    fun contextLoads() {
    }
}
