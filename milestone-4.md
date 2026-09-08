# 마일스톤 4 — Workflow, event, 상태 제어와 ML gateway

## 목표

수집된 canonical fact, 전략, 자격, ML 후보, 알림 요청을 하나의 거대한 service 함수가 아닌
명시적 use case와 상태 전이로 조합한다. 모든 외부 효과는 port 뒤에 두고 fake로 검증한다.

## 선행 조건

- M1~M3 승인
- 상태 전이와 event/outbox ADR 승인

## 구현 대상

### Slice 4A — Strategy edit state machine

- `WaitingForValue -> WaitingForConfirmation -> Applied/Cancelled/Expired`
- 허용 command와 invalid transition
- actor/operator scope
- timeout과 중복 command
- 상태와 event의 version

Telegram DTO는 adapter에만 존재하고 domain state가 Telegram library를 import하지 않는다.

**M4 착수·4A 착수 2026-09-08** — 선행 조건 충족(M1~M3 승인 — M3 잔여 0, `milestone-3.md` 「M3 완료 2026-09-08」 · 상태 전이·event/outbox ADR 0004·0005 는
M0 승인). 준비 정본 `reports/evidence/m4/prep/m4-prep.md`(slice 지도·D-M4-1~8 추천안), 4A 계약 `reports/evidence/m4/4a/scope.md`(base `9948c6e`).
**레인 격리** — 다른 세션이 `main` 에서 M3 후속을 진행 중이라 M4 는 별도 worktree + 브랜치 `m4/2026-09-08` 에서 산다(`main` 병합은 종결 뒤 사용자 승인 사항).
착수 전 운영자 결정: **시작 slice 4A** · **D-M4-1 (a)** 채널 독립 use case 만 세우고 Telegram 어댑터는 `후속`(`OPEN-STR-12` 활성 유지 — 상태 기계는
웹 편집 6A 에도 쓰이므로 채택과 독립) · **D-4A-2 (a)** 상태 기계를 `workflow`(application) 안에 순수 Kotlin 으로(도메인 게이트가 `isDomain=false` 라
걸리지 않으므로 채널 타입 경계는 소스 스캔 test 가 진다) · **D-4A-1 (a)** `strategy-edit-*` corpus 신설(`authored-from-approved-spec`).
Phase 2.5 설계 검토는 세션 모델이 직접 했다(`_workspace/m4-4a/02_design-review.md`) — 전이표 밖 거부·이중 적용·채널 타입 유입을 각각 소진 `when`·
상태 종단성·allow-list 소스 스캔의 **구성형**으로 닫고, `EventSink` port 는 STR-07 이 `폐기`로 못 박은 「호출자 규율」 재현을 막기 위해 남긴다
(발행 비원자성은 4C 가 닫을 **알려진 제한**으로 선언). 과잉으로 뺀 셋: 처리한 command id 전체 집합 · 별도 `Effect` 목록 타입 · 만료 sweep use case.

### Slice 4B — application use case

- notice 수집 완료
- qualification 평가
- low-cost strategy filter
- 필요 시 ML inference 요청
- decision 후보 조립
- state 저장과 domain event 기록

transaction 경계와 실패 시 상태를 명시한다. catch-all exception으로 성공처럼 계속하지
않는다.

### Slice 4C — event/outbox

- `StrategyUpdated`, `NoticeQualified`, `PredictionRequested`, `DecisionPrepared`,
  `NotificationRequested`
- event id, aggregate version, idempotency/correlation/causation id
- DB state와 outbox의 atomic commit
- consumer inbox/dedup
- duplicate, out-of-order, crash-after-commit test

Spring in-process event는 로컬 관찰용으로 쓸 수 있지만, 신뢰성 있는 외부 side effect의
유일한 보장으로 사용하지 않는다.

### Slice 4D — ML gateway

- M2 generated coroutine gRPC client
- deadline, cancellation, circuit breaker, bounded retry
- domain input → contract DTO mapping
- ML response → candidate mapping
- release/checksum/schema provenance 보존
- ML unavailable을 `review/unavailable`로 처리하는 fail-safe 정책

### Slice 4E — notification adapter contract

- delivery request와 rendered content 분리
- dry-run/fake sender
- masking과 owner isolation
- 동일 idempotency key의 단일 delivery effect

## 완료 조건

- 상태 전이 property test와 invalid transition test 통과
- use case test가 DB/network 없이 fake port로 실행
- duplicate/redelivery/out-of-order event에서 상태가 수렴
- ML timeout 시 thread/connection이 고갈되지 않고 업무 결과가 fail-safe
- dry-run에서 실제 Telegram/email 호출 0
- trace/correlation id가 수집→판정→ML→알림 요청까지 유지
- 한 application 함수에 수집·DB·ML·알림 구현이 함께 들어가지 않음

## Codex 독립 리뷰

> **2026-09-04 운영자 결정:** 아래 관점은 Phase 4 `verifier` 가 적용한다. Codex 리뷰는 코드 slice 의
> 기본 경로가 아니며 운영자가 명시 요청할 때만 건다. 완료 조건의 「Codex `approve`」는
> 「verifier `ready-for-review`」로 읽는다.

- transaction/outbox 사이에 유실 창이 있는지
- invalid state/event가 조용히 허용되는지
- retry와 idempotency가 함께 설계됐는지
- ML 장애가 위험한 추천으로 fail-open하는지
- Telegram/framework 타입이 domain을 오염시키는지
- 테스트가 실제 side-effect adapter를 차단하는지

## 범위 밖

- 실제 broker/Telegram/email
- Python ML 계산 구현
- public API/UI
