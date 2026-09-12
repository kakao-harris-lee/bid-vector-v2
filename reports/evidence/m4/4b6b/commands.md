# M4/4B-6b 실행 명령 로그

base_sha: `571059ab5ada307126a9dd35bc33b846dd4ae63f`(slice base, scope.md 정본 — verifier r1
F-3 정정. 이전 판은 계약 커밋 `9787329`를 잘못 base 로 적었다)
head_sha(코드 마지막 커밋, verifier r1 수정 라운드 포함): `d21cb21`(evidence 갱신 커밋은
목록을 만들 뿐 자기 자신을 가리키지 않는다)

## RED — main 없이 test 컴파일 실패 확인

- cmd: `./gradlew --no-daemon :workflow:compileTestKotlin`
- exit: 1 — `OpportunityAnalysisTest`·`EmbeddingBridgeTest`·`OpportunityAnalysisFixtures`
  전부 `Unresolved reference`(`OpportunityAnalysis`·`bridgeEmbeddingReason`·
  `toUnitVector` 등) — 조합기·브리지가 아직 없어 실패함을 확인.

## GREEN — main 작성 뒤 S-2

- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.evaluation.*'`
- 최종 exit: 0 — evaluation 패키지 총 85 tests, 0 failed(`OpportunityAnalysisTest` 21·
  `OpportunityPolicyDataTest` 13·`TextSynthesisTest` 9·`EmbeddingBridgeTest` 4 + 기존
  38, 각 파일 test-results XML `tests`/`failures` 속성으로 실측).
- 중간 실패 1건 — `BigDecimal` scale 이 다른 `1` vs `1.0`을 data class `equals`가 다르게
  봐 `matchedScore shouldBe UnitScore(BigDecimal.ONE)` 실패. `compareTo(BigDecimal.ONE)
  shouldBe 0`로 교정.

## S-3 — CompositionBoundaryTest 단독

- cmd: `./gradlew --no-daemon :workflow:test --tests '*CompositionBoundaryTest*'`
- exit: 0 — 신설 파일(`OpportunityAnalysis.kt`·`OpportunityAnalysisPipeline.kt`·
  `PredictionFacts.kt`·`EmbeddingBridge.kt`)이 이 패키지 스캔(`walkTopDown`) 대상에
  자동 포함됨을 확인(allow-list 재구성 불필요 — `bidvector.workflow.embedding`·
  `bidvector.workflow.prediction`은 `bidvector.workflow` 루트로 이미 허용).

## S-5 — WorkflowGateRegistrationTest(신설, 운영자 결정 2026-09-11 (a))

- cmd: `./gradlew --no-daemon :workflow:test --tests '*WorkflowGateRegistrationTest*'`
- 1차 exit: 1 — 양방향 차집합이 기존 미등재 `LadderPolicySlotTest`(4B-2 신설, 당시
  등재 누락)를 실측으로 찾음. `gate-tests.properties`에 등록해 해소.
- 2차 exit: 0 — 3 tests(등재 완전성 1 + 양성 대조 누락/잉여 각 1), 0 failed.
- 양성 대조(등재 한 줄 삭제 → 붉음): test 자체가 가짜 discovered/registered 집합으로
  차집합 술어를 실측하는 별도 test 둘로 대체 실행(운영 파일을 건드리지 않고 확인).

## S-4 — gateExecutionGate

- cmd: `./gradlew --no-daemon :workflow:gateExecutionGate`
- exit: 0 — 신설 6종(`WorkflowGateRegistrationTest`·`EmbeddingBridgeTest`·
  `OpportunityAnalysisTest`)과 새로 등재한 `LadderPolicySlotTest` 전부 실제로
  실행됐음을 확인(등재만 하고 실행에서 빠지는 결함 없음).

## S-6 — adapters UnavailableMlAnalysisTest(4B-3 fail-safe 무영향 증명)

- cmd: `./gradlew --no-daemon :adapters:test --tests '*UnavailableMlAnalysisTest*'`
- exit: 0 — `adapters` 모듈 전체 compile+test 통과, use case·`UnavailableMlAnalysis`
  무편집을 실측으로 확인(이 slice는 `workflow/evaluation` 패키지만 만졌다).

## S-7 — 비밀값 단어 누출 검사

- cmd: `grep -rniE -f config/quality/leak-patterns.txt workflow/src/main/kotlin/bidvector/workflow/evaluation`
- exit: 1(매치 0건 — grep 관례상 「매치 없음」이 exit 1) — 신설 코드에
  `config/quality/leak-patterns.txt` 패턴 매치 없음.

## S-8 — qualityBaseline

- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0.

## S-1 — 전건 `clean check`(중간 수정 라운드 포함)

- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- 1차 exit: 1 — `workflow:sizeGate`(`analyze` 98줄, 함수 50줄 한도 초과) +
  `workflow:detekt`(`CyclomaticComplexMethod` 19>14·`ReturnCount` 12/4>2·
  `MaxLineLength` 2건). guard 체인을 `Step`/`andThen` 값 조합자로 재작성(사다리꼴
  `if(...)return` 제거) — 각 단계를 독립 함수로 분리.
- 2차 exit: 1 — `detekt`(`TooManyFunctions` class 17>11).  port 접근 함수(class 10개)와
  순수 변환 함수를 분리(파일 미분할 상태).
- 3차 exit: 1 — `detekt`(`TooManyFunctions` **파일** 17>11, class 는 해소).
  순수 변환 단계를 `OpportunityAnalysisPipeline.kt`(9)·`PredictionFacts.kt`(5)로
  파일 분할(`internal` 전환).
- 4차 exit: 1 — `ktlintMainSourceSetCheck`(dangling KDoc 1 + when-entry 중괄호
  일관성 2). `./gradlew --no-daemon :workflow:ktlintFormat` 자동 교정.
- 5차(최종) exit: 0 — 346 tasks, 321 executed. `qualityBaseline`·전 모듈
  `detekt`/`ktlint`/`sizeGate`/`domain*Gate`/`cpdCheck`/`gateExecutionGate`/
  `koverVerify`/`test`/`check` 전부 통과.

## S-0 — 격리 worktree(`git worktree add --detach`)

- cmd: `git worktree add --detach /tmp/bv-4b6b-s0-check HEAD && (cd /tmp/bv-4b6b-s0-check && ./gradlew --no-build-cache --no-daemon clean check)`
- exit: 0 — 355 tasks, 355 executed(0 up-to-date, 완전 격리 트리에서 재현). 종료 뒤
  `git worktree remove --force` 로 제거 확인(`git worktree list` 에 잔존 없음).

## verifier r1 수정 라운드(1/5) — F-1(high)·F-2(medium)·F-4(low)

### F-1 재현·수정 확인

- 재현(수정 전): `predictedFacts`에 `predicted(conservative=1.00, base=Rate.ofFraction(BigDecimal("1.05")), aggressive=1.10)`를
  넣으면 `MarginInputs.init`(`recommendedRate ≤ 1`)이 `IllegalArgumentException`을
  던져 `analyze()` 밖으로 새고 use case 배치 전체가 소실됨(verifier 보고 실측 그대로).
- 수정: `predictionUntrustworthy`(fitness 범위 밖 **또는** `candidates.base > 1`)를
  `MarginInputs` 생성 **전**에 판정 — 참이면 `absentPair(ContractViolation)`.
- 수정 뒤: `PredictionFactsTest`(신설, 2 tests) —
  `candidates.base 1.0 은 통과하고 1.0000001 은 두 성분 Absent(ContractViolation) 이며
  예외가 없다`·`floorRate 1.0 은 margin 통과, 1.0000001 은 margin 만 Absent`. 둘 다
  `shouldNotThrowAny` 로 감싸 예외 자체가 없음을 단언.
- cmd: `./gradlew --no-daemon :workflow:test --tests '*PredictionFactsTest*'` → exit 0.
- 부작용 수정: `predictedFacts`가 두 조건을 각각 `if(...)return`으로 둬
  detekt `ReturnCount`(3>2)에 걸림 → `predictionUntrustworthy(Boolean)`로 뽑아
  1회 early-return + 1회 최종 return으로 정리(커밋 `d21cb21`).

### F-2 재현·수정 확인(양성 대조, 실측 뒤 원복)

- 재현(수정 전, verifier 보고 그대로): `gate.tests.workflow`에서
  `WorkflowGateRegistrationTest` 등재 한 줄을 지우고
  `./gradlew --no-daemon :workflow:test --tests '*WorkflowGateRegistrationTest*'`
  (`--rerun-tasks` 없이) → task `UP-TO-DATE` 로 건너뛰어 exit 0(거짓 초록).
- 수정: `workflow/build.gradle.kts`의 `tasks.withType<Test>`에
  `inputs.file(rootProject.file("config/quality/gate-tests.properties"))
  .withPathSensitivity(PathSensitivity.RELATIVE)` 추가.
- 수정 뒤 재현: 같은 조작(등재 한 줄 삭제, `--rerun-tasks` 없이 같은 명령) →
  task 가 실제로 재실행되어 **FAILED**(`expected:<[]> but was:<["bidvector.workflow.WorkflowGateRegistrationTest"]>`).
  즉시 `config/quality/gate-tests.properties` 원복 → 같은 명령 재실행 → `BUILD SUCCESSFUL`.
  (조작·원복 전 과정 실측, 운영 파일은 최종적으로 등록된 상태로 남음 — `git status`로 확인.)

### F-4 재현·수정 확인

- 재현(수정 전): notice 임베딩이 `Unavailable`이어도 `embedPairStep`이 profile
  임베딩까지 호출(`embed.requestsSeen.size == 2`).
- 수정: `embedPairStep`이 notice 응답을 먼저 검사해 `Unavailable`이면 즉시
  `halt`, profile 은 호출하지 않는다.
- 수정 뒤: 신설 test("notice 임베딩이 이미 Unavailable 이면 profile 은 호출하지
  않는다") — `embed.requestsSeen.size shouldBe 1`. cmd:
  `./gradlew --no-daemon :workflow:test --tests '*OpportunityAnalysisTest*'` → exit 0.

### 재검증 — 전체 재실행(수정 라운드 뒤)

- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.evaluation.*' --tests '*WorkflowGateRegistrationTest*'`
  → exit 0.
- cmd: `./gradlew --no-build-cache --no-daemon clean check` → exit 0(346 tasks, 321
  executed) — S-1 전건 재확인.
- cmd: `git worktree add --detach /tmp/bv-4b6b-s0-r1 HEAD && (cd /tmp/bv-4b6b-s0-r1 &&
  ./gradlew --no-build-cache --no-daemon clean check)` → exit 0(355 tasks, 355
  executed) — S-0 재확인, worktree 제거 확인.
- cmd: `grep -rniE -f config/quality/leak-patterns.txt workflow/src/main/kotlin/bidvector/workflow/evaluation`
  → exit 1(매치 0) — S-7 재확인.

## 알려진 제한(하드코드 아님, checklist.md 「알려진 제한」 참고)

- F-2 인계(4B-6a): `EmbedTextRequest.text` 상한 단위 — `GrpcEmbeddingGateway`/
  `EmbeddingRequestMapping`(편집하지 않고 읽기만) 확인 결과 클라이언트 쪽에
  길이 검사가 없다(값만 그대로 proto에 싣는다) — 서버 쪽 `embedding.text.max-chars`
  단위(코드포인트 vs UTF-16)는 이 slice가 확인할 수 없다. `OPPORTUNITY_POLICY
  .textMaxChars`(4000)와 계약 값이 이미 같은 것으로 기존 4B-6a 테스트가 고정돼
  있어, 실질적 위험은 서로게이트 쌍(BMP 밖 문자)이 낀 텍스트뿐 — checklist.md
  참고.
- `adapters` 모듈도 verifier r1 F-2 와 같은 사각(`gate-tests.properties` 미선언
  입력)을 가질 수 있으나 이 slice 는 `adapters` 를 편집하지 않는다(out_of_scope) —
  checklist.md 「알려진 제한」 6 참고.
