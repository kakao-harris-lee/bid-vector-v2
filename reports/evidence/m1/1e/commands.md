# M1/1E — 실행 명령과 종료 코드

base_sha: `2af32f6` · head_sha: `456b982` (6개 slice 커밋: `0ef1228`·`0d02b9b`·`07450a1`·
`e3ef928`·`41ed454`·`456b982`, 하네스 레인 변경 없음 — `git log --oneline 2af32f6..HEAD --
CLAUDE.md .claude/` 결과 없음). S-0·S-1의 타임스탬프는 `41ed454` 시점 실측이고, 뒤이은
`456b982`(변이 실측이 드러낸 test 신설)은 `:strategy:check`·전체 `check` 재실행으로
회귀 없음을 재확인했다(아래 「변이 실측」 절 뒤 재확인 로그).

## 2026-09-06T13:11:15Z
- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` (S-0)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 35s, 318 actionable tasks: 318 executed (전건 재실행, 캐시 없음)

## 2026-09-06T13:09:44Z
- cmd: `./gradlew --no-build-cache clean check` (S-1, 저장소 루트)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL in 16s, 318 actionable tasks: 293 executed, 25 up-to-date

## 2026-09-06T13:10:07Z
- cmd: `./gradlew :strategy:test` (S-2)
- exit: 0
- 핵심 결과: strategy 도메인 test 42건(WatchRulesTest 24·StrategyValidationTest 14·
  CompileFailureHarnessTest 4) 전부 통과, 실패·건너뜀 0

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
