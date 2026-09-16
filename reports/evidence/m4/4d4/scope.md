# Slice 계약 — M4 / 4D-4 · 판정 근거 캐리어 + 근거 문구 합성(ML-04 ② 사용자 가시 착지)

> **지위**: 운영자 결정 2026-09-16 「(A) 캐리어 + 문구 합성(초안 그대로, 추천) 으로 진행」 — 선택지 (B) 캐리어만 / (C) 6A 와 묶음은 기각.
> `OPEN-4D3-DIAGNOSTICS-RENDER`(4D-3 D-4D3-6, capability-map OPEN 표)를 닫는다. 세션 모델 단독 작성.
> Phase 2.5 설계 검토는 아래 「위협 모델 경계·우회·(2b)」 절(세션 모델, 타입 불변식 slice 라 필수).
> 레인: worktree `bid-vector-v2-m4e`, 브랜치 `m4-4d4/2026-09-16`, base = `origin/main`(PR #27 병합 뒤). 구현
> `kotlin-implementer`(sonnet) → `verifier`(opus). Kotlin `workflow` 모듈과 문서만 — `decision`·`adapters`·`contracts`·
> Python 무접촉. 다른 레인(M5 Python, `bid-vector-v2-m5e`/`m5c`)과 소스 겹침 0.

## 착수 조사 실측(2026-09-16 — 계약 경계의 근거)
| 사실 | 위치 | 귀결 |
| --- | --- | --- |
| `Predicted.diagnostics`(4D-3)를 읽는 main 코드가 0 — `predictedFacts` 는 `fitness`·`candidates.base` 만 읽고 `Pair<ScoreFact, ScoreFact>` 를 낸다 | `workflow/evaluation/PredictionFacts.kt` | 진단이 여기서 끊긴다 — 이 slice 의 본체 ① |
| `MlAnalysisOutcome.Analyzed(priorityScore, probabilityScore, matchedScore)` 셋뿐 · `NotificationRequest(noticeId, correlationId, verdict)` 셋뿐 | `workflow/evaluation/Ports.kt` | 근거 캐리어 자리가 없다 — 본체 ② |
| `CompetitionSampleSupply.Supplied.excluded`(4B-7 제외 사유 8종 계수)도 `OpportunityAnalysis` 안에서 버려진다 — milestone-4 가 D-4B7-9 를 「`Analyzed` 가 드롭 사유를 안 나름 — RENDER 이후 실효」로 등재 | `workflow/evaluation/OpportunityAnalysis.kt` `predictionFacts` | 같은 캐리어에 싣는다(D-4D4-7) |
| `decision` 모듈은 `shared-kernel` 만 의존(`LadderInput` KDoc) — `PredictionDiagnostics` 는 `workflow.prediction` 소속이라 `Verdict`·`BidNowReason` 안에 태울 수 없다 | `decision/LadderInput.kt` | 근거는 `decision` 을 **우회**해 `workflow` 안에서 나른다(D-4D4-1) — 1차 설계 제약 |
| 4E 는 port 만 세웠다 — `ContentRenderer.render(contentRef, channel): RenderedContent` 구현 0, `NotificationRequestPort` 구현 0, `NotificationRequest → NotificationIntent` 다리 0, `ContentRef(value: String)` 는 불투명 문자열, 본문 골든 0, 본문 `body` 단언 test 0 | `workflow/notification/*` · 4E scope D-4E-1·5 | 채널 본문 착지는 6A — 이 slice 는 6A 렌더러가 **호출할 순수 함수**까지(D-4D4-4·5) |
| enum → 문구 매핑이 저장소에 0. 규율: 「판정은 `ReasonCode` enum + 구조화 payload, 사람이 읽는 문장은 렌더링 시점에 생성하고 영속하지 않는다, 문장으로만 구분되는 판정을 만들지 않는다」 | `docs/discovery/data-dictionary.md` §3.1 | 캐리어(구조)가 정본, 문구는 파생(D-4D4-4) — 영속 편집 0 |
| ML-04 ② 문면: 「기관 표본이 임계 미만이면 수축 가중치가 응답 근거에 실린다」 · OPEN 행 문면: 「리뷰·알림 문구에 『기관 표본 n건 < 임계, 수축 가중치 w』」 | `docs/discovery/capability-map.md` §ML-04 · OPEN 표 | 종결 조건의 골든 두 줄(D-4D4-4) |
| 4D-3 out_of_scope 가 허락한 표면은 「4E 알림·리뷰 텍스트」 둘이고 「`Diagnostics` 를 이벤트/persistence 에 싣는 것」은 명시 배제. 리뷰 화면(NOTI-10 앱 알림함)은 M6/6A 라 M4 에 코드가 없다 — 오늘 닿는 표면은 알림 본문 하나 | `reports/evidence/m4/4d3/scope.md` out_of_scope | 문구 함수는 알림 본문용 순수 함수 하나, 이벤트·persistence 편집 0(우회 (8)) |
| 4E 정책 슬롯은 `environmentModes`·`maskedSuffixLength` 둘, 4B 슬롯(`OpportunityPolicyData`)에도 문구·노출 슬롯 없음 | `workflow/notification/NotificationDeliveryPolicyData.kt` · `workflow/evaluation/OpportunityPolicyData.kt` | 정책 슬롯 신설 0(D-4D4-6) — 임계값은 엔진 정책이라 문구에 숫자로 넣지 않는다 |
| `Analyzed(` 생성 자리: main 1(`OpportunityAnalysisPipeline.finalOutcomeOf`), test 3 파일(`EvaluateCandidatesUseCaseTest`·`EvaluateCandidatesUseCaseIsolationTest`·`EvaluationTestFixtures`) · `NotificationRequest(` 생성 자리: main 1(`EvaluateCandidatesUseCase.reach`) | `git grep` | 필수 인자 추가의 파급 전수 |

```yaml
milestone: M4
slice: 4d4-prediction-evidence-and-lines
base_sha: 44721cf61ba9275f604918890c93f8744b5e2cc1
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionEvidence.kt        # 신설 — sealed 캐리어(D-4D4-1)
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt           # predictedFacts·absentPair 반환형 → PredictionComponents(내부)
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysis.kt       # predictionFacts·composeOutcome 이 evidence 를 나른다
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisPipeline.kt  # finalOutcomeOf(…, evidence)
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/Ports.kt                     # Analyzed.evidence · NotificationRequest.evidence(둘 다 필수 인자)
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCase.kt # reach(…, evidence) — Unavailable 가지는 NotPredicted(reason)
  - workflow/src/main/kotlin/bidvector/workflow/notification/EvidenceLines.kt           # 신설 — 순수 문구 합성(D-4D4-4)
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCaseTest.kt
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluateCandidatesUseCaseIsolationTest.kt
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/EvaluationTestFixtures.kt
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisTest.kt
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/PredictionFactsTest.kt
  - workflow/src/test/kotlin/bidvector/workflow/notification/EvidenceLinesTest.kt       # 신설 — 골든·전수(exhaustive)·Locale 무접촉
  - config/quality/gate-tests.properties                                                # gate.tests.workflow 에 EvidenceLinesTest 등재만(다른 키 무편집)
  - milestone-4.md                                                                      # 4D-4 착수·종결 문단(팀장)
  - docs/discovery/capability-map.md                                                    # OPEN 표: OPEN-4D3-DIAGNOSTICS-RENDER 닫힘, 신설 OPEN 등재(팀장)
  - reports/evidence/m4/4d4/**
out_of_scope:
  - decision/** · adapters/** · contracts/** · ml-engine/**                             # 진단은 decision 을 우회, 어댑터·wire 무변경
  - workflow/src/main/kotlin/bidvector/workflow/notification/{Ports,DeliveryRequest,RenderedContent,DispatchNotification}.kt  # 4E port·타입 무변경 — ContentRef 로 근거를 나르는 방식은 6A 결정(OPEN-4D4-CONTENT-REF)
  - NotificationRequest → NotificationIntent 다리 · ContentRenderer/NotificationSender 구현 · 앱 알림함 기록(NOTI-10) · outbox 등록  # 6A/M6
  - Review 판정의 근거 노출(NotificationRequest 는 BidNow 만) · Reached 반환값에 근거 싣기            # OPEN-4D4-REVIEW-EVIDENCE
  - 사다리 점수·LadderInput·Verdict 산식(근거는 판정을 바꾸지 않는다) · 임계 재판정(엔진 정책) · 정책 슬롯 신설 · 문구 영속
  - workflow/src/main/kotlin/bidvector/workflow/prediction/**                            # 4D-3 산출물 소비만
acceptance_commands:
  - ./gradlew --no-build-cache --no-daemon clean check
rollback: in_scope 경로 한정 restore(rollback.md) — 공유 파일(milestone-4.md·capability-map.md·gate-tests.properties)은 hunk 격리
```

## 결정
| ID | 결정 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-4D4-1** | 근거 캐리어 `PredictionEvidence`(sealed, `workflow.evaluation`) 신설: `Diagnosed(diagnostics: PredictionDiagnostics, release: ModelReleaseRef, excludedSamples: Map<SampleExclusionReason, Int>)` · `NotPredicted(reason: MlUnavailableReason)`. 예측을 시도하지 않았거나(기초금액 없음·표본 공급 Unavailable) 시도가 `Unavailable`·`Unmeasurable`·`ContractViolation` 으로 끝난 경우가 전부 `NotPredicted` — 「분석됐는데 근거가 없다」는 상태가 없다 | `decision` 우회 제약 · data-dictionary §3.1(구조가 정본) | 계약 |
| **D-4D4-2** | `predictedFacts`·`absentPair`·`absentPairForUnavailableSupply` 의 반환형을 `Pair` 에서 `internal data class PredictionComponents(budgetCapture, expectedMargin, evidence)` 로. `MlAnalysisOutcome.Analyzed` 에 `evidence: PredictionEvidence` **필수 인자**(기본값 없음) · `finalOutcomeOf(priorityOutcome, matchScore, evidence)` | 필수 인자 = 누락이 컴파일 거부(4D-3 `Predicted.diagnostics` 와 같은 처방) | 계약 |
| **D-4D4-3** | `NotificationRequest` 에 `evidence: PredictionEvidence` 필수 인자. `reach(…, evidence)` — `Analyzed` 가지는 `outcome.evidence`, `Unavailable` 가지는 `NotPredicted(outcome.reason)`. 생성자 `internal` 그대로 — 근거는 같은 `reach` 호출의 `outcome` 에서만 온다 | 4B-3 「알림 요청은 판정과 같은 자리」 | 계약 |
| **D-4D4-4** | 문구 합성은 `workflow.notification` 의 **순수 최상위 함수** `evidenceLinesFor(verdict: Verdict.BidNow, evidence: PredictionEvidence): List<String>` — 채널 무관, 한국어, 영속 없음, 부작용 없음. 줄 순서 고정: ① 판정 근거(`BidNowReason` 각 1줄) ② 예측 근거(`Diagnosed` 는 구간·학습 행·수축 가중치, `NotPredicted` 는 사유 1줄) ③ 표본 제외 요약(`excludedSamples` 비면 생략, 사유별 1줄). **골든 두 줄(ML-04 ② 착지)**: `agencySampleBelowThreshold` 참이면 `기관 표본 {n}건(임계 미만) · 수축 가중치 {w}`, 거짓이면 `기관 표본 {n}건 · 수축 가중치 {w}`. 숫자는 `BigDecimal.toPlainString()`·`Int.toString()` — `String.format`·Locale 금지(`ArchitectureGateTest` 축) | capability-map OPEN 행 문면 · data-dictionary §3.1 「렌더링 시점 생성」 | 계약 |
| **D-4D4-5** | 6A 가 `ContentRenderer` 구현에서 `evidenceLinesFor` 를 호출해 채널 본문에 싣는다. 이 slice 는 4E port·타입·`ContentRef` 를 편집하지 않는다 — 근거를 `ContentRef` 로 어떻게 참조할지(요청 저장 후 id 참조 vs 본문 직렬화)는 6A 결정 → **`OPEN-4D4-CONTENT-REF`** | 4E D-4E-1·5(adapters 편집 0, 렌더러 구현은 후속) | 계약 |
| **D-4D4-6** | 정책 슬롯 신설 0 · 노출 여부 게이트 0 — 모든 `NotificationRequest` 가 근거를 동반한다. 임계값은 엔진 정책이라 문구에 숫자로 넣지 않는다(4D-3 D-4D3-4 「Kotlin 은 임계를 재판정하지 않는다」의 문구 버전) | 정책 표 슬롯 없음(실측) | 계약 |
| **D-4D4-7** | D-4B7-9 실효: `Supplied.excluded` 는 `Diagnosed.excludedSamples` 로, `Unavailable.reason` 은 `NotPredicted(reason)` 으로 `Analyzed` 까지 나른다 — `PredictionFactsTest` 가 같은 패키지 직접 호출로만 재던 것을 `OpportunityAnalysisTest`(통합 층)에서도 잰다 | milestone-4 D-4B7-9 등재 문면 | 계약 |
| **D-4D4-8** | `CandidateEvaluation.Reached` 는 무변경. `Review` 판정의 근거 노출·앱 알림함 기록의 근거 동반은 **`OPEN-4D4-REVIEW-EVIDENCE`**(NOTI-01 「웹 알림 기록은 항상」의 기록 경로 자체가 아직 없다 — 6A 와 함께) | 범위 한정 | 계약 |

## 위협 모델 경계·우회·(2b) 값 획득 축(Phase 2.5, 세션 모델)
**방어하는 것**: 근거가 판정 경로 어딘가에서 **조용히 사라지는 것**(누락이 컴파일 거부), 근거가 **판정을 바꾸는 것**(구조적 불가), 문구가 **판정의 정본이 되는 것**(구조가 정본, 문구는 파생·비영속).
**방어하지 않는 것**: `MlAnalysisPort` 구현이 짓는 근거의 **참됨**(점수와 같은 층 — 4B-3 위협 모델 승계, 진단 위조는 4D-3 알려진 제한 승계) · 채널 본문에서의 **길이·마스킹·표현**(6A 렌더러 소관) · 임계값의 타당성(엔진 정책).

| # | 우회 | 닫힘 |
| --- | --- | --- |
| (1) | `Analyzed`·`NotificationRequest` 를 근거 없이 짓는다 | 필수 인자·기본값 없음 — 컴파일 거부(변이 실측: 인자 제거 시 main 1·test 3 파일 붉음) |
| (2) | 근거가 `LadderInput`·`Verdict` 에 스며 판정을 바꾼다 | `decision` 무접촉(모듈 의존이 `shared-kernel` 뿐) · test: 같은 점수·다른 근거 → 같은 `Verdict` |
| (3) | 다른 공고·다른 호출의 근거가 `NotificationRequest` 에 실린다 | `reach` 안에서 같은 `outcome` 의 값만 — 생성자 `internal`, 두 번째 생성 경로 없음(4B-2 결정 5 승계) |
| (4) | 문구가 결과 타입을 만든다(문장으로만 구분되는 판정) | `evidenceLinesFor` 는 `List<String>` 만 내고 어떤 sealed 결과도 만들지 않는다 · 입력이 sealed 라 `when` 전수 — 새 `MlUnavailableReason`·`SampleExclusionReason` 추가 시 컴파일 거부 |
| (5) | `Diagnosed` 가 `Supplied.excluded` 를 빼먹고 빈 맵을 넣는다 | `OpportunityAnalysisTest`: 4B-7 fixture 의 제외 계수가 `Analyzed.evidence` 에 그대로(D-4D4-7) |
| (6) | `BidNow` 가 `NotPredicted` 와 동반(예측 없이 priority 가 임계를 넘음 — 재정규화로 가능) | 불가능 상태가 아니라 정직한 상태 — 골든: 「예정가 분포 근거 없음: {사유}」 1줄. `Unavailable` 가지는 점수 셋 null 이라 `BidNow` 자체가 불가(기존 test) |
| (7) | `String.format`·`%.2f`·`Locale` 로 숫자를 찍는다 | `ArchitectureGateTest` Locale 누출 축(4B-8 실측) · `toPlainString()` 고정 |
| (8) | 문구를 이벤트·persistence 에 싣는다 | persistence·event 편집 0(out_of_scope) — `RenderedContent` 무변경 |

(2b) 값 획득 축 — 이번 slice 가 새로 public 으로 내놓는 것:
| 표면 | 밖에 허락하는 것 | 없던 권한인가 | 결과가 쓴 값을 나르는가 | 처분 |
| --- | --- | --- | --- | --- |
| `PredictionEvidence` sealed + `Diagnosed`·`NotPredicted`(public 생성자) | 값 짓기 | 아니오 — `Analyzed` 를 짓는 주체(port 구현)가 이미 점수를 짓는다 | 아니오(읽기 사실) | 닫지 않음(경계 밖) |
| `Analyzed.evidence`·`NotificationRequest.evidence` 프로퍼티 | 진단 읽기 | 아니오 — `Predicted.diagnostics` 가 이미 public | 아니오 | — |
| `evidenceLinesFor` 최상위 public 함수 | 문자열 생성 | 아니오(부작용 0) | 아니오 | — |
| `PredictionComponents` | — | `internal` | — | — |
| `object` 커널 주입 자리 | 해당 없음(신설 `object` 0) | — | — | — |
수정 라운드마다 이 표를 갱신한다.

## 종결 조건
`Analyzed.evidence` 가 `Predicted.diagnostics`·`Supplied.excluded` 와 등가(통합 층 test) · `NotificationRequest.evidence == Analyzed.evidence`(use case test) · 우회 (1)~(8) 각 실측 · `EvidenceLinesTest` 골든(두 줄 + `NotPredicted` 11 사유 전수 + `SampleExclusionReason` 8 전수 + 같은 점수·다른 근거 → 같은 verdict) · 전건 `check` · verifier `ready-for-review` · 사용자 승인. `OPEN-4D3-DIAGNOSTICS-RENDER` 닫힘, `OPEN-4D4-CONTENT-REF`·`OPEN-4D4-REVIEW-EVIDENCE` 등재(Phase 6 에스컬레이션).

## 하네스 레인 변경
없음(리뷰 요청 시점에 `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 로 재확인).

## 계약 갱신 이력
(없음)
