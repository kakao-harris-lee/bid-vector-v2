# Slice 계약 — M4 / 4A · Strategy edit state machine — **초안, 구현 전**

> **지위**: M2 진행 중 세션 모델이 쓴 초안. 착수는 M1~M3 승인 뒤 운영자 지시, `prep/m4-prep.md` D-M4-1~3 답 수령 뒤. 1E 의 `OperatorStrategy`·
> `validate`·`StrategyRevision`·`StrategyEvent.StrategyUpdated` 를 **소비**하고 편집 흐름 상태 기계를 세운다. Telegram 채택(`OPEN-STR-12`)과 독립(D-M4-1 (a)).

```yaml
milestone: m4
slice: 4a-strategy-edit-state-machine
base_sha: 040ab9d   # 초안 앵커 — **M3 승인 뒤 재고정**
head_sha: 리뷰 시점의 HEAD
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/strategy/**   # StrategyEditSession aggregate(상태 기계)·command·전이표·timeout 정책 슬롯·use case(begin/provideValue/confirm/cancel/expire)·port(StrategyRepository·EditSessionRepository·Clock)
  - workflow/src/test/**                                        # 전이표 전수 test(허용 쌍·거부 쌍 관측)·property(중복 command 멱등·timeout)·fake port
  - workflow/build.gradle.kts                                   # implementation(project(":strategy")) 한 줄(이미 있으면 무변경)
  - config/quality/gate-tests.properties                        # 조건부 — `gate.tests.workflow`
  - fixtures/**                                                 # 조건부 — D-4A-1 로 `strategy-edit-*` case 신설(curator, authored-from-approved-spec)
  - app/src/test/kotlin/bidvector/app/conformance/**             # 조건부 — runner dispatch
  - milestone-4.md, reports/evidence/m4/4a/**
out_of_scope:
  - strategy/**                                                 # 1E 승인 산출물 — 필요한 타입 부재 시 멈추고 보고
  - Telegram/웹 어댑터(DTO·키보드·alias 41개)                    # 채널은 어댑터(후속/6A). 도메인 state 가 Telegram library 를 import 하지 않는다(4A 문면)
  - 저장 구현(3D 스키마의 세션 테이블·Flyway)                     # 4A 는 port + fake. 실제 테이블은 4C/3D 와 함께
  - 이벤트 봉투·outbox 등록                                       # 4C — 4A 는 `StrategyUpdated` payload 를 **낳는 자리**까지(port `EventSink` 에 넘김)
  - 스냅샷 재계산 디스패치(STR-07 소비자)·감시 run(STR-08/09)      # M4 4B·후속
  - STR-13~15 튜닝·온보딩·실험 갱신(`후속`)                        # actor `System` 경로의 실행
  - M2 경로·capability-map·data-dictionary 편집
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "./gradlew :workflow:test"                                                                        # S-2 — 전이표 전수·property·use case
  - "./gradlew :workflow:moduleDependencyGate :workflow:sizeGate :workflow:cpdCheck"                   # S-3 — application 층 게이트(`domainApiTypeGate`·`domainSourceReferenceGate` 는 모든 모듈에 등록되나 `isDomain` 이 false 면 no-op — workflow 에는 걸리지 않는다, 실물 확정)
  - "./gradlew :workflow:test --tests '*EditSessionImportBoundaryTest*'"                               # S-3b — 전이 함수·세션 타입의 외부 import 0(D-4A-2 (a)) + 양성 대조(Telegram import 를 넣은 fixture 가 실패)
  - "./gradlew :app:test --tests '*Conformance*'"                                                      # S-4 — 조건부
  - "./gradlew qualityBaseline"                                                                        # S-5
rollback: |
    **정본은 `reports/evidence/m4/4a/rollback.md`**(착수 시). workflow/.../strategy/** 를 걷으면 앵커 상태.
```

작성: 2026-09-07, 세션 모델 단독. 근거: `milestone-4.md` 4A · `capability-map.md` STR-07·STR-11(「결정과 무관하게 확정」·「채택 시 요구되는 관찰 가능 동작」)·STR-15 ·
`data-dictionary.md` §2.2(전이표 형태)·§2.2.6 · 1E `scope.md` D-4·D-10·D-17 · `ADR 0005` D-9·D-10 · `ADR 0006` D-2.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **상태 기계** — `EditSessionState = sealed { WaitingForValue(field), WaitingForConfirmation(draft, validation), Applied(revision), Cancelled(reason), Expired }`. 전이표(명시 state/event table): `WaitingForValue --ValueProvided--> WaitingForConfirmation`(validation 결과 동반) · `WaitingForConfirmation --Confirmed--> Applied`(**apply 시 재검증** — 스테이지 사이에 전략이 바뀌었을 수 있음) · `WaitingForConfirmation --Rejected/Edit--> WaitingForValue` · 어느 비종료 상태 `--Cancelled--> Cancelled` · `--TimedOut--> Expired`. **표에 없는 쌍은 거부이며 거부가 관측 가능**(결과 타입 `Transition.Rejected(from, command, reason)`) | 4A 「`WaitingForValue -> WaitingForConfirmation -> Applied/Cancelled/Expired`」·「허용 command 와 invalid transition」 · STR-11 「apply 시점에 검증이 재실행된다」 · §13.2 |
| ② | **검증 실패는 상태를 바꾸지 않는다** — `ValueProvided` 가 1E `validate` 에서 `Invalid` 면 세션은 `WaitingForValue(같은 field)` 유지, 저장된 `OperatorStrategy` 불변 | STR-11 「검증 실패 시 저장된 전략이 변하지 않고 같은 항목의 입력 대기 상태가 유지된다」 · STR-03 acceptance |
| ③ | **actor / operator scope** — **command 의 actor(4A 소유)**: 모든 command 가 `actor: Actor`(`Operator(id)` · `System(reason)` — D-M4-2 타입만)를 나르고, 세션은 시작 actor 와 다른 operator 의 command 를 거부. **봉투의 actor(4C 소유)**: `Applied` 가 낳는 `StrategyUpdated` 의 봉투는 이 command actor 를 **필수**로 싣는다(「누가 바꿨나」 기록 — `OPEN-STR-04` 실물이 actor 기록 부재). 단일 회사(`OPEN-STR-07` 해소)라 operator 식별자는 세션 소유권 검사에만 | 4A 「actor/operator scope」 · 1E D-17 · §2.2.6 「actor 슬롯은 4A 가 넓힌다」(command 축) · D-M4-2·D-M4-4(봉투 축) |
| ④ | **timeout 과 중복 command** — 세션 `expiresAt`(정책 데이터 슬롯, 값은 승인) 을 넘긴 command 는 `Expired` 전이 뒤 거부. 같은 `commandId` 의 재전달은 **멱등**(첫 결과 반환, 상태 불변) — property test | 4A 「timeout 과 중복 command」 |
| ⑤ | **상태와 event 의 version** — 세션은 `sessionVersion` 을, 적용 결과는 `StrategyRevision`(1E)을 갖는다. `Confirmed` 는 「내가 본 revision」을 실어 **낙관적 동시성**(그 사이 다른 경로가 전략을 바꿨으면 거부 → 재검증 경로) | 4A 「상태와 event 의 version」 |
| ⑥ | **적용은 이벤트를 낳는다** — `Applied` 전이가 `StrategyEvent.StrategyUpdated(revision, policyVersion)` 를 `EventSink` port 로 넘긴다(봉투·outbox 는 4C). **모든 편집 경로가 이 use case 를 지난다**(웹·채널·온보딩) — 우회 write 경로 없음이 STR-07 의 구조적 답 | STR-07 「전략 저장이 도메인 이벤트를 낳고 파생 산출물이 구독한다」·「모든 편집 경로에서 동일」 |
| ⑦ | **채널 독립** — use case 입력은 도메인 값(field id·원시 값 문자열)이고 Telegram/웹 DTO·alias 라우팅은 어댑터. `moduleDependencyGate` 는 **외부 좌표를 domain 층에서만 검사**하므로(실물 확정 — `groupViolations`·`mainExternalViolations` 의 `isDomain` 가드) `workflow` 의 Telegram 무의존은 그 게이트가 재지 않는다 → **D-4A-2 (a) 의 「전이 함수·세션 타입의 외부 import 0」 test** 가 방어 장치(kotlin-stdlib·shared-kernel·strategy 외 import 0 을 소스 스캔으로 단언) | 4A 「Telegram DTO 는 adapter 에만 존재하고 domain state 가 Telegram library 를 import 하지 않는다」 · STR-11 「채널 독립 유스케이스 하나」 |
| ⑧ | **corpus** — D-4A-1: STR-11 「채택 시 요구되는 관찰 가능 동작」 둘 + STR-03 편집 거부 셋을 `strategy-edit-*` case 로(1E `strategy-validation-*` 재사용) | M4 완료 조건(fixture 기반) |

**만들지 않는 것**: 채널 어댑터 · 저장 테이블 · 봉투/outbox · 스냅샷 무효화 소비자 · 온보딩/실험 갱신 실행 · 사람이 읽는 메시지 문장.

---

## 운영자 결정 필요 — 착수 전(D-4A-1·2) · 계약 고정(D-4A-3~5)

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-4A-1** | **corpus 신설** — STR-11 「채택 시 요구되는 관찰 가능 동작」 둘 + STR-03 acceptance 중 **편집 거부 둘**(`review > bidNow` 거부·`minBudget > maxBudget` 거부, 1E case 재사용) + **상태 구분 하나**(「설정됐는가」≠「좁히는가」 — 거부가 아니라 값 구분) | (a) **STR-11·STR-03 문면 승인 → curator 신설(`authored-from-approved-spec`)** (b) 1E case 재사용만 | **(a)** — 1E case 는 validation 만 덮고 전이·timeout·중복은 없음 | 착수 전 |
| **D-4A-2** | **상태 기계의 자리** — 조사 (g): `db-scheduler`·`resilience4j` 는 `group.forbidden` 이라 domain 금지이나 **순수 전이 함수**는 프레임워크 무의존 | (a) **`workflow`(application) 안에 순수 Kotlin 으로**(만료 트리거·저장·재시도와 같은 모듈, 도메인 게이트는 안 걸리므로 `group.forbidden` 무관 — 단 전이 함수 자체는 외부 import 0 을 test 로 단언) (b) `strategy` 도메인 모듈에 전이 함수만 두고 `workflow` 가 트리거(1E 승인 산출물 편집 — 계약 갱신 필요) | **(a)** — 편집 흐름은 운영 관심사(ADR 0005 D-9·D-10 과 같은 갈래)이고 1E 가 도메인 어휘를 「전략이 바뀌었다」까지로 닫았다(§2.2.6). (b) 는 도메인 게이트(타입 멤버 30·API 타입)가 세션 타입까지 재고 1E 재승인이 필요. `milestone-4.md` 「domain state 가 Telegram library 를 import 하지 않는다」는 (a) 에서 「전이 함수 외부 import 0」 test 로 성립 | 착수 전 |
| **D-4A-3** | timeout 값·재확인 창은 정책 데이터 슬롯(값은 승인) — legacy 값이 있으면 `legacy-behavior` 로 test 정책에만 | — | 계약 고정 |
| **D-4A-4** | `Expired`·`Cancelled` 는 종단 — 재개는 새 세션(같은 draft 를 seed 로 복사는 허용, 상태 재사용 금지) | — | 계약 고정 |
| **D-4A-5** | `System` actor 의 `Confirmed` 는 **거부**(STR-15 `후속` — 실험 갱신도 사람이 확인) — 타입은 있으나 전이표에 `System` 행이 없다 | D-M4-2 | 계약 고정 |

---

## 위협 모델 — 4A 고유 경계

**방어한다**: (a) 검증 없이 적용(② + 1E `@ConsistentCopyVisibility` + `internal constructor` 조합) (b) 전이표 밖 전이의 조용한 무시(① 거부 관측) (c) 우회 write 경로(⑥ — `StrategyRepository.save` 는 use case 만 호출: 리뷰 항목 + ArchUnit 가능 여부 실측) (d) 중복 command 의 이중 적용(④) (e) stale confirm(⑤ revision 대조) (f) System 의 무승인 갱신(D-4A-5) (g) 전이 함수·세션 타입의 외부 import(S-3b 소스 스캔 test — `moduleDependencyGate` 가 아니다).
**방어하지 않는다**: 채널 어댑터의 alias 매핑 옳음 · 저장 구현의 원자성(4C/3D) · 스냅샷 재계산의 실행(4B/후속) · timeout 값의 옳음.

**우회 후보(≥5)**: (1) `Applied` 로 직접 `copy` → 전이는 `apply(command)` 하나(1E 관례 — `@ConsistentCopyVisibility` + `internal constructor` 조합이 `copy()` 를 닫는다) (2) `Confirmed` 에 revision 없이 → 필수 필드 (3) `StrategyRepository.save` 를 어댑터가 직접 호출 → 리뷰·ArchUnit(착수 시) (4) timeout 을 정책에서 무한 → 정책 상한 불변식 (5) 같은 `commandId` 로 다른 command → `IdempotencyConflict` 거부 (6) `System` actor 로 `Confirmed` → D-4A-5 거부 test.

---

## 조사 결과 — 이 slice 에 영향을 주는 것

- 1E 조사(`_workspace/m1-1e/01_scout_preflight.md`): legacy 에 편집 상태 기계 없음(`updated_at` 만), STR-11 의 2단 흐름은 Telegram 모듈 안·pending 은 analytics 로그 행 → ①·D-M4-3.
- 조사 (a): `Expired` TTL 슬롯 0건·전이표 0건·invalid transition 개념 없음(「목표 상태 → 효과」 룩업) → 4A 는 신설. `OPEN-STR-04` 실물은 운영자 POST + 가드 셋, 빠진 것은 actor 기록 → ③ 의 actor 는 기록 필수.
  fixture 0건 → D-4A-1 (a). `OPEN-STR-12` 는 착수 전 운영자 결정(Telegram 어댑터 포함 여부 — 상태 기계는 무관). 배치: 순수 전이는 어느 층이든, 트리거·재시도는 workflow/adapters → D-4A-2.

---

## OPEN — 수령·신설

| OPEN | 4A 처리 |
| --- | --- |
| `OPEN-STR-12` | D-M4-1 (a) — 상태 기계는 채널 독립. 채택은 관측 뒤(활성 유지) |
| `OPEN-STR-04` | D-M4-2·D-4A-5 — actor 타입만, System 확인 경로 거부 |
| `OPEN-STR-07`(해소) | 세션 소유권 검사에만 operator 식별자 |
| 신설 후보 `OPEN-4A-WRITE-PATH-GATE` | 「모든 편집 경로가 use case 를 지난다」를 ArchUnit/의존 게이트로 표현할 수 있는지 — ADR 0005 §6 이 미확인으로 둔 것과 같은 축 |
