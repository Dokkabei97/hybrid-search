# 이커머스 시맨틱 검색 임베딩 리서치

> 한국어 이커머스 카탈로그를 대상으로 "무엇을, 어떤 포맷으로, 어느 길이까지 임베딩할 것인가"를
> 학계·산업 사례·모델 벤더 공식 문서까지 종합해 정리한 문서.
> 현 프로젝트(`dengcao/Qwen3-Embedding-0.6B` + Qdrant + ES Nori)를 중심 케이스로 하되,
> **OpenAI / Gemini / EmbeddingGemma / BGE-M3 / Cohere / Voyage / Nomic 교체 시에도 재활용 가능하도록**
> 모델 불문 철칙과 모델별 조정 지점을 분리해 기술한다.

---

## 0. TL;DR (5줄)

1. **숫자(가격·평점·재고)는 절대 임베딩 텍스트에 넣지 말고** payload filter로 분리한다.
2. **리뷰 원문 concat 금지**, aspect 키프레이즈(3~5개)만 구조화 필드로 추가한다.
3. `title + brand + category + 핵심 attributes + description(선두 300~400자)` **구조화 prefix**가 title-only 대비 NDCG +3~6%.
4. **쿼리 임베딩 캐시**(Caffeine, TTL 1h)는 ROI 최고 — 이커머스 쿼리는 Zipf 분포(상위 1k가 40~60% 트래픽).
5. 위 네 가지는 **모델 불문 보편 성립**. 모델 교체 시 조정하는 것은 `instruction prefix 포맷`, `description 컷오프 값`, `브랜드 alias 필요 여부` 세 축뿐이다.

---

## 1. 이 문서의 범위

- **대상 태스크**: 이커머스 상품 검색(retrieval) — 쿼리 → 상품 랭킹. 추천/개인화는 범위 밖.
- **대상 모델**: Dense text embedding 일반 (OpenAI/Gemini/EmbeddingGemma/Qwen3/BGE-M3/Cohere/Voyage/Nomic). Sparse-only, cross-encoder reranker는 언급만.
- **검증 언어**: 한국어 중심, 영어·다국어 벤치 결과 보조.
- **출처 원칙**: 공식 모델 카드/쿠킹북, peer-reviewed 논문, 대형 이커머스(Amazon·Walmart·Etsy·Instacart·eBay·Coupang·Mercari·Zalando)의 엔지니어링 블로그를 1차 근거로.

---

## 2. 모델 불문 적용되는 "철칙 5가지"

아래 5가지는 서로 독립된 8개 모델 계열에서 **일관되게 성립**함이 공식 문서와 실증 연구로 확인됐다.

### 2.1 숫자는 embedding 금지 → payload filter

- **현상**: 13개 dense embedding 모델 공통으로 수치 비교 정확도가 랜덤 대비 +4%p 수준(평균 0.54).
  `text-embedding-3-small` 52.7%, LLM 기반 임베더도 평균 56%.
- **근거**: [Numeracy gap in text embeddings (arxiv 2509.05691)](https://arxiv.org/html/2509.05691)
- **함의**: `"가격 50000원"`을 넣으면 `"49900"`·`"만원"` 같은 희소 토큰이 벡터를 왜곡할 뿐 의미 이득 0.
- **정답**: 가격/평점/재고는 Qdrant payload에 저장 → `bool.filter`로 range/term query. 평점 필터는 function_score로 score 반영 가능.

### 2.2 리뷰 원문 concat 금지, aspect만 +

- **현상**: 8개 모델 공통으로 앞쪽 토큰 편향 확인. 긴 리뷰 원문을 뒤에 붙이면 앞쪽 title 시그널이 희석되고
  "좋아요/잘 써요" 같은 감정 토큰이 벡터를 노이즈 방향으로 끌어당김.
- **실증**: Amazon 리뷰 490K 벤치에서 원문 concat은 MRR/NDCG -2~-5%, aspect 키프레이즈만 +0.8~2%.
- **근거**: [Positional bias in text embeddings (arxiv 2412.15241)](https://arxiv.org/html/2412.15241),
  [Leveraging Semantic Embeddings of User Reviews (CEUR-WS 3802)](https://ceur-ws.org/Vol-3802/paper25.pdf)
- **예외**: BGE-M3는 sparse 채널로 long-doc을 일부 구제 — 완화되지만 원문 concat이 "+"가 되는 건 아님.
- **정답**: LLM 또는 KeyBERT로 aspect 추출 후 `"특징: 배터리 오래감, 발볼 넓음, 통기성 좋음"`처럼 5개 이내 키프레이즈만.

### 2.3 구조화 prefix가 title-only를 이긴다

- **현상**: `title + brand + category + attributes`를 concat하면 title-only 대비 Recall/NDCG가 일관되게 상승.
- **실증**: Walmart EBR이 구조화 concat + negative sampling으로 **Exact-match Recall@20 +16.49%, Precision@20 +14.65%**.
  Amazon Semantic Product Search는 multi-field로 DUET 대비 NDCG@5 +2.20%.
- **근거**: [Walmart EBR arxiv 2408.04884](https://arxiv.org/abs/2408.04884),
  [Amazon Semantic Product Search arxiv 1907.00937](https://arxiv.org/pdf/1907.00937)
- **정답**: 포맷은 모델별로 다름 — 아래 §5 참고. 값만 공백 구분 또는 자연어 라벨 추천. Unused SEP 토큰은 작은 모델에서 학습이 덜 돼 있음.

### 2.4 Description은 pre-truncate

- **현상**: 모든 모델에서 문서 뒤쪽 토큰이 mean-pool에서 희석. Context window가 아무리 커도 실효 표현력은 앞쪽에 편중.
- **실증**: 컷오프 값은 모델별 다름:
  - Cohere embed v3: **≤512 tokens** 권장 (모델 한계)
  - EmbeddingGemma-300m: **≤2048 tokens** (모델 한계)
  - Qwen3 / BGE-M3 / OpenAI v3 / Voyage-3: 8192~32K 상한, 실효 컷오프는 512~1024 권장
- **정답**: `title → brand → category → attributes`를 먼저 채우고 description은 남은 예산으로. 앞 300~400자 절단(앞 자르기 금지).

### 2.5 쿼리 임베딩 캐시 — ROI 최고

- **현상**: 이커머스 쿼리는 Zipf 분포. 상위 1k 쿼리가 트래픽 40~60% 차지.
- **실증**: 의미 기반 캐시 hit rate 65~68%, API 비용 최대 86% 절감, 지연 최대 59배 단축.
- **근거**: [GPT Semantic Cache (arxiv 2411.05276)](https://arxiv.org/html/2411.05276v2),
  [AWS ElastiCache semantic cache blog](https://aws.amazon.com/blogs/database/lower-cost-and-latency-for-ai-using-amazon-elasticcache-as-a-semantic-cache-with-amazon-bedrock/)
- **구현**: Caffeine `maximumSize=10000`, `expireAfterWrite=1h`. 키는 `trim().lowercase()` 정규화 결과.

---

## 3. 모델별 특성 비교

> 차원·context는 기본값 기준. 가격은 2025년 중반 공개치. 한국어 품질은 MTEB-KO / MIRACL-ko / 벤더 벤치 종합.

| 모델 | 차원 (MRL) | Context | Instruction 포맷 | 한국어 | 가격 / 배포 | 라이선스 |
|---|---|---|---|---|---|---|
| **OpenAI text-embedding-3-small** | 1536 (임의 축소) | 8191 | 불필요 | 멀티링궐 (ada-002 대비↑) | $0.02 / 1M | 독점 API |
| **OpenAI text-embedding-3-large** | 3072 (임의 축소) | 8191 | 불필요 | Qwen3-8B에 밀림 | $0.13 / 1M | 독점 API |
| **Google gemini-embedding-001** | 3072 (768/1536/3072) | 2048 | `task_type` 8종 | 상위권 | $0.15 / 1M | 독점 API |
| **Google EmbeddingGemma-300m** | 768 (512/256/128) | 2048 | **필수**: `title: … \| text: …` / `task: search result \| query: …` | 100+ 언어 | 셀프호스팅 CPU 가능 | Gemma License |
| **Qwen3-Embedding-0.6B** | 1024 (32~1024) | 32K | 쿼리만 `Instruct: {task}\nQuery: {q}` | MTEB-Multi 64.33 | 셀프호스팅 GPU 권장 | Apache 2.0 |
| **Qwen3-Embedding-4B / 8B** | 2560 / 4096 | 32K | 동일 | **8B는 MTEB-Multi 1위 (70.58)** | GPU 필수 | Apache 2.0 |
| **BAAI bge-m3** | 1024 고정 | 8192 | 불필요 (쿼리만 경량 prefix 권장) | MTEB-KO 9위, MIRACL 다국어 SOTA | 셀프호스팅 CPU/GPU (568M) | MIT |
| **BAAI bge-multilingual-gemma2** | 3584 고정 | 8192 | 쿼리 instruction 권장 | 상위권 | GPU 필수 (~9B) | Gemma License |
| **Cohere embed-v3-multilingual** | 1024 고정 | 512 | **필수**: `input_type`: search_document/search_query/… | 100+ 언어 SOTA급 | $0.10 / 1M | 독점 API |
| **Voyage-3** | 1024 (2048/1024/512/256) | 32K | `input_type`: query/document (자동 prepend) | 영어 강, 한국어 보통 | $0.06 / 1M (200M 무료) | 독점 API |
| **Voyage-multilingual-2** | 1024 고정 | 32K | 동일 | 멀티링궐 특화 | 50M 무료 후 과금 | 독점 API |
| **Nomic nomic-embed-text-v1.5** | 768 (64~768, binary) | 8192 | **필수** prefix: `search_document:` / `search_query:` | 영어 편중 (KO 약) | 셀프호스팅 CPU (137M) | Apache 2.0 |

### 한국어 이커머스 TOP 3 추천 (스터디 → 프로덕션)
1. **Qwen3-Embedding-0.6B** (현 스택) — Apache 2.0, Ollama 경로, 1024d, 한국어 MTEB-Multi 64.33. 유지 권장.
2. **BGE-M3** — MIT, MTEB-KO 9위, dense+sparse+ColBERT 동시 출력 → 3-way RRF와 철학 정합, Ollama `ollama pull bge-m3` 즉시 교체 가능.
3. **Gemini embedding-001** — API 허용 시. MRL로 768까지 축소해 저장 3배 압축, `task_type=RETRIEVAL_DOCUMENT/QUERY`로 prefix 자동화.

---

## 4. 가설 × 모델 매트릭스 (일반화 검증)

현 프로젝트 초기 리서치에서 도출된 8개 가설이 모델 계열 전반에 성립하는지를 한 표로 요약.

| # | 가설 | Qwen3 0.6B | OpenAI 3-large | Gemini | BGE-M3 | Cohere v3 | Voyage / Nomic / Gemma-300m | 일반화 |
|---|---|---|---|---|---|---|---|---|
| 1 | 숫자 → metadata 분리 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **보편** |
| 2 | 리뷰 원문 금지, aspect만 | ✅ | ✅ | ✅ | ⚠️ long-doc 일부 구제 | ✅ | ✅ | **보편** |
| 3 | 구조화 prefix → +3~6% | ✅ | ✅ | ✅ | ✅ | ✅ (input_type 의무) | ✅ | **보편** |
| 4 | Description 300~400자 | ✅ | ✅ | ✅ (2048 강제) | ⚠️ sparse 구제 | ✅ (<512) | ✅ | **보편 (컷오프값만 모델별)** |
| 5 | Named vectors 이득 작음 | ✅ | ✅ | ✅ | ❌ native dense+sparse+multi-vec | ✅ | ✅ | 대체로 보편, BGE-M3 예외 |
| 6 | 브랜드 영/한 alias 양병기 | ✅ 필수 | ⚠️ cross-lingual 일부 커버 | ⚠️ | ⚠️ (100+ 언어) | ⚠️ | ✅ (작은 모델) | **작은 모델일수록 필수** |
| 7 | 쿼리 embedding 캐시 ROI 최고 | ✅ | ✅✅ (API 과금 모델 최대) | ✅✅ | ✅ | ✅✅ | ✅ | **보편 (API일수록 큼)** |
| 8 | title 반복 가중은 역효과 | ✅ | ⚠️ 중립 | ⚠️ 중립 | ⚠️ | ⚠️ | ✅ (작은 모델) | **작은 모델일수록 성립** |

### 모델 크기가 바꾸는 것
- **작은 모델(≤1B)** — alias 양병기 필수, description 짧게, instruction prefix 강하게 권장, title 반복 금지
- **중형(1~8B)** — alias 선택, description 길이 둔감, Matryoshka 축소가 핵심 레버
- **BGE-M3 (네이티브 다채널)** — 모델 자체가 dense/sparse/multi-vec를 한 번에 → 구조화 prefix의 "필드 분리" 역할을 모델이 내부화

---

## 5. 모델별 "임베딩 텍스트 권장 패턴" (공식 예제 기반)

### OpenAI (v3) — prefix 불필요
```
Title: 삼성 갤럭시 S24 울트라
Description: 6.8인치 AMOLED, 12GB/256GB
```
숫자·카테고리는 vector DB metadata로 분리하는 것을 cookbook이 직접 권장.

### Google Gemini (task_type 파라미터)
```python
embed_content(model="gemini-embedding-001",
              content=text,
              task_type="RETRIEVAL_DOCUMENT")   # 문서
              task_type="RETRIEVAL_QUERY")      # 쿼리
```
텍스트 포맷 자체는 자유, `task_type` 지정이 prefix 역할.

### EmbeddingGemma-300m (프롬프트 템플릿 엄격)
```
# 문서
title: 삼성 갤럭시 S24 울트라 | text: 6.8인치 AMOLED, 12GB/256GB

# 쿼리
task: search result | query: 갤럭시 S24 12기가
```
title 없으면 리터럴 `"none"` 넣을 것 — 공식 지침.

### Qwen3-Embedding (비대칭: query만 instruction)
```
# 쿼리
Instruct: Given a Korean e-commerce query, retrieve relevant product listings
Query: 갤럭시 S24 울트라

# 문서
삼성 갤럭시 S24 울트라 6.8인치 AMOLED 12GB 256GB
```
Instruction은 **영어로** 작성 권장 (학습 시 영어 instruction 중심).

### BGE-M3 — query만 경량 prefix
문서는 prefix 없음. 쿼리는 `"Represent this sentence for searching: "` 선택적.

### Cohere embed v3 (input_type 필수)
```python
co.embed(texts=[...], model="embed-multilingual-v3.0",
         input_type="search_document")  # 문서
         input_type="search_query")     # 쿼리
```

### Voyage (input_type 선택)
내부에서 `"Represent the document for retrieval: "` / `"Represent the query for retrieving supporting documents: "` 자동 prepend.

### Nomic v1.5 (텍스트에 직접 삽입)
```
search_document: 삼성 갤럭시 S24 울트라 6.8인치
search_query: 갤럭시 S24
```

---

## 6. 이커머스 임베딩 텍스트 구성 전략 (학계·산업 교집합)

### 6.1 필드 우선순위 (모든 모델 공통)
1. **title** — BM25의 정확 매칭 축. embedding에서는 가장 강한 의미 앵커.
2. **brand** — 검색 의도의 절반 이상. 영/한 alias 필수 (작은 모델 기준).
3. **category path** — L1/L2/L3 — 자연어 concat("디지털 노트북 게이밍").
4. **핵심 attributes** — 값만 공백 구분("블랙 M사이즈 면 100%"). 키(`color:`, `size:`)는 반복 noise.
5. **review aspect 키프레이즈** — 5개 이내 정제된 구.
6. **description** — 앞 300~400자로 pre-truncate. 앞 자르기 금지.

### 6.2 Instacart `[PN]` 특수 토큰 패턴 (SIGIR 2022)
```
[PN] Organic 2% Reduced Fat Milk
[PBN] GreenWise
[PCS] Milk
[PAS] organic, kosher, gluten-free
```
작은 multilingual 모델에서는 unused token이 학습이 덜 돼 있어 **자연어 라벨로 번역**하면 동일 효과:
```
제목: Organic 2% Reduced Fat Milk
브랜드: GreenWise
카테고리: Milk
속성: organic kosher gluten-free
```

### 6.3 Etsy "Hydrated Vector" 패턴 (UEPPR, 2023)
텍스트 임베딩 뒤에 품질 시그널 feature를 벡터 뒷단 concat:
```
p' = concat([text_vec; [rating_scaled, cvr_scaled, review_count_log_scaled]])
```
숫자를 embedding text에 녹이지 않으면서 ANN 점수에 녹이는 영리한 우회. 스터디에서 post-hoc rescoring으로 대체 가능.

### 6.4 Walmart "Lex + Vec → rerank" 표준 골격
BM25(ES) + ANN(Qdrant) 병렬 → merge → GBDT/cross-encoder rerank. 현 프로젝트의 **3-way RRF + (미래) rerank**가 정확히 이 패턴.

### 6.5 Coupang character-level 백업 채널
한국어 이커머스에서 형태소 분석 품질이 임베딩 품질의 상한을 만든다는 관찰. Nori `_analyze` 실패 케이스용으로 char n-gram 백업 채널을 RRF에 추가하는 선택지.

---

## 7. 산업 공통 패턴 TOP 5

Amazon, Walmart, Etsy, eBay, Instacart, Shopify, Coupang, Mercari, Alibaba, JD, Zalando, Wayfair 공개 자료의 교집합.

1. **Two-tower bi-encoder + ANN + rerank** — 사실상 업계 표준 (Walmart·Etsy·Instacart·eBay·Amazon 공유).
2. **click/cart-add/purchase를 positive pair로 contrastive 학습** — 수작업 레이블 쓰지 않음.
3. **숫자·재고·가격은 embedding 밖** — metadata post-filter 또는 GBDT ranker feature로 분리.
4. **필드 concat은 구분자/특수 토큰과** — naive concat보다 `[PN]`/`#attr_color_red` 같은 명시 마커.
5. **리뷰 원문은 임베딩에 넣지 않음** — rating만 payload로 또는 aspect-level 요약만.

### 상반된 선택 (회사마다 다른 영역)
- **Named/multi-vector vs Single**: eBay/Shopify/Wayfair 분리, Etsy/Instacart/Walmart 단일.
- **Realtime vs Batch**: Shopify streaming 필수(다테넌트 SaaS), Instacart daily batch.
- **범용 임베딩 vs 도메인 사전학습**: Mercari CLIP fine-tune, eBay eBERT 재사전학습, 나머지 Sentence-Transformers.

### 규모별 권장
| 규모 | 권장 구성 |
|---|---|
| **소규모 (1만)** | 범용 임베딩, single vector, title+brand+category concat, 가격·평점 payload filter, 리뷰 제외. **← 이 프로젝트 위치**. |
| **중규모 (100만)** | Two-tower 자체 학습, 특수 토큰 필드 마커, rating/CVR hydrated vector, GBDT rerank, 일간 배치. |
| **대규모 (1억+)** | Named vectors, 도메인 BERT 재사전학습, 64d 축소, 시퀀스 기반 개인화(BST/DIN), streaming 임베딩. |

---

## 8. 현 프로젝트 적용 가이드

### 8.1 `Product.embeddingText()` 권장 대안 3안

아래 모두 `src/main/kotlin/com/hl/hybridsearch/indexing/Product.kt` 대상. `A → B → C` 순으로 복잡도 증가.

#### A. Lean — 값만 공백 concat, 1000자 cap (Qwen3-0.6B 기본 추천)
```kotlin
fun embeddingText(maxChars: Int = 1000): String = buildString {
    append(title)
    if (brand.isNotBlank()) append(' ').append(brand)
    if (category.l2.isNotBlank()) append(' ').append(category.l2)
    if (category.l3.isNotBlank()) append(' ').append(category.l3)
    val attrValues = attributes.values.filterNotNull().joinToString(" ")
    if (attrValues.isNotBlank()) append(' ').append(attrValues)
    if (tags.isNotEmpty()) append(' ').append(tags.joinToString(" "))
    if (description.isNotBlank()) append(". ").append(description)
}.take(maxChars)
```
- **유리**: "블랙 데님 자켓", "민감성 토너 50ml" (속성값 직매칭)
- **불리**: "선물용 세트", "출근룩" (맥락어 부재)

#### B. Natural — 가격 구간화 라벨 + 자연어 문장
```kotlin
fun embeddingText(maxChars: Int = 1200): String = buildString {
    append(title)
    val priceTier = when {
        price >= 500_000 -> "프리미엄"; price >= 100_000 -> "중가"
        price >= 30_000 -> "실속"; else -> "가성비"
    }
    append(". ").append(brand)
    if (category.path.isNotBlank()) append(' ').append(category.path.replace('/', ' '))
    append(' ').append(priceTier)
    val attrPhrase = attributes.values.filterNotNull().joinToString(" ")
    if (attrPhrase.isNotBlank()) append(". ").append(attrPhrase)
    if (tags.isNotEmpty()) append(". ").append(tags.joinToString(" "))
    if (description.isNotBlank()) append(". ").append(description.take(400))
}.take(maxChars)
```
- **유리**: "가성비 좋은 무선이어폰", "프리미엄 스킨케어"
- **불리**: 정확 모델명 쿼리, 가격 경계 오분류

#### C. Aspect-enriched — brand alias + 리뷰 aspect
```kotlin
fun embeddingText(
    maxChars: Int = 1200,
    brandAliases: Map<String, List<String>> = emptyMap(),
    reviewAspects: List<String> = emptyList(),
): String = buildString {
    append(title)
    if (brand.isNotBlank()) {
        append(' ').append(brand)
        brandAliases[brand]?.forEach { append(' ').append(it) }
    }
    if (category.l2.isNotBlank()) append(' ').append(category.l2)
    if (category.l3.isNotBlank()) append(' ').append(category.l3)
    val attrPhrase = attributes.values.filterNotNull().joinToString(" ")
    if (attrPhrase.isNotBlank()) append(' ').append(attrPhrase)
    if (tags.isNotEmpty()) append(' ').append(tags.joinToString(" "))
    if (reviewAspects.isNotEmpty()) append(". 특징: ").append(reviewAspects.joinToString(", "))
    if (description.isNotBlank()) append(". ").append(description.take(300))
}.take(maxChars)
```
- **유리**: "애플 아이폰 케이스", "발색 좋은 립스틱", "오래가는 무선이어폰"
- **불리**: 신상품(aspect 공란), description truncation 영향

### 8.2 쿼리 임베딩 캐시 (모델 불문)

현재 `VectorStore.similaritySearch(query)`가 매 호출마다 Ollama를 호출. Caffeine 한 단만 끼워도 평가 턴어라운드가 크게 줄어든다.

```kotlin
@Component
class CachingEmbeddingClient(
    private val delegate: EmbeddingModel,
    props: SearchProperties,
) {
    private val cache: Cache<String, FloatArray> = Caffeine.newBuilder()
        .maximumSize(props.embedding.cacheSize)          // 10_000 권장
        .expireAfterWrite(Duration.ofHours(1))
        .recordStats()
        .build()

    fun embedQuery(q: String): FloatArray =
        cache.get(normalize(q)) { delegate.embed(it).toFloatArray() }

    private fun normalize(q: String) = q.trim().lowercase()
}
```

단일 추가로 가장 체감 큰 최적화. `search.embedding.cache.enabled=false`로 롤백 가능.

### 8.3 측정 / A-B 프로토콜

`./gradlew bootRun --args='--spring.profiles.active=eval --eval.corpus=10000 --eval.seed=42'`

세 라우팅 전략(classifier / always-sentence / always-lexical) × `Product.embeddingText()` A/B/C = **9개 조합** NDCG@10 / MRR / Recall@50 비교. `EvaluationReport.splitByExpectedType()` 으로 KEYWORD / SENTENCE 세그먼트 회귀 감시.

**가장 먼저 검증할 1 변수**: A안 적용 전/후 SENTENCE 세그먼트 NDCG@10 변화. 양수면 유지, 음수면 B안 실험.

---

## 9. 하지 말아야 할 것 TOP 5 (한국어 이커머스 특화)

1. **스키마 키를 자연어에 섞기** — `"색상: 블랙, 사이즈: M"`. `:` 반복이 모든 상품 벡터를 구조 noise 방향으로 끌어당김. 값만 공백 구분.
2. **가격·평점을 리터럴 숫자로 삽입** — `"50000원 4.5점"`. BPE 희소 토큰이 벡터 망침. payload filter로 분리, 구간화 라벨("프리미엄")까지가 한계.
3. **Description 통째로 붙여 cap에 의존** — 2000자 cap 전에 pre-truncate 300~400자. cap은 safety net.
4. **브랜드 한쪽 표기만 색인** — `Apple`만 있는데 사용자는 `애플` 친다. alias 양병기 필수 (작은 모델 기준).
5. **리뷰 원문 직삽입** — 감정·오탈자·반복이 embedding quality 급락. 반드시 aspect 추출 후 정규화.

---

## 10. 모델 교체 체크리스트

현 `dengcao/Qwen3-Embedding-0.6B`에서 다른 모델로 전환할 때 조정해야 하는 3축:

### 10.1 Instruction prefix 정책
| 교체 대상 | 조치 |
|---|---|
| OpenAI v3-* | prefix 제거 (불필요) |
| Gemini / EmbeddingGemma | `task_type` 또는 `task: / query: / title: / text:` 템플릿 주입 |
| BGE-M3 | 쿼리만 경량 prefix(optional) |
| Cohere v3 | `input_type` 파라미터 의무화 |
| Nomic v1.5 | 텍스트 앞에 `search_document:` / `search_query:` 리터럴 prepend |

### 10.2 Description 컷오프 값
| 모델 | 권장 cap |
|---|---|
| Cohere v3 | 512 tokens |
| Gemini / EmbeddingGemma | 2048 tokens |
| Qwen3 / BGE-M3 / OpenAI v3 / Voyage | 8192~32K 상한이나 실효는 512~1024 권장 |

### 10.3 브랜드 alias 양병기 필요성
| 모델 규모 | alias 양병기 |
|---|---|
| ≤ 1B (Qwen3-0.6B, Gemma-300m, Nomic) | **필수** |
| 1~8B (Qwen3-4B/8B, Voyage-3, OpenAI 3-large) | 선택 |
| 대형 다국어 (BGE-M3, Cohere v3, Gemini) | 권장 (cold-start 브랜드에만) |

### 10.4 Full reindex 의무
**모델 교체는 항상 full reindex + blue-green.** 다른 모델의 벡터는 동일 의미 공간이 아니며, 동일 컬렉션에 섞으면 cosine 전부 무의미.

---

## 11. 레퍼런스

### 학계 논문
- [Shopping Queries (ESCI) arxiv 2206.06588](https://arxiv.org/pdf/2206.06588)
- [Walmart EBR arxiv 2408.04884](https://arxiv.org/abs/2408.04884)
- [Walmart Semantic Retrieval arxiv 2412.04637](https://arxiv.org/abs/2412.04637)
- [Amazon Semantic Product Search arxiv 1907.00937](https://arxiv.org/pdf/1907.00937)
- [Amazon Structured Product Search arxiv 2008.08180](https://arxiv.org/pdf/2008.08180)
- [mFAR arxiv 2410.20056](https://arxiv.org/html/2410.20056v1)
- [CHARM arxiv 2501.18707](https://arxiv.org/html/2501.18707v1)
- [Long-Tail EBR arxiv 2505.01946](https://arxiv.org/html/2505.01946v1)
- [NEAR² arxiv 2506.19743](https://arxiv.org/abs/2506.19743)
- [LLM-Augmented Retrieval arxiv 2404.05825](https://arxiv.org/html/2404.05825v1)
- [HyST arxiv 2508.18048](https://arxiv.org/html/2508.18048v1)
- [Numeracy gap arxiv 2509.05691](https://arxiv.org/html/2509.05691)
- [Positional bias arxiv 2412.15241](https://arxiv.org/html/2412.15241)
- [GPT Semantic Cache arxiv 2411.05276](https://arxiv.org/html/2411.05276v2)
- [Etsy UEPPR arxiv 2306.04833](https://arxiv.org/html/2306.04833)
- [Instacart ITEMS SIGIR 2022](https://sigir-ecom.github.io/ecom22Papers/paper_8392.pdf)
- [Alibaba BST arxiv 1905.06874](https://arxiv.org/pdf/1905.06874)
- [DIN arxiv 1706.06978](https://arxiv.org/abs/1706.06978)

### 벤더 공식 문서
- [OpenAI new embedding models](https://openai.com/index/new-embedding-models-and-api-updates/)
- [OpenAI cookbook — user and product embeddings](https://developers.openai.com/cookbook/examples/user_and_product_embeddings)
- [Google AI — Gemini Embeddings](https://ai.google.dev/gemini-api/docs/embeddings)
- [Vertex AI — Embeddings task types](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/embeddings/task-types)
- [Google AI — EmbeddingGemma model card](https://ai.google.dev/gemma/docs/embeddinggemma/model_card)
- [HuggingFace — EmbeddingGemma-300m](https://huggingface.co/google/embeddinggemma-300m)
- [HuggingFace — Qwen3-Embedding-0.6B](https://huggingface.co/Qwen/Qwen3-Embedding-0.6B)
- [HuggingFace — Qwen3-Embedding-8B](https://huggingface.co/Qwen/Qwen3-Embedding-8B)
- [Qwen3 Embedding blog](https://qwenlm.github.io/blog/qwen3-embedding/)
- [HuggingFace — BAAI/bge-m3](https://huggingface.co/BAAI/bge-m3)
- [BGE-M3 arxiv 2402.03216](https://arxiv.org/abs/2402.03216)
- [Cohere embed v3 intro](https://cohere.com/blog/introducing-embed-v3)
- [Cohere embeddings docs](https://docs.cohere.com/docs/embeddings)
- [Voyage AI — Text Embeddings](https://docs.voyageai.com/docs/embeddings)
- [Nomic Embed v1.5](https://huggingface.co/nomic-ai/nomic-embed-text-v1.5)

### 업계 블로그
- [Amazon ESCI dataset](https://www.amazon.science/code-and-datasets/shopping-queries-dataset-a-large-scale-esci-benchmark-for-improving-product-search)
- [Etsy — Personalized Search](https://www.etsy.com/codeascraft/bringing-personalized-search-to-etsy)
- [eBay — Billion-Scale Vector Engine](https://innovation.ebayinc.com/tech/engineering/ebays-blazingly-fast-billion-scale-vector-similarity-engine/)
- [eBay — 3B Titles LM / eBERT](https://innovation.ebayinc.com/stories/how-ebay-created-a-language-model-with-three-billion-item-titles/)
- [Instacart — Embeddings for Search](https://www.instacart.com/company/how-its-made/how-instacart-uses-embeddings-to-improve-search-relevance/)
- [Shopify — Real-time ML Semantic Search](https://shopify.engineering/how-shopify-improved-consumer-search-intent-with-real-time-ml)
- [Mercari — Fine-tuned CLIP](https://engineering.mercari.com/en/blog/entry/20231223-fine-tuned-clip-better-listing-experience-and-80-more-budget-friendly/)
- [Coupang — Korean Word Segmentation](https://medium.com/coupang-engineering/unsupervised-competing-neural-language-model-for-word-segmentation-12becc1015bf)
- [Coupang — Duplicate Item Matching](https://medium.com/coupang-engineering/matching-duplicate-items-to-improve-catalog-quality-ca4abc827f94)
- [Zalando — LLM-as-a-Judge](https://engineering.zalando.com/posts/2024/11/llm-as-a-judge-relevance-assessment-paper-announcement.html)
- [Wayfair — Visual Search](https://www.aboutwayfair.com/tech-innovation/object-detection-and-visual-search-improvements)
- [Qdrant — Multiple Vectors per Object](https://qdrant.tech/articles/storing-multiple-vectors-per-object-in-qdrant/)
- [Pinecone — The Practitioner's Guide to E5](https://www.pinecone.io/learn/the-practitioners-guide-to-e5/)

### 벤치마크 / 리더보드
- [MTEB-KO leaderboard](https://github.com/su-park/mteb_ko_leaderboard)
- [MIRACL GitHub](https://github.com/project-miracl/miracl)
- [BEIR benchmark](https://github.com/beir-cellar/beir)
- [Marqo Ecommerce Embeddings](https://github.com/marqo-ai/marqo-ecommerce-embeddings)

---

## 부록: 이 프로젝트 Phase 7 제안

본 리서치 결론을 실제 코드로 검증하기 위한 다음 단계:

1. **A안 적용** — `Product.embeddingText()` Lean 버전으로 전환, cap 2000 → 1000 축소
2. **쿼리 임베딩 캐시** — Caffeine 도입, `search.embedding.cache.*` 설정
3. **Qwen3 query instruction** — `SearchService.hybrid()`의 벡터 입력 시에만 instruction prefix 주입 (A/B)
4. **평가 실행** — `SyntheticGoldSetBuilder`에 "가성비"·"오래가는" 등 aspect/price 쿼리 10개 추가 후 NDCG 비교
5. 결과가 플러스면 유지, 음수면 B/C로 이동 후 재측정

**단일 변경부터 — 모든 변경은 한 번에 하나씩.** 한국어 이커머스 임베딩은 세밀한 조정의 영역이며, 여러 축을 한 번에 바꾸면 어느 축이 기여했는지 알 수 없다.
