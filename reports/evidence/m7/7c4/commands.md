# M7/7C④ — 명령과 결과 (Phase 2a)

전부 `flock /home/deploy/.local/bid-vector-build.lock ./gradlew --no-daemon …` 로 돌렸다(호스트 전체에서 무거운 빌드 하나).
무거운 명령 앞마다 확인 셋(pgrep · `free -m` · RSS 상위)을 **별도 호출로 먼저** 돌렸고, 다른 레인(vr5 · 7C② · 7B)의 빌드가
도는 동안에는 락에서 기다렸다(7B 의 full check 뒤에서 40분 넘게 선 적도 있다 — 끼어들지 않았다). 날짜는 전부 2026-10-24 KST.

**판정 SHA**: r1 은 `8afb94d0`, r2(D-7C4-3 수정 라운드)는 `2258342e`, **r2 종결은 `4bd7cd59`**(팀장 수용 뒤 더한 어휘 전수 test 와 알려진 제한 한 줄). base main `fc15975c`. 아래 §r1 의 rollback 수는
실측 HEAD `1b3ade0b` 의 것이었고 **r2 에서 전부 다시 쟀다**(`rollback.md` 가 r2 판이다).

## r1 — acceptance: `check` job (`.github/workflows/ci.yml` 의 명령 그대로)

- **찬 `check`**: `check --continue --rerun-tasks --no-build-cache` (HEAD `1b3ade0b`, 18:21~18:34) → **exit 0**,
  `BUILD SUCCESSFUL in 12m 5s`, **365 task 전부 실행**(up-to-date 0 — 캐시 적중으로 조용히 넘어간 게이트가 없다).
  루트 게이트 셋(`leakPatternGate` · `contractGate` · `qualityBaseline`)이 실행으로 남았고, 모듈마다 `sizeGate` ·
  `moduleDependencyGate` · `packageOwnershipGate` · `gateExecutionGate` · `gateRegistrationGate` · `typeShapeGate` ·
  `testShapeGate` · `domainApiTypeGate` · ktlint 셋 · detekt 가 같은 로그에 있다. 비밀값 스캔 어휘는 그 게이트의 정책
  파일이 들고, 여기에 축어로 옮기지 않는다.
- **`web` job 은 돌리지 않았다** — 2a 는 HTTP 표면이 없어 `web/` · OpenAPI · 생성 타입에 닿지 않는다(변경 경로에 그 셋이
  없다). 2b 에서 돈다.
- 이 slice 의 test 는 그 run 에서 전부 돌았다 — `workflow` 의 `profile` 셋(7 · 9 · 3) · `adapters` 의 `profile` 둘(실 DB:
  왕복·소유 경계·절 독립 10, 스키마·권한 7). 실패 0.

## 표적 — RED 먼저

- **RED (17:40~17:55)** `:workflow:test --tests 'bidvector.workflow.profile.*'` → exit 1. 둘이 함께 붉었다.
  - `OnboardingChecklistTest` **9 중 7** 이 `IllegalStateException`(「선행 항목이 선언되지 않은 「아직 모름」 항목」) —
    앞 세션이 남긴 미커밋 D-7C4-2 구현의 **초기화 순환**이다(checklist §3.1). 이어받기 전에 돌려서 드러났다.
  - `ProfileJudgementIsolationTest` 1 — 축 어휘를 셋으로 먼저 고정한 RED(`QUAL_10_PERFORMANCE` 가 아직 없다).
- **GREEN (18:18)** 같은 명령 → exit 0, 세 class 19 test 실패 0(결과 XML 시각 18:18:11 — 캐시가 아니라 실행).

## 변이 — 바꿔치우기, 통제군은 `--rerun`

버릴 clone(`scratchpad/7c4/mut`, HEAD `1b3ade0b`)에서 하나씩 적용했다. 적용 여부는 **원문 부재**로 확인했고(출현 1회가
아니면 중단), 라운드마다 결과 XML 시각을 함께 적었다 — 앞 라운드와 같은 시각이면 그 라운드는 돌지 않은 것이다.

| # | 바꿔치운 것 | 결과 |
|---|---|---|
| 통제군 0 | 없음 | exit 0 · 실패 0 (18:36:08.890) |
| M1 | 분야 미답의 적용 상태를 「선행 대기」 → 「해당 없음」(D-7C4-2 이전 납작화) | `OnboardingChecklistTest` **3 실패** (18:36:25.774) |
| M2 | 완료·미완 분류 술어를 「걸리는 것」 → 「해당 없음이 아닌 것」(대기를 분모에 넣는다) | 같은 class **3 실패**(전 항목 수 8↔9 · 서로소) (18:36:43.194) |
| M3 | 대기의 선행을 `NARA_READINESS` → `TRADE_NAME` | 같은 class **2 실패** (18:37:00.974) |
| M4 | 판정 대기 축 표에서 `QUAL_10_PERFORMANCE` 행 제거 | `ProfileJudgementIsolationTest` **1 실패** (18:37:19.398) |
| M5 | 그 축의 ID 를 `QUAL_10_TRACK_RECORD` 로(= wire 값 변경) | 같은 class **1 실패** (18:37:36.806) |
| M6 | **음성 대조**(더하기): `workflow.evaluation` 의 한 파일이 `profile` 열거를 참조 | 같은 class **1 실패** — 적발 대상이 `evaluation/ProfilePortsKt.class` 로 이름까지 나왔다 (18:38:46.802) |
| 통제군 1 | 복원 뒤 | exit 0 · 실패 0 (18:37:57.914) |

- M3 은 「대기의 선행은 그 스냅샷에서 걸리는 항목이다」 test 를 **통과한다**(`TRADE_NAME` 도 걸리는 항목이다) — 그 test 가
  재는 것은 죽은 선행·순환뿐이고, 어느 항목이 선행인지는 빈 계정 test 가 잡는다. 둘이 함께 있어야 M3 이 붉다.
- **M6 은 첫 시도가 돌지 않았다**: 삽입을 import 앞에 넣어 `compileKotlin` 이 깨졌고(변이 자체의 결함), 결과 XML 시각이
  앞 라운드와 **같아서** 그 미실행이 드러났다. 파일 끝에 넣어 다시 돌린 것이 위 행이다. 종료 코드만 보면 둘 다 1 이라
  구별되지 않는다 — 시각을 함께 적는 이유다.
- 격리 술어는 텍스트가 아니라 **상수 풀**을 본다 — 전체 한정 이름으로 쓴 M6 의 참조를 텍스트 정규식은 못 봤을 것이다.

## rollback

`rollback.md` 참조(되돌림 목록은 명령으로 도출하고, ①~⑦ 을 임시 clone 에서 실측했다).

## r2 (D-7C4-3 수정 라운드) — 명령과 결과

처분 표는 `scope.md` 의 D-7C4-3 절이다. 아래는 그 처분의 실측이다.

### acceptance — 같은 `check` job 명령

- **찬 `check`**: `check --continue --rerun-tasks --no-build-cache` (HEAD `2258342e`, 22:20~22:32) → **exit 0**,
  `BUILD SUCCESSFUL in 11m 13s`, **356 task 전부 실행**. 루트 게이트 둘(`leakPatternGate` · `contractGate`)이 로그에 실행으로
  남았고 `FAILED` 는 0 이다. 이 slice 의 test 전부 초록 — workflow `profile` 7 · 9 · 5, adapters `profile` 8 · 10 · 6 · 5.
- **task 수가 r1 의 365 에서 356 으로 줄었다** — 두 로그의 task 집합을 `comm` 으로 대조했다. 차이는 `:build-logic:` 의 빌드
  인프라 12개(컴파일 · jar · 플러그인 생성)뿐이고 **게이트 · test 는 한 건도 빠지지 않았다**(r2 에만 있는 task 0).
  `:build-logic:test` 는 두 판 모두에 있다.
- **그 앞 판은 붉었다**: 같은 명령이 `:adapters:ktlintTestSourceSetCheck FAILED`(체인 호출 줄바꿈 둘)로 떨어졌고, 그 커밋
  뒤 위 초록을 다시 쟀다. 종료 코드로 판정했다 — 로그를 눈으로 훑고 넘기지 않았다.
- `web` job 은 r2 에서도 해당 없다(변경 경로에 `web/` · `openapi/` · 생성 타입이 없다).

### ④ — RED 먼저 (실 DB)

- `:adapters:test --tests 'bidvector.adapters.profile.*'` (V22 무변경 + 새 경계 test) → exit 1,
  `BusinessProfileSchemaGuardTest` **8 중 2 실패**, 둘 다 「예외가 날 줄 알았는데 안 났다」 — 공백뿐인 상호와 중복 등록분야다.
- V22 CHECK 를 좁힌 뒤 같은 명령 + `CleanMigration*` + 보존 test → exit 0, 그 class 8 초록, `CleanMigration*` 다섯과 보존
  test 전부 초록(CHECK 제약 수는 5 · 3 그대로 — 한 열의 CHECK 가 조건 여럿을 AND 로 들어도 제약은 하나다).

### ① — 게이트 전·후 표 (우회 X1 · X2 · X3)

verifier r1 이 실측한 우회 셋을 **그 레인의 clone 에서 읽어 와**(그 clone 에는 쓰지 않았다) 버릴 clone 에 심고, 게이트
**구판과 새 판에서 같은 명령**을 돌렸다. 명령은 `:workflow:test --tests 'bidvector.workflow.profile.*'
:adapters:test --tests '…ProfileAdapterDependencyTest' --rerun --continue`.

| 우회 | 전 — 게이트 구판 `8afb94d0` | 후 — 새 판 `2258342e` |
|---|---|---|
| X1 `adapters.profile` 의 V12 판정 입력 port 구현이 V22 본사 소재지를 `ProfileFacts.regionTerms` 로 접는다 | **초록** | **붉다** — 어댑터 의존 test 5 중 2(허용 루트 밖 좌표 · 판정 입력 port 구현의 값 타입 참조) |
| X2 의존 역전: `workflow.evaluation` 이 인터페이스를 선언하고 `workflow.profile` 이 구현한다 | **초록** | **붉다** — 격리 test 의 「구현 · 상속하지 않는다」 |
| X3 `Class.forName("bidvector.workflow.profile.…")` 점 표기 | **초록** | **붉다** — 격리 test 의 이름 참조(점 표기 포함) |

- 전: `BUILD SUCCESSFUL`, 두 test task 가 **실행**됐다(`FROM-CACHE` · `UP-TO-DATE` 아님 — 로그로 확인).
- 후: 격리 test 5 중 2 · 어댑터 의존 test 5 중 2, 두 task 모두 실행(결과 XML 시각 22:32:18 · 22:32:29).
- **첫 시도는 X1 을 재지 못했다**: `--continue` 없이 돌려 `:workflow:test` 가 먼저 실패하자 `:adapters:test` 가 돌지 않았고,
  그 XML 시각이 「전」 run 과 **같아서** 미실행이 드러났다. `--continue` 를 붙여 다시 쟀다 — 종료 코드만 보면 둘 다 1 이라
  구별되지 않는다.
- 이 표가 ① 의 변이 증거다(게이트 술어를 되돌리는 변이 = 「전」 열).

### 추가 변이와 통제군

| # | 바꿔치운 것 | 결과 |
|---|---|---|
| N4 | `bindDetails` 가 낸 다음 자리를 하나 밀어 쓴다(`updatedAtIndex - 1`) | `JdbcBusinessProfileStoreTest` **붉다**(exit 1, 22:05:29) — 파생한 자리가 실제로 잠겨 있다 |
| 통제군 | 복원 뒤 같은 명령 `--rerun` | **초록**(exit 0, 22:05:48 — 앞 라운드와 다른 시각) |

### 어휘를 한쪽에서만 지우는 변이 (팀장 요청, 2026-10-24)

V22 의 등록분야 CHECK 는 같은 닫힌 어휘를 **두 자리**에 든다 — 「어휘 안인가」(부분집합)와 「값마다 한 번뿐인가」(값별 절).
부질의가 CHECK 에 들어갈 수 없어 생긴 모양이므로, **한 값을 한쪽에서만 지우는 변이**가 잡히는지 쟀다. 그 전에 중복 거부
test 를 `NaraRegistrationField.entries` **전수**로 넓혔다(값별 절이 하나 빠지면 그 값의 중복만 통과하므로, 값 하나만 재는
test 로는 못 잡는다).

| # | 바꿔치운 것 | 결과 |
|---|---|---|
| V1 | 부분집합 어휘에서 `CONSTRUCTION` 만 지운다(값별 절은 그대로) | **붉다** — 「소재지 17 · 등록분야 셋 전부를 표가 받는다」 하나 (23:24:40) |
| V2 | 값별 절에서 `CONSTRUCTION` 만 지운다(부분집합은 그대로) | **붉다** — 「등록분야 배열은 어휘 전수에서 중복 원소를 거부한다」 하나 (23:24:57) |
| 통제군 | 복원 뒤 같은 명령 `--rerun` | **초록**(23:25:15 — 앞 라운드와 다른 시각) |

두 변이가 **서로 다른 test 하나씩**에 걸린다 — 두 자리가 각각 자기 test 를 가진다는 뜻이다. 어휘가 늘면 전수 test 가 함께
늘어난다(열거를 돈다).

### r2 종결 acceptance (`4bd7cd59`)

- 찬 `check` → **exit 0**, `BUILD SUCCESSFUL in 11m 9s`, **356 task 전부 실행**, 루트 게이트 둘 실행, `FAILED` 0.
  이 slice test 전부 초록 — workflow 7 · 9 · 5, adapters 8 · 10 · 6 · 5(중복 거부가 전수로 늘었어도 class 당 수는 그대로다).
