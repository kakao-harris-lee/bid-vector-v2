# commands.md — M4/4B-2

명령과 종료 코드만 남긴다(출력 전문 금지, evidence-pack 스킬 규격). 감사자는 명령을 다시 돌린다.

## 2026-09-09T14:00Z — 구현 + RED→GREEN(컴파일 단위)
- 값 타입·port·use case를 `workflow/src/main/kotlin/bidvector/workflow/evaluation/**`에
  신설(`EvaluationStage`·`EvaluationDropReason`·`CandidateEvaluation`·`Ports.kt`·
  `LadderPolicySlot`·`EvaluateCandidatesUseCase`).
- cmd: `./gradlew --no-daemon :workflow:compileKotlin` — exit 0(1차 시도 성공).
- test(`EvaluateCandidatesUseCaseTest`·`EvaluateCandidatesUseCaseIsolationTest`) 14건 신설.
- cmd: `./gradlew --no-daemon :workflow:compileTestKotlin` — exit 0.
- cmd: `./gradlew --no-daemon :workflow:test --tests '*EvaluateCandidatesUseCaseTest*'`
  — exit 0(14 tests, 0 failed, 1차 시도 성공).

## 2026-09-09T14:05Z — 결정 4·10 회귀 보호 mutation 실측(진짜 RED 확인)
- `evaluate()`의 `strategies.load()`·`capacity.snapshot()`를 `mapIndexed` 루프 **안**으로
  옮기는 mutation을 심었다(전략·용량을 후보마다 다시 읽음).
- cmd: `./gradlew --no-daemon :workflow:test --tests '*EvaluateCandidatesUseCaseTest*'`
  — exit 1 — **정확히 2건 실패**(`전략은 후보가 여럿이어도 진입에서 한 번만 읽는다`:
  expected 1 but was 3, `용량은 후보가 여럿이어도 run당 한 번만 읽는다`: expected 1 but
  was 2). 나머지 12건은 그대로 통과 — mutation이 의도한 두 test만 정확히 잡음을 확인.
- mutation 되돌림 — `diff`로 원본과 byte-identical 복원 확인.
- cmd: `./gradlew --no-daemon :workflow:test --tests '*EvaluateCandidatesUseCaseTest*'`
  — exit 0(14 tests, 0 failed, 복원 확인).

## 2026-09-09T14:08Z — S-3b(`CompositionBoundaryTest`) 신설 — RED→GREEN 실측
- `CompositionBoundaryTest.kt` 신설(4A `EditSessionImportBoundaryTest` 골격 재사용, allow-list
  를 이 slice가 실제로 쓰는 도메인 모듈로 확장).
- cmd: `./gradlew --no-daemon :workflow:test --tests '*CompositionBoundaryTest*'` — **exit 1**
  (1건 실패) — `EvaluationDropReason.kt` KDoc의 「`strategy.MatchScore`」·「`strategy.Score`」
  (전체 패키지 경로 없이 줄인 표기)가 술어에 걸림(`"strategy"`가 allow-list에 없는 루트로
  판정 — allow-list는 `"bidvector.strategy"`만 허용). **진짜 RED**(문서·코드 스캔이 실제로
  뭔가를 잡았다는 증거).
- 수정: KDoc을 `bidvector.strategy.MatchScore`·`bidvector.strategy.Score`로 완전정규화.
- cmd: `./gradlew --no-daemon :workflow:test --tests '*CompositionBoundaryTest*'` — exit 0
  (4 tests, 0 failed).

## 2026-09-09T14:15Z — 값 획득 축 실측(설계 검토 (2)) — 닫는다·연다·경계로 처리 전부
- probe는 항상 `app` 모듈(다른 모듈)에 심었다(4B-1에서 자기 모듈에 심어 한 번 놓친 교훈).
- **FORGERY-1**(닫는다) — `CandidateEvaluation.NotReached(...)`를 `app` test에서 직접
  생성: cmd `./gradlew --no-daemon :app:compileTestKotlin` — exit 1 —
  `Cannot access 'constructor(...): CandidateEvaluation.NotReached': it is internal in
  'bidvector.workflow.evaluation.CandidateEvaluation.NotReached'`.
- **FORGERY-2**(닫는다) — `CandidateEvaluation.Reached(...)` 직접 생성: exit 1 —
  `Cannot access 'constructor(...): CandidateEvaluation.Reached': it is internal in
  'bidvector.workflow.evaluation.CandidateEvaluation.Reached'`.
- **FORGERY-3**(닫는다) — `NotificationRequest(...)` 직접 생성: exit 1 —
  `Cannot access 'constructor(...): NotificationRequest': it is internal in
  'bidvector.workflow.evaluation.NotificationRequest'`(FORGERY-2와 같은 컴파일에서
  둘 다 확인 — `grep "^e:"`로 두 에러 라인 모두 캡처).
- **OPEN-1**(연다) — `CorrelationId("app-module-value")`를 `app` 모듈에서 직접 생성:
  cmd `./gradlew --no-daemon :app:compileTestKotlin` — exit 0(컴파일 성공).
- **OPEN-2**(연다) — `EvaluateCandidatesUseCase(...)`를 `app` 모듈에서 port 여덟 개를
  자기 구현으로 채워 조립: exit 0. use case 자체를 얻는 것은 권한이 아님을 확인 —
  조립된 use case가 낼 수 있는 부작용은 주입한 port 구현이 허락하는 것뿐이다.
- **OPEN-3**(경계로 처리) — `NotificationRequestPort` 구현을 `app` 모듈에 심어 이미
  만들어진 `NotificationRequest` 값을 **받기만** 하는 형태로 컴파일: exit 0. 그 값을
  스스로 지어내는 경로는 FORGERY-3이 이미 닫았으므로 「경계 안 주체에게만 간다」가
  성립함을 실측으로 확인(4C-1 L-4 판정 형식과 같음 — well-formed 소비이지 위조가 아니다).
- probe 파일 삭제(`rm -rf app/src/test/kotlin/bidvector/app/verifyprobe`), 커밋하지
  않음(evidence-pack 「자기 검사 하네스 금지」 — probe 자체가 산출물이 아니라 명령
  기록이다). 삭제 후 `git status --porcelain` — 공백(clean) 재확인.

## 2026-09-09T14:20Z — 파일 크기·품질 게이트 위반 시정
- cmd: `./gradlew --no-build-cache clean check` — **exit 1**(1차) —
  `:workflow:detekt`(`CyclomaticComplexMethod` `evaluateOne` 22>14, `ReturnCount`
  `evaluateOne` 10>2, `MatchingDeclarationName` `EvaluationPolicyData.kt`) +
  `:workflow:sizeGate`(`EvaluateCandidatesUseCaseTest.kt` 543줄 > 500줄 한도).
- 수정: `evaluateOne`을 guard 함수 체인 여덟 개로 분해(4A `apply`·4B-1
  `VerdictLadder.judge` 관례, 각 함수 ReturnCount ≤2) · `EvaluationPolicyData.kt` →
  `LadderPolicySlot.kt`로 파일명 정정(단일 top-level 선언과 일치) · test 파일을 셋으로
  분할(`EvaluateCandidatesUseCaseTest`·`EvaluateCandidatesUseCaseIsolationTest`·공유
  fixture는 `EvaluationTestFixtures.kt`, 전부 `internal` 가시성).
- cmd: `./gradlew --no-daemon :workflow:ktlintFormat` — exit 0(자동 정렬, 함수명이 너무
  긴 test 이름 하나는 수동 축약).
- cmd: `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin
  :workflow:test :workflow:ktlintCheck :workflow:detekt :workflow:cpdCheck
  :workflow:sizeGate :workflow:moduleDependencyGate` — exit 0(전부 통과, 18 tests 0
  failed — 분할 전과 같은 총 테스트 수).

## 2026-09-09T14:30Z — acceptance S-1~S-6(S-0 제외, 커밋 전)
- cmd: `./gradlew --no-build-cache clean check`(S-1) — exit 0.
- cmd: `./gradlew --no-daemon :workflow:test`(S-2) — exit 0(18 tests: `CompositionBoundaryTest`
  4·`EvaluateCandidatesUseCaseIsolationTest` 6·`EvaluateCandidatesUseCaseTest` 8, 기존
  4A/4C-1 test 57건 무영향).
- cmd: `./gradlew --no-daemon :workflow:moduleDependencyGate :workflow:sizeGate
  :workflow:cpdCheck`(S-3) — exit 0.
- cmd: `./gradlew --no-daemon :workflow:test --tests '*CompositionBoundaryTest*'`(S-3b)
  — exit 0.
- cmd: `./gradlew --no-daemon :app:test`(S-4, 필터 없이 전건 — S-6과 별도 호출, 4C-1
  L-7) — exit 0 — `SharedKernelCorpusConformanceTest tests="86" failures="0"`(이
  slice는 corpus를 신설하지 않아 무변화 — 4B-1 승격 상태 그대로).
- cmd: `./gradlew --no-daemon qualityBaseline`(S-5) — exit 0.
- cmd: `./gradlew --no-daemon :app:gateExecutionGate`(S-6, S-4와 별도 호출) — exit 0.

## 2026-09-09T14:35Z — 승인 문서 편집(capability-map.md·milestone-4.md) 뒤 재확인 + secret 스캔
- `capability-map.md`: `OPEN-4B1-OFF-LADDER-DROPS` 종결 표시 + `OPEN-4B2-1`·`-2`·`-4`·
  `-5`·`-6` 다섯 등재(`-3`은 설계로 닫혀 등재하지 않음, checklist.md 근거).
- `milestone-4.md` 「### Slice 4B」에 「4B-2 구현 2026-09-09」 문단 신설 — 산출·
  `OPEN-4B1-03` 답(밖이다)·값 획득 축 요약·`OPEN-4B2-*` 등재 상황.
- cmd: `python3 -c "import json; json.load(open('reports/evidence/m4/4b2/golden-manifest.json'));
  json.load(open('reports/evidence/m4/4b2/differential.json'))"` — exit 0(JSON 유효성).
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"
  workflow/src/main/kotlin/bidvector/workflow/evaluation/
  workflow/src/test/kotlin/bidvector/workflow/evaluation/ reports/evidence/m4/4b2/
  config/quality/gate-tests.properties` — exit 0(매치 1건, checklist.md의 「secret 스캔
  통과」 서술 자기참조, 실 비밀값 0건).
- cmd: `git status --porcelain` — `config/quality/gate-tests.properties`·
  `docs/discovery/capability-map.md`·`milestone-4.md`·`workflow/src/{main,test}/.../evaluation/**`·
  `reports/evidence/m4/4b2/**`만, scope.md in_scope와 정확히 일치.

## 2026-09-09T14:40Z — 커밋(head `a0d254fe71006ab3b84cbd5ee825bb8a7e09e97e`) + S-0
- cmd: `git add <in_scope 경로 6개 개별 인자> && git commit ... -- <같은 경로>` — exit 0.
  18 files changed(1615 insertions·1 deletion).
- cmd: `d=$(mktemp -d) && git clone --quiet --no-hardlinks --branch m4/2026-09-08
  --single-branch . "$d/repo" && (cd "$d/repo" && git checkout --quiet a0d254f &&
  ./gradlew --no-build-cache clean check)` — exit 0 — `BUILD SUCCESSFUL in 52s`, 353
  actionable tasks 전부 executed(캐시 없는 임시 clone).
- clone 삭제(`rm -rf`), 원 worktree엔 영향 없음.

## 2026-09-10T09:00Z — 수정 라운드 1(재작업 1/5) — M-1·M-2·L-1·L-2·L-3·L-4
- verifier 판정 `ready-for-review`(`_workspace/m4-4b2/03_verifier_report.md`), 산출물층
  blocker/high 0.
- **M-1**(운영자 결정 2026-09-10): `EvaluateCandidatesUseCase`의 `watchGateDrop`에서
  `WatchVerdict.NoGate`를 `NotReached(WatchGate, WatchGateNotConfigured)`로 되돌림
  (`EvaluationDropReason.WatchGateNotConfigured` 신설). 함수당 50줄 한도 초과
  (`watchGateDrop` 56줄) → `watchVerdictDrop`을 top-level 함수로 분리.
- **M-2**: `MlAnalysisPort.analyze(notice: Notice)` →
  `analyze(notice: Notice, correlationId: CorrelationId)`.
- **L-1**: `EvaluateCandidatesUseCase`에 `judge` 위임 생성자 인자(기본값
  `VerdictLadder::judge`) 신설. test는 `CountingJudge`(얇은 래퍼)로 대체해 호출
  횟수를 센다.
- fixture 정정: `testStrategy()` 기본 draft에 `focusCategories = listOf
  (DEFAULT_FOCUS_CATEGORY)` 추가(M-1로 `NoGate`가 더 이상 통과가 아니라, 다른 단계를
  재는 test가 감시 게이트를 먼저 지나야 한다) + `MATCHING_SUBJECT` 신설
  (`EvaluationTestFixtures.kt`). 영향받은 기존 test 셋(`사다리 임계가 미설정이면...`·
  `적합도가 운영자 최소치 미만이면...`·`한 후보의 실패가...`)의 전략/subject
  구성만 조정 — 그 test들이 재는 stage/reason 기대값은 무변경.
- 신설 test 셋: `감시 규칙이 미설정이면 WatchGate 에서 멈추고 NoGate 를 그대로 싣는다`
  (M-1)·`사다리 호출은 공고당 정확히 한 번 돈다`(L-1)·`correlationId 는 ML 분석
  port 에도 실린다`(M-2). 기존 `판정은 공고당 정확히 한 번 돈다`는
  `ML 분석은 공고당 정확히 한 번 불린다`로 이름 정정(그 test가 실제로 재는 것은 ML
  분석 호출 횟수이지 사다리 호출 횟수가 아니었다).

- cmd: `./gradlew --no-daemon :workflow:compileKotlin` — exit 0(M-1/M-2/L-1 반영 직후).
- cmd: `./gradlew --no-daemon :workflow:compileTestKotlin` — exit 1(1차) →
  `FakeMlAnalysisPort`가 새 `analyze` 시그니처를 구현하지 않음. 수정(correlationId 파라미터
  추가 + `correlationIdSeenFor` 기록) 후 exit 0.
- cmd: `./gradlew --no-daemon :workflow:test` — exit 1(1차, 10 tests 실패) — `testStrategy()`
  기본 draft가 watch 미설정이라 M-1 반영 후 기존 test 다수가 (의도와 다르게) WatchGate에서
  멈춤. fixture 수정(`focusCategories`+`MATCHING_SUBJECT`) 후 재실행 — exit 1(3 tests,
  `테스트가 직접 만든 미설정 draft` 케이스들) → 그 test들에 `focusCategories`를 개별
  추가해 감시 게이트를 통과시킨 뒤 exit 0(75 tests, 0 failed).
- **L-1 mutation 실측**: `reach` 안에서 `judge(...)`를 두 번 부르게 심었다 —
  cmd `./gradlew --no-daemon :workflow:test --tests '*EvaluateCandidatesUseCaseIsolationTest*'`
  — exit 1 — **정확히 1건**(`사다리 호출은 공고당 정확히 한 번 돈다`,
  `expected:<1> but was:<2>`) 실패, 나머지 7건(ML 분석 계수 test 포함)은 그대로
  초록 — verifier가 지적한 사각(judge 이중 호출을 ML 분석 계수로는 못 잡음)이 이제
  닫혔음을 실측 확인. mutation 되돌림 — `diff`로 원본과 byte-identical 확인.
- cmd: `./gradlew --no-daemon :workflow:ktlintFormat` — exit 0.
- cmd: `./gradlew --no-build-cache clean check` — **exit 1**(1차) — `:workflow:sizeGate`
  (`watchGateDrop` 56줄 > 50줄 한도). 수정: `watchVerdictDrop`을 별도 함수로 분리(클래스
  메서드로 추가하면 detekt `TooManyFunctions`(11 초과)에 걸려 `notReached`·
  `watchVerdictDrop` 둘 다 top-level 함수로 뺐다 — 인스턴스 상태가 필요 없어서다).
- cmd: `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin
  :workflow:test :workflow:sizeGate :workflow:detekt :workflow:cpdCheck
  :workflow:ktlintCheck :workflow:moduleDependencyGate` — exit 0(21 tests: `Composition
  BoundaryTest` 4·`EvaluateCandidatesUseCaseIsolationTest` 8·`EvaluateCandidatesUseCaseTest`
  9, 기존 4A/4C-1 test 57건 무영향).

## 2026-09-10T09:20Z — acceptance S-1~S-6(재실행, S-4·S-6 별도 호출)
- cmd: `./gradlew --no-build-cache clean check`(S-1) — exit 0.
- cmd: `./gradlew --no-daemon :workflow:test`(S-2) — exit 0.
- cmd: `./gradlew --no-daemon :workflow:moduleDependencyGate :workflow:sizeGate
  :workflow:cpdCheck`(S-3) — exit 0.
- cmd: `./gradlew --no-daemon :workflow:test --tests '*CompositionBoundaryTest*'`(S-3b)
  — exit 0.
- cmd: `./gradlew --no-daemon :app:test`(S-4, 별도 호출) — exit 0 —
  `SharedKernelCorpusConformanceTest tests="86" failures="0"`(무변화).
- cmd: `./gradlew --no-daemon qualityBaseline`(S-5) — exit 0.
- cmd: `./gradlew --no-daemon :app:gateExecutionGate`(S-6, 별도 호출) — exit 0.

## 2026-09-10T09:30Z — secret 스캔 · clean-tree 게이트
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"
  workflow/src/main/kotlin/bidvector/workflow/evaluation/
  workflow/src/test/kotlin/bidvector/workflow/evaluation/ reports/evidence/m4/4b2/`
  — exit 0(매치 4건, 전부 이 라운드의 secret 스캔 자기참조 서술, 실 비밀값 0건).
- cmd: `git status --porcelain -- <in_scope 경로 8개 개별 인자>` — exit 0, 정확히 이
  8개 파일과 일치. 양성 대조(비파괴): `EvaluationStage.kt`에 개행 추가 → `M` 관측 →
  `sed -i '' -e '$ d'`로 추가한 줄만 절삭 → 재확인(비어있음, exit 0).

## 2026-09-10T09:40Z — 커밋(head `76b0ec59082e0e6903c6038f869597cbedd3cb5f`) + rollback 재실측
- cmd: `git add <in_scope 경로 8개> && git commit ... -- <같은 경로>` — exit 0.
  8 files changed(402 insertions·67 deletions).
- rollback 재실측(임시 clone, `76b0ec5` pin): `git log --oneline 401bc4d..HEAD --
  <공유 파일>`로 재확인 — `config/quality/gate-tests.properties`·
  `docs/discovery/capability-map.md`·`milestone-4.md` 셋 다 이 range에서
  **4B-2 자신의 커밋(`a0d254f`)만** 만졌다(이번 라운드 커밋 `76b0ec5`는 셋 다
  무접촉) — commit-hash 격리 불필요, base..HEAD로 안전.
- cmd: `git diff 401bc4d..HEAD -- <공유 파일 3개> | git apply -R`(각각) — exit 0(3건).
- cmd: `git restore --source=401bc4d --staged --worktree -- <신규 파일 16개(디렉터리
  단위)>` — exit 0.
- `git status --porcelain`: **D 16 · M 3**(목록과 일치).
- ① `gate.tests.workflow` 4A/4C-1 몫만 남고 `evaluation.*` 셋 사라짐. ②
  `capability-map.md`의 `OPEN-4B1-OFF-LADDER-DROPS` 미종결(`~~` 취소선 없음)로
  복귀, `OPEN-4B2-*` 다섯 부재. ③ 신규 디렉터리(`workflow/.../evaluation`·
  `reports/evidence/m4/4b2`) 잔여 파일 0건.
- cmd: `./gradlew --no-daemon :workflow:compileKotlin :app:compileTestKotlin` — exit 0.
- cmd: `./gradlew --no-daemon :workflow:test :app:test --tests '*Conformance*'` —
  exit 0. conformance = **86**(무변화, 4B-2는 fixture를 만지지 않는다). 다섯 확인
  전부 통과.
- clone 삭제(`rm -rf`), 원 worktree엔 영향 없음.
