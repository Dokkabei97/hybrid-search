# hybrid-search

한국어 이커머스 검색을 대상으로 하는 **하이브리드 검색 스터디 프로젝트** — Elasticsearch(BM25, Nori) + Qdrant(dense vector) 를 RRF 로 융합한다. "엔터프라이즈 운영환경에 약간만 손보면 적용 가능한 수준"을 목표로 하면서도 학습 루프를 해치는 오버엔지니어링은 의도적으로 배제했다 (서킷브레이커·메트릭·인증 등).

## Stack

| | |
|---|---|
| Language | Kotlin 2.2 (JDK 21 toolchain) |
| Framework | Spring Boot 4.0.5, Spring AI 2.0.0-M4 |
| Lexical | Elasticsearch 9 + `analysis-nori` (index/search/classify 3-way analyzer) |
| Vector | Qdrant 1.17 (gRPC 직접 호출) |
| Embedding | Ollama + Google EmbeddingGemma-300m (768d) |
| Build | Gradle Kotlin DSL |

## Quickstart

### 1. 로컬 인프라

```bash
docker-compose up -d
docker exec hs-elasticsearch bin/elasticsearch-plugin install --batch analysis-nori
docker restart hs-elasticsearch
docker exec hs-ollama ollama pull embeddinggemma
```

### 2. 합성 카탈로그 시딩 (결정론적)

```bash
./gradlew bootRun --args='--spring.profiles.active=seed --seed.total=100000 --seed.seed=42'
```

### 3. 앱 기동

```bash
./gradlew bootRun
```

### 4. 검색 호출

```bash
curl 'http://localhost:8080/api/search?q=여름+원피스&categoryL1=패션의류&brand=유니클로&size=10'
```

## Architecture

### Query path

```
query
  │
  ├─ NoriAnalyzer._analyze?explain=true  ─┐
  │                                       ├─ QueryClassifier → KEYWORD | SENTENCE
  │                                       ┘
  │   ┌────────── KEYWORD ────────── LexicalSearcher.searchMulti (ES only)
  └───┤
      └────────── SENTENCE ──── coroutine 3채널 병렬
                                 ├─ LexicalSearcher.searchTitle     (BM25, title 단일)
                                 ├─ LexicalSearcher.searchMulti     (BM25, title/brand/description)
                                 └─ VectorSearcher → VectorQueryPort → QdrantVectorAdapter
                                 └─ ReciprocalRankFusion.fuse(lists, weights)
```

- 채널별 장애는 `SourceHealth(lexical, vector)` 로 응답에 노출 (OK | FAILED | DISABLED)
- `SearchResponse.degraded` 는 `sources` 에서 파생되는 computed property — single source of truth
- 벡터 조회는 Qdrant gRPC 네이티브 클라이언트 직접 사용 (`hnsw_ef` / `exact` 튜닝 위해)

### Index path

```
Product
  │
  ├─ ElasticsearchBulkWriter.upsert  (ES bulk API, 부분성공 추적)
  │       │ 성공한 id 만
  │       ▼
  └─ QdrantBulkWriter.upsert         (Spring AI VectorStore, all-or-nothing)

BulkIndexResult.orphanedInLexical → 향후 reconciliation 후보
```

- 배치(500) / 동시성(Semaphore 4) / 지수 백오프 재시도 — 모두 `search.indexing.*` 로 외부화
- ES 먼저 성공한 id 만 Qdrant 로 넘겨 dual-store inconsistency 최소화

### Port / Adapter 경계

```
search/port/                     indexing/port/
  VectorQueryPort         ◄──    VectorWriter, LexicalWriter
  VectorFilter (AST)
  VectorSearchMode

search/adapter/                  indexing/adapter/
  QdrantVectorAdapter            ElasticsearchBulkWriter
                                 QdrantBulkWriter (uses Spring AI VectorStore)
```

**읽기는 네이티브, 쓰기는 추상** 비대칭 구성. 쓰기는 "임베딩 → upsert" 이상의 튜닝 여지가 적어 `VectorStore` 를 유지하고, 읽기는 `hnsw_ef` / `exact` / payload filter 같은 벤더 기능이 경쟁력이라 직접 호출한다.

벤더 교체 시 어댑터 한 장만 바꾸면 된다 (Qdrant → Milvus/Weaviate).

## Project layout

```
src/main/kotlin/com/hl/hybridsearch/
├── HybridSearchApplication.kt
├── analyzer/       ── NoriAnalyzer, QueryClassifier (공격적 KEYWORD 라우팅)
├── api/            ── REST Controllers + GlobalExceptionHandler
├── bootstrap/      ── CliSeedRunner, IndexTemplateInitializer
├── catalog/        ── SyntheticCatalogGenerator (결정론적 seed 기반)
├── config/         ── SearchProperties (모든 튜닝 파라미터)
├── eval/           ── NDCG/MRR 평가 러너 + 합성 gold set
├── indexing/       ── IndexingService + port/adapter (ES/Qdrant dual-write)
└── search/
    ├── SearchService.kt
    ├── LexicalSearcher.kt
    ├── VectorSearcher.kt       (파사드)
    ├── QueryEmbedder.kt        (prefix 템플릿 포함)
    ├── ReciprocalRankFusion.kt
    ├── port/                   VectorQueryPort, VectorFilter, VectorSearchMode
    └── adapter/                QdrantVectorAdapter (gRPC 직접)
```

## Configuration

모든 튜닝은 `application.yaml` 의 `search.*` 네임스페이스를 통한다.

| 키 | 기본 | 설명 |
|---|---|---|
| `search.classifier.maxKeywordTokens` | 5 | 이 이하면 KEYWORD 라우팅 |
| `search.classifier.nounRatioThreshold` | 0.5 | 명사 비율 이상이면 KEYWORD |
| `search.rrf.k` | 60 | RRF 공식의 k 상수 |
| `search.rrf.{title,body,vector}Weight` | 1.0 | 채널 가중치 |
| `search.topK.{lexical,vector}` | 50 | 채널별 후보 풀 크기 |
| `search.embedding.dimension` | 768 | EmbeddingGemma 기본 |
| `search.embedding.queryInstruction` | `"task: ... query: {query}"` | 쿼리 prefix (Gemma 지침) |
| `search.embedding.documentInstruction` | `"title: {title} | text: {text}"` | 문서 prefix |
| `search.vector.fastHnswEf` | 64 | 프로덕션 기본 HNSW ef |
| `search.vector.accurateHnswEf` | 256 | recall 우선 HNSW ef |
| `search.indexing.{batchSize,concurrency,maxRetries}` | 500 / 4 / 3 | 벌크 인덱싱 파라미터 |

### 임베딩 모델 교체

1. `spring.ai.ollama.embedding.options.model` 교체
2. `search.embedding.dimension` 을 새 차원으로
3. `search.embedding.{query,document}Instruction` 를 모델 권장 포맷으로 (off 는 빈 문자열)
4. Qdrant `products` 컬렉션 삭제 → 앱 재기동 (새 차원으로 재생성)
5. 카탈로그 재시딩

상세 prefix 매트릭스: [`docs/embedding-research.md`](docs/embedding-research.md) §10

## Evaluation

A/B 라우팅 전략 비교 (classifier vs always-sentence vs always-lexical):

```bash
./gradlew bootRun --args='--spring.profiles.active=eval --eval.corpus=10000 --eval.queries=10 --eval.seed=42'
```

- `SyntheticCatalogGenerator` 와 `SyntheticGoldSetBuilder` 는 **같은 seed** 를 공유 → 카탈로그·질의 세트가 비트 단위 재현
- `EvaluationReport.splitByExpectedType()` 로 KEYWORD/SENTENCE 세그먼트별 NDCG/MRR 분리 측정

## Build & test

```bash
./gradlew compileKotlin                   # 컴파일
./gradlew test                            # 전체 단위 테스트 (외부 의존 0)
./gradlew test --tests "*RrfTest*"        # 선택 테스트
```

`HybridSearchApplicationTests#contextLoads` 는 `@Disabled` (Testcontainers 미도입).

## Out of scope — 의도적 제외

재도입 전 사용자 확인 필요. 구조적 확장점은 이미 준비됨.

- **Micrometer 메트릭 / Resilience4j 서킷브레이커** — `SourceHealth` / `IndexingPort` 는 확장 지점 역할
- **인증/권한** — `/api/products`, `/api/search` 공개
- **Testcontainers 통합 테스트**
- **typed `EsProductSource` DTO** — 현재 `SearchHit.payload: Map<String, Any?>` 유지
- **Qdrant HNSW/quantization collection-level 튜닝** — Spring AI `initialize-schema=true` 기본값 사용
- **Qdrant payload index 생성** — 대량 데이터 필터 성능 필요 시 추가

## Known boundary issues

1. **`SearchService.search(request, forceType)` public 노출** — 평가 전용 파라미터가 프로덕션 시그니처에 섞임
2. **`Product` → ES/Qdrant 변환 이중화** — `ElasticsearchBulkWriter.toEsDocument` + `QdrantBulkWriter.toAiDocument` 가 각자 스키마 해석. `Product.toEsFields()` / `toVectorMetadata()` 로 단일 출처 이전 예정
3. **Brand 케이스 비대칭** — ES 는 `keyword_lower` normalizer 로 소문자, Qdrant 는 원본 케이스 저장. 필터링 시 두 스토어 결과가 비대칭

## Git workflow

- `main` 은 **squash merge** 로만 통합 (`gh pr merge <N> --squash --delete-branch`)
- 커밋/PR 메시지: Conventional Commits (`feat(area): ...`, `refactor(area): ...`, `chore: ...`)
- 각 PR 은 Phase 단위로 스코프 고정 — 스키마 변경과 기능 추가를 섞지 않는다

## See also

- [`CLAUDE.md`](CLAUDE.md) — 아키텍처·컨벤션 상세 (Claude Code 용)
- [`docs/embedding-research.md`](docs/embedding-research.md) — 임베딩 모델 선택·prefix 매트릭스 리서치
