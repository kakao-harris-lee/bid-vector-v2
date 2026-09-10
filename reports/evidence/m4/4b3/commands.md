# commands.md — M4/4B-3

## RED — decision
- cmd: `./gradlew --no-daemon :decision:compileTestKotlin`
- exit: 1
- 핵심 결과: `VerdictLadderTest.kt` 의 `mlUnavailableReason` named-parameter 미해결 2건(신설 슬롯 부재).

## GREEN — decision
- cmd: `./gradlew --no-daemon :decision:test`
- exit: 0
- 핵심 결과: `VerdictLadderTest` 전건(신설 3 test 포함) 통과.

## RED — workflow
- cmd: `./gradlew --no-daemon :workflow:compileTestKotlin`
- exit: 1
- 핵심 결과: `MlAnalysisOutcome.Unavailable` unresolved reference + suspend override 시그니처 불일치 2건.

## GREEN — workflow
- cmd: `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin`
- exit: 0

## RED — adapters
- cmd: `./gradlew --no-daemon :adapters:compileTestKotlin`
- exit: 1
- 핵심 결과: `UnavailableMlAnalysis` unresolved reference 1건.

## GREEN — adapters
- cmd: `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.ml.UnavailableMlAnalysisTest' --tests 'bidvector.adapters.ml.MlGateRegistrationTest' --tests 'bidvector.adapters.ml.MlAdapterDependencyTest'`
- exit: 0

## S-2a·S-2b·S-2c·S-3 (합동 실행)
- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.evaluation.*' :decision:test :adapters:test --tests 'bidvector.adapters.ml.UnavailableMlAnalysisTest' :app:test --tests '*Conformance*'`
- exit: 0
- 핵심 결과: workflow evaluation·decision·adapters ml·app conformance 전건 통과 — 기본값 슬롯이라 conformance corpus 기대값 무변경.

## S-4
- cmd: `./gradlew --no-daemon :workflow:gateExecutionGate :decision:gateExecutionGate :adapters:gateExecutionGate`
- exit: 0

## S-5 (S-2a에 포함, 단독 확인)
- cmd: `./gradlew --no-daemon :workflow:test --tests '*CompositionBoundaryTest*'`
- exit: 0

## S-6
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0

## S-1 (1차)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 1
- 핵심 결과: `adapters:ktlintMainSourceSetCheck` — `UnavailableMlAnalysis.kt` import 순서 위반(`workflow.event` vs `workflow.evaluation` 사전순).

## 수정 — ktlintFormat
- cmd: `./gradlew --no-daemon ktlintFormat -p adapters`
- exit: 0
- 핵심 결과: `UnavailableMlAnalysis.kt`·`UnavailableMlAnalysisTest.kt` import 순서 자동 정정.

## S-1 (2차)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 1
- 핵심 결과: `workflow:sizeGate` — `EvaluateCandidatesUseCase.analyzeAndJudge` 53줄(50줄 한도 초과) + `workflow:detekt` — `EvaluationTestFixtures.kt` MaxLineLength 1건.

## 수정 — 함수 분리·줄바꿈
- 핵심 결과: `analyzeAndJudge`에서 `LadderInput` 조립을 `ladderInputFor` 헬퍼로 분리(클래스 함수 수 10→11, detekt `TooManyFunctions` 기본 한도 11 이내) + `unavailableAnalysis` 시그니처 줄바꿈.

## S-1 (3차)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 344 actionable tasks(320 executed). `shared-kernel:cpdCheckObserved` 관측 경고는 이 slice가 만지지 않은 모듈의 기존 baseline 잡음(관측 전용, `cpdCheck` 자체는 통과).

## S-0 (isolated worktree, HEAD=`eb1a1fc`)
- cmd: `git worktree add --detach /tmp/4b3-s0-worktree HEAD`
- exit: 0
- cmd: `./gradlew --no-build-cache clean check` (worktree 안)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 353 actionable tasks(전건 실행). `gate.tests.adapters` 등재
  커밋(`eb1a1fc`) 포함 HEAD 로 재실행해 `MlGateRegistrationTest` 통과 확인(gate 등재 전
  중간 HEAD(`2254911`)에서의 1차 시도는 의도적으로 실패 — 등재 커밋 전이라 당연한 결과였고
  결함이 아니다).
- cmd: `git worktree remove --force /tmp/4b3-s0-worktree`
- exit: 0

## 인증값 노출 스캔
- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m4/4b3/ --exclude=scope.md`
- exit: 1(매치 0)
