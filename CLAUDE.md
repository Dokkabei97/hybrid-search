# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project context

한국어 이커머스 검색을 대상으로 하는 **하이브리드 검색(ES BM25 + Qdrant 벡터) 스터디 프로젝트**.
"엔터프라이즈 운영환경에 약간만 손보면 적용 가능한 수준"을 목표로 하되, 스터디의 학습 루프를 해치는
오버엔지니어링은 의도적으로 배제한다 (서킷브레이커·Micrometer·인증 등 — 아래 "Out of scope" 참고).

- Stack: Kotlin 2.2, Spring Boot 4.0.5, Spring AI 2.0.0-M4 (Ollama + Qdrant), Elasticsearch 9 (Nori)
- JDK toolchain 21, Gradle Kotlin DSL

## Build, test, run

```bash
# 컴파일만
./gradlew compileKotlin

# 전체 테스트 (HybridSearchApplicationTests#contextLoads는 @Disabled, 나머지 단위 테스트는 외부 의존 0)
./gradlew test

# 단일 테스트 클래스/메서드
./gradlew test --tests "com.hl.hybridsearch.search.ReciprocalRankFusionTest"
./gradlew test --tests "*ReciprocalRankFusionTest.weights scale channel contribution"

# 앱 기동 (기본 프로파일 — Spring AI auto-config가 ES/Qdrant/Ollama에 연결 시도)
./gradlew bootRun

# 합성 카탈로그 적재 (결정론적, seed 동일 → 동일 카탈로그)
./gradlew bootRun --args='--spring.profiles.active=seed --seed.total=100000 --seed.seed=42 --seed.chunk=10000'

# NDCG/MRR 평가 A/B (classifier vs always-sentence vs always-lexical)
./gradlew bootRun --args='--spring.profiles.active=eval --eval.corpus=10000 --eval.queries=10 --eval.seed=42'
```

### Local infra (docker-compose)

`docker-compose.yml`은 ES 9 + Qdrant 1.17.1 + Ollama를 띄운다. **Nori 플러그인은 수동 설치 필요** —
docker-compose up 이후:

```bash
docker exec hs-elasticsearch bin/elasticsearch-plugin install --batch analysis-nori
docker restart hs-elasticsearch
docker exec hs-ollama ollama pull embeddinggemma   # Google EmbeddingGemma-300m, 768d, context 2048
```

### 임베딩 모델 교체 시 체크리스트
1. `spring.ai.ollama.embedding.options.model` 환경변수/설정 교체
2. `search.embedding.dimension` 을 새 모델 출력 차원으로 수정 (Gemma 768, Qwen3 1024, OpenAI v3-large 3072 등)
3. `search.embedding.query-instruction` / `document-instruction` 을 모델 권장 포맷으로 (또는 빈 문자열로 off)
4. Qdrant 컬렉션 재생성 — 차원이 바뀌면 기존 벡터와 호환 불가. `products` 컬렉션 삭제 후 앱 재기동 → `initialize-schema=true` 가 새 차원으로 재생성
5. 합성 카탈로그 재시딩: `./gradlew bootRun --args='--spring.profiles.active=seed'`
자세한 prefix 매트릭스는 `docs/embedding-research.md` §10 참고.

## Architecture — 큰 그림

### Query path (검색)
1. `NoriAnalyzer`가 ES `_analyze?explain=true`를 호출 → `QueryFeatures` (POS 태그 포함)
2. `QueryClassifier`가 공격적 KEYWORD 성향으로 분기 판정:
   - **KEYWORD** → `LexicalSearcher.searchMulti` 단일 호출 (ES only)
   - **SENTENCE** → title-BM25 + multi-field-BM25 + vector 3채널 **coroutine 병렬** → `ReciprocalRankFusion.fuse(lists, weights)`로 융합
3. 각 채널 실패는 `SourceHealth(lexical, vector)` 상태로 응답에 노출 (`OK | FAILED | DISABLED`)
4. `SearchResponse.degraded`는 `sources`에서 파생되는 **computed property** — single source of truth

### Index path (색인)
- `IndexingService` = 포트 기반 dual-write 코디네이터
- `LexicalWriter`(`ElasticsearchBulkWriter`) **먼저 성공한 id만** `VectorWriter`(`QdrantBulkWriter`)에 전달 → dual-store inconsistency 최소화
- 배치(500)/동시성(Semaphore 4)/지수 백오프 재시도(200ms→5s)는 `search.indexing.*` 설정
- `BulkIndexResult.orphanedInLexical`은 reconciliation 후보 (현재는 로깅만)

### Analyzer 3-way (한국어 index/search 비대칭)
`src/main/resources/elasticsearch/index-template.json`에서 세 analyzer를 정의한다:
- `nori_index` — 색인용 (`decompound_mode=mixed`, stoptags 확장, readingform)
- `nori_search` — 검색용 (`decompound_mode=discard`) — 짧은 쿼리가 과분해되는 것을 방지
- `nori_classify` — `QueryClassifier` 전용 (tokenizer only, POS 보존)

### Evaluation loop (eval 프로파일)
- `SyntheticCatalogGenerator` (`catalog/`)와 `SyntheticGoldSetBuilder` (`eval/`)는 **동일 seed**를 공유 → corpus와 gold queries가 비트 단위 재현 가능
- `EvaluationRunner.run(gold, strategy, forceType)` — `SearchService.evaluate(request, forceType)`(internal) 로 분류기를 우회해 A/B 전략 비교:
  - `classifier` (prod 경로, `forceType = null`)
  - `always-sentence` (`forceType = SENTENCE`)
  - `always-lexical` (`forceType = KEYWORD`)
- `SearchController` 는 `SearchService.search(request)` 만 호출 — eval 전용 `forceType` 파라미터는 `internal fun evaluate` 로 격리되어 prod API 에 노출되지 않는다.
- `EvaluationReport.splitByExpectedType()` — KEYWORD/SENTENCE 세그먼트별 메트릭

### Profile-gated runners
- `@Profile("seed")` → `CliSeedRunner` 만 활성
- `@Profile("eval")` → `CliEvaluationRunner` 만 활성 (기본적으로 seed + evaluate를 모두 수행)

### 설정 외부화
모든 튜닝 파라미터는 `SearchProperties` (`search.*` 네임스페이스)를 통한다 —
`classifier.{maxKeywordTokens,nounRatioThreshold,maxParticlesForKeyword}`,
`rrf.{k,titleWeight,bodyWeight,vectorWeight}`, `topK.*`, `embedding.*`, `indexing.*`, `fallback.*`.

## Design conventions

- **포트는 `indexing/port/`, 어댑터는 `indexing/adapter/`**. 새 스토어가 늘어도 이 패턴 유지.
- **계층 방향**: `api → search → (analyzer, indexing, catalog) → config`. 역참조·순환 금지.
- **`eval` 패키지는 leaf (application/driver)** — 다른 패키지는 `eval`을 import하지 않는다.
- **결정론**: 랜덤이 들어가는 모든 컴포넌트(`SyntheticCatalogGenerator`, `SyntheticGoldSetBuilder`)는 `seed: Long` 파라미터 필수. 같은 seed → 같은 결과.
- **응답 계약**: `SearchResponse.sources`를 항상 채워야 함 (KEYWORD 경로는 `vector=DISABLED`).
- **컨트롤러 입력 검증**: `SearchRequest.init { require(...) }`로 생성 시점 검증. `GlobalExceptionHandler`가 `IllegalArgumentException` → 400 매핑.

## Out of scope (의도적 제외 — 재도입 전 사용자 확인)

- **Micrometer 메트릭 / Resilience4j 서킷브레이커** — 스터디 범위 밖. PR #7로 시도했으나 close됨. 필요 시 구조적 확장점은 이미 준비됨 (`SourceHealth`, `IndexingPort`).
- **인증/권한** — `/api/products`, `/api/search` 공개.
- **Testcontainers 통합 테스트** — `HybridSearchApplicationTests#contextLoads`는 `@Disabled`.
- **typed `EsProductSource` DTO** — 현재 `SearchHit.payload: Map<String, Any?>` 유지.
- **Qdrant HNSW/quantization 튜닝** — 기본값 (Spring AI `initialize-schema=true`).

## Canonical form & single-source-of-truth 정책

아래 세 가지는 함께 움직인다 — 하나만 건드리면 silent mismatch 발생:

1. **`SearchFilters.of(...)`** (`search/model/SearchFilters.kt`) — primary constructor 는 private.
   `brand` 는 `trim().lowercase()`, `categoryL1` 은 `trim()` (케이스 보존). `@ConsistentCopyVisibility` 로 `copy()` 도 막혀있어 factory 단일 관문이 진짜로 강제된다.
2. **`Product.toEsFields(now)` / `toVectorPayload()`** (`indexing/Product.kt`) — 저장 표현을 도메인이 소유.
   `toVectorPayload` 의 `brand` 는 lowercase (ES `keyword_lower` normalizer 와 동일 결과). 어댑터는 이 두 메서드를 호출만 한다 — 스키마 해석을 어댑터 쪽에 흩지 말 것.
3. **ES index-template** (`src/main/resources/elasticsearch/index-template.json`) — `brand` 는 `keyword_lower` normalizer, `category.*` 는 normalizer 없음. 새 필드 추가 시 (1)(2)(3) 을 한 묶음으로 본다.

계약 테스트: `ProductSchemaTest` (키셋 고정), `SearchFiltersTest` (정규화 규칙 고정), `VectorFilterTest` (AST 매핑 고정).

## Git / PR workflow

- `main`은 **squash merge**로만 통합 (`gh pr merge <N> --squash --delete-branch`).
- Commit/PR 메시지는 Conventional Commits (`feat(area): ...`, `refactor(area): ...`, `chore: ...`).
- 각 PR은 Phase 단위로 스코프 고정 — 한 PR에 스키마 변경과 기능 추가를 섞지 않는다.

## Kiwi morphology (not used)

과거에 Kiwi 형태소 분석을 시도했으나 Maven Central 미배포로 `open-korean-text`를 거쳐 최종적으로 **Nori `_analyze` 호출 방식**으로 수렴. `MorphologyAnalyzer` 추상이 남아있어 나중에 Kiwi HTTP 프록시로 교체 가능.
