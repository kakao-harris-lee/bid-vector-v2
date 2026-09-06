# Slice 계약 — M2 / 2A · 공통 값과 오류 계약 — **초안, 구현 전**

> **지위**: M1/1E 병행 중에 세션 모델이 쓴 **계약 초안**이다. 구현·gradle·의존성 추가·fixture 편집은 하지 않았다.
> 착수는 M1 전체 승인 뒤 운영자 별도 지시로 하며, 그때 `base_sha` 를 재고정하고 `m2-prep.md` 「착수 전 결정」
> D-M2-1~9 의 답을 받은 뒤 `milestone-2.md` 착수 문단을 쓴다.

```yaml
milestone: m2
slice: 2a-common-values-and-errors
base_sha: c9022d9989c4b2a09cf8b9ff94795176dc5dc00c   # 초안 작성 시점 HEAD — **M1 전체 승인 뒤 착수 시 재고정**
head_sha: 리뷰 시점의 HEAD
in_scope:
  - contracts/proto/bidvector/ml/v1/common.proto      # 값 타입·enum·요청 봉투(request_id·correlation_id·feature_schema_version·model_release_selector·deadline 정책 필드)
  - contracts/proto/bidvector/ml/v1/error.proto       # application error(retryable / non_retryable) + oneof 결과 봉투 패턴
  - contracts/buf.yaml, contracts/buf.lock            # lint 규칙(D-M2-7 잠정 proto3, enum UNSPECIFIED=0, 패키지 `bidvector.ml.v1`)
  - ml-contract/**                                    # D-M2-1 (a) — Kotlin 생성 전용 모듈(생성물만, 수동 편집 금지, 래칫 제외 선언)
  - ml-engine/pyproject.toml, ml-engine/src/ml_engine/contracts/**, ml-engine/tests/test_contract_roundtrip.py   # D-M2-3 (a) — 최소 골격
  - build-logic/**                                    # 조건부 — protobuf 플러그인 convention 한 파일(D-M2-2 (a))
  - settings.gradle.kts, gradle/libs.versions.toml    # `ml-contract` include · grpc/protobuf 버전 고정(조사 노트 02 의 조합)
  - config/quality/architecture-policy.properties     # 의존 방향 표에 `adapters <- ml-contract` 등재 + 도메인 참조 금지(게이트 정의 편집 — 사유를 evidence 에)
  - adapters/src/test/kotlin/**                        # round-trip test(Kotlin 쪽) — 계약 타입 ↔ 도메인 타입 매핑은 **M4**, 여기서는 wire ↔ wire 만
  - milestone-2.md                                    # 「Slice 2A」 착수 문단, **착수 시**
  - reports/evidence/m2/2a/**
out_of_scope:
  - shared-kernel/**, decision/**, qualification/**, strategy/**, procurement/**, settlement/**, workflow/**   # 도메인은 계약 타입을 보지 못한다(ADR 0006 D-6)
  - adapters/src/main/**                              # 도메인 ↔ 계약 매핑·client 배선은 M4 4D
  - prediction.proto, training.proto                  # 2B·2C
  - breaking-change gate 의 **증명**(breaking mutation 을 실제로 잡는 test)·unknown field/max payload/deadline/cancellation test·fake servicer   # 2D — 2A 는 lint 와 round-trip 까지
  - ml-engine 의 features/training/inference/serving  # M5
  - 인증·TLS·네트워크 배치                              # M6 6C
  - fixtures/**                                       # 2A 의 round-trip 표본은 test 안 리터럴이 아니라 `contracts/testdata/` 의 canonical 바이트 — fixture corpus 와 별개 축
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1 — 기존 게이트 전건 초록 + `ml-contract` 래칫 제외가 **모듈 단위**로 선언됨
  - "(cd contracts && buf lint && buf build)"                                                          # S-2 — lint·컴파일(네트워크 없이 — 로컬 protoc 플러그인만)
  - "./gradlew :ml-contract:generateProto :adapters:test --tests '*ContractRoundTrip*'"               # S-3 — Kotlin 생성 + round-trip
  - "(cd ml-engine && python -m pytest tests/test_contract_roundtrip.py -q)"                          # S-4 — Python 생성물 + round-trip
  - "./gradlew qualityBaseline"                                                                        # S-5
rollback: |
    **정본은 `reports/evidence/m2/2a/rollback.md`**(착수 시 작성 — 경로 한정 restore + 신규 경로 rm, 임시 clone 실측).
    `ml-contract` 모듈 include 와 `architecture-policy` 의존 행을 걷으면 나머지 빌드는 2A 이전과 같다.
```

작성: 2026-09-06, 세션 모델(Fable 5.1) 단독. 근거는 `m2-prep.md` 머리의 목록과 같다.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — **착수 시 재고정한 base 로 다시 낸다.** 초안 시점은 해당 없음.

---

## 이 slice 가 하는 일

`milestone-2.md` 「Slice 2A」 일곱 항목을 **`.proto` 둘 + 양쪽 생성 + round-trip** 으로 낸다.

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **`Money`** — `int64 amount_won` · `Currency currency`(허용값 `KRW` 하나, `UNSPECIFIED` 거부) · `Basis basis` · `VatTreatment vat_treatment` · `ProvenanceKind provenance`. 소수 표현 없음(D-M2-6). **basis 가 없는 금액은 wire 에 없다** — 필드가 `optional` 이 아니라 `UNSPECIFIED` 거부 | 2A 「money」 · `v2-지침서.md` §4.1 「unit/basis/provenance가 없거나 모르는 값은 추측하지 않고 거부」 |
| ② | **`Rate`** — `string fraction`(십진 문자열, 정규형: 선행 0·후행 0 없음, `scale` 와 일치) · `uint32 scale` · `RateSource source`(어느 축의 율인가: `FLOOR_RATE`·`ASSESSMENT_RATE`·`BID_RATE`·`AWARD_RATE` — shared-kernel 의 율 넷과 1:1). percent 표현 없음 — 어댑터가 fraction 으로만 만든다 | 2A 「rate: fraction 고정, source와 scale 명시」 · §4.1 「`Rate`는 내부 fraction 표현 하나만」 |
| ③ | **enum 셋** — `Basis`·`VatTreatment` 는 shared-kernel enum 값을 **이름까지** 미러(값 추가는 양쪽 동시, 계약 version 상승). `ProvenanceKind` 는 `BaseAmountProvenance` 승인 라벨 다섯(decision 27, 라벨 집합 불변)을 미러 — `data-dictionary.md` §6.3 「`denominator_source` 피처는 도메인 provenance 어휘를 그대로 피처 공간에 싣는다」 | §6.3 · `capability-map.md` §14.2 `OPEN-DIC-05` 닫힘 |
| ④ | **요청 봉투** — `RequestEnvelope { request_id, correlation_id, feature_schema_version, model_release_selector, deadline_budget }`. `deadline_budget` 은 **값이 아니라 정책 참조**(`policy_version`) — 실제 deadline 은 gRPC 메타데이터로 나가고 봉투는 「어느 정책으로 정했는가」만 싣는다(ADR 0010 D-1). `model_release_selector = oneof { latest_promoted, exact_release(release_id, checksum) }` — 미지정 거부 | 2A 「`request_id`, `correlation_id`, `feature_schema_version`」·「`model_release_selector`, deadline 정책」 · §9 「계약 version과 모델 artifact로 모든 추천을 재현 가능」 |
| ⑤ | **결과 봉투 패턴** — 모든 응답은 `oneof result { <Success>, Unmeasurable unmeasurable, ApplicationFailure failure }`. `Unmeasurable { UnmeasurableReason reason, string detail_code }` 는 **transport error 가 아니다**(gRPC status `OK` 위의 도메인 결과). `ApplicationFailure { FailureCode code, bool retryable, string detail_code }` | 2A 「retryable/non-retryable application error」 · 「설계 규칙」 `oneof` · 완료 조건 「`Unmeasurable`가 transport error나 0으로 변환되지 않음」 · ADR 0001 D-6 |
| ⑥ | **fail-closed 규칙** — 모든 enum 은 `*_UNSPECIFIED = 0` 이고 수신 측은 `UNSPECIFIED` 와 **정의 밖 정수**를 거부한다(proto3 open enum). `feature_schema_version` 이 servicer 가 아는 집합 밖이면 `ApplicationFailure(UNSUPPORTED_SCHEMA, retryable=false)`. `model_release_selector.exact_release` 가 없으면 `UNSUPPORTED_RELEASE`. **조용한 기본값 대체 없음** | 2A 「지원하지 않는 enum/schema를 조용히 fallback하지 않는 규칙」 · 완료 조건 「미지원 schema/release가 fail-closed」 |
| ⑦ | **round-trip** — Kotlin 생성물과 Python 생성물이 같은 `contracts/testdata/*.binpb` 를 읽어 canonicalization(JSON canonical form 또는 deterministic serialization) 후 바이트 동일. `Rate.fraction` 정규형이 양쪽에서 같은 문자열 | 완료 조건 「Kotlin/Python round-trip 결과가 canonicalization 후 일치」 |
| ⑧ | **lint** — buf lint 표준 규칙 + 패키지 `bidvector.ml.v1` + 필드 번호 재사용 금지·`reserved` 규칙은 2D 의 breaking gate 가 증명 | 「설계 규칙」 필드 번호·`reserved` |

**만들지 않는 것**: 피처 벡터 메시지(2B) · training job(2C) · breaking mutation 증명·fake servicer(2D) · 도메인 ↔ 계약 매핑(M4)
· Python 쪽 validation 구현(5E — 2A 는 규칙을 계약 주석과 test 로만) · 사람이 읽는 오류 문장(`detail_code` 는 코드).

---

## 운영자 결정 필요 — 착수 전은 `m2-prep.md` D-M2-1~9 · 계약 고정(세션 모델 판단, 사후 확인)

| ID | 판단 | 근거 |
| --- | --- | --- |
| **D-2A-1** | `Money` 에 **`EstimatedAmount`·`YegaAmount` 를 위한 타입 분리를 wire 에 두지 않는다** — wire 는 `Money + basis` 하나이고 축 분리는 Kotlin 도메인(1B)이 소유. 계약이 실어야 하는 것은 「어느 basis 인가」이지 Kotlin 의 타입 계층이 아니다. ML 이 받는 금액은 `BASE_AMOUNT` basis 뿐(2B 가 입력 필드 이름으로 고정) | §3.2 「ml-engine 은 업무 entity 를 모른다」 · ADR 0003 D-2 |
| **D-2A-2** | `Provenance` 의 **성분 중 wire 로 나가는 것은 라벨(`ProvenanceKind`)뿐** — `Published`·`FilledFromBudgetKey` 의 payload(출처 키·시각)는 Kotlin 안에 머문다. 피처가 요구하는 것은 「어떤 출처인가」의 범주 | §6.3 · ADR 0003 D-2 |
| **D-2A-3** | **`Unmeasurable` 사유 어휘는 계약 소유 enum**(`UnmeasurableReason { INSUFFICIENT_SAMPLES, UNTRAINED_SEGMENT, FEATURE_ABSENT, … }`)이고 Kotlin `ReasonCode` 에 더하지 않는다 — 어댑터가 도메인 `Measurement.Unmeasurable` 로 매핑(M4). `UNTRAINED_SEGMENT` 와 `INSUFFICIENT_SAMPLES` 는 **다른 값**(ML-02 「학습된 적 없음」과 「표본 얕음」 구별) | `capability-map.md` ML-02 acceptance · 1D `FloorUnmeasurableReason` 선례(어휘 소유 분리) |
| **D-2A-4** | **결측은 값의 부재가 아니라 사유다** — 2A 가 `MissingReason { UNKNOWN, NOT_APPLICABLE, NOT_COLLECTED_YET }` enum 을 정의하고 2B 의 모든 피처가 `oneof { value, MissingReason missing }` 을 갖는다. `0` 이나 빈 문자열로 결측을 나르는 필드는 없다 | `data-dictionary.md` §6.3 「모든 피처가 결측 사유를 provenance로 갖는다」 · §13.3 「`MissingReason`은 피처 계약의 필수 동반 필드」 |
| **D-2A-5** | **`double` 은 계약 어디에도 없다** — 율·점수·불확실성 성분 전부 decimal string + scale. 점수 `[0,1]` 도 같다(1E 의 `Score` 와 같은 이유 — 축 혼동 방지는 이름·메시지로, 정밀도는 문자열로) | D-M2-6 · §9 재현성 · 1E D-1 |
| **D-2A-6** | 생성물 래칫 제외는 **모듈 단위 선언 하나**(`ml-contract` 의 convention 에서 detekt·ktlint·CPD·size 를 끄되 `architecture` 게이트는 켠다 — 도메인 참조 금지는 생성물에도 적용) | ADR 0007 · `config/quality` 게이트 정의 |

---

## 위협 모델 — 2A 고유 경계

**방어한다** — 계약의 **의미 손실**: (a) `Unmeasurable` 이 transport error 나 `0` 으로 접힘(oneof + 완료 조건 test) (b) 미지 enum 정수·`UNSPECIFIED` 가 기본값으로 통과(양쪽 거부 test) (c) 율이 `double` 로 나가 재현성 손실(타입 부재) (d) basis·VAT·provenance 없는 금액이 통과(`UNSPECIFIED` 거부) (e) 생성 코드 수동 편집(재생성 diff 0 게이트 — 2D 가 증명, 2A 는 규칙) (f) 도메인이 계약 타입을 import(architecture 게이트 — `ml-contract` 는 `adapters` 만 참조 가능) (g) 필드 번호 재사용(buf breaking — 2D).
**방어하지 않는다** — 어댑터가 도메인 값을 **정직하게** 매핑하는가(M4 4D 의 test), Python 이 validation 을 실제로 수행하는가(5E), 정책 데이터 내용(deadline 값), 네트워크·인증(M6), 생성 도구 자체의 결함.

**우회 후보(≥5)**: (1) enum 에 `UNSPECIFIED` 없이 첫 값을 0 으로 → buf lint `ENUM_ZERO_VALUE_SUFFIX` 가 잡는다 (2) `Unmeasurable` 을 `ApplicationFailure(retryable=true)` 로 내보내 재시도 루프 → 2D deadline/retry test + ADR 0010 의 「`Unmeasurable` 은 재시도 대상이 아니다」 (3) `fraction` 문자열에 `1e-3` 같은 지수 표기 → 정규형 test 가 거부 (4) `feature_schema_version` 을 빈 문자열로 → 필수 필드 validation(빈 문자열 = 미지정) (5) `ml-contract` 를 도메인 모듈이 `testImplementation` 으로 참조 → architecture 게이트가 test 소스셋도 본다(1A 관례 확인 필요 — 착수 시 실측) (6) 생성물을 손으로 고쳐 커밋 → 2D 재생성 diff 게이트 (7) `KRW` 외 currency 를 enum 에 추가 → 계약 version 상승 + breaking gate.

---

## 조사 결과 — 이 slice 에 영향을 주는 것 (`_workspace/m2-prep/`, 레인 완료 후 인라인)

- legacy 인터페이스 실물(율의 단위·금액 표현·결측 접힘): 대기
- gRPC 스택 고정 버전 조합·proto3/editions·buf 로컬 실행 가능성: 대기

---

## OPEN — 수령·신설

| OPEN | 2A 처리 |
| --- | --- |
| `OPEN-ADR-11` | 봉투의 `deadline_budget` 정책 참조 필드까지만 — 규칙·값은 ADR 0010 / D-M2-9 |
| `OPEN-ML-03` | D-M2-8 (a) — 2A 는 「`double` 없음」과 「점수는 축 이름을 가진 메시지」 규칙까지, 메시지 자체는 2B |
| `OPEN-DIC-05`(닫힘) | `ProvenanceKind` 다섯 라벨 미러 — 소비만 |
| 신설 후보 `OPEN-2A-CANONICAL-FORM` | round-trip 의 canonicalization 방식(protobuf deterministic serialization vs JSON canonical) — 조사 노트 02 결과로 착수 시 확정 |
