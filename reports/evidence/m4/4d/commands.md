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

## 인증값 노출 스캔(verifier r1 F-6 — 이 절 자체에 정규식 원문을 옮겨 적지 않는다.
정규식을 문자 그대로 이 문서에 박으면 다음 라운드의 같은 스캔이 이 절 자신을 매치한다
— 장부가 스스로를 낡게 만드는 형태라 evidence-pack 규격이 금하는 「출력 전문」과 같은
급의 문제다. 아래 첫 명령은 `config/quality/leak-patterns.txt` **파일 경로**만 인자로
받아 원문을 재현하지 않는다.)
- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m4/4d/ --exclude=scope.md`
- exit: 1(매치 0)
- cmd: CLAUDE.md 「리뷰 요청 조건」범용 스캔(어휘는 `leak-patterns.txt`와 동일) — 정규식
  원문은 위와 같은 이유로 이 문서에 옮기지 않는다.
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

## S-0 (격리 worktree)
<!-- verifier r1 F-8 — 실행 시점 HEAD 짧은 SHA 를 이 문서에 박지 않는다(낡는 좌표,
     evidence-pack 규격). 명령 자체가 `HEAD`(symbolic)를 인자로 받아 실행 시점 커밋을
     그대로 checkout 한다 — 재현에는 커밋 값이 필요 없다. -->

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: 353 actionable tasks 전건 실행, BUILD SUCCESSFUL. worktree 제거 확인(`git worktree remove --force`).

## clean-tree 게이트(경로 개별 인자 + 양성 대조)
- cmd: `git status --porcelain -- <in_scope 경로 개별>`
- exit: 0, 출력 없음(clean).
- 양성 대조: `CallBudget.kt`에 `// probe` 한 줄을 심고 같은 명령 재실행 → 매치 확인 →
  `git checkout HEAD -- <파일>`로 절삭 복원(이 파일의 유일한 미커밋 편집이라 안전) →
  재확인 출력 없음.

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

## verifier r1 수정 라운드 1 재검증 — RED 확인

- cmd: `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.ml.GrpcBidPredictionGatewayTest'`(F-1 백오프 경과시간·예산 probe 신설 직후, 수정 전)
- exit: 1
- 핵심 결과: 백오프 지연 probe·예산 초과 probe 둘 다 RED(지연 없음, 재시도 3회로 끝남).
- cmd: `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.ml.*' :workflow:test --tests 'bidvector.workflow.prediction.*'`(F-2 release 공백 probe 신설 직후, 수정 전)
- exit: 1
- 핵심 결과: `mapSuccess` 시그니처 불일치로 컴파일 실패(신 파라미터 미구현) — RED.
- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.prediction.PredictionValueTest'`(F-5 순서·부호 probe 신설 직후, 수정 전)
- exit: 1
- 핵심 결과: `BidRateCandidates` 역순·`PriceFitness` 음수 생성이 예외 없이 성공 — RED.

## verifier r1 수정 라운드 1 재검증 — 수정 뒤 GREEN

- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: 전 모듈(detekt·ktlint·cpd·gateExecutionGate·qualityBaseline·contractGate 포함) GREEN.
- cmd: `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.ml.*'`
- exit: 0
- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.prediction.*'`
- exit: 0
- cmd: `./gradlew --no-daemon :decision:test`
- exit: 0
- cmd: `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.contract.*'`
- exit: 0
- cmd: `./gradlew --no-daemon :adapters:gateExecutionGate :decision:gateExecutionGate :workflow:gateExecutionGate`
- exit: 0

## T-9/T-10 양성 대조 재실측(수정 라운드 뒤)

- cmd: `RetryRules.kt`에 `import io.github.resilience4j.retry.Retry`를 심고 `:adapters:test --tests 'bidvector.adapters.ml.MlAdapterDependencyTest'`
- exit: 1(매치 실측 — `expected:<[]> but was:<["import io.github.resilience4j.retry.Retry"]>`), 복원 후 diff 0.
- cmd: `gate-tests.properties`의 `BreakerTest`를 `BreakerTestTYPO`로 변이하고 `:adapters:gateExecutionGate`
- exit: 1(`게이트 test class 가 실행되지 않았다 — bidvector.adapters.ml.BreakerTestTYPO`), 복원 후 diff 0.

## 인증값 노출 재스캔(F-6 해소 확인)

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m4/4d/ --exclude=scope.md`
- exit: 1(매치 0 — 자기매치 3건 해소 확인)
