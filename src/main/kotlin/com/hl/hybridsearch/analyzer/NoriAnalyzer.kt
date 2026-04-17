package com.hl.hybridsearch.analyzer

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.indices.analyze.ExplainAnalyzeToken
import com.hl.hybridsearch.config.SearchProperties
import jakarta.json.JsonString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Nori(mecab-ko-dic) 기반 질의 분석.
 *
 * 인덱스 `documents`의 `nori_raw` analyzer(필터 없이 토크나이저만)를
 * `_analyze?explain=true`로 호출해 POS 정보를 포함한 토큰 리스트를 얻는다.
 *
 * POS 매핑:
 *   - NN*, NR, NP            → NOUN
 *   - VV, VX                 → VERB
 *   - VA                     → ADJECTIVE
 *   - MAG, MAJ               → ADVERB
 *   - J*                     → JOSA
 *   - E*                     → EOMI
 *   - S*                     → PUNCTUATION (discard_punctuation=true라 보통 오지 않음)
 *   - 나머지                  → OTHER
 */
@Component
class NoriAnalyzer(
    private val esClient: ElasticsearchClient,
    private val properties: SearchProperties,
) : MorphologyAnalyzer {

    private val log = LoggerFactory.getLogger(javaClass)
    private val analyzerName = "nori_raw"

    override fun analyze(text: String): QueryFeatures {
        if (text.isBlank()) return QueryFeatures(raw = text, tokens = emptyList())

        val response = esClient.indices().analyze { r ->
            r.index(properties.indexName)
                .analyzer(analyzerName)
                .explain(true)
                .text(text)
        }

        val tokens = response.detail()
            ?.tokenizer()
            ?.tokens()
            ?.mapNotNull { toToken(it) }
            ?.filter { it.pos != QueryFeatures.Pos.PUNCTUATION && it.pos != QueryFeatures.Pos.OTHER }
            ?: emptyList()

        return QueryFeatures(raw = text, tokens = tokens)
    }

    private fun toToken(t: ExplainAnalyzeToken): QueryFeatures.Token? {
        val surface = t.token() ?: return null
        val posCode = extractPosCode(t, "leftPOS") ?: extractPosCode(t, "rightPOS") ?: ""
        return QueryFeatures.Token(surface = surface, pos = mapPos(posCode))
    }

    private fun extractPosCode(t: ExplainAnalyzeToken, key: String): String? {
        val attr = t.attributes()[key] ?: return null
        val raw = when (val v = attr.toJson()) {
            is JsonString -> v.string
            else -> v.toString().trim('"')
        }
        if (raw.isBlank()) return null
        return raw.substringBefore("(")
    }

    private fun mapPos(code: String): QueryFeatures.Pos = when {
        code.startsWith("NN") || code == "NR" || code == "NP" -> QueryFeatures.Pos.NOUN
        code == "VA" -> QueryFeatures.Pos.ADJECTIVE
        code.startsWith("V") -> QueryFeatures.Pos.VERB
        code.startsWith("MA") -> QueryFeatures.Pos.ADVERB
        code.startsWith("J") -> QueryFeatures.Pos.JOSA
        code.startsWith("E") -> QueryFeatures.Pos.EOMI
        code.startsWith("S") -> QueryFeatures.Pos.PUNCTUATION
        else -> QueryFeatures.Pos.OTHER
    }
}
