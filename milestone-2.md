# 마일스톤 2 — Versioned 계약과 gRPC 경계

## 목표

Kotlin application과 Python ML engine 사이의 유일한 동기 계약을 먼저 정의하고 양쪽 생성
코드와 compatibility gate를 만든다. ML이 DB나 Kotlin 업무 entity를 알아야 하는 요청은
설계 실패다.

## 선행 조건

- M1 전체 승인
- M0의 transport ADR 승인
- Money/Rate/Basis canonical 표현 확정

## 구현 대상

### Slice 2A — 공통 값과 오류 계약

- money: 정수 원화 또는 scale이 명시된 decimal string
- rate: fraction 고정, source와 scale 명시
- basis, VAT, provenance enum
- `request_id`, `correlation_id`, `feature_schema_version`
- `model_release_selector`, deadline 정책
- retryable/non-retryable application error
- 지원하지 않는 enum/schema를 조용히 fallback하지 않는 규칙

**M2 착수·2A 착수 2026-09-06** — 선행 조건 충족(M1 1A~1E 종결 승인 `040ab9d`, `ADR 0003` 승인, `ADR 0002` canonical 표현).
준비 정본 `reports/evidence/m2/prep/m2-prep.md`(slice 지도·착수 전 결정 D-M2-1~14, 준비 세션의 리뷰 r1~r4 반영), 2A 계약
`reports/evidence/m2/2a/scope.md`. 운영자 결정: **`ADR 0010` 승인**(값은 정책 데이터·규칙은 ADR, deadline 필수, transport/
application/domain 3층 분리, 제3 변환 금지, training 은 별도 job API 로 Kotlin 이 폴링 — `OPEN-ADR-11` 닫힘) · D-M2-1~14 전부
추천안. 구조는 **`contracts/proto/` 단일 출처 + `ml-contract` included build**(생성물 VCS 밖, `build-logic` 선례) — 1A 게이트
가족이 subproject 안의 생성물을 어떤 형태로든 거부함을 준비 리뷰가 실측했고, 게이트 정의 편집은 `ResolvedDependencies` 의
composite 치환 의존 분류 정정 한 분기뿐이다(D-2A-0 (c)). 생성 Java 패키지 루트는 `bidvector` 밖(`contract.bidvector.ml.v1`)
이라 도메인의 import 는 T-A 가 구조적으로 거부한다. 2A 가 양쪽 생성 + round-trip 을 선행 흡수하고(D-M2-2) 2D 는 게이트 증명만.
`EstimateShortfall` RPC 는 제외(1D 가 Kotlin `decision` 에 둠, D-M2-4). money 는 `int64` 원·rate 는 decimal string·`double`
없음(D-M2-6), proto3(D-M2-7). 계약 어휘: `PriceFitness`·`Uncertainty` 세 성분만, 낙찰 확률 필드 없음(`OPEN-ML-03` 계약 수준
닫힘), `denominator_source` 는 V2 라벨만(재학습은 M5), Platt 자격 라벨은 계약 밖(`OPEN-ML-02` 남은 물음은 M5·M4). 환경
전건으로 buf 1.72.0 을 설치했고 Python `grpcio-tools` 는 `ml-engine` 가상환경에 2A 가 둔다.

### Slice 2B — Prediction RPC

`BidPredictionService`에 최소 다음 RPC를 정의한다.

- `CalculateOptimalBid`
  - 입력: canonical 금액, floor rate, versioned feature vector, 정제된 경쟁 표본,
    optimization objective
  - 출력: 후보 타점, 모델 점수, 불확실성, model release/checksum, diagnostics
- `EstimateShortfall`
  - 이 계산을 Python에 둘 필요가 M0에서 승인된 경우에만 정의
  - 그렇지 않으면 Kotlin domain 소유로 유지하고 RPC에서 제외
- `GetModelMetadata`
  - release, feature schema, readiness 정보

업무 최종 verdict, operator 권한, DB id 조회, notification 여부는 메시지에 넣지 않는다.

**2A 종결·2B 착수 2026-09-07** — 2A 는 verifier ready-for-review + 사용자 승인으로 닫혔다(`reports/evidence/m2/2a/checklist.md`
「사용자 승인」). 2B 계약 정본 `reports/evidence/m2/2b/scope.md`. 착수 전 운영자 결정 넷(전부 추천안): **D-2B-1 (a)** wire 는 변환된
피처 벡터가 아니라 **원 fact**(`FeatureInputs`) — 변환은 ml-engine `features/` 가 training·serving 공용으로 하고 피처 이름·순서
계약은 ml-engine 안(5B)에 남는다(§3.2 training-serving skew 방지) · **D-2B-2 (a)** 응답은 **율만** — 원 단위 투찰가는 Kotlin 이
1B 산술(`RoundingPolicy`)로 만든다 · **D-2B-3 (a)** 경쟁 표본은 식별자 없는 `CompetitionSample`(+ `ReserveDrawObservation`:
예비가격 15·추첨번호 4) — 정제는 송신 전 Kotlin 어댑터, serving 은 DB 를 갖지 않는다 · **D-2B-4 (a)** `OptimizationObjective`
enum 은 M5 지원값 하나(`SCENARIO_TRIPLE`)만, 미지원 fail-closed. `EstimateShortfall` 은 D-M2-4 로 제외. 후보는 항상 3(보수/기준/
공격, 라벨 enum + 순서), 점수는 `PriceFitness`, 불확실성은 §6.5 세 성분, 낙찰 확률 필드 없음(D-M2-8).

### Slice 2C — 비동기 training 계약

장시간 학습은 동기 RPC 응답을 기다리지 않는다.

- immutable dataset reference와 manifest checksum
- training request id와 idempotency key
- accepted/running/succeeded/failed 상태
- artifact manifest와 evaluation report reference
- cancel/retry 권한과 상태 전이

transport는 별도 job API 또는 broker contract 중 ADR에서 하나를 선택한다.

**2B 종결·2C 착수 2026-09-07** — 2B 는 verifier ready-for-review + 사용자 승인으로 닫혔다(`reports/evidence/m2/2b/checklist.md`
「사용자 승인」; 신설 OPEN 셋은 `capability-map.md` §14.3). 2C 계약 정본 `reports/evidence/m2/2c/scope.md`. transport 는 `ADR 0010`
D-8 대로 **별도 job API**(같은 gRPC 서버의 unary 셋 `StartTraining`·`GetTrainingJob`·`CancelTrainingJob`, Kotlin 이 폴링, broker·
서버 스트리밍 불채택). 착수 전 운영자 결정 둘(추천안): **D-2C-1 (a)** `idempotency_key` 는 Kotlin 이 업무 의도 단위로 발급하고
ml-engine 은 불투명 문자열로 저장·대조만 · **D-2C-2 (a)** `training_spec_version` 은 ml-engine 안의 versioned training spec(5C
소유)을 가리키며 요청은 version 문자열만 — 미지 version 은 job 실패가 아니라 `StartTraining` 거부(`UNSUPPORTED_TRAINING_SPEC`).
전이표 `ACCEPTED → RUNNING → SUCCEEDED | FAILED`, `ACCEPTED | RUNNING → CANCELLED`, 표 밖 전이 거부. `JobFailureCode` 는 2A
`FailureCode` 와 다른 enum(층이 다르다). 시각은 UTC `Timestamp`, job 목록 조회 없음(D-2C-6).

### Slice 2D — 생성·호환성·provider test

- canonical `.proto` 단일 출처
- Kotlin/Python code generation
- lint와 breaking-change gate
- 양쪽 serialization round-trip
- unknown field/enum, max payload, deadline, cancellation test
- fake servicer를 이용한 consumer/provider test

## 설계 규칙

- `oneof`로 success/unmeasurable/application failure를 구분한다.
- transport error와 domain result를 섞지 않는다.
- 필드 번호를 재사용하지 않고 제거 필드는 `reserved` 처리한다.
- feature 배열만 보내지 말고 schema/version/order를 검증한다.
- 생성된 코드는 수동 편집하지 않는다.
- Kotlin/Python 양쪽에 같은 rule을 재구현하지 않는다.

## 완료 조건

- 계약 lint와 breaking-change test 통과
- Kotlin/Python round-trip 결과가 canonicalization 후 일치
- 미지원 schema/release가 fail-closed
- `Unmeasurable`가 transport error나 0으로 변환되지 않음
- 요청만으로 Python serving이 DB 조회 없이 계산 가능
- 최대 메시지 크기와 deadline 정책이 근거와 함께 문서화됨
- fake server 장애 시 retry 가능한 경우만 제한 횟수로 재시도

## Codex 독립 리뷰

> **2026-09-04 운영자 결정:** 아래 관점은 Phase 4 `verifier` 가 적용한다. Codex 리뷰는 코드 slice 의
> 기본 경로가 아니며 운영자가 명시 요청할 때만 건다. 완료 조건의 「Codex `approve`」는
> 「verifier `ready-for-review`」로 읽는다.

- 계약에 업무 규칙이나 DB coupling이 새어 들어갔는지
- unit/basis/provenance가 모든 numeric field에 명시됐는지
- oneof/error semantics가 양쪽에서 같은지
- compatibility gate가 실제 breaking mutation을 잡는지
- gRPC를 쓰기 위해 불필요하게 거대한 payload/서비스를 만든 것은 아닌지

## 범위 밖

- 실제 LightGBM/KDE 구현
- 실제 KONEPS/LLM 호출
- public web API
- production broker/DB
