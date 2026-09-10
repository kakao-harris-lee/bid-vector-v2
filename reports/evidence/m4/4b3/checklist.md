# checklist.md — M4/4B-3 리뷰 준비도

## 리뷰 요청 조건 (evidence-pack 스킬 기준)

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 경로>` 결과 없음(커밋 뒤 확인, clean-tree 게이트).
- [x] scope.md의 acceptance_commands(S-0~S-6) 전부 exit 0으로 commands.md에 기록됨.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `clean check` 전건(ktlint·detekt·cpd·sizeGate·moduleDependencyGate·gateExecutionGate·qualityBaseline·contractGate 포함) GREEN, 3차 시도에서 통과(ktlint import 순서·함수 50줄 한도 수정 뒤).
- [x] 변경된 fixture와 정책 version의 근거가 기록됨 — 신설 fixture 없음(golden-manifest.json N/A). 정책 값 변경 없음(differential.json N/A — Python/V2 동작 차이 판정 대상 없음, 이 slice는 배선만).
- [x] 알려진 제한과 rollback 방법이 기록됨(아래 「알려진 제한」·`rollback.md`).
- [x] 인증값 노출 스캔 통과 — commands.md 「인증값 노출 스캔」 절, 매치 0.

## 설계 검토 (4) 우회 후보 ↔ 코드 대응 ↔ 실측

| # | 우회 | 코드 대응 | 실측 근거 |
| --- | --- | --- | --- |
| 1 | `Unavailable`에서 알림이 나감 | `EvaluateCandidatesUseCase.reach`의 `if (verdict is Verdict.BidNow)` — `Review`는 알림 0(무변경, 이 slice가 만들지 않은 기존 게이트) | `EvaluateCandidatesUseCaseTest.ML 미가용이면 최소치 설정과 무관하게 Reached(Review(MlUnavailable)) 로 남고 알림은 0건이다` — `notifications.requested.shouldBeEmpty()`. `UnavailableMlAnalysisTest`가 실 adapter 배선으로 같은 사실을 재확인 |
| 2 | 사유가 `ScoreNotProvided`로 접힘 | `LadderInput.mlUnavailableReason` 슬롯 → `VerdictLadder.judge`의 `priorityScore == null` 분기가 그대로 실음(`mlUnavailable(input.mlUnavailableReason)`) | `VerdictLadderTest.④ ML 부재 — mlUnavailableReason 슬롯 값이 사다리까지 그대로 전달된다`(11값 표) + `EvaluateCandidatesUseCaseTest.ML 미가용 사유는 어댑터가 실은 값 그대로 사다리까지 전달된다`(`DeadlineExceeded` 왕복) |
| 3 | `Unavailable`이 `scoreThresholdDrop`으로 새어 「최소치 미달 드롭」이 됨 | `analyzeAndJudge`의 `when` 가지가 `Unavailable`을 `scoreThresholdDrop` 호출 없이 곧장 `reach`로 보냄 | `EvaluateCandidatesUseCaseTest.ML 미가용이면 최소치 설정과 무관하게...` — `minimumMatchScore`·`minimumProbabilityScore` 둘 다 설정된 전략에서도 `Reached(Review)`(`NotReached(ScoreThreshold)` 아님) |
| 4 | 실 배선이 test fake 뿐 | `adapters/ml/UnavailableMlAnalysis`가 main 소스셋에 존재, `MlAnalysisPort` 실 구현 | `UnavailableMlAnalysisTest`가 이 adapter로 **실 `EvaluateCandidatesUseCase`**(public 보조 생성자, `judge`는 항상 `VerdictLadder::judge`)를 돌려 `Reached(Review(MlUnavailable(ScoreNotProvided)))` + 알림 0건을 증명 |
| 5 | 동기 port로 되돌아감(취소 전파 단절) | `MlAnalysisPort.analyze`·`EvaluateCandidatesUseCase.evaluate`/`evaluateOne`/`analyzeAndJudge` 시그니처가 `suspend` | 컴파일 층 — `analyze`를 non-suspend로 override 시도하면 `Suspend function ... cannot override non-suspend function` 컴파일 실패(RED 단계에서 정확히 이 형태로 실측, commands.md) |
| 6 | `MlUnavailableReason` 새 값이 `judge`에서 빠짐 | `judge`는 값을 그대로 옮길 뿐 `mlUnavailableReason`에 대해 분기하지 않는다(소진 `when` 불필요 — 4D-1 `MlUnavailableReasonTest`가 sealed 소진을 별도로 지킨다) | `VerdictLadderTest`의 11값 표(4D-1이 정의한 열 값 전부를 손으로 나열해 전달 경로 확인) |

## 값 획득·위조 축 표

| 타입 | 생성 | 판정 | 근거 |
| --- | --- | --- | --- |
| `MlAnalysisOutcome.Unavailable` | public | 연다 | 미가용 위조는 fail-safe 방향이라 위험 없음(4D-1·설계 검토 (2)와 같은 판단) |
| `LadderInput.mlUnavailableReason` | public(기본값 있음) | 연다 | 입력값, 기본값 `ScoreNotProvided`가 기존 호출부 무변경을 보장 |
| `ReviewReason.MlUnavailable` | `internal`(4B-1, 무변경) | 닫힌 채 | 유일한 생성 경로는 `VerdictLadder.judge` — 이 slice가 그 경계를 바꾸지 않았다 |

## 기존 test 무변경 확인

- `EvaluateCandidatesUseCaseTest`·`EvaluateCandidatesUseCaseIsolationTest`의 기존 test 12건은
  `useCase.evaluate()` 호출을 `runBlocking { }`으로 감싼 것 외 단언·입력 무변경(diff 확인).
- `VerdictLadderTest`의 기존 9 test는 `LadderInput` 생성부에서 `mlUnavailableReason` 인자를
  넘기지 않아(기본값 사용) 무변경.
- `app` corpus conformance 실행자(`app/src/test/kotlin/bidvector/app/conformance/**`)는
  `EvaluateCandidatesUseCase`를 아직 배선하지 않아(grep 결과 0건, preflight 확인) 이 slice의
  suspend 화가 `app` 컴파일에 영향을 주지 않는다 — S-3(`*Conformance*`) 전건 통과로 재확인.

## 알려진 제한

1. **실 점수 provider 부재(2E·M5 진행 중)** — `UnavailableMlAnalysis`가 provider 부재 기간에
   앱이 배선해야 할 `MlAnalysisPort`의 유일한 production 구현이다(verifier r1 L-2 정정 —
   「유일한 실 배선」이 「앱이 이미 이 경로로 돈다」로 읽히지 않도록 문면을 좁혔다. 조립
   루트(`EvaluateCandidatesUseCase`에 실제로 꽂는 자리)는 이 slice 범위 밖 — M6/`app`
   소관). `OPEN-4D-LADDER-SCORE-SOURCE` 결정 (a)의 임시 상태 (c)를 실물화한 것으로,
   2E·M5·4D-2가 실 gateway로 교체한다(scope.md OPEN 절).
2. **후보 순회는 순차(sequential)다** — `evaluate()`의 `mapIndexed`는 병렬화하지 않는다.
   4B-2가 이미 내린 결정을 그대로 유지(scope.md out_of_scope).
3. **예산(`CallBudget`) 인자 없음** — `MlAnalysisPort.analyze`는 `correlationId`만 받는다.
   deadline·재시도 예산은 4D-2가 실 gateway를 배선할 때 port 시그니처를 다시 정한다.
4. **`ladderInputFor` 헬퍼로 클래스 함수 수가 10→11** — detekt `TooManyFunctions` 기본
   한도(11)에 닿았다. 다음 slice가 이 클래스에 로직을 더하려면 파일 분리를 먼저 검토해야
   한다.

## milestone-4.md 종결 문단

착수 계약은 scope.md에 반영했다(운영자 결정 2026-09-10 D-2(c)·D-6(b) 인용). **4B 절 종결
문단은 사용자 승인 시점에 추가한다**(scope.md in_scope 목록의 「4B 절 종결 문단(승인
시점)」 — 4D-1·4E 관례와 동일, 아직 사용자 승인 전이라 이 slice(구현 레인)에서는
작성하지 않는다).
