package com.hl.hybridsearch.bootstrap

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.hl.hybridsearch.config.SearchProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

@Component
class IndexTemplateInitializer(
    private val esClient: ElasticsearchClient,
    private val resourceLoader: ResourceLoader,
    private val properties: SearchProperties,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val resourcePath = "classpath:elasticsearch/index-template.json"

    override fun run(args: ApplicationArguments) {
        registerTemplate()
        ensureIndex()
    }

    private fun registerTemplate() {
        val name = properties.indexTemplateName
        try {
            resourceLoader.getResource(resourcePath).inputStream.use { stream ->
                esClient.indices().putIndexTemplate { r ->
                    r.name(name).withJson(stream)
                }
            }
            log.info("Index template '{}' registered", name)
        } catch (e: Exception) {
            log.warn("Failed to register index template '{}'", name, e)
        }
    }

    private fun ensureIndex() {
        val indexName = properties.indexName
        try {
            val exists = esClient.indices().exists { r -> r.index(indexName) }.value()
            if (!exists) {
                esClient.indices().create { r -> r.index(indexName) }
                log.info("Index '{}' created from template", indexName)
            } else {
                log.debug("Index '{}' already exists", indexName)
            }
        } catch (e: Exception) {
            log.warn("Failed to ensure index '{}'", indexName, e)
        }
    }
}
