# Slice 계약 — M2 / 2A · 공통 값과 오류 계약 — **초안, 구현 전**

> **지위**: M1/1E 병행 중에 세션 모델이 쓴 **계약 초안**이다. 구현·gradle·의존성 추가·fixture 편집은 하지 않았다.
> 착수는 M1 전체 승인 뒤 운영자 별도 지시로 하며, 그때 `base_sha` 를 재고정하고 `m2-prep.md` 「착수 전 결정」
> D-M2-1~14 와 아래 D-2A-0a·0b·7 의 답을 받고 **`ADR 0010` 을 승인**(2A 착수 전건 — ④·⑥ 이 그 D-1·D-3 위에 선다)한 뒤
> `milestone-2.md` 착수 문단을 쓴다. **게이트 정의(`config/quality/**`·`build-logic/**`)를 편집하는 slice 이므로 Phase 2.5
> 설계 검토 대상**이다(1A·1A-b 와 같은 급).

```yaml
milestone: m2
slice: 2a-common-values-and-errors
base_sha: c9022d9989c4b2a09cf8b9ff94795176dc5dc00c   # 초안 작성 시점 HEAD — **M1 전체 승인 뒤 착수 시 재고정**
head_sha: 리뷰 시점의 HEAD
in_scope:
  - contracts/proto/bidvector/ml/v1/common.proto      # 값 타입·enum·봉투(RequestEnvelope 필수 둘 + PredictionEnvelope)
  - contracts/proto/bidvector/ml/v1/error.proto       # ApplicationFailure(FailureCode 전체 어휘 — 2B·2C 가 쓰는 값 포함)·Unmeasurable·oneof 결과 봉투 패턴
  - contracts/buf.yaml, contracts/buf.lock            # lint 규칙(proto3, enum UNSPECIFIED=0, 패키지 `bidvector.ml.v1`, `option java_package` 규칙)
  - ml-contract/build.gradle.kts, ml-contract/src/main/proto -> contracts/proto (symlink 또는 srcDir 참조)   # D-2A-0a — 생성물은 build/ 아래, VCS 밖
  - ml-engine/pyproject.toml, ml-engine/tests/conftest.py(생성 fixture), ml-engine/tests/test_contract_roundtrip.py   # D-M2-3 (a) — 최소 골격. Python 생성물도 VCS 밖(pytest 가 grpc_tools.protoc 로 임시 생성)
  - build-logic/**                                    # protobuf 플러그인 convention + `ml-contract` 의 게이트 적용 범위(D-2A-0a·D-2A-6) — 게이트 정의 편집, Phase 2.5
  - settings.gradle.kts, gradle/libs.versions.toml    # `ml-contract` include · grpc/protobuf/protoc 버전 리터럴 고정(조사 노트 02)
  - config/quality/architecture-policy.properties     # D-2A-0b — `layer.order`·`layer.contract` 신설(층 개정), `module.expected-source-sets` 예외 없음(생성 srcDir 는 main 에 붙는다) — 게이트 정의 편집, 사유를 evidence 에
  - config/quality/gate-tests.properties              # `gate.tests.adapters` — 2A round-trip test 등재(키는 모듈 이름 규약)
  - adapters/src/test/kotlin/**                        # round-trip test(Kotlin 쪽) — 계약 타입 ↔ 도메인 타입 매핑은 **M4**, 여기서는 wire ↔ wire 만. 손으로 쓰는 test 는 전부 여기(ml-contract 에는 생성물만)
  - milestone-2.md                                    # 「Slice 2A」 착수 문단, **착수 시**
  - reports/evidence/m2/2a/**
out_of_scope:
  - shared-kernel/**, decision/**, qualification/**, strategy/**, procurement/**, settlement/**, workflow/**   # 도메인은 계약 타입을 보지 못한다(ADR 0006 D-6)
  - adapters/src/main/**                              # 도메인 ↔ 계약 매핑·client 배선은 M4 4D
  - prediction.proto, features.proto, training.proto  # 2B·2C
  - breaking-change gate 의 **증명**·unknown field/max payload/deadline/cancellation test·fake servicer   # 2D — 2A 는 lint 와 round-trip 까지
  - ml-engine 의 features/training/inference/serving  # M5
  - 인증·TLS·네트워크 배치                              # M6 6C
  - fixtures/**                                       # 2A 의 round-trip 표본은 `contracts/testdata/` 의 canonical 바이트 — fixture corpus 와 별개 축
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1 — 기존 게이트 전건 초록(층 개정 뒤) + `:ml-contract:generateProto` 가 check 의 의존
  - "(cd contracts && buf lint && buf build)"                                                          # S-2 — lint·컴파일(네트워크 없이 — buf 1.72.0 로컬 완결)
  - "./gradlew :ml-contract:generateProto :adapters:test --tests '*ContractRoundTrip*'"               # S-3 — Kotlin 생성(build/ 아래) + round-trip
  - "./gradlew :ml-contract:generateProto && ./gradlew :ml-contract:generateProto && git status --porcelain -- ml-contract"   # S-4 — 생성이 VCS 를 건드리지 않음(빈 출력) — 「생성물 비커밋」의 실측
  - "(cd ml-engine && python -m pytest tests/test_contract_roundtrip.py -q)"                          # S-5 — Python 임시 생성 + round-trip(Gradle check 밖 — CI 에 Python 툴체인 없음, 알려진 제한)
  - "./gradlew qualityBaseline"                                                                        # S-6
rollback: |
    **정본은 `reports/evidence/m2/2a/rollback.md`**(착수 시 작성 — 경로 한정 restore + 신규 경로 rm, 임시 clone 실측).
    `ml-contract` include·`layer.contract` 행·convention 편집을 걷으면 나머지 빌드는 2A 이전과 같다. 생성물은 VCS 에 없어 걷을 것이 없다.
```

작성: 2026-09-06, 세션 모델(Fable 5.1) 단독. 근거는 `m2-prep.md` 머리의 목록과 같다. **리뷰 r1(PR #2, 2026-09-06) 반영**: 게이트 가족 충돌
(D-2A-0a·0b), provenance 축 분리(①·③), `latest_promoted` 구멍(⑥), `FailureCode` 단독 소유(⑤), `Rate` scale 한 자리(②), `RateSource` → origin(D-2A-7),
봉투 분리(④), 래칫 제외 범위(D-2A-6).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — **착수 시 재고정한 base 로 다시 낸다.** 초안 시점은 해당 없음.

---

## 이 slice 가 하는 일

`milestone-2.md` 「Slice 2A」 일곱 항목을 **`.proto` 둘 + 양쪽 생성(VCS 밖) + round-trip** 으로 낸다.

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **`Money`** — `int64 amount_won` · `Currency currency`(허용값 `KRW` 하나) · `Basis basis` · `VatTreatment vat_treatment` · `AmountProvenanceKind provenance`. 다섯 성분은 shared-kernel `AmountRecord` 와 1:1 이고 `provenance` 는 **`Provenance` 여섯 변형의 라벨**(`PUBLISHED`·`DERIVED_FROM_OPENING`·`FILLED_FROM_BUDGET_KEY`·`COPIED_FROM_BASE_AMOUNT`·`OPERATOR_DECLARED`·`UNDECLARED`) — payload 성분(`noticeRevision`·`key`)은 Kotlin 에 남는다(D-2A-2). 소수 표현 없음(D-M2-6). **basis·provenance 가 `UNSPECIFIED` 인 금액은 거부** | 2A 「money」 · `v2-지침서.md` §4.1 「unit/basis/provenance가 없거나 모르는 값은 추측하지 않고 거부」 · ADR 0002 D-1 |
| ② | **`Rate`** — `string fraction` **하나**. 정규형 = `BigDecimal.toPlainString()`(지수 표기 없음, **scale 보존** — `0.8700` 과 `0.87` 은 다른 문자열이고 shared-kernel 의 `Rate` 가 `BigDecimal` scale 을 값의 일부로 나르는 것과 같다). 별도 `scale` 필드 없음(같은 사실 두 자리 금지). 승인 문면 「scale 명시」는 「문자열이 scale 을 잃지 않는다」로 읽는다. percent 표현 없음 — 어댑터가 fraction 으로만 만들고 **원문 unit 의 기록은 Kotlin 어댑터의 provenance**(§4.1)이지 wire 가 아니다. 축은 **필드·메시지 이름**이 나르고(`Candidate.bid_rate`·`CompetitionSample.observed_bid_rate`·`award_rate`), 관측/추천의 구분은 `origin`(D-2A-7) | 2A 「rate: fraction 고정, source와 scale 명시」 · §4.1 「`Rate`는 내부 fraction 표현 하나만」·「원문 unit을 기록한다」 |
| ③ | **enum** — `Basis`·`VatTreatment` 는 shared-kernel enum 값을 **이름까지** 미러(값 추가는 양쪽 동시, 제공자 먼저 배포). **provenance 축은 둘**: `AmountProvenanceKind`(① — 금액이 어떻게 얻어졌나, 여섯) 과 `BaseAmountProvenanceLabel`(기초금액 판정 라벨 다섯 — `CLEAN`·`DERIVED_YEGA`·`DERIVED_VAT`·`SUSPECT_RATIO`·`UNKNOWN`, decision 27, 라벨 집합 불변). 후자는 2B 의 피처 fact(`denominator_source` 자리)이고 `Money` 성분이 아니다 — 두 축을 한 이름에 접지 않는다(D-M2-10 이 legacy 어휘에 대해 지적한 오류를 V2 안에서 반복하지 않는다) | §6.3 · `capability-map.md` §14.2 `OPEN-DIC-05` 닫힘 · shared-kernel `Provenance`·`BaseAmountProvenance` |
| ④ | **봉투 둘** — `RequestEnvelope { request_id, correlation_id }` 는 **모든 RPC 필수**(빈 문자열 = 미지정 거부). `PredictionEnvelope { RequestEnvelope base, feature_schema_version, ModelReleaseSelector model_release_selector, deadline_policy_version }` 는 `CalculateOptimalBid` 전용 — `GetModelMetadata`·training RPC 는 `RequestEnvelope` 만. `deadline_policy_version` 은 **값이 아니라 정책 참조** — 실제 deadline 은 gRPC 메타데이터로(ADR 0010 D-1). `ModelReleaseSelector = oneof { LatestPromoted latest_promoted, ExactRelease exact_release(release_id, artifact_checksum) }` — `PredictionEnvelope` 에서 미지정 거부 | 2A 「`request_id`, `correlation_id`, `feature_schema_version`」·「`model_release_selector`, deadline 정책」 · §9 「계약 version과 모델 artifact로 모든 추천을 재현 가능」 |
| ⑤ | **결과 봉투 패턴** — 모든 응답은 `oneof result { <Success>, Unmeasurable unmeasurable, ApplicationFailure failure }`. `Unmeasurable { UnmeasurableReason reason, string detail_code }` 는 **transport error 가 아니다**. `ApplicationFailure { FailureCode code, bool retryable, string detail_code }`. **`FailureCode` 는 2A 가 단독 소유**하는 application 어휘이고 2B·2C 가 쓰는 값도 여기 등재한다: `UNSUPPORTED_SCHEMA`·`UNSUPPORTED_RELEASE`·`UNSUPPORTED_TRAINING_SPEC`·`INVALID_REQUEST`·`MODEL_NOT_READY`(retryable 후보)·`IDEMPOTENCY_CONFLICT`·`JOB_NOT_FOUND`. transport status(`RESOURCE_EXHAUSTED` 등)와 **이름을 겹치지 않는다** — 2C 의 job 실패 사유는 별도 enum `JobFailureCode`(2C D-2C-4) | 2A 「retryable/non-retryable application error」 · 「설계 규칙」 `oneof` · 완료 조건 「`Unmeasurable`가 transport error나 0으로 변환되지 않음」 · ADR 0001 D-6 · ADR 0010 D-3 |
| ⑥ | **fail-closed** — 모든 enum 은 `*_UNSPECIFIED = 0` 이고 수신 측은 `UNSPECIFIED` 와 **정의 밖 정수**를 거부한다(proto3 open enum). `feature_schema_version` 이 servicer 가 아는 집합 밖이면 `ApplicationFailure(UNSUPPORTED_SCHEMA, retryable=false)`. **제3 변환 금지** — `Success.release` 는 (a) `exact_release` 요청이면 그 release 와 같아야 하고 (b) `latest_promoted` 요청이면 **servicer 가 그 시점의 승격 release 로 답하고 client 는 `GetModelMetadata.promoted` 와 대조**한다(불일치 = `UNSUPPORTED_RELEASE` 취급, 승격이 그 사이 바뀐 경우는 재호출). 어느 경로에서도 다른 predictor 로의 폴백은 계약 위반(ADR 0010 D-3, legacy (c-2) 반례는 정확히 release 를 지목하지 않는 경로에서 일어났다). **조용한 기본값 대체 없음** | 2A 「지원하지 않는 enum/schema를 조용히 fallback하지 않는 규칙」 · 완료 조건 「미지원 schema/release가 fail-closed」 |
| ⑦ | **round-trip** — Kotlin 생성물과 Python 생성물이 같은 `contracts/testdata/*.binpb` 를 읽어 canonicalization(deterministic serialization — `OPEN-2A-CANONICAL-FORM`) 후 바이트 동일. `Rate.fraction` 정규형이 양쪽에서 같은 문자열(scale 보존 포함) | 완료 조건 「Kotlin/Python round-trip 결과가 canonicalization 후 일치」 |
| ⑧ | **lint** — buf lint 표준 규칙 + 패키지 `bidvector.ml.v1` + `option java_package = "bidvector.mlcontract.v1"`(D-2A-0a — 소유 패키지 규칙 `bidvector.<모듈명 하이픈 제거>`) + 필드 번호·`reserved` 규칙은 2D 의 breaking gate 가 증명 | 「설계 규칙」 필드 번호·`reserved` · 1A `PackageOwnershipPolicy` |

**만들지 않는 것**: 피처 벡터 메시지(2B) · training job(2C) · breaking mutation 증명·fake servicer(2D) · 도메인 ↔ 계약 매핑(M4)
· Python 쪽 validation 구현(5E — 2A 는 규칙을 계약 주석과 test 로만) · 사람이 읽는 오류 문장(`detail_code` 는 코드) · 생성물의 VCS 등재.

---

## 운영자 결정 필요 — 착수 전(D-2A-0a·0b·7) · 계약 고정(D-2A-1~6, 세션 모델 판단·사후 확인)

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-2A-0a** | **생성 코드가 1A 게이트 가족과 공존하는 방식.** `ConventionCoverageGate` 가 모든 subproject 에 convention 을 강제하고, `SourceLanguageGate` 가 `src/` 의 확장자를 `kt`·`kts`·`md` 로 제한하며(protobuf 메시지 생성물은 `.java`), convention 이 java srcDirs 를 비우고, `module.expected-source-sets=main,test` 가 `generated` source set 을 막는다 | (a) **생성물을 VCS 에 두지 않는다** — `generateProto` 가 `build/generated/` 에 내고 protobuf 플러그인이 그 디렉터리를 `main` source set 에 붙인다(`src/` 밖이라 `SourceLanguageGate` 무접촉, source set 추가 없음). convention 에서 **`ml-contract` 한 모듈만** java 컴파일을 켠다(생성물이 `.java`). protoc·플러그인은 Maven 아티팩트라 리뷰 레인의 RO 의존 캐시(codex-review-gate §4b)가 덮는다 (b) 생성물을 `src/generated` 에 커밋 + `SourceLanguageGate` 에 모듈 예외 + `expected-source-sets` 예외 + java srcDirs 예외 | **(a)** — (b) 는 게이트 정의 편집이 셋이고 전부 「예외」이며, 수동 편집 금지 게이트(재생성 diff 0)를 그 예외 위에 다시 세워야 한다. (a) 는 수동 편집이 **구조적으로 불가**(`build/` 는 VCS 밖)하고 편집은 convention 한 곳. **대가**: 리뷰어가 생성 소스를 diff 로 못 본다 — 정본은 `.proto` 이고 생성물은 결정적(S-4 두 번 생성 대조)이라 볼 이유가 없다. `D-M2-1` (a) 의 근거 ③·`2d` D-2D-2 를 이 결정에 맞춘다 | 착수 전 · Phase 2.5 |
| **D-2A-0b** | **`ml-contract` 의 층.** `ModuleDependencyPolicy` 는 `layer.order`·`layer.<층>` 목록이고 한 모듈의 허용 의존은 **자기보다 아래 층 전부**다. 층 밖이면 허용 집합이 비어 `adapters` 의 의존이 위반, `layer.domain` 이면 `group.forbidden` 의 `io.grpc`·`com.google.protobuf` 가 자기에게 걸리고, `layer.application` 이면 계약이 application 이 된다 | (a) **새 층 `contract` 를 `application` 과 `adapters` 사이에**(`layer.order=domain,application,contract,adapters,app`, `layer.contract=ml-contract`) (b) `layer.adapters` 에 같이 둠 (c) `layer.application` | **(a)** — 의존 방향 `adapters <- ml-contract` 가 층 규칙으로 성립하고 도메인·application 은 아래 층이라 참조 불가. (b) 는 같은 층 의존의 허용 여부가 정책에 없고, (c) 는 의미가 틀리다. **대가**: `layer.order` 개정 = 게이트 정의 편집(Phase 2.5). `ml-contract` 가 아래 층(domain·application)을 의존할 수 있는 형태가 되지만 빌드 파일에 그 의존이 없음을 `ModuleDependencyGate` 의 실측 목록으로 남긴다 | 착수 전 · Phase 2.5 |
| **D-2A-7** | **승인 문면 「source 명시」의 읽기.** 두 읽기가 있다 — (i) 율의 **축**(floor/assessment/bid/award) (ii) §4.1 「percent 입력은 adapter 에서 변환하며 **원문 unit 을 기록**」의 unit 출처 | (a) **어느 쪽도 wire 필드로 두지 않는다** — 축은 필드·메시지 이름이 나르고(조사 g-1), 원문 unit 은 Kotlin 어댑터 provenance 소유. 대신 V2 가 실제로 갖는 구분 **`BidRateOrigin { OBSERVED, RECOMMENDED }`** 을 `bid_rate` 가 있는 자리(`CompetitionSample`·`Candidate`)에 `origin` 으로 싣는다(shared-kernel `BidRate.origin` 미러) (b) `RateSource` 축 enum (c) 원문 unit enum | **(a)** — (b) 는 이름이 이미 나르는 사실을 두 자리에 두고 관측/추천 구분을 잃는다(리뷰 r1). (c) 는 ml-engine 이 알 이유가 없는 어댑터 사정 | 착수 전 |
| **D-2A-1** | `Money` 에 **`EstimatedAmount`·`YegaAmount` 를 위한 타입 분리를 wire 에 두지 않는다** — wire 는 `Money + basis` 하나이고 축 분리는 Kotlin 도메인(1B)이 소유. ML 이 받는 금액은 `BASE_AMOUNT` basis 뿐(2B 가 입력 필드 이름으로 고정) | — | §3.2 · ADR 0003 D-2 | 계약 고정 |
| **D-2A-2** | **provenance 의 wire 성분은 라벨뿐** — `Provenance.Published(noticeRevision)`·`FilledFromBudgetKey(key)` 의 payload 는 Kotlin 안. 피처가 요구하는 것은 범주 | — | §6.3 · ADR 0003 D-2 · 조사 g-1 | 계약 고정 |
| **D-2A-3** | **`Unmeasurable` 사유 어휘는 계약 소유 enum**(`UnmeasurableReason { INSUFFICIENT_SAMPLES, UNTRAINED_SEGMENT, FEATURE_ABSENT }`)이고 Kotlin `ReasonCode` 에 더하지 않는다 — 어댑터가 `Measurement.Unmeasurable` 로 매핑(M4). `UNTRAINED_SEGMENT` ≠ `INSUFFICIENT_SAMPLES` | — | ML-02 · 1D `FloorUnmeasurableReason` 선례 | 계약 고정 |
| **D-2A-4** | **결측은 사유** — `MissingReason { UNKNOWN, NOT_APPLICABLE, NOT_COLLECTED_YET }` 을 2A 가 정의, 2B 의 모든 fact 가 `oneof { value, missing }` | — | §6.3 · §13.3 | 계약 고정 |
| **D-2A-5** | **`double` 은 계약 어디에도 없다** — 율·점수·불확실성 성분 전부 decimal string | — | D-M2-6 · §9 · 1E D-1 | 계약 고정 |
| **D-2A-6** | **래칫 제외는 `ml-contract` 모듈 단위** — D-2A-0a 로 이 모듈은 **생성물만** 갖는다(손으로 쓰는 test 는 전부 `adapters/src/test`). 그래서 detekt·ktlint·CPD·size 를 모듈 단위로 끄는 사유(「생성물이라 통과 불가」)가 모듈 전체에 성립한다. `architecture`·`packageOwnership`·`jarContent` 게이트는 켠다(`option java_package` 가 소유 패키지를 맞춘다) | — | ADR 0007 · 1A `config/quality` | 계약 고정 |

---

## 위협 모델 — 2A 고유 경계 (게이트 정의 편집 slice — Phase 2.5)

**(0) 경계 문장**: 2A 가 방어하는 것은 **계약의 의미 손실**과 **생성물 경계의 침식**(도메인이 계약 타입을 보는 것, 생성물을 손으로 고치는 것)이다. 게이트 정의(`architecture-policy`·convention)를 편집하는 사람은 방어하지 않는다 — `milestone-1.md` 「완료 조건 — 게이트 회피 경계」와 같은 경계.

**방어한다**: (a) `Unmeasurable` 이 transport error 나 `0` 으로 접힘(oneof + test) (b) 미지 enum 정수·`UNSPECIFIED` 통과(양쪽 거부 test) (c) 율의 `double` 유출·scale 손실(타입 부재 + 정규형 test) (d) basis·provenance 없는 금액(`UNSPECIFIED` 거부) (e) 생성물 수동 편집(구조 — VCS 밖, S-4) (f) 도메인이 계약 타입을 import(층 규칙 — `ml-contract` 는 `contract` 층, 아래 층은 참조 불가) (g) 다른 release 의 답(⑥ — 두 선택자 모두) (h) provenance 두 축의 혼용(enum 둘, `Money` 에는 하나만).
**방어하지 않는다**: 어댑터 매핑의 정직성(M4) · Python validation 의 실행(5E) · 정책 데이터 내용 · 네트워크·인증(M6) · 생성 도구의 결함 · `ml-contract` 가 아래 층을 참조하는 빌드 편집(실측 목록으로만).

**우회 후보(≥5)**: (1) enum 에 `UNSPECIFIED` 없이 첫 값 0 → buf lint (2) `Unmeasurable` 을 `ApplicationFailure(retryable=true)` 로 → 2D 재시도 test + ADR 0010 「`Unmeasurable` 은 재시도 대상 아님」 (3) `fraction = "1e-3"` → 정규형 거부 (4) `feature_schema_version` 빈 문자열 → 필수 validation (5) `ml-contract` 를 도메인이 `testImplementation` 으로 → `ModuleDependencyGate` 가 test 구성도 보는지 착수 시 실측(1A 관례) (6) 생성 디렉터리를 `src/` 로 옮겨 커밋 → `SourceLanguageGate` 가 `.java` 를 잡는다(D-2A-0a 의 부수 효과 — 게이트가 (b) 를 막는다) (7) `KRW` 외 currency 추가 → breaking gate + 계약 version (8) `latest_promoted` 응답을 폴백 predictor 로 → ⑥ (b) 대조 test.

---

## 조사 결과 — 이 slice 에 영향을 주는 것 (`_workspace/m2-prep/`)

- legacy 인터페이스 실물(`01_scout_ml_interface.md`, `ed4b06c`): 피처 금액은 태그 없는 `float` 원(① 다섯 성분 근거 — `won` 만이면 R-BASIS-06 재발) · 율은 `float`
  fraction(② 근거) · 응답 모델 식별은 `model_version` 문자열 하나(④ 근거) · **모든 추론 실패가 다른 predictor 의 값 있는 답으로 접힘**(`orchestration.py:264-273`)
  → ⑥ 제3 변환 금지 · `confidence` 클램프 [0.45, 0.95] → D-2A-3 · `Provenance` 는 라벨만 wire 로(D-2A-2) · `denominator_source` 어휘는 V2 라벨과 다른 축(D-M2-10).
- gRPC 스택(`02_grpc_stack_compat.md`): grpc-kotlin **1.5.0 리터럴** · grpc-java **1.84.0** · protobuf-java **3.25.9** · grpcio/grpcio-tools **1.83.1** · protobuf(py) **7.36.1** ·
  buf **1.72.0**. proto3 확정. **2A 착수 시 grpc-kotlin 1.5.0 + grpc-java 1.84.0 조합의 컴파일·런타임 스모크가 선행**(POM 선언 `grpc-stub:1.62.2`, 검증 이력 없음).
  Java·Python protobuf 런타임은 독립 버전 축 — 「같은 major」 서술 금지.
- 1A 게이트 구현체 실측(리뷰 r1): `PackageOwnershipPolicy.ownedPackage = bidvector.<모듈명 하이픈 제거>` → `option java_package` · `SourceLanguageGate.allowedExtensions` →
  생성물은 `src/` 밖 · `layer.order=domain,application,adapters,app` → 층 신설 · `module.expected-source-sets=main,test` → 생성 srcDir 는 main 에 붙임.

---

## OPEN — 수령·신설

| OPEN | 2A 처리 |
| --- | --- |
| `OPEN-ADR-11` | 봉투의 `deadline_policy_version` 참조까지만 — 규칙·값은 ADR 0010 / D-M2-9 |
| `OPEN-ML-03` | D-M2-8 (a) — 2A 는 「`double` 없음」과 「점수는 축 이름을 가진 메시지」 규칙까지, 메시지는 2B |
| `OPEN-DIC-05`(닫힘) | `BaseAmountProvenanceLabel` 다섯 미러 — 소비만 |
| 신설 후보 `OPEN-2A-CANONICAL-FORM` | round-trip canonicalization 방식(protobuf deterministic serialization vs JSON canonical) — 착수 시 확정 |
| 신설 후보 `OPEN-2A-LAYER-CONTRACT` | D-2A-0b 층 신설의 파급(`architecture-policy` 다른 키·capability-map §9 소유 축 표) — 착수 시 역방향 grep |
