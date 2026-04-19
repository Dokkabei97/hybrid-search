package com.hl.hybridsearch

import com.hl.hybridsearch.config.SearchProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(SearchProperties::class)
class HybridSearchApplication

fun main(args: Array<String>) {
    runApplication<HybridSearchApplication>(*args)
}
