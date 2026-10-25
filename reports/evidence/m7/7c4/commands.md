# M7/7C④ — 명령과 결과 (Phase 2a)

전부 `flock /home/deploy/.local/bid-vector-build.lock ./gradlew --no-daemon …` 로 돌렸다(호스트 전체에서 무거운 빌드 하나).
무거운 명령 앞마다 확인 셋(pgrep · `free -m` · RSS 상위)을 **별도 호출로 먼저** 돌렸고, 다른 레인(vr5 · 7C② · 7B)의 빌드가
도는 동안에는 락에서 기다렸다(7B 의 full check 뒤에서 40분 넘게 선 적도 있다 — 끼어들지 않았다). 날짜는 전부 2026-10-24 KST.

**판정 SHA**(팀장이 동결하고 검토가 판정한 SHA): r1 `8afb94d0` · r2 `efc856e5`. **r3 의 판정 SHA 는 이 파일에 적지 않는다** — evidence 에 적으려 하면 그 커밋이 다시 SHA 를 움직인다(하네스 규율: 마지막 HEAD 의 정본은 verifier 와 PR 조치 코멘트다). base main `fc15975c`. r3 의 **실측 HEAD** 는 `49e8c4cf`(마지막 산출물 커밋)이고, 그 뒤 커밋은 이 evidence 뿐이어서 되돌림 목록 27 경로가 움직이지 않는다(유효성 술어 빈 출력).
그 밖의 SHA 는 **실측 HEAD** 라고 적는다(r2 F8 — 둘을 같은 낱말로 쓰면 어느 것이 판정 대상인지 흐려진다):
r2 의 찬 `check` 는 `2258342e`, 팀장 수용 뒤 더한 어휘 전수 test 를 포함한 재측정과 rollback 실측은 `4bd7cd59`. 아래 §r1 의 rollback 수는
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
- **task 수 365 → 356 의 분해**(r2 CR R2-L5 의 산수 지적 — 「365 − 12 = 353 ≠ 356」은 **이름 수와 actionable 수를
  섞은 것**이다). 네 로그를 같은 방식으로 세면: 이름(`> Task :…` 고유) **443 · 431 · 431 · 443**, actionable
  **365 · 356 · 356 · 365**(r1 찬 check · r2 찬 check · r2 종결 재측정 · 되돌린 트리). 그래서
  **이름 차 12 = actionable 차 9 + action 없는 task 3** 이고 산수가 닫힌다(443−365 = 78 과 431−356 = 75 의 차가 그 셋이다).
  차이는 전부 `:build-logic:` 이고 **r2 에만 있는 task 는 0** 이다 — 게이트 · test 는 한 건도 빠지지 않았다.
  셋을 개별로 지목하지는 못한다(Gradle 이 action 유무를 로그에 적지 않는다). `:build-logic:classes` 가 그런 lifecycle
  task 로 알려진 쪽이고, **중요한 주장은 이름 집합 대조**(r2-only 0)가 진다 — actionable 수는 그 보조다.
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
## r3 (D-7C4-4 수정 라운드) — 명령과 결과

처분 표는 `scope.md` 의 D-7C4-4 절이다.

### ① 격리 규칙의 축 전환 — 전·후 표 (우회 아홉)

verifier r2 가 만든 우회를 그 레인 clone 에서 **읽기만** 해서 버릴 clone 에 심고, 게이트 **구판
`d55624ef` 와 새 판 `49e8c4cf`** 에서 같은 명령을 돌렸다(`:workflow:test --tests
'bidvector.workflow.profile.*' :adapters:test --tests '…IsolationGateTest' --tests
'…ProfileAdapterDependencyTest' --rerun --continue`). 두 판 모두 test task 가 **실행**됐다(FROM-CACHE
아님 — 로그로 확인). `indy` 둘은 이 레인이 만든 변종이다.

| # | 우회 | 전 `d55624ef` | 후 `49e8c4cf` |
|---|---|---|---|
| N1 | 허용 파일의 `String` 반환 helper 를 거친 **이행** 값 흐름 | 초록 | **초록 — 닫지 않는다**(제한 5) |
| N2 | 판정 입력 port 구현이 **SQL 문자열**로 V22 표를 직접 읽는다 | 초록 | **붉다** — 규칙 1(표 이름) · 규칙 3(port) |
| N3 | X1 을 **새 어댑터 패키지**로 옮긴다 | 초록 | **붉다** — 규칙 2(값 타입) · 규칙 3 |
| N4 | 인터페이스 선언을 **제3 패키지**로 옮긴다 | 초록 | **초록 — 닫지 않는다**(제한 5) |
| N5 | 실행 시 조립한 이름(`Class.forName(joinToString)`) | 초록 | **초록 — 닫지 않는다**(제한 5) |
| N6 | evaluation 의 **typealias** 로 profile 타입을 감춘다 | **붉다** | **붉다** — 이름 참조(전개된 이름이 바이트코드에 남는다) |
| N7 | **허용 파일 안 중첩 class** 가 판정 입력 port 를 구현 | **붉다** | **붉다** — 구판은 파일 이름 허용으로, 새 판은 규칙 3 으로 |
| X1-indy | 허용 파일 안에서 `OperatorProfilePort { … }` **SAM 람다**로 V22 를 접는다 | 초록 | **붉다** — 규칙 3(적발 대상 `JdbcBusinessProfileStoreKt`) |
| X2-indy | `workflow.profile` 안 람다가 evaluation 의 `fun interface` 를 SAM 으로 만든다 | 초록 | **붉다** — 람다 축(적발 대상 `IndyProfilePortKt`) |

- **새로 닫힌 것은 넷**(N2 · N3 · X1-indy · X2-indy), **이미 닫혀 있던 것은 둘**(N6 · N7), **닫지 않는
  것은 셋**(N1 · N4 · N5 — 제한 5 와 2b 표적 4).
- 통제군(우회 없는 새 판): 다섯 class 전부 실패 0, 두 test task 실행.
- 적발 대상이 class 이름까지 나온다 — 규칙 3 의 목록은 `NewPackageRegionFold` · `NestedFoldHost$Fold` ·
  `JdbcBusinessProfileStoreKt` · `SqlRegionFold` 넷이었다.

### ① 의 오탐 하나 — 깨끗한 트리에서 붉었다

람다 축의 첫 판은 `BootstrapMethods` 절과 indy 자리의 **모든** `bidvector` 좌표를 셌다. 그래서 **우회 없는
트리에서 `OnboardingSnapshot.class` 가 붉었다** — `data class` 의 `toString()` 이 문자열 접합 indy 를 쓰고
그 자리의 **인자**에 판정 패키지 타입이 실린다(`makeConcatWithConstants:(…ProfileFacts…)Ljava/lang/String;`).
그 패키지를 **쓰는** 것은 허용 방향이라 위반이 아니다. 술어를 **indy 반환 타입**으로 좁혀 닫았다(별 커밋).

**앞 보고의 정정**: 「이 저장소 컴파일 산출물에 `LambdaMetafactory` 상수 0 건」은 **틀렸다**. `grep` 이
NUL 을 담은 바이너리 줄을 건너뛰어서 생긴 수였고(`-a` 없이 셌다), 다시 세면 **275 개 class** 가 그 상수를
가진다. 그래서 람다 축의 양성 대조를 합성 표본이 아니라 **실제 산출물**로 바꿨다 — 이 패키지 열거의
Kotlin 람다가 indy 라 `kotlin/jvm/functions/Function1` 이 나온다. CR 의 OQ-R2-1(indy 가 class 파일을 내지
않는가)은 X1-indy · X2-indy 가 **class 파일 없이도 적발됐다**는 사실로 닫힌다.

### ② 면허명 CHECK — RED 먼저

- RED: 경계 test 만 커밋한 뒤 `:adapters:test --tests 'bidvector.adapters.profile.*'` → `SchemaGuardTest`
  **8 중 1 실패**(`' '` · `'  '` · `'\t\n'` 에서 「예외가 안 났다」). 같은 run 에서 신설 격리 게이트는 5/0.
- GREEN: `license_name ~ '[^[:space:]]'` 로 좁힌 뒤 같은 명령 + `CleanMigration*` → **exit 0**,
  `SchemaGuardTest` 8/0, `CleanMigration*` 여섯 전부 초록(CHECK 제약 수 5 · 3 그대로).

### ③ N8 과 DB 변두리 탐침 (실 DB, 버릴 clone)

- **N8**(`array_positions` 의 `'CONSTRUCTION'` → `'CONSTRUCTIO'` 오타): **붉다** — 「등록분야 배열은 어휘
  전수에서 중복 원소를 거부한다」 하나가 잡는다. r2 F7 이 「`efc856e5` 에서는 잡히지 않는다」고 적은 자리를
  `4bd7cd59` 의 전수 test 가 닫은 것이 **실측으로 확인**됐다.
- 탐침(단언 없이 수락/거부만 출력, N8 변이를 그대로 둔 채). DB locale 은 **`en_US.utf8` / `en_US.utf8`**.

| 입력 | 결과 | 앱(`isNotBlank` · 어휘)과의 관계 |
|---|---|---|
| 상호 U+3000 단독(전각 공백) | 거부 | 앱도 거부 — **같다** |
| 상호 U+2003 단독(em space) | 거부 | 앱도 거부 — **같다** |
| 상호 U+00A0 단독(NBSP) | 수락 | **앱은 거부** — Kotlin 의 `isWhitespace` 는 `isSpaceChar` 도 보므로 **DB 가 넓다**(r3 F10 정정) |
| 상호 U+200B 단독(zero width) | 수락 | 앱도 수락 — **같다** |
| 분야 `{CONSTRUCTION,CONSTRUCTION}`(N8 변이 적용) | 수락 | 변이가 그 값의 중복을 열었다는 직접 증거 |
| 분야 `{GOODS,GOODS}` | 거부 | 값별 절이 막는다 |
| 분야 `{NULL}` | 거부 | `<@` 가 NULL 원소에서 참이 아니다 |
| 분야 `{{GOODS}}`(다차원) | 거부 | **사유가 CHECK 위반이 아니다** — `array_positions` 가 1차원만 받는다(CR R2-L4) |
| 면허명 공백 둘 | 거부 | 새 술어가 막는다(②) |
| 면허명 U+3000 | 거부 | 앱도 거부 — 같다 |

**정정(r3 F10 · F12 처분)**: 이 표의 NBSP 행이 틀렸고, 뒤이어 적었던 「이 locale 에서 차이 0」도 틀렸다 —
NBSP 단독은 **DB 수락 · 앱 거부**로 **DB 가 넓다**. 나머지 넷(U+3000 · U+2003 · 공백 둘 · U+200B)은 양쪽이
같다. locale 의존(제한 6)과 이 NBSP 차이(제한 4)를 scope 에 실측대로 적었다. DB CHECK 는 바꾸지 않는다 —
쓰기는 앱 경로뿐이고 앱이 더 엄격하다.

### r3 종결 acceptance (`49e8c4cf`)

- 찬 `check --continue --rerun-tasks --no-build-cache` → **exit 0**, `BUILD SUCCESSFUL in 11m 11s`,
  **356 task 전부 실행**, 이름 수 431(위 분해와 같다), 루트 게이트 둘 실행, `FAILED` 0.
  이 slice test 전부 초록 — workflow 7 · 9 · **7**(람다 축 둘이 늘었다), adapters **5**(신설 격리 게이트) ·
  8 · 10 · 6 · **3**(형제 test 는 V22 관심사를 넘겨 셋으로 줄었다).
- `web` job 은 r3 에서도 해당 없다(변경 경로에 `web/` · `openapi/` · 생성 타입이 없다).

## r4 (D-7C4-5 수정 라운드, PR 전 마지막) — 명령과 결과

처분 표는 `scope.md` 의 D-7C4-5 절이다. 보강은 셋뿐이고 새 기구는 들이지 않았다.

### 전·후 표 — B1 · B3 · B5 (버릴 clone, 구판 `3e51c600` ↔ 새 판 `59e5c240`)

| # | 우회 | 전 | 후 |
|---|---|---|---|
| B1 | 판정 입력 port 구현이 **대문자 SQL**(`… FROM ACCOUNT_BUSINESS_PROFILE …`)로 V22 를 읽는다 | 초록 | **붉다** — 규칙 1(대소문자 무시) · 규칙 3. 적발 `UpperSqlRegionFold` · `UpperSqlRegionFoldKt` |
| B3 | 허용 파일과 **같은 이름**(`JdbcBusinessProfileStore.kt`)을 다른 패키지에 두고 V22 값 타입 · 표 이름을 쓴다 | 초록 | **붉다** — 규칙 1 · 규칙 2. 적발 `SpoofedRegionReader` |
| B5 | 그 위조 파일 안에서 **다른** 판정 입력 port(`CapacityPort`)를 구현하며 V22 를 읽는다 | 초록 | **붉다** — 규칙 1 · **규칙 3**(port 전수). 적발 `SpoofedCapacityFold` |

- 통제군(우회 제거): 다섯 class 전부 실패 0.
- 「전」 run 은 `:adapters:test` 가 **실행**됐고(`FROM-CACHE` 아님) exit 0 이다. 두 run 모두 `:workflow:test` 는
  **FROM-CACHE** 였다 — 이 라운드의 우회가 전부 adapters 쪽이어서 workflow 게이트는 이 표의 근거가 아니다. 그쪽 실행은
  같은 HEAD 의 표적 run(11:16, 7/0)과 아래 찬 check 가 진다.
- B2(자원 파일 SQL) · B4(인자 람다)는 **닫지 않는다** — scope 제한 5 의 넷째 · 다섯째다.

### ③ port 집합을 구조로 뽑는다

규칙 3 의 좌표는 이름 목록이 아니라 **`workflow.evaluation` 의 SAM 인터페이스 전수**다 — 닻 class 의 code source(jar 또는
classes 디렉터리)에서 그 패키지의 class 를 열거하고 「인터페이스 + 추상 메서드 하나」만 고른다. 양성 대조가 그 집합이
**둘 이상**이고 `OperatorProfilePort` 를 담으며 `ProfileFacts`(data class)는 담지 않는 것을 잰다.

### ⑤ NBSP — evidence 정정

r3 evidence 의 탐침 표에서 「상호 U+00A0 → 앱도 수락 — 같다」와 그 아래 「이 locale 에서는 probed 범위의 앱 ↔ DB 차이가
0」은 **틀렸다**. Kotlin JVM 의 `Char.isWhitespace()` 는 `Character.isWhitespace || Character.isSpaceChar` 이고 NBSP 는
뒤쪽이 참이므로 **앱은 NBSP 단독을 거부**한다(DB 는 수락). 그래서 그 자리는 **DB 가 앱보다 넓다** — 정정한 문면과 처분은
scope 알려진 제한 4 · 6 에 있다(DB CHECK 는 바꾸지 않는다: 쓰기가 앱 경로뿐이다).

### ⑥ 「LambdaMetafactory 275」 정정

`command grep -rlaF --include='*.class' 'java/lang/invoke/LambdaMetafactory' .` 로 다시 세면 **전체 348 · `build-logic`
제외 286 · 그중 main 만 92** 다. 어느 모집단도 275 가 아니다 — 그 수를 source KDoc 에서 지웠다.
**원인**: 이 셸의 `grep` 은 **함수**(ugrep 래퍼)이고 `-I`(바이너리 건너뜀)와 `--ignore-files`(gitignore 존중 → `build/`
제외)를 붙인다. 그래서 앞 라운드의 「0 건」과 「275」가 나왔다. 「`-a` 누락」이라는 앞 설명도 **틀렸다**(GNU `grep -l` 은
`-a` 없이도 바이너리 일치를 낸다). 바이너리 · gitignore 대상을 셀 때는 `command grep` 을 쓴다.
결론은 그대로다 — indy 람다는 실재하고, 람다 축의 양성 대조는 **실제 산출물**(`profile/OnboardingItem.class`)을 쓴다.

### r4 종결 acceptance · rollback (`59e5c240`)

- 찬 `check --continue --rerun-tasks --no-build-cache` → **exit 0**, `BUILD SUCCESSFUL in 11m 15s`, **356 task 전부 실행**,
  이름 431, 루트 게이트 둘, `FAILED` 0. 이 slice test 전부 초록 — 신설 격리 게이트 **6**(양성 대조 하나가 늘었다) ·
  나머지 그대로.
- rollback: 목록 **27 경로(A 16 · M 11)** — r3 와 같다(이 라운드가 새 파일을 만들지 않았다). ①~⑦ 과 ③′(되돌린 트리가 앞
  실측 트리와 소스까지 동일) 전부 통과, ④⑤⑥ exit 0(`BUILD SUCCESSFUL in 12m 4s`, 365 task 전부 실행).

## r5 (D-7C4-6 — PR 대기 중 low 정리) — 명령과 결과

처분 표는 `scope.md` 의 D-7C4-6 절이다. **측정은 바뀐 게이트만 표적**으로 했다(팀장 지시) — 찬 `check` · rollback 재실측 ·
evidence 종결은 **7B 머지 뒤 병합 라운드**에서 새 base 로 한 번에 한다.

### 바뀐 게이트 표적 (worktree, `c8d22ed2`)

`:adapters:test --tests '…BusinessProfileIsolationGateTest' --rerun` → **exit 0**, **6 test · 실패 0**(결과 XML 21:23:01,
새 실행). 늘어난 대조가 이 수를 그대로 둔 채 안을 바꿨다 — 「`*Port` 전수 포함」 · 「멤버 둘 표본도 port」 · 「봉인 결과
타입은 제외」 · 「점 표기 좌표도 담는다」.

### 전·후 표 — C7 (멤버 둘짜리 판정 입력 port)

verifier r4 가 만든 우회를 그 레인 clone 에서 **읽기만** 해서 버릴 clone 에 심었다. C7 은 **허용 파일 안**의 다른 top-level
class 가 **추상 메서드 둘**인 evaluation port(`RegionFactsSource`)를 구현하며 V22 를 읽는다 — 허용 키(규칙 1 · 2)는 그 파일을
허락하므로 **규칙 3 만이 잡을 수 있는 자리**다.

| # | 우회 | 전 `79d94976` | 후 `c8d22ed2` |
|---|---|---|---|
| C7 | 허용 파일 안에서 **멤버 둘짜리** 판정 입력 port 를 구현하며 V22 를 읽는다 | **초록**(6/0, 21:34:57 — 실행) | **붉다** — 규칙 3, 적발 `RegionFactsAdapter.class` (6 중 1, 21:50:35 — 실행) |

- 통제군(우회 제거): 6/0, exit 0.
- **첫 「후」 시도는 돌지 않았다**: `git checkout <SHA>` 가 **추적 파일의 내 덧붙임을 그대로 남기는** 바람에 C7 을 두 번
  심었고(같은 class 선언 둘) 컴파일이 깨졌다. 종료 코드는 1 로 같았고 **결과 XML 시각이 「전」 run 과 같아서** 미실행이
  드러났다 — 덧붙임을 먼저 되돌리고 다시 쟀다(`RegionFactsAdapter` 출현 1 회를 확인한 뒤 실행). 같은 실패 모드를 이 slice
  에서 세 번째로 같은 방법(시각 대조)으로 잡았다.
