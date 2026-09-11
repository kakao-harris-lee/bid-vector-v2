# M4/4B-6b 체크리스트

## D-4B6B-1~7 근거

| ID | 판단 | 구현 근거 |
| --- | --- | --- |
| D-4B6B-1 | `TextKind` 단일화(4D-2 판) | `TextSynthesis.kt`에서 `evaluation.TextKind` 삭제, `SynthesizedText.kind: bidvector.workflow.embedding.TextKind`로 교체. `TextSynthesisTest`(9 tests, golden 텍스트 불변) |
| D-4B6B-2 | 재정규화 포함 벡터 변환 | `EmbeddingBridge.kt`의 `EmbeddingVector.toUnitVector(normEpsilon)` — L2 norm 재계산 뒤 나눔. `EmbeddingBridgeTest`(ε 0.009·다차원 케이스로 재정규화 뒤 ε 0.0001 안에 듦을 실측) |
| D-4B6B-3 | release 불일치 → ReleaseMismatch | `OpportunityAnalysisPipeline.combineEmbeddings`가 `notice.release != profile.release`를 즉시 검사. `OpportunityAnalysisTest`("notice profile release 불일치는 ReleaseMismatch") |
| D-4B6B-4 | 가격예측 미가용 ≠ 사다리 미가용 | `PredictionFacts.predictionFacts`가 baseAmount null·predict Unavailable/Unmeasurable 셋 다 budgetCapture·expectedMargin **두 성분만** Absent — 조합기는 계속 `Analyzed`로 간다. test 셋(baseAmount null·예측 Unavailable·예측 Unmeasurable) 모두 `shouldBeAnalyzed()` 단언 |
| D-4B6B-5 | 정책 슬롯 여섯(예산 둘·selector·objective·offset·반올림) | `OpportunityPolicyData.kt` 신설 슬롯 + `init`(예산 > 0). `OpportunityPolicyDataTest`(13 tests, 슬롯별 불변식 + 출하값 대조) |
| D-4B6B-6 | `predictedRate = recommendedRate = candidates.base`(alignment 상수 1) | `PredictionFacts.predictedFacts`가 `predicted.candidates.base`를 두 자리에 그대로 씀. 값을 지어내지 않고 계약 공백(M2가 예측 낙찰율을 안 나름)을 그대로 반영 — `OPEN-4B6B-PREDICTED-RATE`로 남긴다(알려진 제한 참고) |
| D-4B6B-7 | `capacity = 1 − loadRatio` | `OpportunityAnalysis.finalizeAnalysis`의 `capacityScore = UnitScore(BigDecimal.ONE.subtract(loadRatio.value))`. `CapacitySnapshot(max=0)` 케이스로 0 나눗셈 없음 실측(`deriveLoadRatio`가 `maxOf(1,max)` 처리, 4B-5 소유) |

## (2b) 값 획득 축 — scope.md 표 실측 갱신

| 표면 | 가시성 | 판정 | 실측 |
| --- | --- | --- | --- |
| `OpportunityAnalysis` 주 생성자(정책 슬롯 셋) | `internal` | 닫는다 | 컴파일러가 강제 — 같은 모듈(`workflow`) test 코드만 호출 가능(`OpportunityAnalysisTest`의 "categoryOffset 정책 범위 밖" 케이스가 실제로 이 생성자를 쓴다) |
| `OpportunityAnalysis` 보조 생성자(port 여섯·Clock) | `public` | 연다 | port 주입은 없던 권한이 아니다 — `EvaluateCandidatesUseCase`가 이미 이 자리에 `MlAnalysisPort`를 받는다. use case 통합 test가 이 보조 생성자만으로 실제 배선 |
| `EmbeddingBridge.kt` 함수 셋 | `internal` | 닫는다 | 같은 패키지(`bidvector.workflow.evaluation`)에서만 호출 — `CompositionBoundaryTest` 무관(같은 패키지 참조는 애초에 허용 대상) |
| `OpportunityPolicyData` 새 슬롯 여섯 | `public data` | 연다(정책 입력) | `init`이 예산 > 0 강제. `categoryOffset`·`releaseSelector`·`objective`·`recommendedAmountRounding`은 자체 범위 검증 없음(호출 시점 `SemanticMatch.of`/`MoneyArithmetic`가 검증) |
| `Step`/`ok`/`halt`(신설, `OpportunityAnalysis.kt`) | `internal` sealed + `internal` 함수 | 닫는다(값 획득 아님) | 같은 패키지 세 파일(`OpportunityAnalysis.kt`·`OpportunityAnalysisPipeline.kt`·`PredictionFacts.kt`)만 이 타입을 생성·소비 — `MlAnalysisOutcome`으로만 밖에 나간다 |
| `ResolvedPolicies`·`SynthesizedTexts`·`EmbeddedVectors`(신설) | `internal data class` | 닫는다 | 패키지 내부 파이프라인 전용 캐리어 — 외부(공개) API에 노출 안 됨 |
| `WorkflowGateRegistrationTest` | test | — | 자기 자신도 `gate.tests.workflow`에 등재 |

**수정 라운드 없음** — 이 slice는 단일 착수→구현→검증 라운드로 끝나 "이번 수정이
새 public 표면을 만들었는가" 갱신 대상 라운드가 없다. 위 표가 착수 시점 그대로다.

## 위협 모델 대응표 — 설계 검토 우회 후보 (1)~(12)

| # | 우회 후보 | 막는 장치 |
| --- | --- | --- |
| (1) | fake `EmbedTextPort`가 비정규화 벡터 반환 | `EmbeddingVector.init`이 거부(4D-2 소유) — 조합기는 재정규화만 함(`EmbeddingBridgeTest`) |
| (2) | 두 임베딩 release 다름 | `combineEmbeddings`의 release 비교(D-4B6B-3, `OpportunityAnalysisTest`) |
| (3) | `profile.current()`가 빈 `ProfileFacts` | `synthesizeProfileText`가 Empty → `InvalidRequest`(`OpportunityAnalysisTest` "PROFILE 합성 Empty") |
| (4) | 예측이 `fitness` 1.5 반환 | `predictedFacts`의 범위 검사 → 두 성분 Absent(ContractViolation), Analyzed 유지(`OpportunityAnalysisTest` "fitness 범위 밖") |
| (5) | `floorRate` 1.2 | `expectedMarginFact`의 `floorRate.fraction > 1` 검사 → margin만 Absent(InvalidRequest)(`OpportunityAnalysisTest` "floorRate 1 초과") |
| (6) | 정책 `categoryOffset` 0.5 | `SemanticMatch.of`의 `OffsetOutOfRange` → InvalidRequest(`OpportunityAnalysisTest` "categoryOffset 정책 범위 밖") |
| (7) | 예산 0 | `OpportunityPolicyData.init`이 예산 > 0 강제(`OpportunityPolicyDataTest` "embeddingBudget/predictionBudget 이 0 이하면 생성 실패") |
| (8) | 같은 notice 로 두 번 | 무상태 — `OpportunityAnalysisTest` "같은 입력 두 번은 같은 결과" |
| (9) | notice·profile 차원 다른 벡터 | `SemanticMatch.of`의 `DimensionMismatch` → ContractViolation(`OpportunityAnalysisTest` "차원 불일치") |
| (10) | `deadlineAt` 과거(remaining 음수) | `deriveUrgency`(4B-5 소유)가 밴드 최상위로 접음 — 조합기는 그대로 넘김, 예외 없음 실측(`OpportunityAnalysisTest` "deadlineAt 과거") |
| (11) | `CapacitySnapshot(max=0)` | `deriveLoadRatio`(4B-5)가 `maxOf(1,max)`로 0 나눗셈 회피 — 조합기는 값을 접지 않고 그대로 전달, 예외 없음 실측(`OpportunityAnalysisTest` "CapacitySnapshot max 0") |
| (12) | 예산 소진 뒤 두 번째 embed 호출 | `embedOne`이 호출마다 `CallBudget(opportunity.embeddingBudget)`을 **새로** 만듦(합산 아님) — `OpportunityAnalysisTest` "두 번째 embed 호출도 새 예산을 받는다" |

## 알려진 제한

1. **프로필 재임베딩 비용** — `embedPairStep`이 매 `analyze` 호출마다 profile 텍스트를
   다시 임베딩한다(캐시 없음, 설계 검토 (3) 과잉으로 명시 제외). 호출 빈도가 늘면
   비용 축적 — 캐시는 후속 slice 소관.
2. **alignment 상수 1(`OPEN-4B6B-PREDICTED-RATE`)** — D-4B6B-6에 따라
   `predictedRate = recommendedRate = candidates.base`라 `deriveExpectedMargin`의
   `alignmentOf`가 항상 `1`(완전 정렬)을 낸다. margin weight의 20%가 상수로 고정되는
   셈 — 전략이 시나리오(conservative/aggressive)를 고르는 후속 slice 또는 M2
   additive 확장에서 해소.
3. **F-2 인계(4B-6a 절단 단위 vs gateway 상한 단위)** — `GrpcEmbeddingGateway`·
   `EmbeddingRequestMapping`을 읽었으나(편집 안 함) 클라이언트 쪽에 텍스트 길이
   검사가 없다(값을 그대로 proto에 싣는다). 서버가 실제로 어느 단위(코드포인트 vs
   UTF-16)로 `embedding.text.max-chars`를 재는지는 이 slice가 확인할 수 없다 —
   `OPPORTUNITY_POLICY.textMaxChars`(4000)와 계약 값이 같다는 사실만
   `OpportunityPolicyDataTest`가 고정한다(4B-6a 종결 시 이미 고정된 test). 실질
   위험은 BMP 밖 문자(서로게이트 쌍)가 낀 텍스트에서 코드포인트 수 ≠ UTF-16 길이
   차이뿐이라 낮다고 판단.
4. **`BaseAmountProvenance` 라벨** — `BidPredictionRequest.baseAmountProvenanceLabel`은
   계약상 필수(2B `denominator_source` 자리)인데, `Notice`는 이 축(판정 라벨, M1/1D
   `ProvenanceRules` 소유)을 나르지 않는다. `BaseAmountProvenance.Unknown`(그 타입
   자체의 "규칙 무매치/미판정" 정의)으로 고정 — 이 값을 실제로 판정하려면
   `ProvenanceRules.judgeRow`(승자금액·낙찰율 등 이 slice가 갖지 않은 fact)가
   필요해 범위 밖으로 남긴다. `OPEN-4B6B-BASE-AMOUNT-PROVENANCE`로 등재.
5. **`AgencyId`** — `Notice`에 발주기관 식별자 필드가 없어(`OPEN-2B-AGENCY-ID`,
   4D-1 scope 승계) `BidPredictionRequest.agencyId = null` 고정.

## 계약 범위 확장 — 사용자 승인 필요 항목

- `OpportunityPolicyData.recommendedAmountRounding`(D-4B6B-6 구현에 필요, 계약
  착수 시점 D-4B6B-5 다섯 슬롯 목록에 없던 여섯째 슬롯) — `policy-values.md` §4
  참고. `MoneyArithmetic.roundedWith`가 `RoundingPolicy`를 요구해 어쩔 수 없이
  추가했다. 값(scale 0·HALF_UP)은 코드베이스 전역 관례와 정확히 같아 재량 낮음.
- 신설 파일 둘(`OpportunityAnalysisPipeline.kt`·`PredictionFacts.kt`) — 계약
  in_scope 목록에는 `OpportunityAnalysis.kt` 하나만 있었으나, detekt
  `TooManyFunctions`(11/파일) 게이트가 조합기 전체를 한 파일에 두는 것을 막아
  port 접근(class)·순수 파이프라인 변환·예측 파생 셋으로 나눴다(commands.md
  S-1 3~4차 참고). 기계적 분할이 아니라 이미 코드 안에 있던 책임 경계(port I/O
  대 순수 변환)를 파일 경계로 옮긴 것이다.
