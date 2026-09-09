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

## verifier r2 수정 라운드 2 재검증 — RED 확인 (임시 clone, 커밋 2161d2e 코드 + 신설 test)

**규칙(팀리드 지시)** — 값 타입 `init`은 마지막 안전판이지 게이트가 아니다. G-1·G-2·G-4
각각 신설 test를 **수정 전 production 코드**(2161d2e, d405d10 뒤 일부는 b81a723 전)에
돌려 RED를 먼저 확인했다.

- cmd: `:adapters:test --tests 'bidvector.adapters.ml.GrpcBidPredictionGatewayVerifierR2Test'`
  (G-1·G-2 신설 test, production 코드는 2161d2e — `BidRateCandidates`/`PriceFitness`의
  `init`만 있고 구조 검증층 술어가 없던 시점)
- exit: 1(6개 중 4개 실패)
- 핵심 결과: G-1 두 test 모두 `IllegalArgumentException`이 `predict` 밖(`GrpcBidPredictionGateway.
  handleSuccess`→`handleResponse`→`predict`)까지 새어 test framework 가 직접 잡음(값이 아니라
  예외로 실패) — `BidRateCandidates.<init>`(`BidPredictionOutcome.kt`)에서 발생. G-2도 동형:
  `PriceFitness.<init>`이 `score는 음수일 수 없다`로 던짐. G-4는 `Unavailable(reason=CircuitOpen)`
  로 실패(기대 `Predicted`) — 예산 부족 두 호출이 breaker 를 실제로 열었음을 실측 확인.
  G-5·클램프-포화 test 2건은 이 시점에도 이미 GREEN(해당 조건은 F-2/기존 구조가 처리).

## verifier r2 수정 라운드 2 재검증 — 수정 뒤 GREEN

- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: 전 모듈(detekt·ktlint·cpd·sizeGate·gateExecutionGate·qualityBaseline·contractGate
  포함) GREEN. 첫 실행에서 `:adapters:sizeGate`가 `GrpcBidPredictionGatewayTest.kt`(563줄,
  500줄 한도 초과 — G-1·G-2·G-4·G-5 test 신설분)로 실패해, 그 test들과 `protoResponse`·
  `fixedServicer`를 `GrpcBidPredictionGatewayVerifierR2Test.kt`(신설)·`MlTestFixtures.kt`
  (공용 fixture)로 분할한 뒤 재실행해 통과(367·231·158줄, 재분할 뒤 clean check 재확인).
- cmd: `:adapters:detekt :adapters:ktlintCheck :adapters:cpdCheck :adapters:sizeGate
  :adapters:test --tests 'bidvector.adapters.ml.*' :adapters:test --tests
  'bidvector.adapters.contract.*' :workflow:test --tests 'bidvector.workflow.prediction.*'
  :decision:test`
- exit: 0

## rollback 재실측(G-3) — 상세는 rollback.md

- cmd: 위 rollback.md 「실측(임시 clone, 2026-09-10, HEAD=`b81a723`)」 5단계 전체
- exit: 0(5단계 모두, 단 ②만으로는 ④가 막힘을 먼저 확인 — M 항목 hunk 격리를 같이 적용한
  뒤 통과. rollback.md 참고, 이전(a96c53c 대상) 실측 기록은 거짓 양성이었음을 함께 정정)
- **정정** — 이전 rollback 실측(2161d2e 이전 라운드 기록)의 `git restore` 명령이
  당시 아직 없던 `reports/evidence/m4/4d/{commands,checklist,policy-values}.md`를 포함해
  pathspec 오류로 전체가 무동작이었다(`git restore`는 원자적). 그 「exit 0」은 원본 트리를
  그대로 컴파일·테스트한 결과였다 — 이번 라운드에 HEAD 기준으로 다시 재현해 잡았다
  (자기 감사, evidence-pack 규격의 대상).

## verifier r2 재검증용 임시 clone 이 worktree 공유 index 를 오염시킨 사고와 복구

- **원인** — `cp -r` 로 이 worktree(`bid-vector-v2-m4e`, `git worktree add` 연결 worktree)를
  `/tmp/4d1-red-repro`에 복사했는데, `.git`이 디렉터리가 아니라
  `gitdir: /Users/harris/Development/private/bid-vector-v2/.git/worktrees/bid-vector-v2-m4e`
  를 가리키는 **포인터 파일**이라 복사본의 git 명령이 이 worktree 의 공유 index 를 그대로
  건드렸다(작업 트리 파일은 복사본과 원본이 물리적으로 분리돼 있어 무사했다). RED 재현
  중 `git checkout 2161d2e -- <경로>` + `git rm --cached CandidateShapeValidation.kt`를
  복사본에서 실행한 것이 실제로는 이 worktree 의 index 를 오염시켰다.
- **발견** — `git status --porcelain`에서 이 worktree 의 실제 수정 이력에 없는 `D `/`MM`
  상태(`CandidateShapeValidation.kt` 등 5개)가 나타나 확인.
- **영향 확인** — `grep -c` 로 작업 트리 파일 내용을 직접 대조(예:
  `ResilientPredictionCall.kt`의 `BudgetExhausted` 존재, `BidPredictionOutcome.kt`에
  `PriceFitness` 음수 거부 부재)해 **작업 트리 파일 내용은 훼손되지 않았음**을 확인 —
  오염은 index(스테이징 영역)에 한정.
- **복구** — `git reset`(경로 없이, HEAD 기준 mixed reset)으로 index 만 HEAD(`b81a723`)에
  맞춰 되돌리고 작업 트리는 그대로 뒀다. `git diff --stat HEAD -- adapters/src/main/kotlin
  workflow/src/main/kotlin decision/src/main/kotlin` — 출력 없음(작업 트리가 HEAD 와
  완전히 일치, 오염 없음 재확인). 이후 `./gradlew --no-build-cache clean check`를 다시
  돌려 GREEN 확인(아래).
- **재발 방지** — 이 worktree 의 RED 재현·rollback 실측에는 이후 `cp -r`(worktree)이
  아니라 `git clone .`(항상 완전한 독립 `.git` 디렉터리를 만든다, rollback.md 의 기존
  관례와 동일)만 쓴다.

## verifier r2 라운드 전체 — 사고 복구 뒤 최종 재확인

- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: index 복구 뒤 전 모듈 GREEN 재확인(353+ actionable tasks).

## T-9/T-10 양성 대조 재실측(수정 라운드 2 뒤)

- cmd: `RetryRules.kt` 1행 뒤에 `import io.github.resilience4j.retry.Retry`를 심고
  `:adapters:test --tests 'bidvector.adapters.ml.MlAdapterDependencyTest'`
- exit: 1(매치 실측 — `expected:<[]> but was:<["import io.github.resilience4j.retry.Retry"]>`),
  `git checkout -- RetryRules.kt` 복원 후 diff 0.
- cmd: `gate-tests.properties`의 `BreakerTest`를 `BreakerTestTYPO`로 변이하고
  `:adapters:gateExecutionGate`
- exit: 1(`게이트 test class 가 실행되지 않았다 — bidvector.adapters.ml.BreakerTestTYPO`),
  복원 후 `:adapters:gateExecutionGate :decision:gateExecutionGate :workflow:gateExecutionGate`
  재실행 exit 0(S-5 재확인).

## 인증값 노출 재스캔(라운드 2, G-3 문서 편집 뒤)

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m4/4d/ --exclude=scope.md`
- exit: 1(매치 0)

## verifier r3 수정 라운드 3 — RED 확인(H-1)

- cmd: `git stash push -- adapters/src/main/kotlin/bidvector/adapters/ml/ResilientPredictionCall.kt`
  로 프로덕션 파일만 r2 상태(`cbf769a`)로 되돌리고(신설 `BreakerTest` H-1 test 는 그대로 둔
  채) `:adapters:test --tests 'bidvector.adapters.ml.BreakerTest'`
- exit: 1
- 핵심 결과: `HALF_OPEN 에서 예산 소진 호출이 permit 을 반납해 이후 호출이 서버에 닿는다(H-1)`
  실패 — `org.opentest4j.AssertionFailedError: expected:<DeadlineExceeded> but was:<CircuitOpen>`,
  verifier r3 실측(permit 10개 소진 뒤 11번째부터 CircuitOpen)과 동형 — RED.
- cmd: `git stash pop` 으로 프로덕션 파일 복원
- exit: 0, `git status --porcelain` 재확인 — 의도한 파일만 남음.

## verifier r3 수정 라운드 3 — 수정 뒤 GREEN(H-1)

- cmd: `:adapters:ktlintFormat :adapters:detekt :adapters:cpdCheck :adapters:sizeGate
  :adapters:test --tests 'bidvector.adapters.ml.BreakerTest'`
- exit: 0(BreakerTest 2건 전부 통과 — 기존 회귀 test 1 + H-1 신설 1).
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0(344 actionable tasks, H-1 커밋 `3472036` 시점).

## verifier r3 수정 라운드 3 — H-2·H-4·H-6 검증

- cmd: `:adapters:ktlintFormat :adapters:detekt :adapters:cpdCheck :adapters:sizeGate
  :adapters:test --tests 'bidvector.adapters.ml.SuccessShapeFailClosedTest'
  :adapters:test --tests 'bidvector.adapters.ml.MlGateRegistrationTest'`
- exit: 0 — `SuccessShapeFailClosedTest` 7건(기존 6 + H-6 table-driven 1, table 내부 8 case
  전부 대조 통과)·`MlGateRegistrationTest` 2건(완전성 술어 + 양성 대조) 전부 통과.
- cmd: `:adapters:gateExecutionGate`
- exit: 0 — 신설 두 class 등재·실행 확인(S-5).
- cmd: T-10 변이 — `gate-tests.properties`의 `SuccessShapeFailClosedTest,` 를
  `SuccessShapeFailClosedTestTYPO,` 로 바꾸고 `:adapters:gateExecutionGate`
- exit: 1(`게이트 test class 가 실행되지 않았다 — bidvector.adapters.ml.SuccessShapeFailClosedTestTYPO`),
  복원 후 `:adapters:gateExecutionGate :decision:gateExecutionGate :workflow:gateExecutionGate`
  재실행 exit 0(S-5 재확인) — `git diff config/quality/gate-tests.properties` 로 typo 잔존
  없음 확인.
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0(344 actionable tasks, H-2·H-4·H-6 커밋 `9bb67c3` = 이 라운드 마지막 코드·test
  커밋 시점 — 이 HEAD 가 rollback H-3 재실측의 대상이다, rollback.md 참고).

## rollback 재실측(H-3) — 상세는 rollback.md

- cmd: 위 rollback.md 「실측(임시 clone, HEAD=`9bb67c3`)」 5단계 전체(A 33개 restore +
  M 9개 hunk 격리 11건 + compile + test)
- exit: 0(전 단계) — A 목록에 `MlGateRegistrationTest.kt`·`SuccessShapeFailClosedTest.kt`
  누락(H-3 지적 자체)을 채우고, `gate-tests.properties`의 M 항목 hunk 커밋에 `9bb67c3`를
  추가한 뒤 재측정.

## 인증값 노출 재스캔(라운드 3 편집 뒤)

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m4/4d/ --exclude=scope.md`
- exit: 1(매치 0)

## `file:line` 좌표 재스캔(verifier r3 H-5)

**이 절 자체에 원문을 옮겨 적지 않는다** — 위 인증값 노출 스캔 절과 같은 이유(자기매치
회피, evidence-pack 규격). 매치됐던 파일·줄만 가리킨다.

- cmd: `grep -rnE '\.kt:[0-9]+' reports/evidence/m4/4d/ _workspace/m4-4d/03_impl_report.md`
- exit: 1(매치 0) — `commands.md`의 RED 확인 절(G-1 재현 기록)에 있던 `BidRateCandidates`
  값 타입 생성자 참조 한 곳이 파일명 뒤에 줄 번호를 달고 있었다. 그 파일 안의 줄 번호를
  빼(클래스·파일명만 남기고) 해소했다.
