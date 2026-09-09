# commands.md — M4/4D-1

## RED — workflow.prediction
- cmd: `./gradlew --no-daemon :decision:compileTestKotlin :workflow:compileTestKotlin`
- exit: 1
- 핵심 결과: 구문 오류(KDoc 내 `**/` 시퀀스) — RED.

## GREEN
- cmd: `./gradlew --no-daemon :decision:test :workflow:compileTestKotlin`
- exit: 0
- 핵심 결과: decision·workflow.prediction 값 불변식·경계 test 전건 통과.

## RED — adapters main 최초 컴파일
- cmd: `./gradlew --no-daemon :adapters:compileKotlin`
- exit: 1
- 핵심 결과: unresolved reference 1건 + internal constructor cross-module 접근 불가 5건.

## GREEN
- cmd: `./gradlew --no-daemon :adapters:compileKotlin`
- exit: 0

## GREEN — adapters test 컴파일
- cmd: `./gradlew --no-daemon :adapters:compileTestKotlin`
- exit: 0

## S-2a
- cmd: `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.ml.*'`
- exit: 0
- 핵심 결과: 신설 7 class 전건 통과.

## S-2b
- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.prediction.*'`
- exit: 0

## S-2c
- cmd: `./gradlew --no-daemon :decision:test`
- exit: 0
- 핵심 결과: `MlUnavailableReasonTest` 포함 decision 전 test 통과.

## S-3
- cmd: `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.contract.*'`
- exit: 0
- 핵심 결과: 2A~2D 전 consumer test 승격 뒤에도 전건 통과(단언·case 무변경).

## S-4
- cmd: `./gradlew --no-daemon :adapters:test --tests '*MlAdapterDependencyTest*' :workflow:test --tests '*PredictionBoundaryTest*'`
- exit: 0

## S-5
- cmd: `./gradlew --no-daemon :adapters:gateExecutionGate :decision:gateExecutionGate :workflow:gateExecutionGate`
- exit: 0

## S-6
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0

## secret/leak 스캔
- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m4/4d/ --exclude=scope.md`
- exit: 1(매치 0)
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m4/4d/`
- exit: 1(매치 0)

## S-1 (1차)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 1
- 핵심 결과: `adapters:cpdCheck`(breaker 설정 체인 중복) + `adapters:detekt` 30 issues.

## S-1 (2차)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 1
- 핵심 결과: cpdCheck 해소, detekt 잔여 7건.

## S-1 (3차)
- cmd: `./gradlew --no-daemon :adapters:detekt`
- exit: 0

## S-1 (4차)
- cmd: `./gradlew --no-daemon :adapters:ktlintFormat`
- exit: 0

## S-1 (5차 — 전 모듈 확인)
- cmd: `./gradlew --no-daemon :decision:ktlintCheck :decision:detekt :workflow:ktlintCheck :workflow:detekt`
- exit: 1
- 핵심 결과: decision 통과, `workflow:detekt`가 `Ports.kt`(`MatchingDeclarationName`)에서 실패.

## S-1 (6차 — app 파급)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 1
- 핵심 결과: `app:compileTestKotlin`(`VerdictExecutors.kt` 소진 `when`이 `MlUnavailableReason` 신설 아홉 값 미포괄) + `adapters:compileTestKotlin`(import 누락 1건).

## S-1 (7차)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 1
- 핵심 결과: 컴파일 통과, `app:ktlintTestSourceSetCheck`만 잔존.

## S-1 (8차 — 최종)
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: 전 모듈 통과.

## rollback 실측(임시 clone) — rollback.md 참고
- cmd: `git clone . /tmp/4d1-rollback-verify && git checkout a96c53c`
- exit: 0
- cmd: A 항목 `git restore --source=<base> --staged --worktree -- <경로들>`
- exit: 0
- cmd: `git diff <base> -- <각 경로>` (복원 확인)
- exit: 0(출력 없음 — 내 줄이 사라졌다)
- cmd: `./gradlew --no-daemon :decision:compileKotlin :workflow:compileKotlin :adapters:compileKotlin :app:compileKotlin`
- exit: 0
- cmd: `./gradlew --no-daemon :decision:test :workflow:compileTestKotlin :adapters:test --tests 'bidvector.adapters.contract.*' :app:compileTestKotlin`
- exit: 0
