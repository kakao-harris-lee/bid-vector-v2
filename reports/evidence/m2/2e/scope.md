# Slice 계약 — M2 / 2E · 임베딩 RPC (opportunity-analysis 점수 경로의 M2 additive 확장)

> **지위**: **착수 계약 2026-09-10.** M2 완료(2026-09-07) 뒤의 **후속 slice** — 운영자 결정 2026-09-10: `OPEN-4D-LADDER-SCORE-SOURCE` (a) 「M2 계약 v1 additive
> 확장 + M5 provider」 · E-1 (c) 「계약은 모델 의존 성분만, `priority` 는 Kotlin 조합」 · E-3 (a) · E-4 (a). 세션 모델 단독 작성(운영자 지시 2026-09-04).
>
> **왜 임베딩 RPC 인가 (착수 조사 `_workspace/m2-2e/01_scout_opportunity_scoring.md` §1.3·§2·§3 실측).** legacy 사다리 점수 셋 가운데 **모델에 의존하는 것은
> 둘**뿐이다 — (i) 분류기 8축 중 `semantic_similarity` 한 축(나머지 일곱 — 업종·면허·협회·기술분야·지역·예산·용량 — 은 V2 에서 1C `qualification`·1E `WatchRules`·
> 용량 port 가 **이미 소유**) (ii) `similarity_signal`(유사 공고 검색, pgvector 코사인 평균). 그런데 (i) 도 실물은 **Kotlin 소유 fact(업종·면허·지역 코드)를 문장으로
> 합성해 임베딩한 코사인**(legacy `classification/semantic.py` 의 project/profile semantic text)이고, (ii) 는 **DB(pgvector) 위의 kNN** 이라 「serving 은 DB 를
> 갖지 않는다」(2B D-2B-3 (b) 기각 사유·`v2-지침서.md` §3.2)와 정면 충돌한다. 두 경우 모두 **ML 이 실제로 하는 일은 텍스트 → 벡터 하나**이고, 코사인·kNN·가중합은
> 산술이다. 그래서 계약은 `EmbedText` 를 나르고, semantic match(코사인)·유사 공고 검색(adapters/persistence pgvector)·`priority` 조합은 Kotlin(4B-4)이 한다.
> **이것이 E-1 (c) 의 정직한 실물이다** — 「모델 의존 성분」의 최소 단위가 점수가 아니라 벡터였다.
>
> **대안 (b)** — ml-engine 이 `match`·`similarity` 점수를 직접 내는 `AnalyzeOpportunity` RPC. 요청이 후보 임베딩 집합이나 프로필 텍스트를 실어야 하고(DB 금지),
> Kotlin 소유 fact 를 Python 이 다시 읽어 판정하는 경계(ML-09)가 계약면에서 재발한다. **채택하지 않는다** — 종결 승인 시 운영자 재확인(D-2E-1).
>
> **레인 격리.** 다른 세션은 `m4/2026-09-08`(=`main`, `8b50461`)에서 4C-2 를 진행 중. 이 slice 는 `8b50461` 에서 가른 브랜치 `m2-2e/2026-09-10`(worktree `bid-vector-v2-m4e`).
> 2E 경로(`contracts/**`·`ml-engine/tests/**`·`adapters/**/contract/**`)는 4C-2 와 겹치지 않고, 공유 파일은 `config/quality/gate-tests.properties`(`gate.tests.adapters` 키 —
> 이번 병합에서 주석 충돌 1회 실측, 줄 단위)·`docs/discovery/capability-map.md` 뿐.

```yaml
milestone: m2
slice: 2e-embedding-rpc
base_sha: 8b50461016232386e456be532fab4eed4299fcfb   # 4B-3 병합 커밋 = m4/2026-09-08 = main
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — 값을 박지 않는다.
branch: m2-2e/2026-09-10
in_scope:
  - contracts/proto/bidvector/ml/v1/embedding.proto        # EmbeddingService { EmbedText, GetEmbeddingMetadata } + 요청·응답 메시지(신설 파일)
  - contracts/proto/bidvector/ml/v1/error.proto            # 조건부 — 새 FailureCode 값이 필요할 때만 additive(예: FAILURE_CODE_TEXT_TOO_LONG). 기존 값·번호 무변경
  - contracts/testdata/embedding/**                        # canonical 바이트 + JSON 원본(2B 관례)
  - contracts/tools/breaking-mutations.sh                  # 신설 파일을 mutation 스윕 대상에 추가(2D 스크립트가 파일 목록을 하드코딩 — 조사 §5)
  - adapters/src/test/kotlin/bidvector/adapters/contract/EmbeddingContractTest.kt   # consumer test — in-process fake servicer(2B `PredictionContractTest` 골격)
  - adapters/src/test/kotlin/bidvector/adapters/contract/MultiServiceContractTest.kt # 셋째 서비스(EmbeddingService) 추가, 세 서비스 공존 실측(verifier r1 F-5 — 착수 문면의 「넷째」는 계수 오류)
  - ml-engine/tests/test_embedding_contract.py             # provider 쪽 계약 test — fake servicer 가 fail-closed·oneof·차원 불변식을 지키는지(2B 관례)
  - ml-engine/tests/crosslang_smoke_server.py              # 조건부 — 교차언어 스모크에 새 서비스 등록
  - config/quality/contract-policy.properties              # `embedding.text.max-chars` 정책 값 신설(매직넘버 금지)
  - config/quality/gate-tests.properties                   # gate.tests.adapters 등재만
  - milestone-2.md                                         # 「Slice 2E」 절 신설(완료 뒤 후속 slice — 착수 문단)
  - docs/discovery/capability-map.md                       # §14 `OPEN-4D-LADDER-SCORE-SOURCE` 행 갱신(분해) + 신설 OPEN 둘만
  - reports/evidence/m2/2e/**
out_of_scope:
  - 점수 산출(`match`·`similarity`·`priority`)               # Kotlin 4B-4(코사인·조합 커널)·adapters(pgvector kNN)
  - 프로필 임베딩의 저장·갱신, 공고 임베딩 backfill(projection)  # adapters/persistence + 스케줄(4C-2·후속) — `SimilarityProjectionNotReady` 의 소유자
  - 실 servicer(M5)·모델 선택(sentence-transformers 384차원·해시 fallback 은 legacy 실물일 뿐)   # M5. 계약은 벡터의 형태·차원·release 만
  - 실 client 배선(4D-2)·`MlAnalysisPort` 변경(4B-4)
  - Platt·`calibrated_win_rate`(`OPEN-ML-02`, E-2 (c))
  - 승인 태그 이동(2D 규칙 — 새 태그는 운영자) · 다른 .proto 의 변경
  - shared-kernel/**, 도메인 모듈, fixtures/**, ml-contract/**(생성물은 VCS 밖)
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1 — contractGate(buf lint·breaking against 승인 태그·생성 결정성) 포함
  - "(cd contracts && buf lint && buf build)"                                                          # S-2
  - "(cd contracts && ./tools/breaking-mutations.sh)"                                                 # S-3 — 신설 파일 포함 스윕 exit 0(전건 잡힘)
  - "./gradlew --no-daemon :adapters:test --tests '*Embedding*Contract*' --tests '*MultiServiceContractTest*'"   # S-4
  - "(cd ml-engine && python -m pytest tests/test_embedding_contract.py tests/test_contract_roundtrip.py -q)"     # S-5
  - "./tools/contract-crosslang-smoke.sh"                                                            # S-6 — 로컬 실측 1회(2D D-2D-3 (a))
  - "./gradlew --no-daemon :adapters:gateExecutionGate"                                              # S-7
  - "./gradlew qualityBaseline"                                                                       # S-8
rollback: |
    **정본은 `reports/evidence/m2/2e/rollback.md`**. `embedding.proto`·testdata·test·정책 값을 걷으면 승인 태그 상태. 생성물은 VCS 밖.
    공유 파일(`gate-tests.properties`·`capability-map.md`·`contract-policy.properties`·`breaking-mutations.sh`)은 줄 단위(최신→과거 hunk).
```

근거: `milestone-2.md` 설계 규칙·완료 조건 · `ADR 0003`(gRPC)·`ADR 0009`·`ADR 0010` D-3·D-6·D-7 · 2A ④·⑥·D-2A-3 · 2B ③·⑤·D-2B-3 · 2D ②·⑥ · `capability-map.md`
ML-03·ML-09·`OPEN-ML-02` · `data-dictionary.md` §6.1 · 조사 노트 `_workspace/m2-2e/01_scout_opportunity_scoring.md` · 운영자 결정 2026-09-10.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline 8b50461016232386e456be532fab4eed4299fcfb..HEAD -- CLAUDE.md .claude/` — 착수 시 **없음**.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **`EmbeddingService.EmbedText`** — 요청 `EmbedTextRequest { PredictionEnvelope envelope(release 선택자·schema version 재사용), string text, TextKind kind ∈ {NOTICE, OPERATOR_PROFILE} }`, 응답 `oneof result { Embedding success, ApplicationFailure failure }`. `Unmeasurable` 가지는 없다(임베딩에는 「측정 불가」인 도메인 결과가 없다 — 2B `GetModelMetadata` 와 같은 판단; 빈 텍스트는 `INVALID_REQUEST`) | 결정 (a) additive · ADR 0010 D-3 결과 봉투 |
| ② | **`Embedding { repeated float values(정확히 dimension 개), uint32 dimension, VectorNormalization normalization ∈ {L2}, ModelRelease release }`** — 차원은 응답이 나르고 client 는 `GetEmbeddingMetadata.dimension` 과 대조(불일치 = 계약 위반). 벡터는 L2 정규화(코사인 = 내적). `release` 다섯 성분 비공백(4D-1 규칙 재사용) | ADR 0009·2B `ModelRelease` provenance |
| ③ | **`GetEmbeddingMetadata`** — `{ ModelRelease promoted, uint32 dimension, repeated TextKind supported_text_kinds, Readiness readiness }`. `latest_promoted` 대조는 4D-1 `releaseSatisfiesSelector` 를 4D-2 가 재사용 | `OPEN-2A-RELEASE-CHECK-4D` 관례 |
| ④ | **fail-closed** — `TextKind`·`VectorNormalization` UNSPECIFIED/정의 밖 거부 · `text` 빈 문자열/공백 거부(`INVALID_REQUEST`) · `text` 상한(`contract-policy.properties` `embedding.text.max-chars`, 초과 = `INVALID_REQUEST`; 값은 정책 데이터, 초기값은 승인 대상 `OPEN-2E-TEXT-MAX`) · `values` 개수 ≠ `dimension` = consumer 거부 · norm ≠ 1±ε = consumer 거부(ε 도 정책 값) | 2A ⑥ |
| ⑤ | **testdata + consumer/provider test** — canonical 표본(성공·`UNSUPPORTED_SCHEMA`·빈 텍스트·차원 불일치) · Kotlin in-process fake servicer consumer test · Python provider test · 세 서비스 공존(`MultiServiceContractTest`) · breaking 스윕에 신설 파일 등록 · `contractGate` 통과 | 2B·2D 관례 |
| ⑥ | **버전 축** — 패키지 `bidvector.ml.v1` 안 additive, 승인 태그 `contracts/v1-approved-2026-09-07` 대비 `buf breaking` 통과. `feature_schema_version` 은 임베딩에선 「텍스트 합성 규약 version」(어떤 fact 를 어떤 순서로 문장에 넣는가 — Kotlin 소유, 4B-4)으로 읽는다 — 이름은 봉투 재사용을 위해 유지하고 KDoc/주석이 뜻을 고정 | ADR 0010 D-7 |

**만들지 않는 것**: 점수 · kNN · 프로필/공고 임베딩 저장 · 모델 선택 · 실 servicer · 실 client · 다른 .proto 변경.

---

## 계약 고정 결정 (D-2E-1~6)

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-2E-1** | 계약은 `EmbedText`(벡터) — 점수 RPC 가 아니다 | 머리 「왜 임베딩 RPC 인가」 | **착수 가정, 종결 승인 시 운영자 재확인** |
| **D-2E-2** | 별도 서비스 `EmbeddingService`(운영자 결정 D-3 (a)) — `BidPredictionService` 와 release 축·readiness 가 다르다 | 운영자 결정 2026-09-10 | 계약 고정 |
| **D-2E-3** | 벡터는 `repeated float` + `dimension` + 정규화 enum — bytes 아님(사람이 읽는 testdata JSON, 언어 간 표현 일치, 2B decimal string 관례의 정신) | 2B ⑤ | 계약 고정 |
| **D-2E-4** | `probability` 이름은 어디에도 없다 · `strategy/Score.kt` `ProbabilityScore` KDoc 「낙찰 확률 추정」은 4B-4 가 정정(E-3 (a) — 파일 소유가 strategy 라 2E 밖) | ML-03·D-M2-8 | 계약 고정 |
| **D-2E-5** | 텍스트 합성 규약(어느 fact 를 문장에 넣는가, 개인정보 배제)은 Kotlin 소유·version 화(`OPEN-2E-TEXT-SYNTHESIS`, 4B-4) — 계약은 「텍스트 + kind」만 | §6.1 경계 · ML-09 | 계약 고정 |
| **D-2E-6** | `EmbedText` 는 멱등(같은 텍스트·같은 release = 같은 벡터) — transport 재시도 안전(ADR 0010 D-4). `latest_promoted` 는 4D-1 과 같은 규칙 | ADR 0010 D-4 | 계약 고정 |

---

## 위협 모델 — 2E 고유 경계

**방어한다**: (a) 벡터 차원·정규화 불일치의 조용한 통과(④) (b) 빈/초과 텍스트(④) (c) release 미지목·공백 응답(②) (d) 점수·판정 필드의 계약
유입(D-2E-1 — `Embedding` 에 점수 필드 0) (e) 세 서비스 공존 시 stub 충돌(⑤). **방어하지 않는다**: 임베딩 품질 · 실 servicer(M5) · kNN·코사인의 옳음(4B-4·adapters) ·
텍스트에 개인정보가 섞이는 것(합성 규약 소유 4B-4 — S-3 상당 스캔은 그 slice) · 승인 태그를 옮기는 행위 · 생성 도구 결함 ·
**신설 `embedding.proto` 자체의 breaking 변경**(verifier r1 F-1(high), 착수 문면 정정 — 승인 태그
`contracts/v1-approved-2026-09-07` 에 이 파일이 없어 `buf breaking`·`contractGate`·S-3 스윕
어느 것도 이 파일 **내부**의 필드·enum 값·필드 번호 변경을 못 본다(실측: enum 값 삭제가
전 게이트를 완전 통과). 다음 승인 태그가 이 파일을 포함하기 전까지 구조적 사각이다 — 정본
방어는 **종결 승인 시 새 승인 태그(`contracts/v1-approved-<date>`) 신설**(운영자, 태그
이동은 이 slice 가 하지 않는다). `EmbeddingTestdataCanonicalTest`(JSON→binpb 왕복)가
필드·enum 값·필드 번호 변경 셋은 부분적으로 잡지만 `rpc` 삭제는 못 잡는 임시 안전망이다.

**우회 후보(≥5)**: (1) `values` 개수 ≠ `dimension` → consumer 거부 test (2) 정규화 안 된 벡터(norm≠1±ε) → consumer 거부 (3) `TextKind` 정의 밖 정수 → fail-closed
(4) 승인 태그 대비 필드 번호 재사용·enum 값 삭제 → **막지 못한다**(위 「방어하지 않는다」
참고 — `breaking-mutations.sh`의 `embedding.proto` 등록은 `package-rename` mutation 하나에만
참여한다, 나머지 10종은 기존 5파일만 대상. `EmbeddingTestdataCanonicalTest`가 필드·enum
값·필드 번호 변경 셋을 잡는 임시 대응) (5) `Unmeasurable` 가지 몰래 추가 → 스키마 리뷰·
testdata 부재 (6) release 공백 → 4D-1 `hasNonBlankRelease` 와 같은 규칙(로컬 순수 함수,
consumer test) (7) `text` 상한 리터럴 → 정책 파일.

---

## OPEN — 수령·신설

| OPEN | 처리 |
| --- | --- |
| `OPEN-4D-LADDER-SCORE-SOURCE`(결정 (a)) | 2E(계약)·4B-4(Kotlin 조합·코사인·합성 규약)·M5 provider slice(신설 예정)·4D-2(client) 로 분해 — 이 slice 는 계약 |
| `OPEN-ML-02` | 경계 밖 유지 |
| **`OPEN-2E-TEXT-SYNTHESIS`**(신설) | 임베딩 텍스트 합성 규약의 version·내용·개인정보 배제 — 4B-4 착수 계약이 정한다 |
| **`OPEN-2E-TEXT-MAX`**(신설) | `embedding.text.max-chars`·norm ε 초기값 — 정책 데이터, 종결 승인 시 확정 |
