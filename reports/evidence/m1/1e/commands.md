# M1/1E — 실행 명령과 종료 코드

base_sha: `2af32f6`. **정정(verifier r1 L-3)**: head_sha가 이 문서 세 곳에서 서로 달라
실제 리뷰 head 가 어디에도 없었다 — 진행 순서대로 `456b982`(구현 완료, 아래 초기
절) → `fa1db98`(runner dispatch) → `36c7f0c`(evidence, **verifier r1이 이 head 에서
ready-for-review 판정**) → 이번 라운드(F-1·F-2·F-3 수정 + 장부 정정, 코드+evidence
일괄 1커밋)로 이어진다. 최종 head는 이 파일 하단 「verifier r1 finding 반영」 절의
커밋 SHA다. 하네스 레인 변경은 전 구간 없음(`git log --oneline 2af32f6..HEAD --
CLAUDE.md .claude/` 결과 없음, r1 재확인 포함).

## 2026-09-06T13:11:15Z
- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` (S-0)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 35s, 318 actionable tasks: 318 executed (전건 재실행, 캐시 없음)

## 2026-09-06T13:09:44Z
- cmd: `./gradlew --no-build-cache clean check` (S-1, 저장소 루트)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 16s, 318 actionable tasks: 293 executed, 25 up-to-date

## 2026-09-06T13:10:07Z
- cmd: `./gradlew :strategy:test` (S-2, 이 시점 head `41ed454`)
- exit: 0
- 핵심 결과: strategy 도메인 test 43건(WatchRulesTest 25·StrategyValidationTest 14·
  CompileFailureHarnessTest 4) 전부 통과, 실패·건너뜀 0. **정정(verifier r1 L-4)**:
  이 문서 이전 판이 "42건(WatchRulesTest 24)"라고 적었으나, 같은 head에서 `456b982`가
  이미 WatchRulesTest에 test 하나(D-9 동시 실패 우선순위)를 더해 head 시점 실제 합은
  43(25+14+4)이다 — 명령이 내는 셈을 산문에 옮겨 적어 낡은 사례(evidence-pack 금지
  항목). 이후 F-1·F-2·F-3 라운드가 test를 더 추가했으므로 최종 건수는 하단
  「verifier r1 finding 반영」 절의 재실행 결과를 정본으로 한다.

## 2026-09-06T13:10:08Z
- cmd: `./gradlew :strategy:domainApiTypeGate :strategy:domainSourceReferenceGate :strategy:typeShapeGate :strategy:sizeGate :strategy:cpdCheck` (S-3)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 게이트 다섯 전부 통과(CPD main 중복 0건 실측 — `OPEN-1E-TEXTFOLD`
  텍스트 접기 helper 는 shared-kernel 승격 불필요로 판정)

## 2026-09-06T13:10:09Z
- cmd: `./gradlew qualityBaseline` (S-5)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, `build/reports/quality-baseline/quality-baseline.md` 갱신

## 2026-09-06T13:10:10Z
- cmd: `./gradlew :build-logic:test` (S-7, 1A 승계)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, build-logic test 전건 UP-TO-DATE(회귀 없음)

## S-4·S-6 — 조건부, 이번 라운드 대상 아님
S-4(`:app:test --tests '*Conformance*'`)·S-6(mutation_sweep_adversarial.py)는 D-2·D-3 로
1E 축 authoritative case 가 생길 때만 실행한다(scope.md ⑪). 이번 라운드는 커널·validation
구현까지이고 case 신설은 fixture-curator 후속 지시 대상이라 두 명령을 실행하지 않았다 —
`fixtures/manifest.yaml`·`app/src/test/kotlin/bidvector/app/conformance/**`를 손대지
않았으므로(scope.md in_scope 조건부 미충족) 실행해도 대상 0건으로 공허 통과할 뿐이다.

## clean-tree 게이트 실측 (리뷰 요청 조건 점검)
- cmd: `git status --porcelain -- strategy/ config/quality/gate-tests.properties reports/evidence/m1/1e/`
- exit: 0, 결과: 빈 문자열(커밋됨)
- 양성 대조: `strategy/src/main/kotlin/bidvector/strategy/Score.kt`에 한 줄을 추가한 뒤
  같은 명령을 돌리면 `M strategy/...Score.kt`가 잡히고, `git checkout --` 로 복원 뒤
  다시 빈 결과를 확인했다.

## secret 스캔
- cmd: `git diff 2af32f6..HEAD -- strategy/ config/quality/gate-tests.properties | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
- exit: 1 (grep 매치 없음 = 통과)
- 육안 확인: 이 slice는 Telegram id·사업자 정보 등 개인식별정보를 다루지 않는다(순수
  도메인 값 타입·predicate 뿐).

## 변이 실측 (구현 레인 mutation sweep, 6건 — 전부 최소 하나의 test/게이트로 잡힘)
각 변이는 코드를 실제로 바꿔 대상 test를 재실행하고(`:strategy:test --tests
"bidvector.strategy.WatchRulesTest"` 또는 `:strategy:cpdCheck`) 실패를 확인한 뒤
원본으로 되돌렸다(작업 트리는 변이 뒤 매번 `git status`로 깨끗함을 재확인).

1. **NoGate → Passed 접기** — `evaluate`의 `isEmpty()` 분기를 `Passed(emptySet())`로
   바꿈 → `WatchRulesTest` 3건 FAILED(exit 1).
2. **제외 우선 뒤집기** — 축 평가 순서를 category 먼저로 바꿈 → 기존 test는 안 잡았고
   (category·exclude 중 하나만 Failed인 입력이라 순서 무관), category 도 실패하고
   exclude 도 매치하는 새 입력으로 실측해 FAILED 확인 → 그 test를 영구 등재(`456b982`).
3. **키워드가 FullScopeText 를 봄** — `requiredKeywordOutcome`이 `fullText`를 읽게 바꿈
   → `WatchRulesTest` 4건 FAILED(exit 1).
4. **Undeterminable → Rejected 접기** — `combineAxes`에서 `Undeterminable`을 `Rejected`로
   접음 → `WatchRulesTest` 2건 FAILED(exit 1).
5. **0 = 무제한 sentinel 재도입** — `singleBoundOutcome`에 `limit`이 0원이면 규칙 없음
   취급하는 legacy 게이트를 재도입 → `WatchRulesTest` 1건 FAILED(exit 1).
6. **둘째 validation 경로(D-10 「네 자리 재구현」 재현)** — `buildActionThresholds`의
   점수·`review<=bidNow` 블록을 복사해 `secondPathReviewCheck` 함수를 신설 →
   `:strategy:cpdCheck` FAILED(exit 1, "CPD found duplicate code").

재확인: 원본 복구 뒤 `./gradlew check`(전체) exit 0 — 6건 모두 회귀 없이 원상태.

## rollback 명령 실측 (임시 clone)
- cmd: 임시 `git clone` 위에서 `git restore --source=2af32f6 --staged --worktree -- <in_scope 경로 18개 개별 인자>`
- exit: 0
- 핵심 결과: `strategy/` 신규 파일(main 12·test 3·`compile-fixtures/` 디렉터리 인자 하나가
  하위 fixture 6개를 묶어 삭제)이 삭제로, `strategy/build.gradle.kts`·
  `config/quality/gate-tests.properties`가 수정으로 스테이징됐고
  `git diff 2af32f6 -- strategy/ config/quality/gate-tests.properties`가 빈 결과였다
  (정본은 `rollback.md`).

---

## 후속 — runner dispatch(⑪) 라운드 (base `0ee0f2c`, head `fa1db98`)

curator가 `c9022d9`·`90948da`·`2b04b4b`로 case 12건(money-basis-003 승격 1·strategy-watch
001~008·strategy-validation 001~003)을 authoritative로 냈고, 이 라운드가 그것을 app
conformance runner에 배선한다. 다른 세션의 `7a102cc docs(m2-prep)`가 사이에 끼어
있으나 무관하다(건드리지 않았다).

## 2026-09-06T22:46Z
- cmd: `./gradlew :app:test --tests '*Conformance*'`(초기 시도, 배선 전)
- exit: 1 — `1B 축 insufficient-evidence 이월은 money-basis-003 하나뿐이다` FAILED
  (`expected:<["money-basis-003"]> but was:<[]>`) — money-basis-003 승격으로 그 test의
  전제 자체가 낡았다. `SharedKernelCorpusConformanceTest`를 갱신해 기대값을
  `emptyList()`로 고쳤다(아래 재실행 참고).

## 2026-09-06T22:47Z
- cmd: `./gradlew :app:test --tests '*Conformance*'`(배선 후, `TARGET_DOMAINS`에
  `strategy-watch`·`strategy-validation` 추가 + `STRATEGY_EXECUTORS` 배선 +
  위 test 갱신 뒤)
- exit: 0
- 핵심 결과: `SharedKernelCorpusConformanceTest` 41 tests, 실패·건너뜀 0(동적 test 38건 +
  고정 test 3건 — dispatch 완전성·1B 이월·1C 이월).

## 비-vacuity — 기대값 임시 변조
- cmd: `fixtures/expected/strategy-watch-002.json`의 `$.verdict`를 `"Passed"`→`"Rejected"`로
  임시 변조 후 `./gradlew :app:test --tests '*Conformance*'`
- exit: 1 — `strategy-watch-002` FAILED(단 하나만 실패, 나머지 40건 통과 — dispatch가
  case별로 정확히 대조됨을 확인)
- 원복: `cp` 백업본으로 복구, `git status --short fixtures/expected/strategy-watch-002.json`
  결과 없음(추적 파일이라 diff 없음 = 원복 확인).

## 2026-09-06T22:53Z (S-1 재확인)
- cmd: `./gradlew --no-build-cache clean check`(저장소 루트)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 13s, 309 actionable tasks: 293 executed, 16 up-to-date

## 2026-09-06T22:54Z (S-2·S-4·S-6·S-7 개별 재확인)
- `./gradlew :strategy:test` — exit 0
- `./gradlew :app:test --tests '*Conformance*'` — exit 0
- `python3 fixtures/tools/mutation_sweep_adversarial.py` — exit 0, 강등 대상 0, 잔존
  authoritative 41, 캐치 65 → **116**(+51 — curator ASSERTED 12 case ·
  NULL_ASSERTED(`strategy-watch-004`) · `ADVERSARIAL_VALUE` 신설 토큰 4개 적용분)
- `./gradlew :build-logic:test` — exit 0

## clean-tree 게이트 재확인
- cmd: `git status --porcelain -- strategy/ config/quality/gate-tests.properties
  reports/evidence/m1/1e/ app/build.gradle.kts
  app/src/test/kotlin/bidvector/app/conformance/ fixtures/tools/mutation_sweep_adversarial.py`
- exit: 0, 결과: 코드 커밋(`fa1db98`) 직후 빈 문자열

## secret 스캔 재확인
- cmd: `git diff 0ee0f2c..fa1db98 -- app/ fixtures/tools/mutation_sweep_adversarial.py |
  grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
- exit: 0(매치 5건) — 전부 `token`이 "adversarial token"/"policy token"(도메인 어휘,
  예: `inclusivityFromToken` 함수명·"토큰" 주석)의 부분 문자열이라 오탐이다. 육안 확인:
  자격증명·API 키·개인식별정보 없음.

## 스윕 표(ASSERTED) 회귀 방지 — `"Rejected"` 키 충돌 회피 실측
- 편집 전 `ADVERSARIAL_VALUE`에 `"Rejected": "Accepted"`가 이미 있었다. `"Passed"`↔
  `"Rejected"` 요구를 그대로 옮기면 `"Rejected": "Passed"`가 뒤 값으로 덮어써 그 매핑이
  깨진다(파이썬 dict 리터럴의 중복 키 규칙) — `"Passed": "Rejected"`·`"NoGate": "Passed"`만
  추가하고 `"Rejected"` 키는 다시 매핑하지 않아 피했다.
- **정정(초안 오류)**: 이 절 초안이 "`verdict-004`가 `"Rejected": "Accepted"`를 쓴다"고
  적었으나 grep으로 재확인한 결과 `verdict-004`는 `classification: insufficient-evidence`라
  `mc.authoritative_cases()`가 거르는 `cases` 딕셔너리에 애초에 들지 않는다(스윕 출력에
  `verdict-004`가 한 줄도 없음, 명령: `python3 fixtures/tools/mutation_sweep_adversarial.py
  | grep "verdict-004"` → 빈 출력). 그 case는 `"Rejected"` 키 충돌과 무관하다 — 삭제한다.
- **실측으로 확인한 사실**: `"Rejected"` 키를 다시 매핑하지 않은 선택이 실제로 쓰이는
  자리는 `strategy-watch-001`·`003`·`005`의 `$.verdict`(기대값 `"Rejected"`)다. 스윕
  출력에서 세 case 모두 `$.verdict (a') verifies 주장 필드 값 변이 caught`로 캐치됨을
  확인했다 — 기존 `"Rejected":"Accepted"` fallback이 「기대값과 다른 확정 토큰」이라는
  스윕의 요구를 그대로 만족한다(도메인 어휘는 아니지만 값 변이 탐지에는 충분하다).

---

## verifier r1 finding 반영 라운드 (base `36c7f0c`, 코드+test 커밋 `cda8fef`)

verifier r1(`_workspace/m1-1e/02_verifier_report.md`, head `36c7f0c`) = ready-for-review,
medium 4·장부 5. F-4(capability-map STR-16 주석 위치)는 팀 리드 소유라 이 라운드가
건드리지 않는다. 아래는 F-1·F-2·F-3(코드) + L-2·L-3·L-4·L-5(장부) 일괄 수정이다.

### 코드 변경
- **F-1**: `Score.of`를 `internal fun`으로(`Score.kt`). 컴파일 fixture 3
  (`negative/positive/mutant-3-score-of-*`) + `CompileFailureHarnessTest` test 2개 신설.
- **F-2**: `WatchRulesTest`에 example 3개(카테고리·키워드 ASCII 대소문자 무관, 한글
  주변 텍스트 무변화).
- **F-3**: `WatchRulesTest`에 example 3개(`Exclusive` 하한 경계 탈락·하한 초과 통과·상한
  `Inclusive` vs `Exclusive` 대비).
- **L-5**: `WatchTypes.kt` KDoc의 `:65-68` 줄 범위 인용을 축어 인용문으로 교체.

### 변이 재확인 (verifier가 「살아남음」으로 표시한 둘)
- cmd: `AxisOutcome.kt`의 `foldCase`를 항등 함수로 변이 → `./gradlew :strategy:test --tests bidvector.strategy.WatchRulesTest`
- exit: 1 — F-2 example 3개 전부 FAILED. 원복 확인(`git status` 빈 결과).
- cmd: `WatchBudgetAxis.kt`의 `Exclusive` 갈래 부등호 반전(`cmp <= 0`→`cmp > 0`,
  `cmp >= 0`→`cmp < 0`) → 같은 test 재실행
- exit: 1 — F-3 example 3개 전부 FAILED. 원복 확인(`git status` 빈 결과).

### acceptance 재실행 (전부 foreground, exit 0만 확인)
- 2026-09-06T14:21:34Z `./gradlew --no-build-cache clean check`(저장소 루트) — exit 0,
  BUILD SUCCESSFUL in 14s, 309 tasks: 294 executed, 15 up-to-date.
- `./gradlew :strategy:test` — exit 0. 도메인 test 51건(WatchRulesTest 31·
  StrategyValidationTest 14·CompileFailureHarnessTest 6), 실패·건너뜀 0.
- `./gradlew :app:test --tests '*Conformance*'` — exit 0. `SharedKernelCorpusConformanceTest`
  41건, 실패·건너뜀 0(F-1~F-3는 app 계약과 무관해 case 수 불변).
- `python3 fixtures/tools/mutation_sweep_adversarial.py` — exit 0. 강등 대상 0, 잔존
  authoritative 41(캐치 수는 이 라운드가 스윕 표를 건드리지 않아 116 그대로).
- `./gradlew :build-logic:test` — exit 0.

### clean-tree 게이트 (이 라운드 in_scope)
- cmd: `git status --porcelain -- strategy/ reports/evidence/m1/1e/`
- 커밋 직전 빈 결과 확인(m2-prep 관련 파일들은 다른 세션 소유라 이 게이트 범위 밖 —
  건드리지 않았다).
