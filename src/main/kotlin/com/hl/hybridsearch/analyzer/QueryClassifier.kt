package com.hl.hybridsearch.analyzer

import com.hl.hybridsearch.config.SearchProperties
import org.springframework.stereotype.Component

/**
 * 질의 특성(QueryFeatures)을 기반으로 KEYWORD/SENTENCE를 판정한다.
 *
 * 라우팅 영향:
 *   - KEYWORD  → ES(Nori) 단일 경로 (임베딩/벡터 비용 X)
 *   - SENTENCE → ES + Qdrant 병렬 + RRF 융합
 *
 * 오분류 비용:
 *   - KEYWORD로 잘못 분류 → 의미 검색 누락 (recall 손해)
 *   - SENTENCE로 잘못 분류 → 불필요한 임베딩/벡터 호출 (latency/비용 손해)
 *
 * 규칙은 [SearchProperties.ClassifierProperties]로 튜닝 가능:
 *   - maxKeywordTokens          : 이하면 KEYWORD로 간주하는 상한
 *   - nounRatioThreshold        : 이상이면 KEYWORD 성향
 *   - maxParticlesForKeyword    : 조사+어미 합계가 이 이하여야 KEYWORD
 */
@Component
class QueryClassifier(
    private val properties: SearchProperties,
) {

    fun classify(features: QueryFeatures): QueryType {
        if (features.tokenCount == 0) return QueryType.KEYWORD

        val cfg = properties.classifier

        // 공격적 KEYWORD 라우팅: 확실한 "문장형" 신호일 때만 SENTENCE로 간다.
        // 1) 토큰이 짧으면 조사가 있어도 KEYWORD (예: "좋은 계곡 추천")
        if (features.tokenCount <= cfg.maxKeywordTokens) return QueryType.KEYWORD
        // 2) 명사 비율이 임계값 이상이면 KEYWORD (예: "삼성전자 2025 주가 전망")
        if (features.nounRatio >= cfg.nounRatioThreshold) return QueryType.KEYWORD
        // 3) 서술어(동사/형용사)와 조사/어미가 함께 충분할 때만 SENTENCE
        val hasPredicate = (features.verbCount + features.adjectiveCount) > 0
        val manyParticles = features.particleCount > cfg.maxParticlesForKeyword
        return if (hasPredicate && manyParticles) QueryType.SENTENCE else QueryType.KEYWORD
    }
}
