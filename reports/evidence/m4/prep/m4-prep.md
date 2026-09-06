# M4 준비 — Workflow, event, 상태 제어와 ML gateway · slice 지도와 착수 전 결정 후보 (초안, 구현 전)

> **지위**: M2 진행 중에 세션 모델(Fable 5.1)이 단독으로 쓴 **준비 문서**. 구현 없음. **M4 착수는 M1~M3 승인 + 상태 전이·event/outbox ADR 승인 뒤**
> (`milestone-4.md` 「선행 조건」 — ADR 0004·0005 는 M0 승인 ✓, 상태 전이표는 `data-dictionary.md` §2.2). **M2 와 독립인 것**: 4A(전략 편집 상태 기계 —
> 1E 가 넘긴 것), 4E(알림 어댑터 계약), 4C 의 ML 무관 어휘(`StrategyUpdated`·`NoticeQualified`·`DecisionPrepared`·`NotificationRequested`). **의존**: 4D(M2 client
> 그 자체), 4B 의 「ML inference 요청」·4C 의 `PredictionRequested`.
>
> 근거: `milestone-4.md` · `v2-지침서.md` §4.5·§8 · `ADR 0005` D-1~D-11·`OPEN-ADR-12/13` · `ADR 0004` · `ADR 0008` · `capability-map.md` STR-07~STR-15·
> NOTI-01~NOTI-11·OPS-01/03·§14.2 `OPEN-STR-04`·`OPEN-STR-12`·`OPEN-NOTI-*`·`OPEN-OPS-10` · `data-dictionary.md` §2.2(전이표 넷·§2.2.5 outbox·§2.2.6 전략)·§3.6 ·
> 1E 계약(D-4·D-17, `StrategyEvent.StrategyUpdated`·`StrategyRevision`) · 조사 노트 `_workspace/m4-prep/01_scout_workflow.md`.

---

## 1. M4 가 로드맵에서 서는 자리

- **workflow(application) 모듈**이 use case·상태 전이·port 소유(ADR 0006 D-2, ADR 0005 D-10 — lease port 는 `workflow` 소유). 도메인 연산은 M1 커널(1C·1D·1E), 수집 fact 는 M3, ML 후보는 M2/4D, 알림은 4E.
- 1E 가 넘긴 경계: 「전략이 바뀌었다」 payload(`StrategyEvent.StrategyUpdated(revision, policyVersion)`)까지가 1E, **봉투**(event id·aggregate version·idempotency/correlation/causation)는 4C, **편집 상태 기계**와 actor 슬롯(`OPEN-STR-04`)은 4A.
- 전달 의미는 확정: 채널 배달 **at-most-once**(`OPEN-NOTI-02` 해소), 앱 알림함이 회수 경로(NOTI-10), 브로커 없음(ADR 0005 D-1), 도메인 write 와 outbox 등록은 같은 트랜잭션(D-2).

---

## 2. slice 지도

| slice | 하는 일(요지) | 선행 | M2 의존 | 정본 |
| --- | --- | --- | --- | --- |
| **4A Strategy edit state machine** | `WaitingForValue → WaitingForConfirmation → Applied/Cancelled/Expired` · 허용 command·invalid transition 거부(관측 가능) · actor/operator scope · timeout·중복 command · 상태와 event 의 version · **채널 독립 use case**(Telegram/웹은 어댑터, STR-11 「결정과 무관하게 확정」) · apply 시 재검증(1E `validate`) · 저장 시 `StrategyUpdated` 방출(STR-07) | 1E ✓ · `OPEN-STR-12`(Telegram 채택) 결정 | 없음 | `4a/scope.md`(초안 있음) |
| **4B application use case** | notice 수집 완료 → qualification(1C) → low-cost strategy filter(1E `WatchRules`) → 필요 시 ML 요청(4D) → decision 후보 조립(1D) → state 저장 + event 기록 · 트랜잭션 경계·실패 상태 명시 · catch-all 금지 · lease port(ADR 0005 D-10) | M3 3A·3D · 4A·4C | **부분**(ML 요청 단은 4D 뒤 — 그 전엔 `MlUnavailable` 경로만) | 착수 시 |
| **4C event/outbox** | 이벤트 다섯 · 봉투(id·aggregate version·idempotency/correlation/causation) · DB state + outbox atomic commit(db-scheduler 트랜잭션 스테이징, D-5) · consumer inbox/dedup · duplicate·out-of-order·crash-after-commit test · `OutboxEntryState` 어휘(`OPEN-OPS-10`) | M3 3D(스키마) | `PredictionRequested` 만 — 나머지 넷은 독립 | 착수 시(어휘·봉투는 이 문서 §3 D-M4-4~6 이 미리 닫는다) |
| **4D ML gateway** | M2 client·deadline/cancel/breaker/bounded retry(ADR 0010 규칙, 값은 정책 데이터)·매핑·provenance 보존·fail-safe `MlUnavailable`(ADR 0010 D-6, 사전 등재)·`OPEN-2A-RELEASE-CHECK-4D` 인계 | M2 2A~2D | **전부** | 착수 시 |
| **4E notification adapter contract** | delivery request ↔ rendered content 분리 · dry-run/fake sender · masking·owner isolation(NOTI-03) · 동일 idempotency key 의 단일 delivery effect · 배달 경로 판정 enum(정책 vs 환경 분리) · at-most-once(재시도 없음, D-11 표) | ADR 0005 ✓ | 없음 | `4e/scope.md`(초안 있음) |

**M4 전체 out_of_scope**: 실제 broker/Telegram/email 발송 · Python ML 계산 · public API/UI(ADR 0008·M6 6A) · 피로도 게이트(NOTI-02 `후속`) · 채널 fallback(NOTI-11, `OPEN-NOTI-07`).

---

## 3. 착수 전 결정 후보 D-M4-1~8

| ID | 물음 | 선택지 | 추천·근거 |
| --- | --- | --- | --- |
| **D-M4-1** | **`OPEN-STR-12` Telegram 편집 채널 채택 여부** — 4A 의 전제 | (a) **4A 는 채널 독립 use case + 상태 기계를 세우고 Telegram 어댑터는 `후속`으로 분리(웹 어댑터도 M6 6A)** — 채택 여부와 무관하게 상태 기계는 필요(STR-11 「결정과 무관하게 확정」: 편집은 채널 독립 유스케이스 하나) (b) Telegram 채택 확정 후 4A (c) 4A 자체를 후속 | **(a)** — STR-11 분류가 `근거 부족`(운영자 실사용 불명)이라 채택 결정은 관측 없이 못 내린다. 상태 기계는 웹 편집(6A)에도 같이 쓰이므로 4A 의 가치는 채널과 독립. Telegram DTO 는 어댑터에만(4A 문면) |
| **D-M4-2** | **`OPEN-STR-04` 실험의 무승인 갱신** — actor 슬롯의 형태 | (a) **`StrategyEvent` 봉투(4C)에 `actor: sealed { Operator(id), System(reason) }` 를 두고, `System` 갱신은 4A 상태 기계에서 `WaitingForConfirmation` 을 **건너뛸 수 없다**(STR-15 `후속` 이라 System 경로는 값 없이 타입만)** (b) actor 없음 | **(a)** — 1E D-17 이 「4A 가 봉투에서 넓힌다」로 보냈다. 타입만 두고 System 경로의 실행은 STR-15 채택 시 |
| **D-M4-3** | **편집 상태의 저장 자리** — legacy 는 analytics 이벤트 로그에 pending 저장(`폐기`) | (a) **`workflow` 소유 `StrategyEditSession` aggregate + 3D 스키마의 별도 테이블(canonical 아님·audit 아님 — 세션 상태)** (b) 메모리 (c) outbox 재사용 | **(a)** — timeout·중복 command·crash 뒤 재개가 요구되므로 영속. 로그 스캔(최근 100행) 형태 폐기 |
| **D-M4-4** | **이벤트 봉투 형태**(4C, `OPEN-DIC-07`·`OPEN-DIC-09`) | (a) **`EventEnvelope<P>(eventId: UUIDv7, aggregateId, aggregateVersion, occurredAt, correlationId, causationId?, idempotencyKey, actor?, payload: P)`** — payload 는 도메인 sealed(1E `StrategyUpdated` 등) (b) 평면 필드 | **(a)** — 봉투와 payload 의 소유가 다르다(봉투 = workflow, payload = 도메인). `idempotencyKey` 는 부작용 선언(ADR 0005 D-3)의 키 |
| **D-M4-5** | **`OutboxEntryState` 어휘**(`OPEN-OPS-10`) | (a) **`Pending → Claimed → Delivered \| Failed(final) \| Isolated`** — `Claimed` 에서 워커 사망 시 **`Isolated`(수동 검토, 재실행 없음 — at-most-once)**, `Failed(final)` 은 최대 시도 소진(sweep 무한 점유 금지) (b) db-scheduler 내부 상태 노출 | **(a)** — NOTI-05 「running 으로 남은 행은 자동 재발송하지 않고 격리」·OPS-03 acceptance. db-scheduler 상태는 어댑터 안(D-9) |
| **D-M4-6** | **`MlUnavailable` 이름·자리**(ADR 0010 D-6 — 4D 착수 시 사전 등재) | (a) **`decision` 도메인의 판정 어휘에 `Verdict.Review(reason = MlUnavailable(reason))`** — 추천 없음이지 낮은 추천 아님 (b) workflow 전용 상태 | **(a)** — 하류 처리 「review/unavailable」이 `Verdict` 축(§3.6). 사유 어휘는 2A `UnmeasurableReason`+transport 사유 |
| **D-M4-7** | **lease 어댑터**(`OPEN-ADR-12`) | (a) **PostgreSQL 세션 advisory lock**(TTL 없음 — 홀더 사망 시 즉시 해제, OPS-01 ②) (b) db-scheduler 의 single-instance (c) 테이블 lease + TTL | **(a)** — OPS-01 acceptance 「강제 종료 시 즉시 해제」를 구조로 갖는 유일한 후보. port 는 `workflow` 소유(D-10) |
| **D-M4-8** | **`OPEN-NOTI-04/05/07/08`** — 4E 범위 | (a) **메일 라이브 송신·채널 fallback 은 4E 밖(`후속`), 읽음 상태 되돌림은 「안 한다」, 통지 이후 판정 확정의 재통지는 「기존 메시지 갱신 없이 앱 알림함에 새 항목」** (b) 각각 채택 | **(a)** — 셋은 legacy 미검증·미구현 경로, 하나는 at-most-once 와 정합하는 최소. 운영자 즉답 대상 |

---

## 4. `OPEN` 처리 후보

| OPEN | M4 처리 |
| --- | --- |
| `OPEN-STR-12` | D-M4-1 (a) — 상태 기계는 채널 독립, 채택은 관측 뒤 |
| `OPEN-STR-04` | D-M4-2 (a) — 타입만 |
| `OPEN-DIC-07`·`OPEN-DIC-09` | D-M4-4 봉투 |
| `OPEN-OPS-10` | D-M4-5 어휘 — 4C 착수 시 `data-dictionary.md` §2.2.5 갱신 |
| `OPEN-ADR-12` | D-M4-7 |
| `OPEN-ADR-13`(db-scheduler 실패 기본값) | 4C — 기본 재시도를 끄고 D-11 표대로 부작용 유형별 |
| `OPEN-NOTI-01` | 4E — 미전달을 완료로 마킹하는 경로 **없음**(결과 enum 이 미전달을 나른다) |
| `OPEN-NOTI-04/05/07/08` | D-M4-8 |
| `OPEN-NOTI-06` | 값은 운영 관측 — 4E 는 정책 데이터 슬롯 |
| `OPEN-2A-RELEASE-CHECK-4D` | 4D 계약이 수령 |

---

## 5. 병행 규칙 (M2 진행 중)

- M4 코드 착수 금지(M1~M3 승인 선행). 이 문서·4A·4E 초안·조사 노트까지. `workflow/`·`adapters/` 는 앵커 상태 유지.
- 금지 경로: M2 in_scope 전부 + `capability-map.md`·`data-dictionary.md` + `strategy/**`(1E 산출물, 4A 는 참조만).

---

## 6. 조사 결과 요약 (`_workspace/m4-prep/01_scout_workflow.md`)

- 대기.
