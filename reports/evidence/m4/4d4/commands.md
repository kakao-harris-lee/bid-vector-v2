# commands.md — M4 / 4D-4

## 2026-09-16T11:10:00Z
- cmd: `./gradlew --no-daemon :workflow:compileTestKotlin`
- exit: 1
- 핵심 결과: RED 확인 — `EvidenceLinesTest.kt` 가 아직 없는 `evidenceLinesFor` 를 불러 `Unresolved reference` 다수.

## 2026-09-16T11:20:00Z
- cmd: `./gradlew --no-daemon :workflow:compileKotlin`
- exit: 0
- 핵심 결과: 캐리어 배선(D-4D4-1·2·3·7) 반영 뒤 main 컴파일 통과.

## 2026-09-16T11:25:00Z
- cmd: `./gradlew --no-daemon :workflow:test --tests "bidvector.workflow.notification.EvidenceLinesTest" --tests "bidvector.workflow.evaluation.PredictionFactsTest" --tests "bidvector.workflow.evaluation.OpportunityAnalysisTest" --tests "bidvector.workflow.evaluation.EvaluateCandidatesUseCaseTest" --tests "bidvector.workflow.evaluation.EvaluateCandidatesUseCaseIsolationTest"`
- exit: 0
- 핵심 결과: 5개 표적 test class 전부 GREEN(9+11+27+12+8=67건, 첫 GREEN 전환).

## 2026-09-16T11:35:00Z
- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- exit: 1
- 핵심 결과: ktlint `MaxLineLength` 3건(`EvidenceLines.kt`·`OpportunityAnalysisTest.kt`·`PredictionFactsTest.kt`) — 수정 뒤 재실측.

## 2026-09-16T11:45:00Z
- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- exit: 1
- 핵심 결과: ktlint `standard:function-signature`(`PredictionFacts.kt`) · `standard:when-entry-bracing`(`EvidenceLines.kt`) 2건 — 수정 뒤 재실측.

## 2026-09-16T11:50:00Z
- cmd: `./gradlew --no-daemon :workflow:ktlintMainSourceSetCheck :workflow:ktlintTestSourceSetCheck :workflow:detekt`
- exit: 0
- 핵심 결과: ktlint·detekt 정합 확인(재실행으로 stale 리포트 배제).

## 2026-09-16T11:55:00Z
- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- exit: 1
- 핵심 결과: **아키텍처 경계 충돌 발견** — `NotificationBoundaryTest`(S-5, `workflow.notification` 패키지는 `bidvector.decision` 참조 거부)가 `EvidenceLines.kt`(당시 `workflow.notification` 소속, D-4D4-4 원안)의 `Verdict.BidNow`·`BidNowReason`·`MlUnavailableReason` 전수 `when` 을 걸러 FAIL(265 tests, 1 failed). 팀장 보고 → 계약 갱신 1(72ea556, `workflow.evaluation` 으로 재배치) 승인.

## 2026-09-16T12:05:00Z
- cmd: `git mv` (`EvidenceLines.kt`·`EvidenceLinesTest.kt` → `workflow.evaluation`) 뒤 `./gradlew --no-daemon :workflow:compileTestKotlin`
- exit: 0
- 핵심 결과: 재배치 뒤 컴파일 통과.

## 2026-09-16T12:08:00Z
- cmd: `git grep -n "bidvector\.decision" -- workflow/src/main/kotlin/bidvector/workflow/notification/ workflow/src/test/kotlin/bidvector/workflow/notification/`
- exit: 1
- 핵심 결과: 매치 0 — `workflow.notification` 소스에 `bidvector.decision` 참조 없음(계약 갱신 1 확인).

## 2026-09-16T12:08:30Z
- cmd: `git grep -n "bidvector\.workflow\.evaluation" -- workflow/src/main/kotlin/bidvector/workflow/notification/ workflow/src/test/kotlin/bidvector/workflow/notification/`
- exit: 1
- 핵심 결과: 매치 0 — `workflow.notification` 소스에 `bidvector.workflow.evaluation` 참조 없음.

## 2026-09-16T12:10:00Z
- cmd: `./gradlew --no-daemon :workflow:ktlintMainSourceSetCheck :workflow:ktlintTestSourceSetCheck :workflow:detekt :workflow:test --tests "bidvector.workflow.evaluation.EvidenceLinesTest" --tests "bidvector.workflow.notification.NotificationBoundaryTest"`
- exit: 0
- 핵심 결과: `EvidenceLinesTest` 9건·`NotificationBoundaryTest` 3건 GREEN, ktlint/detekt 통과.

## 2026-09-16T12:15:00Z
- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- exit: 0
- 핵심 결과: 재배치 뒤 첫 전건 GREEN(346 actionable tasks, 46s).

## 2026-09-16T12:25:00Z
- cmd: `./gradlew --no-daemon :workflow:test --tests "bidvector.workflow.evaluation.EvaluateCandidatesUseCaseTest"`
- exit: 0
- 핵심 결과: 우회 (2)·(3) 실측 test 2건 추가 뒤 14건 GREEN(12+2).

## 2026-09-16T12:26:00Z
- cmd: `./gradlew --no-daemon :workflow:ktlintTestSourceSetCheck :workflow:detekt`
- exit: 0

## 2026-09-16T12:30:00Z
- cmd: `./gradlew --no-build-cache --no-daemon clean check`
- exit: 0
- 핵심 결과: 종결 조건 실측 test 추가 뒤 전건 GREEN(346 actionable tasks, 45s). **acceptance_commands 충족.**

## 2026-09-16T12:40:00Z — 우회 (1) 변이 실측
- cmd: `Analyzed.evidence` 필드를 임시 제거한 뒤 `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin`
- exit: 1
- 핵심 결과: main 2 파일에서 즉시 컴파일 거부 — `EvaluateCandidatesUseCase.kt` 의 `analyzeAndJudge`(`outcome.evidence` 를 읽는 자리)가 `Unresolved reference 'evidence'`, `OpportunityAnalysisPipeline.kt` 의 `finalOutcomeOf`(`Analyzed` 생성 자리)가 `Too many arguments`. main 이 먼저 실패해 `compileTestKotlin` 자체가 진행되지 않는다(모듈 전체가 컴파일 거부, scope.md 사전 추정 「main 1·test 3」보다 이 slice 최종 구현이 만든 두 번째 main 참조점까지 반영된 강한 결과). 원복 뒤 `git diff` 빈 상태 확인. (verifier r1 V-3 정정 — 좌표 대신 함수명·오류 종류로 서술)

## 2026-09-16T12:42:00Z
- cmd: (원복 뒤) `./gradlew --no-daemon :workflow:compileTestKotlin`
- exit: 0
- 핵심 결과: UP-TO-DATE — 원복이 직전 GREEN 상태와 바이트 동일함을 확인.

## 2026-09-16T12:50:00Z — rollback 실측(임시 clone `/tmp/4d4-rollback-check`, `git clone --no-hardlinks`)
- cmd: `git diff 44721cf -- workflow/src/main/kotlin/bidvector/workflow/evaluation/ workflow/src/test/kotlin/bidvector/workflow/evaluation/ config/quality/gate-tests.properties | wc -l`(restore 뒤)
- exit: 0
- 핵심 결과: `0` — in_scope 경로가 base 와 바이트 동일.

## 2026-09-16T12:51:00Z
- cmd: `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin`(rollback ④)
- exit: 0

## 2026-09-16T12:52:00Z
- cmd: `./gradlew --no-daemon :workflow:test`(rollback ⑤)
- exit: 0

## 2026-09-16T12:53:00Z
- cmd: `./gradlew --no-build-cache --no-daemon clean check`(rollback ⑥, 되돌린 트리 전건)
- exit: 0
- 핵심 결과: 되돌린 트리(base `44721cf` 상태, evidence 디렉터리는 유지)에서도 전건 GREEN(355 actionable tasks, 57s) — rollback 이 게이트를 붉히지 않는다.

## 비밀값 스캔
- cmd: `grep -rniE -f config/quality/leak-patterns.txt workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionEvidence.kt workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysis.kt workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisPipeline.kt workflow/src/main/kotlin/bidvector/workflow/evaluation/Ports.kt workflow/src/main/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCase.kt workflow/src/main/kotlin/bidvector/workflow/evaluation/EvidenceLines.kt workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCaseTest.kt workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCaseIsolationTest.kt workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluationTestFixtures.kt workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt workflow/src/test/kotlin/bidvector/workflow/evaluation/PredictionFactsTest.kt workflow/src/test/kotlin/bidvector/workflow/evaluation/EvidenceLinesTest.kt config/quality/gate-tests.properties reports/evidence/m4/4d4/`
- exit: 1
- 핵심 결과: 매치 없음(패턴 무매치 = 통과). 육안 확인 — Telegram id·사업자 정보 없음.

## verifier r1 수정 라운드(V-1·V-2·장부층)

### 2026-09-16T21:10:00Z
- cmd: `./gradlew --no-daemon :workflow:test --tests "bidvector.workflow.evaluation.EvidenceLinesBoundaryTest"`(신설 직후)
- exit: 1
- 핵심 결과: `EvidenceLines.kt` KDoc 산문이 `String.format` 어휘를 그대로 인용해 새 술어가 자기매치(1건) — evidence 자기참조와 같은 함정. KDoc 을 간접 표현으로 재작성.

### 2026-09-16T21:12:00Z
- cmd: (재작성 뒤) `./gradlew --no-daemon :workflow:test --tests "bidvector.workflow.evaluation.EvidenceLinesBoundaryTest"`
- exit: 0

### 2026-09-16T21:15:00Z — 우회 (7) 변이 실측(verifier r1 V-1 재현 확인)
- cmd: `EvidenceLines.kt` 에 `import java.util.Locale` + `diagnosedLines` 의 `agencyCount` 를 `String.format(Locale.getDefault(), "%d", …)` 로 임시 치환한 뒤 `./gradlew --no-daemon :workflow:test --tests "bidvector.workflow.evaluation.EvidenceLinesBoundaryTest"`
- exit: 1
- 핵심 결과: RED 확인 — `EvidenceLines kt 소스에 Locale 서식 API 참조가 없다` 가 4건 매치로 실패. 원복 뒤 `git diff` 가 KDoc 정정분만 남음을 확인.

### 2026-09-16T21:20:00Z
- cmd: (원복 뒤) `./gradlew --no-daemon :workflow:test --tests "bidvector.workflow.evaluation.EvidenceLinesBoundaryTest" --tests "bidvector.workflow.evaluation.EvidenceLinesTest"`
- exit: 0

### 2026-09-16T21:25:00Z
- cmd: `./gradlew --no-daemon :workflow:test --tests "bidvector.workflow.evaluation.OpportunityAnalysisTest"`(V-2 evidence 단언 추가 뒤)
- exit: 0
- 핵심 결과: 27건 GREEN(개수 불변, 기존 두 test 확장).

### 2026-09-16T21:26:00Z
- cmd: `./gradlew --no-daemon :workflow:ktlintMainSourceSetCheck :workflow:ktlintTestSourceSetCheck :workflow:detekt`
- exit: 0

### 2026-09-16T21:35:00Z
- cmd: `./gradlew --no-build-cache --no-daemon clean check`(V-1·V-2·장부층 반영 뒤 재실측)
- exit: <아래 채움>
- 핵심 결과: <아래 채움>

### rollback 재실측(임시 clone, `git clone --no-hardlinks`)
- cmd: ①~⑥ 재실행(목록에 `EvidenceLinesBoundaryTest.kt` 추가 반영) — `rollback.md` 참고.
- exit: <아래 채움>

**마지막 HEAD 의 게이트 결과 정본은 evidence 가 아니라 verifier·PR 조치 코멘트다**(evidence-pack §「리뷰 요청 조건 점검」) — 이 문서는 그 직전까지의 명령만 담는다.
