# Slice 계약 — M4 / 4A · Strategy edit state machine

> **지위**: **착수 계약 2026-09-08.** 초안(2026-09-07, M2 진행 중 작성)을 운영자 결정 넷으로 고정하고 base 를 재고정했다.
> 1E 의 `OperatorStrategy`·`validate`·`StrategyRevision`·`StrategyEvent.StrategyUpdated` 를 **소비**하고 편집 흐름 상태 기계를 세운다.
>
> **레인 격리**: 다른 세션이 같은 저장소의 `main` 에서 M3 후속을 진행 중이라 이 slice 는 **별도 worktree + 브랜치**에서 산다 —
> worktree `/Users/harris/Development/private/bid-vector-v2-m4`, 브랜치 `m4/2026-09-08`. `main` 병합은 slice 종결 뒤 **사용자 승인 사항**이다
> (agent-workflow.md 1절). 이 격리로 2026-09-02 스테이징 규율이 다루던 「공유 working tree 의 레인 혼입」은 이 slice 에서 구조적으로 발생하지 않는다.

```yaml
milestone: m4
slice: 4a-strategy-edit-state-machine
base_sha: 9948c6e4056bbf71fa6683aa67d30c2a49fc6eae
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — **값을 박지 않는다**(evidence 커밋 자신이 head 가 되어 즉시 낡는다, r1 B-3 와 같은 클래스).
#         마지막 **산출물** 커밋은 `d567adc`(장부층 일괄)이고 그 뒤는 이 evidence 절의 커밋뿐이다.
branch: m4/2026-09-08
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/strategy/**   # StrategyEditSession aggregate(상태 기계)·command·전이표·timeout 정책 슬롯·use case(begin/provideValue/confirm/cancel/expire)·port(StrategyRepository·EditSessionRepository·Clock·EventSink)
  - workflow/src/test/**                                       # 전이표 전수 test(허용 쌍·거부 쌍 관측)·property(중복 command 멱등·timeout)·fake port·외부 import 0 소스 스캔
  - workflow/build.gradle.kts                                  # 조건부 — `implementation(project(":strategy"))` 는 이미 있다(무변경이 기본)
  - config/quality/gate-tests.properties                       # `gate.tests.workflow` 신설
  - fixtures/manifest.yaml                                     # `strategy-edit` 축 case 신설(D-4A-1 (a))
  - fixtures/input/strategy-edit-*.json
  - fixtures/expected/strategy-edit-*.json
  - app/src/test/kotlin/bidvector/app/conformance/**            # runner dispatch — `TARGET_DOMAINS` 에 `strategy-edit` 추가 + 실행자
  - milestone-4.md
  - reports/evidence/m4/4a/**
out_of_scope:
  - strategy/**                                                # 1E 승인 산출물 — 필요한 타입 부재 시 멈추고 보고
  - Telegram/웹 어댑터(DTO·키보드·alias 41개)                   # 채널은 어댑터(후속/6A). 도메인 state 가 Telegram library 를 import 하지 않는다(4A 문면)
  - 저장 구현(3D 스키마의 세션 테이블·Flyway)                    # 4A 는 port + fake. 실제 테이블은 4C/3D 와 함께
  - 이벤트 봉투·outbox 등록                                      # 4C — 4A 는 `StrategyUpdated` payload 를 **낳는 자리**까지(port `EventSink` 에 넘김)
  - 스냅샷 재계산 디스패치(STR-07 소비자)·감시 run(STR-08/09)     # M4 4B·후속
  - STR-13~15 튜닝·온보딩·실험 갱신(`후속`)                       # actor `System` 경로의 실행
  - M2·M3 경로 · capability-map.md · data-dictionary.md 편집     # 승인 문서 편집이 필요해지면 slice 를 멈추고 계약을 갱신한다
acceptance_commands:
  - "S-0  d=$(mktemp -d) && git clone --quiet --no-hardlinks --branch m4/2026-09-08 --single-branch <repo> \"$d/repo\" && (cd \"$d/repo\" && ./gradlew --no-build-cache clean check)"
  - "S-1  ./gradlew --no-build-cache clean check"
  - "S-2  ./gradlew :workflow:test"
  - "S-3  ./gradlew :workflow:moduleDependencyGate :workflow:sizeGate :workflow:cpdCheck"
  - "S-3b ./gradlew :workflow:test --tests '*EditSessionImportBoundaryTest*'"
  - "S-4  ./gradlew :app:test --tests '*Conformance*'"
  - "S-5  ./gradlew qualityBaseline"
  - "S-6  ./gradlew :app:gateExecutionGate"
rollback: |
    **정본은 `reports/evidence/m4/4a/rollback.md`.** 경로 한정 `git restore --source=<base_sha> --staged --worktree -- <in_scope 경로 개별 인자>`,
    목록은 `git diff --name-status <base>..HEAD` 로 기계 산출, 임시 clone 에서 exit 0 · D/M 수 · diff 비어 있음 · **되돌린 트리의 compile · test** 까지 실측.
```

작성: 2026-09-07 초안 · 2026-09-08 착수 고정. 세션 모델 단독. 근거: `milestone-4.md` 4A · `capability-map.md` STR-07·STR-11(「결정과 무관하게 확정」·「채택 시 요구되는 관찰 가능 동작」)·STR-15 ·
`data-dictionary.md` §2.2(전이표 형태)·§2.2.6 · 1E `scope.md` D-4·D-10·D-17 · `ADR 0005` D-9·D-10 · `ADR 0006` D-2 · `prep/m4-prep.md` D-M4-1~4.

---

## 운영자 결정 — 2026-09-08 착수 승인

| ID | 결정 | 귀결 |
| --- | --- | --- |
| **시작 slice** | **4A** | `milestone-4.md` 순서이자 의존 0. 4B 가 4A 를 기다리므로 임계 경로 |
| **D-M4-1** | **(a) 채널 독립 use case 만, Telegram 어댑터는 `후속`** | `OPEN-STR-12` 는 활성 유지. 이 slice 는 채널 DTO·alias·키보드를 만들지 않는다 |
| **D-4A-2** | **(a) `workflow`(application) 안에 순수 Kotlin** | 도메인 게이트(`domainApiTypeGate`·`domainSourceReferenceGate`)는 `isDomain=false` 라 걸리지 않는다 → **⑦ 의 import 경계는 S-3b 소스 스캔 test 가 진다** |
| **D-4A-1** | **(a) `strategy-edit-*` case 신설**(`authored-from-approved-spec`) | `fixtures/**` + conformance runner dispatch 가 in_scope 로 확정 |

**세션 모델이 추천대로 고정한 것**(prep §3 의 나머지, 별도 물음 없이 채택 — 뒤집을 근거가 생기면 계약을 갱신한다):
`D-M4-2 (a)` command·봉투의 actor 슬롯은 **타입만**(`System` 실행 경로 없음) · `D-M4-3 (a)` 편집 세션은 영속 대상이나 **4A 는 port + fake 까지**(실 테이블은 4C/3D) · `D-M4-4` 봉투 형태는 4C 소유(4A 는 payload 까지) · 아래 D-4A-3~5.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline 9948c6e4056bbf71fa6683aa67d30c2a49fc6eae..HEAD -- CLAUDE.md .claude/`

- 착수 시점: **없음**(base == HEAD).
- 검증 완료 시점(`d567adc`): **없음** — 같은 명령을 base..HEAD 로 돌려 0건. 이 slice 는 별도 브랜치에 살고 하네스 레인(다른 세션)은 `main` 에 있어 range 에 하네스 커밋이 섞이지 않았다.
  2026-09-02 스테이징 규율이 다루던 혼입이 이번 slice 에서는 **구조적으로 발생하지 않았다**(worktree 격리의 실측된 효과).
- 이 slice 는 별도 브랜치에 살고 하네스 레인(다른 세션)은 `main` 에 있다 — range 에 하네스 커밋이 섞이려면 이 브랜치에서 `.claude/`·`CLAUDE.md` 를 편집해야 한다.
  편집이 생기면 리뷰 요청 시점에 이 절을 갱신하고 「slice 산출물이 아니며 in_scope 밖」을 명시한다. rollback 은 in_scope 경로 한정이라 하네스 경로를 되돌리지 않는다.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **상태 기계** — `EditSessionState = sealed { WaitingForValue(field), WaitingForConfirmation(draft, validation), Applied(revision), Cancelled(reason), Expired }`. 전이표(명시 state/event table): `WaitingForValue --ValueProvided--> WaitingForConfirmation`(validation 결과 동반) · `WaitingForConfirmation --Confirmed--> Applied`(**apply 시 재검증** — 스테이지 사이에 전략이 바뀌었을 수 있음) · `WaitingForConfirmation --Rejected/Edit--> WaitingForValue` · 어느 비종료 상태 `--Cancelled--> Cancelled` · `--TimedOut--> Expired`. **표에 없는 쌍은 거부이며 거부가 관측 가능**(결과 타입 `Transition.Rejected(from, command, reason)`) | 4A 「`WaitingForValue -> WaitingForConfirmation -> Applied/Cancelled/Expired`」·「허용 command 와 invalid transition」 · STR-11 「apply 시점에 검증이 재실행된다」 · §13.2 |
| ② | **검증 실패는 상태를 바꾸지 않는다** — `ValueProvided` 가 1E `validate` 에서 `Invalid` 면 세션은 `WaitingForValue(같은 field)` 유지, 저장된 `OperatorStrategy` 불변 | STR-11 「검증 실패 시 저장된 전략이 변하지 않고 같은 항목의 입력 대기 상태가 유지된다」 · STR-03 acceptance |
| ③ | **actor / operator scope** — **command 의 actor(4A 소유)**: 모든 command 가 `actor: Actor`(`Operator(id)` · `System(reason)` — D-M4-2 타입만)를 나르고, 세션은 시작 actor 와 다른 operator 의 command 를 거부. **봉투의 actor(4C 소유)**: `Applied` 가 낳는 `StrategyUpdated` 는 이 command actor 를 함께 넘긴다(「누가 바꿨나」 기록 — `OPEN-STR-04` 실물이 actor 기록 부재). 단일 회사(`OPEN-STR-07` 해소)라 operator 식별자는 세션 소유권 검사에만 | 4A 「actor/operator scope」 · 1E D-17 · §2.2.6 「actor 슬롯은 4A 가 넓힌다」 · D-M4-2 |
| ④ | **timeout 과 중복 command** — 세션 `expiresAt`(정책 데이터 슬롯, 값은 승인) 을 넘긴 command 는 `Expired` 전이 뒤 거부. 같은 `commandId` 의 재전달은 **효과 0 · 상태 불변**(설계 검토 (4) 2 — 「첫 결과와 같은 값」이 아니다: 만료 뒤 재전달은 정직하게 `SessionExpired` 를 낸다) — property test | 4A 「timeout 과 중복 command」 |
| ⑤ | **상태와 event 의 version** — 세션은 `sessionVersion` 을, 적용 결과는 `StrategyRevision`(1E)을 갖는다. `Confirmed` 는 「내가 본 revision」을 실어 **낙관적 동시성**(그 사이 다른 경로가 전략을 바꿨으면 거부 → 재검증 경로) | 4A 「상태와 event 의 version」 |
| ⑥ | **적용은 이벤트를 낳는다** — `Applied` 전이가 `StrategyEvent.StrategyUpdated(revision, policyVersion)` 를 `EventSink` port 로 넘긴다(봉투·outbox 는 4C). **모든 편집 경로가 이 use case 를 지난다** — 우회 write 경로 없음이 STR-07 의 구조적 답 | STR-07 「전략 저장이 도메인 이벤트를 낳고 파생 산출물이 구독한다」·「모든 편집 경로에서 동일」 |
| ⑦ | **채널 독립** — use case 입력은 도메인 값(field id·원시 값 문자열)이고 Telegram/웹 DTO·alias 라우팅은 어댑터. `moduleDependencyGate` 는 **외부 좌표를 domain 층에서만 검사**하므로 `workflow` 의 Telegram 무의존은 그 게이트가 재지 않는다 → **S-3b 소스 스캔 test** 가 방어 장치(전이 함수·세션 타입의 import 가 kotlin-stdlib·shared-kernel·strategy·workflow 자신 뿐임을 단언 + 양성 대조) | 4A 「Telegram DTO 는 adapter 에만 존재하고 domain state 가 Telegram library 를 import 하지 않는다」 · STR-11 「채널 독립 유스케이스 하나」 |
| ⑧ | **corpus** — D-4A-1 (a): STR-11 「채택 시 요구되는 관찰 가능 동작」 둘 + STR-03 편집 거부 둘 + 상태 구분 하나를 `strategy-edit-*` case 로 신설하고 `SharedKernelCorpusConformanceTest` 가 실행자를 갖는다 | M4 완료 조건(fixture 기반) · `data-extract.md` |

**만들지 않는 것**: 채널 어댑터 · 저장 테이블 · 봉투/outbox · 스냅샷 무효화 소비자 · 온보딩/실험 갱신 실행 · 사람이 읽는 메시지 문장 · 재시도.

---

## 계약 고정 결정 (D-4A-3~5)

| ID | 결정 |
| --- | --- |
| **D-4A-3** | timeout 값·재확인 창은 **정책 데이터 슬롯**(값은 승인 대상). legacy 값이 있으면 `legacy-behavior` 로 test 정책에만 쓴다 — 코드 상수 금지 |
| **D-4A-4** | `Expired`·`Cancelled` 는 **종단**. 재개는 새 세션(같은 draft 를 seed 로 복사는 허용, 상태 재사용 금지) |
| **D-4A-5** | `System` actor 의 `Confirmed` 는 **거부**(STR-15 `후속` — 실험 갱신도 사람이 확인). 타입은 있으나 전이표에 `System` 행이 없다 |

---

## 위협 모델 — 4A 고유 경계

**방어한다**: (a) 검증 없이 적용(② + 1E `@ConsistentCopyVisibility` + `internal constructor` 조합) (b) 전이표 밖 전이의 조용한 무시(① 거부 관측) (c) 우회 write 경로(⑥ — `StrategyRepository.save` 는 use case 만 호출) (d) 중복 command 의 이중 적용(④) (e) stale confirm(⑤ revision 대조) (f) System 의 무승인 갱신(D-4A-5) (g) 전이 함수·세션 타입의 외부 import(S-3b 소스 스캔 test — `moduleDependencyGate` 가 아니다).

**방어하지 않는다**: 채널 어댑터의 alias 매핑 옳음 · 저장 구현의 원자성(4C/3D) · 스냅샷 재계산의 실행(4B/후속) · timeout 값의 옳음 · **빌드 스크립트를 임의로 쓰는 저자**(2026-09-03 경계 — 게이트가 같은 트리의 빌드 스크립트인 한 `-x`·`enabled=false` 한 줄로 어떤 게이트도 열린다. 이 경계 밖 finding 은 수정이 아니라 경계 참조로 답한다).

**우회 후보(≥5)**: (1) `Applied` 로 직접 `copy` → 전이는 `apply(command)` 하나(`@ConsistentCopyVisibility` + `internal constructor`) (2) `Confirmed` 에 revision 없이 → 필수 필드 (3) `StrategyRepository.save` 를 어댑터가 직접 호출 → port 를 `internal` 로 닫거나 리뷰 항목(실측으로 판정) (4) timeout 을 정책에서 무한/음수 → 정책 생성 불변식 (5) 같은 `commandId` 로 다른 command → `IdempotencyConflict` 거부 (6) `System` actor 로 `Confirmed` → D-4A-5 거부 test (7) 전이 함수가 Telegram 타입을 받음 → S-3b.

---

## 조사 결과 — 이 slice 에 영향을 주는 것

`_workspace/m4-prep/01_scout_workflow.md`(legacy `ed4b06c`, 2026-09-07) · `_workspace/m1-1e/01_scout_preflight.md`:

- **4A 는 이식이 아니라 신설** — `Expired` 에 대응하는 TTL·시각 슬롯 0건, (상태, 이벤트) 전이표 0건, 가장 가까운 구조가 「목표 상태 → 효과」 룩업이라 invalid transition 개념 자체가 없다(`data-dictionary.md` §2.2.6 「legacy 에 편집 상태 기계는 없다」와 일치).
- **`OPEN-STR-04` 의 실물이 문서 서술과 다르다** — 실험의 전략 갱신은 자동 트리거가 아니라 운영자 HTTP POST 둘 + 가드 셋이고, 없는 것은 승인 게이트가 아니라 **actor 기록**(전략 테이블에 `updated_by`·`revision` 없음) → ③ 의 actor 는 기록으로 필수.
- **pending 상태가 analytics 이벤트 로그 행에 영속되고 최근 100행을 훑는다** → `폐기`, D-M4-3 (a).
- **fixture 0건** — 편집 전이 축 case 없음. `strategy-validation`·`strategy-watch` 11건은 전이를 재지 않는다 → D-4A-1 (a).
- **배치 제약** — `db-scheduler`·`resilience4j` 가 `group.forbidden` 이라 domain 층 금지. 순수 전이 함수는 무의존이면 어느 층이든 가능하고, 만료 트리거·저장은 `workflow`/`adapters` → D-4A-2 (a).

---

## OPEN — 수령·신설

| OPEN | 4A 처리 |
| --- | --- |
| `OPEN-STR-12` | D-M4-1 (a) — 상태 기계는 채널 독립. 채택은 관측 뒤(활성 유지) |
| `OPEN-STR-04` | D-M4-2·D-4A-5 — actor 타입만, System 확인 경로 거부 |
| `OPEN-STR-07`(해소) | 세션 소유권 검사에만 operator 식별자 |
| 신설 후보 `OPEN-4A-WRITE-PATH-GATE` | 「모든 편집 경로가 use case 를 지난다」를 ArchUnit/의존 게이트로 표현할 수 있는지 — ADR 0005 §6 이 미확인으로 둔 것과 같은 축. 구현 레인이 실측으로 판정하고 불가하면 이 OPEN 을 등재한다 |
