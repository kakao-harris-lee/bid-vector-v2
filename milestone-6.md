# 마일스톤 6 — Public API, 통합 검증과 배포 후보

## 목표

승인된 V2 capability를 독립 시스템으로 조립하고, 기존 `bid-vector` 없이 clean environment에서
검증 가능한 release candidate를 만든다. 기존 API 전체를 호환하거나 모든 기능을 재현하는
단계가 아니다.

## 선행 조건

- M1~M5 전체 승인 (slice 별 verifier `ready-for-review` + 사용자 승인; Codex 는 운영자 요청 시)
- M0 capability map의 V2 필수 범위 확정
- 실제 외부 호출/배포 여부에 대한 사용자 승인 경계 확인

## 구현 대상

### Slice 6A — public API와 auth

- M0에서 승인된 endpoint만 설계
- OpenAPI 단일 출처와 generated client/type
- 명시적 error body, pagination, nullability
- operator scope/RBAC와 audit
- controller는 workflow use case만 호출

기존 FastAPI path/schema와의 호환은 제품 요구로 승인된 항목에만 적용한다.

### Slice 6B — persistence와 migration completeness

- clean PostgreSQL에서 Flyway 전체 재현
- constraint/index/optimistic lock
- backup/restore와 migration rollback 정책
- raw/canonical/audit/outbox 데이터의 수명과 masking

### Slice 6C — container와 local environment

- Kotlin app, Python ML serving, PostgreSQL, broker가 필요한 경우에만 구성
- multi-stage build와 non-root runtime
- health/readiness 분리
- secret는 environment/secret store로만 주입
- dependency/image version 고정과 SBOM/vulnerability check

### Slice 6D — E2E와 장애 주입

- mock KONEPS → canonical fact → qualification → ML → decision → fake notification
- 중복 공고, ML timeout, broker redelivery, DB conflict, malformed contract
- restart 후 outbox/inbox 수렴
- model rollback과 incompatible schema 거부
- 동일 input/policy/model version의 결과 재현

### Slice 6E — 제품 acceptance와 운영 runbook

- capability별 acceptance scenario
- metric/SLO, log/trace/dashboard
- backup/restore, model rollback, incident procedure
- live read probe와 notification dry-run 절차
- 기존 시스템과 V2를 비교한 차이 목록과 의도적 폐기 기능

## 완료 조건

- 새 checkout/clean database에서 one-command build/test 가능
- 기존 `bid-vector` import, symlink, DB schema에 runtime dependency 없음
- M0 필수 capability E2E 전체 통과
- 실제 외부 effect 없이 dry-run acceptance 가능
- 중요 mutation 생존 0 또는 사용자 승인된 명시적 예외
- 계약/모델/policy version으로 결과 재현
- rollback/restore rehearsal 증거 존재
- security/secret scan 통과
- verifier 최종 `ready-for-review` 와 사용자 release 승인 (Codex 최종 리뷰는 운영자가 요청하면 추가)

## Codex 독립 리뷰

> **2026-09-04 운영자 결정:** 아래 관점은 Phase 4 `verifier` 가 적용한다. Codex 리뷰는 코드 slice 의
> 기본 경로가 아니며 운영자가 명시 요청할 때만 건다. 완료 조건의 「Codex `approve`」는
> 「verifier `ready-for-review`」로 읽는다.

- E2E가 fake shortcut으로 핵심 production wiring을 우회하지 않는지
- 기존 프로젝트에 숨은 runtime dependency가 없는지
- auth/operator isolation/secret masking이 실제로 검증되는지
- failure injection 후 상태가 수렴하는지
- 완료 범위가 M0 capability map보다 과장되지 않았는지
- 배포·외부 호출이 사용자 승인 없이 자동화되지 않았는지

## 완료 후 별도 승인 사항

verifier·Codex 승인은 다음을 자동 허용하지 않는다.

- 실제 KONEPS/LLM/Telegram/email 호출
- 운영 DB 또는 credential 사용
- public 배포
- 기존 `bid-vector` 중지·삭제
- 원격 merge/push
