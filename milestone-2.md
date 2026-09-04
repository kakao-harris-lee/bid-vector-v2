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

### Slice 2C — 비동기 training 계약

장시간 학습은 동기 RPC 응답을 기다리지 않는다.

- immutable dataset reference와 manifest checksum
- training request id와 idempotency key
- accepted/running/succeeded/failed 상태
- artifact manifest와 evaluation report reference
- cancel/retry 권한과 상태 전이

transport는 별도 job API 또는 broker contract 중 ADR에서 하나를 선택한다.

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
