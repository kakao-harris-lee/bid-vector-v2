# Slice 계약 — M4 / 4D-3 · `Diagnostics` 도메인 소비(2F wire 값 → Kotlin 도메인, fail-closed 형태 검증)

> **지위**: 운영자 결정 2026-09-16(「추천 방식으로 진행」 — 5D-3 다음 (a) M4 4D-1 후속 `OPEN-2F-DIAGNOSTICS-DOMAIN`).
> 2F(PR #16, 태그 `contracts/v1-approved-2026-09-16`) 가 wire 에 올린 `Diagnostics` 넷(`shrinkage_weight`·
> `excluded_observations`·`agency_sample_count`·`agency_sample_below_threshold`) + 기존 둘(`training_row_count`·
> `segment_support`)을 Kotlin 도메인 값으로 소비한다. 4D-1 gateway 의 `mapSuccess` 는 지금 `success.diagnostics` 를
> **읽지 않는다**(읽는 코드 0 — 계약 test 의 proto 보존 단언뿐). 세션 모델 단독 작성. Phase 2.5 설계 검토는
> `_workspace/m4-4d3/02_design-review.md`(세션 모델, 게이트 술어 변경이라 필수).
> 레인: worktree `bid-vector-v2-m4e`, 브랜치 `m4-4d3/2026-09-16`, base = `origin/main`(PR #18 5E-1 병합 뒤). 구현
> `kotlin-implementer`(sonnet) → `verifier`(opus). Kotlin(`workflow`·`adapters`)과 문서만 — 다른 레인(5E-2, Python)과
> 소스 겹침 0(공유 후보는 `milestone-5.md` 인데 이 slice 는 `milestone-4.md` 만 만진다).

## 착수 조사 실측(2026-09-16 — 계약 경계의 근거)
| 사실 | 위치 | 귀결 |
| --- | --- | --- |
| `mapSuccess` 가 `Predicted(candidates, fitness, uncertainty, release)` 만 만든다 — `diagnostics` 미참조 | `adapters/ml/ResponseMapping.kt` `mapSuccess` · `ParsedSuccessFields.kt` | 이 slice 의 본체 |
| 도메인 `Predicted` 에 진단 자리 없음 · `Weight` 류 [0,1] 타입 없음(`decision.UnitScore` 는 **점수** 타입) | `workflow/prediction/BidPredictionOutcome.kt` | 도메인 타입 신설(D-4D3-1) |
| 유일한 요청 조립부가 `agencyId = null`·`competitionSamples = emptyList()`·`baseAmountProvenanceLabel = Unknown` 으로 보낸다 | `workflow/evaluation/PredictionFacts.kt` `predictionRequestFor` | 분포 엔진(5D-2/5D-3)은 표본 0건이면 `Unmeasurable` — **Kotlin 요청은 아직 분포 엔진을 구동하지 못한다**. 표본 공급은 이 slice 밖(`OPEN-4D3-SAMPLE-SUPPLY`) |
| `Notice` 에 기관 fact 가 없다(`id·status·businessCategory·baseAmount·estimatedAmount·allocatedBudget·floorRate·deadlineAt`) | `procurement/NoticeFacts.kt` | 요청 축 `agency_id` 는 채울 원천이 없다 — `OPEN-2B-AGENCY-ID`(수집 축) 그대로 |
| 과거 표본 조회 port 없음 — `OpeningResultRepository.find(id)` 는 공고 단건(`winningRate`·`derivedBaseAmount`·예비가격 행) | `procurement/OpeningResultRepository.kt` · `adapters/persistence/JdbcOpeningResultRepository.kt` | 기관/공종별 이력 질의는 신설 port 가 필요 — `OPEN-4D3-SAMPLE-SUPPLY` |
| `Predicted` 의 유일 소비자는 `predictedFacts`(사다리 `ScoreFact` 둘) — 근거 문구 렌더러 없음 | `workflow/evaluation/PredictionFacts.kt`·`OpportunityAnalysis.kt` | ML-04 ② 「응답 근거에」의 Kotlin 착지는 **도메인 값 보존**까지, 문구 노출은 `OPEN-4D3-DIAGNOSTICS-RENDER` |
| 4D-1 규율: 값 타입 `init` 조건을 늘리면 같은 커밋에서 검증층 술어와 `SuccessShapeFailClosedTest` table 행을 함께 늘린다 | `reports/evidence/m4/4d/checklist.md` 알려진 제한 9 · `BidPredictionOutcome.kt` KDoc | D-4D3-2 |

```yaml
milestone: m4
slice: 4d3-diagnostics-domain
base_sha: 4b9fa2166549ce9e6af9c5f2b09325f11bbbbdcf
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionOutcome.kt   # ① 도메인 타입: Weight([0,1]) · SegmentSupport(Direct/ParentCategory/Global) · PredictionDiagnostics(6 성분, init 불변식) · Predicted.diagnostics 필드(D-4D3-1·3)
  - adapters/src/main/kotlin/bidvector/adapters/ml/ParsedSuccessFields.kt             # ② isAcceptableSuccessShape 에 진단 형태 술어 추가 + 파싱(D-4D3-2) — 기존 파일 관례(검증층이 init 보다 먼저)
  - adapters/src/main/kotlin/bidvector/adapters/ml/DiagnosticsShapeValidation.kt      # ② 신설 허용(ReleaseShapeValidation.kt 와 같은 꼴) — 술어를 한 파일에 모아 table test 가 가리키게
  - adapters/src/main/kotlin/bidvector/adapters/ml/ResponseMapping.kt                 # ③ mapSuccess 가 diagnostics 를 채움 · SegmentSupport 매핑
  - workflow/src/test/kotlin/bidvector/workflow/prediction/PredictionValueTest.kt     # 도메인 불변식 test
  - adapters/src/test/kotlin/bidvector/adapters/ml/{ResponseMappingTest,SuccessShapeFailClosedTest}.kt   # 우회 (1)~(9) + table 행 +N(4D-1 규율)
  - adapters/src/test/kotlin/bidvector/adapters/contract/PredictionAdditiveContractTest.kt              # 필요 시 도메인 경유 단언 추가(proto 보존 단언은 유지)
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/*Test.kt · adapters/src/test/kotlin/bidvector/adapters/ml/*Test.kt   # Predicted 생성자 호출부 갱신(값만, 판정 무변경)
  - adapters/src/test/kotlin/bidvector/adapters/ml/MlTestFixtures.kt · workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisFixtures.kt   # test fixture 의 Predicted/CompetitionSample 생성부(위 와일드카드 `*Test.kt` 가 못 덮는 경로 — 5D-3 F-1 재발 방지, 갱신 이력 2026-09-16)
  - config/quality/gate-tests.properties                                              # 신설 test 파일이 있을 때만(MlGateRegistrationTest 완전성)
  - docs/discovery/data-dictionary.md                                                 # §6.5 에 intervalSource 성분 행(PosteriorPredictive 정의) + Diagnostics 성분 표(D-4D3-5) — 팀장
  - docs/discovery/capability-map.md                                                  # OPEN 표: OPEN-2F-DIAGNOSTICS-DOMAIN·OPEN-2F-DICT-INTERVAL-SOURCE 해소, OPEN-4D3-SAMPLE-SUPPLY·OPEN-4D3-DIAGNOSTICS-RENDER 신설 — 팀장
  - milestone-4.md                                                                    # 4D-3 착수·종결 문단 — 팀장
  - reports/evidence/m4/4d3/**
out_of_scope:
  - 요청 조립 변경(`predictionRequestFor` — 표본 공급·기관 fact) → `OPEN-4D3-SAMPLE-SUPPLY`·`OPEN-2B-AGENCY-ID` · 계약 파일(`contracts/**`) 무변경 · Python(5E-2, 다른 레인) · 근거 문구 렌더링(4E 알림·리뷰 텍스트) → `OPEN-4D3-DIAGNOSTICS-RENDER` · 사다리 점수 산식(진단은 점수를 바꾸지 않는다 — 엔진이 이미 수축했다) · `agency_sample_below_threshold` 의 임계 재판정(임계는 엔진 정책, Kotlin 은 값을 믿고 나른다 — 2F 주석 「읽는 쪽이 직접 판정하지 않는다」) · `Diagnostics` 를 이벤트/persistence 에 싣는 것
acceptance: CI Kotlin `check` job 전건 — 루트 `./gradlew --no-build-cache --no-daemon clean check`(leakPatternGate·contractGate·gate-tests 완전성 포함). ml-engine job 은 소스 비중첩(Kotlin·docs 만)이라 축 밖.
rollback: in_scope 경로 한정. 공유 파일(`BidPredictionOutcome.kt`·`ParsedSuccessFields.kt`·`ResponseMapping.kt`·test 파일·`gate-tests.properties`·discovery 문서·`milestone-4.md`)은 이 slice 자기 이력만 착수 경계 기준 단일 역적용(`git diff <base> -- <파일> | git apply -R`, 2026-09-16 규칙), 신설 파일 삭제, evidence 는 남김. 임시 clone(`git clone --no-hardlinks`)에서 ①~⑥(⑥ = 되돌린 트리에서 루트 `check` 전건). 되돌리면 `Predicted` 에 진단 없음(4D-1 상태) 로 복귀.
```

## 결정(계약 고정 — 운영자 즉답 불필요, 전부 2F·4D-1·2B 결정의 귀결)

| ID | 판단 | 근거 |
| --- | --- | --- |
| D-4D3-1 | 도메인 타입 신설(`workflow/prediction`): `Weight(value: BigDecimal)` — `init` [0,1](wire `Weight` 「[0,1] fraction 가중치 일반」 미러, **`decision.UnitScore` 재사용 안 함** — 점수와 가중치를 한 타입으로 두면 사다리가 가중치를 점수로 받는다) · `SegmentSupport { Direct, ParentCategory, Global }` · `PredictionDiagnostics(trainingRowCount: Int ≥0, segmentSupport, shrinkageWeight: Weight, excludedObservations: Int ≥0, agencySampleCount: Int ≥0, agencySampleBelowThreshold: Boolean)` `init` 으로 음수 거부 · `Predicted.diagnostics: PredictionDiagnostics`(**필수 인자**, 기본값 없음 — 진단 없는 `Predicted` 를 표현 불가하게) | 2F D-2F-5 · 「불가능한 상태는 타입으로 닫는다」 · 4D-1 D-4D-* 값 타입 관례(public data class + `init`) |
| D-4D3-2 | 검증층(`isAcceptableSuccessShape`)에 진단 술어를 **init 짝으로** 추가 — (a) `shrinkage_weight.fraction` 이 정규형 십진(기존 `toValidatedBigDecimalOrNull` 규칙 재사용)이고 [0,1] (b) `segment_support` 가 `UNSPECIFIED`/`UNRECOGNIZED` 아님 (c) `uint32` 셋(`training_row_count`·`excluded_observations`·`agency_sample_count`)이 Kotlin `Int` 로 읽어 음수 아님(2^31 이상 = 계약 위반) (d) `release_kind = DERIVED` ⇒ `training_row_count == 0`(2F 우회 (10) 의 Kotlin 집행 — D-2B-6 「아티팩트 학습 행 수」). 위반은 전부 `Unavailable(ContractViolation)`(fail-closed, 예외 없음). `SuccessShapeFailClosedTest` H-6 table 에 (a)(b)(c) 행을 **같은 커밋**에 추가(4D-1 규율) | 4D-1 「검증층이 `init` 보다 먼저」 구조 · 알려진 제한 9 규율 · 2A ⑥ |
| D-4D3-3 | 소비: `mapSuccess` 가 진단을 채워 `Predicted` 에 싣는다. `predictedFacts`(사다리 점수)는 **값을 바꾸지 않는다** — 수축은 엔진이 이미 반영했고(ML-04 ② 는 「근거에 실린다」이지 「점수를 다시 깎는다」가 아니다) Kotlin 이 임계를 재판정하지 않는다. 진단은 도메인 값으로 보존되어 렌더러(후속)가 읽는다 | 2F `agency_sample_below_threshold` 주석 「읽는 쪽이 직접 판정하지 않고 엔진이 명시」 · ADR 0010 D-3 제3 변환 금지 |
| D-4D3-4 | 요청 조립은 건드리지 않는다. Kotlin 요청이 분포 엔진을 구동하려면 ① 과거 표본 조회 port(기관/공종/기간 질의, `OpeningResult` → `CompetitionSample` 변환, 표본 축 채움) ② `Notice` 의 기관 fact(수집 축 `OPEN-2B-AGENCY-ID`) 가 필요 — 둘 다 이 slice 밖, **`OPEN-4D3-SAMPLE-SUPPLY`** 신설(운영자 결정 필요: 4B 후속 slice 로 열지, M6 수집 축과 묶을지) | 착수 조사 실측(위 표) — 한 slice 에 층을 섞지 않는다(4D-1 교훈) |
| D-4D3-5 | 문서: `data-dictionary.md` §6.5 에 `intervalSource` 행(세 값의 의미 — `CrossValidationResidual`·`TimeHoldoutResidual`·`PosteriorPredictive`(5D-2 분포 엔진의 사후예측 분산, 표본 축 아님)) + `Diagnostics` 성분 표(여섯 성분의 단위·축·provenance) → `OPEN-2F-DICT-INTERVAL-SOURCE` 해소. 팀장(세션 모델) 작성 | 2F 알려진 제한 2 · discovery 문서는 세션 모델 소유 |
| D-4D3-6 | 근거 노출(ML-04 ② 의 사용자 가시 착지 — 리뷰/알림 문구에 「기관 표본 n건 < 임계, 수축 가중치 w」)은 렌더러가 없어 이 slice 밖 → **`OPEN-4D3-DIAGNOSTICS-RENDER`** 신설(4E 알림 본문 규약과 묶어 결정) | `Predicted` 소비자 실측 1(`predictedFacts`) |

## 위협·우회(설계 검토 정본 `_workspace/m4-4d3/02_design-review.md`)
(1) `shrinkage_weight = "1.5"`/`"-0.1"`/`"1e-1"`/`""` → 검증층 (a) 거부(정규형 규칙 재사용, `init` 도달 전) · (2) `segment_support` 미설정(0)·미지 값 → (b) · (3) `uint32` 4,294,967,295 → Kotlin `-1` → (c) · (4) `DERIVED` + `training_row_count = 10` → (d) · (5) 진단 없는 `Success`(proto 기본 인스턴스 — `shrinkage_weight.fraction = ""`) → (a) 거부 — **주의**: 2F 이전 서버(진단 미설정)의 정상 응답이 이제 계약 위반이 된다 → 알려진 제한(배포 순서 「제공자 먼저」, 2F 와 같은 축; 현재 프로덕션 배포 전) · (6) 다른 모듈이 `PredictionDiagnostics` 를 위조해 `Predicted` 를 만든다 → 4D-1 알려진 제한 6 과 같은 축(public 생성자 — 관례 `mapSuccess` 유일 생성, 컴파일 폐쇄 안 함) · (7) `Weight` 를 `UnitScore` 자리에 넣는다 → 타입이 다름(컴파일) · (8) 진단 값으로 점수를 조정하는 코드가 슬쩍 들어온다 → `predictedFacts` 무변경 test(진단만 다른 두 응답 → 같은 `ScoreFact` 쌍) · (9) `agency_sample_below_threshold = true` 인데 `agency_sample_count` 가 크다 → Kotlin 은 임계를 모르므로 **검증하지 않는다**(D-4D3-3, 알려진 제한).

## (2b) 값 획득 축
| 표면 | 처분 |
| --- | --- |
| `Weight`·`SegmentSupport`·`PredictionDiagnostics`(public, `workflow.prediction`) | 연다(값 타입) — `init` 이 범위를 강제. 위조는 4D-1 과 같은 축(관례 유일 생성 경로 `mapSuccess`, verifier 표적) |
| `Predicted.diagnostics` | 기존 public 생성자에 필수 인자 추가 — 획득 권한 확장 아님(값을 나르기만) |
| 검증 술어(`hasValidDiagnosticsShape` 류, `internal`) | 닫는다 — 모듈 내부. `ReleaseShapeValidation.kt` 와 같은 꼴 |
| 「`object` 커널을 세야 할 때 무엇을 주입하는가」 | 주입 없음 — 술어는 순수 함수, 정책 슬롯 없음 |
| 「경계로 처리」 행 | 없음 |

## 종결 조건
우회 (1)~(9) 각 test · `SuccessShapeFailClosedTest` table 에 진단 `init` 짝 행 · `mapSuccess` 가 testdata `calculate_optimal_bid_response_success*.json` 두 응답(ARTIFACT/DERIVED)을 `Predicted` 로 매핑하며 진단 값이 wire 와 같음 · `predictedFacts` 진단 불변 test · 전건 `check` · verifier `ready-for-review` · 사용자 승인. `OPEN-2F-DIAGNOSTICS-DOMAIN`·`OPEN-2F-DICT-INTERVAL-SOURCE` 해소, `OPEN-4D3-SAMPLE-SUPPLY`·`OPEN-4D3-DIAGNOSTICS-RENDER` 등재(Phase 6 에스컬레이션).

## 하네스 레인 변경
없음(리뷰 요청 시점에 `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 로 갱신).

## 계약 갱신 이력
| 날짜 | 변경 | 사유 |
| --- | --- | --- |
| 2026-09-16 (verifier 전 팀장 대조) | `in_scope` 에 test fixture 두 파일 명시(`MlTestFixtures.kt`·`OpportunityAnalysisFixtures.kt`) — `git diff --name-status <base>..HEAD` 13경로 대조에서 `*Test.kt` 와일드카드가 못 덮는 둘을 발견. 코드 무변경 | 5D-3 verifier r1 F-1(in_scope 누락 → clean-tree false-clean)과 같은 클래스 — 이번에는 verifier 전에 팀장이 기계 대조로 잡음 |
