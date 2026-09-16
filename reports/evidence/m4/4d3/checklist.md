# 완료 체크리스트 — M4/4D-3 · `Diagnostics` 도메인 소비

## D-4D3-1~6 근거

- **D-4D3-1(도메인 타입 신설)** — `Weight(value: BigDecimal)`(`init`이 [0,1] 거부),
  `SegmentSupport { Direct, ParentCategory, Global }`, `PredictionDiagnostics(trainingRowCount,
  segmentSupport, shrinkageWeight, excludedObservations, agencySampleCount,
  agencySampleBelowThreshold)`(`init`이 세 `Int` 성분 음수를 각각 거부),
  `BidPredictionOutcome.Predicted.diagnostics`가 필수 인자(기본값 없음) — 전부
  `BidPredictionOutcome.kt`. `PredictionValueTest`의 `Weight` 경계 test 둘·
  `PredictionDiagnostics` 음수 거부 test·정상 생성 test로 고정.
- **D-4D3-2(검증층 술어)** — `DiagnosticsShapeValidation.kt`의
  `hasValidDiagnosticsShape`가 (a)~(d) 넷을 잰다. `ParsedSuccessFields.kt`의
  `isAcceptableSuccessShape` checks 목록에 등재해 `init` 도달 전에 fail-closed 로
  거른다. `SuccessShapeFailClosedTest`의 H-6 table(`shapeInvariantCases`)에 4행
  추가 — `Weight` 상한·하한, `excludedObservations` 오버플로, `segmentSupport`
  UNSPECIFIED. `ResponseMappingTest`에 우회 (1)~(5) 개별 test.
- **D-4D3-3(소비, 점수 불변)** — `ResponseMapping.kt`의 `mapSuccess`가
  `fields.toDiagnostics()`를 `Predicted`에 채운다. 사다리 점수 산식(`predictedFacts`,
  `PredictionFacts.kt`)은 한 줄도 바뀌지 않았다 — `PredictionFactsTest`의 「진단만
  다른 두 `Predicted`는 같은 `ScoreFact` 쌍을 낸다(우회 8)」 test로 고정.
- **D-4D3-4(요청 조립 미착수)** — `predictionRequestFor`(`PredictionFacts.kt`)
  무편집. `OPEN-4D3-SAMPLE-SUPPLY` 등재(`capability-map.md`, 팀장 작성) — 운영자
  결정 대기.
- **D-4D3-5(문서)** — `data-dictionary.md` §6.5·§6.5.1, `capability-map.md` OPEN
  표 갱신은 팀장 커밋(`3672ee5`)이 처리했다. 이 slice(구현 레인)는 문서 파일을
  편집하지 않는다(scope.md 규율).
- **D-4D3-6(근거 노출 미착수)** — 렌더러 없음, `OPEN-4D3-DIAGNOSTICS-RENDER`
  등재(팀장, `capability-map.md`) — 4E 알림 본문 규약과 묶어 결정 대기.

## 우회 (1)~(13) 대응표

| 우회 | 무엇을 막는가 | 막는 게이트/test |
| --- | --- | --- |
| (1) `shrinkage_weight` 범위 밖 형태(`"1.5"`·`"-0.1"`·`""`·`"1e-1"`) | `hasValidDiagnosticsShape`(a) — `isNormalizedFraction` + [0,1] | `ResponseMappingTest`「shrinkage_weight fraction 이 범위 밖 형태면 ContractViolation」(4값 순회) |
| (2) `segment_support` 미설정(0)·미지 값 | `hasValidDiagnosticsShape`(b) — `ProtoSegmentSupport.toDomainOrNull() != null` | `ResponseMappingTest`「segment_support 가 미설정이면」 + H-6 table「segment_support UNSPECIFIED」 |
| (3) `uint32` 4,294,967,295 → Kotlin `-1` | `hasValidDiagnosticsShape`(c) — `>= 0` | `ResponseMappingTest`「uint32 성분이 오버플로」 + H-6 table「excluded_observations 오버플로」 |
| (4) `DERIVED` + `training_row_count = 10` | `hasValidDiagnosticsShape`(d) | `ResponseMappingTest`「DERIVED release 인데 training_row_count 가 0보다 크면」 |
| (5) 진단 없는 `Success`(기본 인스턴스, `shrinkage_weight.fraction = ""`) | (a)가 빈 문자열을 정규형 거부로 잡는다 | `ResponseMappingTest`「진단 없는 응답(proto 기본 인스턴스)은 ContractViolation」— 알려진 제한(아래) |
| (6) 다른 모듈이 `PredictionDiagnostics`를 위조해 `Predicted`를 만든다 | 막지 않음(4D-1 알려진 제한 6과 같은 축) | 알려진 제한(아래) |
| (7) `Weight`를 `UnitScore` 자리에 넣는다 | 타입이 다름 — 컴파일 거부 | 컴파일(별도 test 불필요, 타입 시스템 자체가 증거) |
| (8) 진단 값으로 점수를 조정하는 코드가 슬쩍 들어온다 | `predictedFacts` 무변경 | `PredictionFactsTest`「진단만 다른 두 Predicted 는 같은 ScoreFact 쌍을 낸다」 |
| (9) `agency_sample_below_threshold = true`인데 `agency_sample_count`가 크다 | 막지 않음(Kotlin 은 임계를 모른다) | 알려진 제한(아래) |
| (10) `shrinkage_weight.fraction = "0.25000000000000001"`(정밀도 과잉) | 과잉 거부하지 않음 — `isNormalizedFraction`은 자릿수를 제한하지 않고 [0,1] 안이면 통과 | 별도 test 미추가(설계 검토 (10) 판정 — 규칙에 자릿수 제한이 없음을 `FractionRules.kt` 확인으로 대체, 과잉 거부가 없다는 것은 기존 `isNormalizedFraction` 계약 test 커버리지로 충분) |
| (11) `excluded_observations` 가 표본 수보다 크다 | 검증하지 않음(요청·응답 분리) | 알려진 제한(아래) |
| (12) table test 가 진단 행을 빠뜨린 채 초록 | H-6 table 에 4행이 실제로 존재 — 빠뜨리면 `hasValidDiagnosticsShape`가 새 조건을 추가해도 이 table 이 못 잡는 회귀가 재발 가능(4D-1 알려진 제한 9 승계) | 알려진 제한(아래, verifier 표적 승계) |
| (13) `SegmentSupport`를 `decision` 모듈에 두려는 유혹 | `workflow.prediction`에 배치 | `BidPredictionOutcome.kt`(코드 자체가 증거), `PredictionBoundaryTest`(패키지 경계 test, 회귀 없이 통과) |

## (2b) 값 획득 축 실측

- `Weight`·`SegmentSupport`·`PredictionDiagnostics`(public, `workflow.prediction`) —
  `init`이 범위를 강제. `PredictionDiagnostics` 생성자는 public(4D-1 KDoc 규율과
  같은 이유 — cross-module 어댑터 생성, `internal constructor`는 `adapters`
  모듈에서 호출 불가).
- `Predicted.diagnostics` — 기존 public 생성자에 필수 인자 추가. **`Predicted(...)`
  생성부 전수(컴파일이 드러낸 목록)**:
  1. `adapters/src/main/kotlin/bidvector/adapters/ml/ResponseMapping.kt`
     (`mapSuccess`) — production 유일 생성 경로, `fields.toDiagnostics()`로 채움.
  2. `workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityAnalysisFixtures.kt`
     (`predicted()`) — test 전용 헬퍼, `diagnostics` 파라미터에 기본값(`TEST_DIAGNOSTICS`)
     추가. `PredictionFactsTest`·`OpportunityAnalysisTest`는 이 헬퍼를 통해서만
     `Predicted`를 만들므로 직접 갱신 대상이 아니다.
- 검증 술어(`hasValidDiagnosticsShape`·`toWeightOrNull`·`ProtoSegmentSupport.toDomainOrNull`,
  `internal`) — 닫는다. 모듈 내부.
- 「`object` 커널을 세야 할 때 무엇을 주입하는가」 — 주입 없음. 술어는 순수 함수, 정책
  슬롯 없음.
- 「경계로 처리」 행 — 없음.

## 알려진 제한

- **임계 재판정 미검증(우회 9)** — `agency_sample_below_threshold`와
  `agency_sample_count`의 정합은 Kotlin 이 확인하지 않는다. 엔진이 명시한 값을
  그대로 나른다(D-4D3-3, 2F 주석 「읽는 쪽이 직접 판정하지 않는다」).
- **`excluded_observations` 대조 불가(우회 11)** — 요청 표본 수를 응답 매핑이
  모른다(요청·응답 분리 구조). 검증 대상 밖.
- **기본 인스턴스(2F 이전 서버) 거부 — 배포 순서 축(우회 5)** — 진단 없는 정상
  응답(2F 이전 서버)이 이제 계약 위반으로 거부된다. 현재 프로덕션 배포 전이라 실물
  경로 없음(2F 알려진 제한 3·ADR 0010 D-7 「제공자 먼저」와 같은 축).
- **`PredictionDiagnostics` 위조 승계(우회 6)** — 4D-1 알려진 제한 6과 같은 축.
  public 생성자라 다른 모듈이 값을 위조해 `Predicted`를 만들 수 있다. 방어는
  `mapSuccess` 유일 생성 경로 관례와 code review·test 커버리지가 진다.
- **H-6 table 손 유지 승계(우회 12, 변이 실측)** — 4D-1 알려진 제한 9와 같은 구조적
  한계: `hasValidDiagnosticsShape`에 새 조건을 추가하고 table 행을 깜빡해도
  `SuccessShapeFailClosedTest` 자체는 컴파일·통과한다(table 은 손으로 유지). 변이
  실측(구현 레인, 이번 라운드): `PredictionDiagnostics`에 조건 하나를 늘리고 대응
  table 행을 빼는 시나리오를 코드로 만들지는 않았다 — 4D-1 알려진 제한 9가 이미
  기록한 한계이고 구조 처방(자동 대조)은 이 slice 밖이다. verifier 표적으로 승계.
  verifier r1 표적 2 실측: `Weight.init` 에 조건 하나를 늘리고 표 행을 안 늘린 변이에서
  두 모듈 test 가 통째로 초록 — 4D-1 알려진 제한 9 의 승계가 맞고 새 갈래 아님.

## 사용자 승인

2026-09-16 — 운영자 지시 「추천 방식으로 진행」(5D-3 종결 뒤 (a) 4D-1 후속) 하에 착수·구현·검증·종결·PR 까지
진행(머지는 별도 승인). verifier r1 `ready-for-review`(`_workspace/m4-4d3/03_verifier_report.md`), 재작업 0회.
장부층 F-1(팀장 `30ae0bf`)·F-2~F-5·F-7(구현 레인 `91fb85d`)·F-6(팀장, scope.md `head_sha`) 은 재검증 없이
등재 처리(차단 문턱 밖 — 리뷰 레인이 본다). Phase 6 에스컬레이션: `OPEN-4D3-SAMPLE-SUPPLY`(운영자 선택
(a)/(b)/(c)) · `OPEN-4D3-DIAGNOSTICS-RENDER`(4E 규약과 묶음) — milestone-4.md 4D-3 종결 문단 참조.
