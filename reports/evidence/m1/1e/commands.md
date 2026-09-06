# M1/1E — 실행 명령과 종료 코드

base_sha: `2af32f6` · head_sha: `41ed454` (5개 slice 커밋: `0ef1228`·`0d02b9b`·`07450a1`·
`e3ef928`·`41ed454`, 하네스 레인 변경 없음 — `git log --oneline 2af32f6..HEAD -- CLAUDE.md
.claude/` 결과 없음).

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

## rollback 명령 실측 (임시 clone)
- cmd: 임시 `git clone` 위에서 `git restore --source=2af32f6 --staged --worktree -- <in_scope 경로 18개 개별 인자>`
- exit: 0
- 핵심 결과: `strategy/` 신규 파일(main 12·test 3·`compile-fixtures/` 디렉터리 인자 하나가
  하위 fixture 6개를 묶어 삭제)이 삭제로, `strategy/build.gradle.kts`·
  `config/quality/gate-tests.properties`가 수정으로 스테이징됐고
  `git diff 2af32f6 -- strategy/ config/quality/gate-tests.properties`가 빈 결과였다
  (정본은 `rollback.md`).
