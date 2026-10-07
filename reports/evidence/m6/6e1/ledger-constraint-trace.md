# C-7 — regression ledger ↔ 예방 제약 연결 전수표

M6/6E-1 산출물 C-7(`scope.md` C-7 행, D-6E1-2). 입력은 `docs/discovery/regression-ledger.md`
(이하 ledger)와 이 브랜치의 V2 저장소 전체다. 읽기만으로 만들었다 — 빌드·test 를 돌리지 않았고,
아래 「연결」은 **그 경로가 저장소에 실재하고 CI 가 도는 자리(Kotlin `check`·Python `ml-engine`
pytest·`contractGate`)에 있다**는 정적 확인이다.

## 집계

| 전체 | 연결 | 미연결 | 해당 없음(폐기·후속 capability) |
| --- | --- | --- | --- |
| 61 | 43 | 18 | 0 |

**행 수 등식** — ledger 항목 수와 이 표의 행 수, 그리고 ID 집합이 같다.

```
grep -cE '^### [RP]-[A-Z]+-[0-9]+ ' docs/discovery/regression-ledger.md          # 61
grep -cE '^\| [RP]-[A-Z]+-[0-9]+ \|' reports/evidence/m6/6e1/ledger-constraint-trace.md   # 61
diff <(grep -oE '^### [RP]-[A-Z]+-[0-9]+' docs/discovery/regression-ledger.md | cut -c5- | sort) \
     <(grep -oE '^\| [RP]-[A-Z]+-[0-9]+' reports/evidence/m6/6e1/ledger-constraint-trace.md | cut -c3- | sort)   # 빈 출력
```

61 = 회귀 60(BASIS 7 · RATE 5 · PROV 8 · FLOOR 8 · QUAL 7 · COL 8 · ASYNC 8 · ML 9) + `P-ML-01` 1.
`P-ML-01` 은 ledger 가 「회귀 수에 계상하지 않는다」고 적은 **유지할 예방책**이지만 ledger 의 항목
heading 이므로 행을 둔다.

## 판정 규칙

- **연결** — 그 회귀의 **발생 기제**를 V2 에서 컴파일 실패·test RED·로드/계약 거부·CI 게이트 실패로
  바꾸는 경로가 실재한다. ledger 0.4 대로 「주의한다」류 서술·KDoc 주석은 계상하지 않는다. ledger
  「검증 방법」의 일부 항목이 비어 있으면 상태는 연결로 두되 **잔여** 열에 그 빈자리를 적는다.
- **미연결** — 기제를 막는 경로가 없다. 그 회귀의 V2 표면 자체가 아직 없는 경우(V2 필수 capability
  미구현)도 미연결이다 — 표면이 생길 때 제약이 함께 생긴다는 보장이 저장소에 없기 때문이다.
- **해당 없음** — 회귀 표면이 capability-map 의 `폐기`·`후속` capability 에만 속할 때. 이번 전수에서
  **0건**이다. 후보였던 R-COL-06(COL-10 `폐기`)·R-QUAL-07(QUAL-12 `폐기`)·R-RATE-03(QUAL-11 동반 `폐기`)
  은 `폐기` 대상이 legacy **형태**이고 V2 제약이 따로 살아 있어 연결로 판정했다. R-COL-08 은 우선순위
  입력(STR-12 `후속`)과 겹치지만 COL-04(`V2 필수`) acceptance 가 같은 순서를 요구해 미연결이다.
- **fixture case** 는 `fixtures/manifest.yaml` 에서 `classification: authoritative` 이고
  `SharedKernelCorpusConformanceTest`(또는 ml-kernel 은 `test_kernel_golden.py`)가 실행하는 것만
  경로로 쓴다. `insufficient-evidence` case(floor-applicability-001~005 등)는 실행되지 않으므로
  연결 근거로 쓰지 않았다.

## 미연결 18건

| ID | 무엇이 없는가 |
| --- | --- |
| `R-BASIS-02` | 검색 API 표면이 없다(STR-16 미구현). basis 미표기 금액 거부 계약 없음 |
| `R-PROV-01` | 「실측」 근거 정책 값의 측정 시점 필수 검사 없음 |
| `R-PROV-06` | `est == base` 행을 `clean` 아닌 사유 있는 `Uncertain` 으로 내는 경로 없음 |
| `R-PROV-07` | legacy 유래 행의 ground-truth 유입을 막는 게이트 없음 |
| `R-FLOOR-01` | 하한 적용 가능성(지방계약 미적용) 1급 상태 없음 |
| `R-FLOOR-02` | 적용 가능성이 하한 판정보다 먼저 결정되게 하는 타입 없음 |
| `R-FLOOR-03` | 게시 하한율 개연 밴드 smart constructor 없음(백테스트 한 층만) |
| `R-FLOOR-04` | 도메인 값의 소비자 집합을 선언과 대조하는 test 가 하한 축에 없음 |
| `R-QUAL-02` | 협회 축 부재, 축 추가 시 커널 우회를 막는 게이트 없음 |
| `R-COL-08` | 자격 원문 수집의 우선순위 정책 입력·tier 별 쿼터 계상 없음 |
| `R-ASYNC-01` | 큐 깊이·소비자 진행 관측 없음(OPS-04 미구현) |
| `R-ASYNC-02` | 투입 ≤ 소진 같은 구성 간 불변식의 로드 시점 검사 없음 |
| `R-ASYNC-07` | 노후도 상한을 주장하는 재수집 경로와 그 순서 property 없음 |
| `R-ASYNC-08` | 소비자 정지 시 붉어지는 지표 없음 |
| `R-ML-01` | 학습·서빙 모집단 분포 정합의 승격 거부 게이트 없음 |
| `R-ML-04` | 정산 관측 시각 canonical fact 없음 |
| `R-ML-05` | 「언제 알 수 있었는가」 기준 cutoff 없음(embargo 유지) |
| `R-ML-07` | 정답 fixture 의 재생성 경로 부재를 잠그는 검사 없음 |

## 전수표

형태 어휘: 타입 · 계약 · 테스트 · 게이트. 경로의 test 이름은 이름 일부를 「」로 인용한다.

### 1. 금액 basis

| ID | 요지 | 형태 | 경로 | 상태 | 잔여·비고 |
| --- | --- | --- | --- | --- | --- |
| R-BASIS-01 | 예산 필터가 운영자 미지정 basis 로 거른다 | 타입 + 테스트 | `strategy/src/test/kotlin/bidvector/strategy/CompileFailureHarnessTest.kt` 「1 BudgetBound 는 EstimatedAmount 를 받지 않고 BaseAmount 는 받는다」 · `shared-kernel/src/test/kotlin/bidvector/sharedkernel/CompileFailureHarnessTest.kt` 「11 compareKnownVat 는 basis 교차 쌍을 받지 않고…」 · `strategy/src/test/kotlin/bidvector/strategy/WatchRulesTest.kt` 「EstimatedAmount 대 BaseAmount 비교는 evaluateBudget 자리에 컴파일되지 않는다」 · fixture `money-basis-001`·`money-basis-003`(`SharedKernelCorpusConformanceTest`) | 연결 | 과세 경계 쌍은 `OPEN-REG-05` 대기(ledger 조건부 절) |
| R-BASIS-02 | 검색 API 예산 필터도 같은 결함 | — | 없음. `openapi/bidvector-operator-api.yaml` 에 검색 경로가 없다 | 미연결 | `money-basis-003` 은 커널(`WatchRules.evaluate`) 수준의 두 경로 동일만 잠근다. API 계약은 없다 |
| R-BASIS-03 | capture 점수 분모가 경로마다 다르다 | 타입 + 테스트 | `decision/src/main/kotlin/bidvector/decision/priority/derive/Derivations.kt` `deriveBudgetCapture`(`BaseAmount` 인자) · `decision/src/test/kotlin/bidvector/decision/priority/derive/BudgetCaptureDerivationTest.kt` · 호출부 단일 `workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt` `budgetCaptureFact` | 연결 | 「세 경로 바이트 동일」 property test 없음 |
| R-BASIS-04 | basis 혼합 min/max 로 추천가가 실격선 아래 | 타입 + 테스트 | fixture `money-basis-004`(compile fixture 11 위임) · `shared-kernel/src/main/kotlin/bidvector/sharedkernel/MoneyArithmetic.kt` `ReasonCode.ROUNDED_BELOW_FLOOR` · `shared-kernel/src/test/kotlin/bidvector/sharedkernel/ArithmeticTest.kt` 「Codex3 소수 하한 1000_4 에서 scale0 DOWN 은 하한 미만 값을 조용히 내지 않는다」 | 연결 | 「어느 제약에 binding 됐는가」를 결과에 싣는 필드 없음 · 법정 하한 이상 property 없음 |
| R-BASIS-05 | 시나리오 범위 하단을 법정 하한으로 취급 | 타입 | `shared-kernel/src/main/kotlin/bidvector/sharedkernel/Rate.kt` `FloorRate`(`FloorRateOrigin`)와 `BidRate`(`BidRateOrigin.Recommended`)가 별개 클래스 · 같은 기제 실측 `shared-kernel/src/test/resources/compile-fixtures/negative-2-rate-axis-cross.kt.txt` | 연결 | compile fixture 는 `AwardRate`↔`BidRate` 쌍만 있고 `FloorRate` 쌍 전용 fixture·반사실 코호트 fixture 없음 |
| R-BASIS-06 | 검증 안 된 입력이 신호 없이 투찰 base 가 됨 | 타입 + 계약 | `shared-kernel/src/main/kotlin/bidvector/sharedkernel/Money.kt` `Money.export`(`AmountRecord` 다섯 성분) · `contracts/proto/bidvector/ml/v1/common.proto` `Money`(basis·provenance 필수) · `adapters/src/test/kotlin/bidvector/adapters/contract/ContractRoundTripTest.kt` 「Money 는 provenance 가 UNSPECIFIED 면 거부된다」 · `shared-kernel/src/test/kotlin/bidvector/sharedkernel/UndeclaredProvenanceTest.kt` | 연결 | — |
| R-BASIS-07 | 영속 감사 문구가 분모와 다른 금액을 말함 | 계약 + 게이트 | `workflow/src/main/kotlin/bidvector/workflow/event/NotificationRequestedPayload.kt`(사유는 구조 스냅샷, 문장 미영속) · `workflow/src/test/kotlin/bidvector/workflow/evaluation/NotificationReasonSerializationTest.kt` · `workflow/src/test/kotlin/bidvector/workflow/evaluation/EvidenceLinesBoundaryTest.kt` · `app/src/test/kotlin/bidvector/app/architecture/ArchitectureGateTest.kt` 「도메인 모듈이 허용 목록 밖을 보지 않는다」(`config/quality/architecture-policy.properties` 가 `Locale`·`Formatter` 를 허용하지 않음) | 연결 | 도메인 한글 리터럴 산출 경로 부재 architecture test 없음 · 저장 payload 재렌더링 일치 test 없음 |

### 2. rate scale

| ID | 요지 | 형태 | 경로 | 상태 | 잔여·비고 |
| --- | --- | --- | --- | --- | --- |
| R-RATE-01 | 단위 추측 규칙이 7곳에 독립 구현 | 타입 + 테스트 | `shared-kernel/src/main/kotlin/bidvector/sharedkernel/Rate.kt` `Rate`(internal 생성자, `ofPercent`·`ofFraction` 만) · `shared-kernel/src/test/kotlin/bidvector/sharedkernel/CompileFailureHarnessTest.kt` 「12 Rate 는 단위 미선언 값으로 만들 수 없고…」 · `shared-kernel/src/test/kotlin/bidvector/sharedkernel/RegressionExampleTest.kt` 「E-3 변환은 이름이 단위인 생성 지점 한 곳뿐이고…」 · fixture `rate-unit-001`~`005` | 연결 | 「크기 기반 분기 없음」 architecture test 는 없고 타입이 그 자리를 진다 |
| R-RATE-02 | 콜사이트 수렴 미완 | 타입 | `shared-kernel/src/main/kotlin/bidvector/sharedkernel/Rate.kt`(internal 생성자 — 단위를 이름으로 요구하는 공개 경로 둘만) · `shared-kernel/src/test/kotlin/bidvector/sharedkernel/CompileFailureHarnessTest.kt` 12 「`Rate` 는 단위 미선언 값으로 만들 수 없고 명시 단위 경로로는 만들 수 있다」 | 연결 | **r1 정정(cr G-10)** — 앞 판은 경로 칸을 「R-RATE-01 과 같은 경로」로 적어 해소를 독자에게 넘겼다(연결 43 행 가운데 경로 칸에 식별자가 없는 유일한 행). R-RATE-01 과 **공유하는 산출물을 그 칸에 다시 적는다** — 그쪽 경로가 바뀌어도 이 행의 근거가 조용히 따라 움직이지 않는다. ledger 가 별도 test 를 요구하지 않음 |
| R-RATE-03 | 값 크기로 단위를 추측하는 규칙 자체 | 타입 + 계약 | fixture `rate-unit-003`·`rate-unit-004`(compile fixture 12 위임) · `contracts/proto/bidvector/ml/v1/common.proto` `Rate`(fraction 문자열만) | 연결 | — |
| R-RATE-04 | 밴드 밖 율이 `None` 이 되어 크래시 | 타입 + 테스트 | `shared-kernel/src/main/kotlin/bidvector/sharedkernel/Carrier.kt` `Measurement`·`Fact`(꺼내는 길은 소진 `when` 뿐) · `CompileFailureHarnessTest` 「10 Measurement Measured 는 임의 타입을 진짜 기록으로 포장할 수 없고…」 · `RegressionExampleTest` 「E-4 밴드 밖 입력이…클램프하지 않는다」 · fixture `floor-shortfall-005`(밴드 밖 표본 → 사유 있는 측정 불가) | 연결 | — |
| R-RATE-05 | 유효 창과 판별 임계의 결합이 주석에만 | 타입 | 결합 상대인 크기 판별 임계가 V2 에 존재하지 않는다 — R-RATE-01 경로와 fixture `rate-unit-003`·`004` 가 그 부재를 잠근다 | 연결 | ledger 문면의 「묶인 정책 객체 + 로드 시점 거부」와 다른 형태다. 판별 임계를 다시 들이면 이 판정은 무효 |

### 3. VAT / provenance

| ID | 요지 | 형태 | 경로 | 상태 | 잔여·비고 |
| --- | --- | --- | --- | --- | --- |
| R-PROV-01 | 근거 없는 「66% 오염」 수치 복제 | — | 없음. `shared-kernel/src/main/kotlin/bidvector/sharedkernel/Policy.kt` `PolicyVersion` 은 `source` 문자열만 갖고 측정 시점 필드가 없다 | 미연결 | `FloorShortfallPolicyData.minAssessmentSamplesRationale` 비공백 검사는 한 정책에만 있고 측정 시점 축이 아니다 |
| R-PROV-02 | 분류기가 추정가격을 안 봐 10배 base 가 `clean` | 계약 + 테스트 | `decision/src/main/kotlin/bidvector/decision/ProvenanceRules.kt` `ProvenanceRow.budgetEstimate`·`isSuspectRatio`·정책 `ruleOrder` · fixture `base-amount-provenance-001`~`003` · `decision/src/test/kotlin/bidvector/decision/ProvenanceRulesTest.kt` 「② 같은 매치 집합이라도 정책 순서가 다르면 다른 라벨을 낸다」 · `ml-engine/tests/inference/test_assessment.py` `test_admit_clean_only_passes_clean_provenance` | 연결 | 임계 마진은 `OPEN-DEC-07` 대기 |
| R-PROV-03 | 같은 임계를 곱셈형·나눗셈형으로 각자 구현 | 타입 + 테스트 | `workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleConversion.kt` `provenanceLabelFor`(표본·대상 공고 공용 분류기 하나) · `workflow/src/test/kotlin/bidvector/workflow/evaluation/PredictionFactsTest.kt` 「대상 라벨 — 기초금액이 추정가격의 신뢰 상한…SuspectRatio」 · `workflow/src/test/kotlin/bidvector/workflow/evaluation/SampleEligibilityTest.kt` 「라벨 — 기초금액이 추정가격의 신뢰 상한…SuspectRatio」 · `ProvenanceRulesTest` 「술어 — suspect-ratio 는 엄격 초과만 매치한다」 | 연결 | 임계 ±1 ULP property 없음 |
| R-PROV-04 | 수집 write 가 백필 재태깅을 되돌림 | 타입 + 테스트 | `procurement/src/main/kotlin/bidvector/procurement/ResolvedBaseAmount.kt` `mayOverwrite` · `procurement/src/test/kotlin/bidvector/procurement/ResolvedBaseAmountTest.kt` · `adapters/src/test/kotlin/bidvector/adapters/persistence/PrecedenceParityTest.kt` · `adapters/src/test/kotlin/bidvector/adapters/persistence/PrecedenceMutationTest.kt` 「N-1 재현(회귀) — 값은 그대로 두고 provenance 만 강등하는 직접 SQL 은 거부된다」 · `adapters/src/test/kotlin/bidvector/adapters/persistence/NoticeVersioningTest.kt` | 연결 | — |
| R-PROV-05 | 어휘 문자열이 두 축에서 충돌 | 타입 | `shared-kernel/src/main/kotlin/bidvector/sharedkernel/Provenance.kt` `Provenance`·`BaseAmountProvenance` 별개 sealed · `Rate.kt` `FloorRateOrigin`·`BidRateOrigin` · 왕복 `PrecedenceParityTest`·`adapters/src/test/kotlin/bidvector/adapters/persistence/PrecedenceLabelColumnTest.kt` | 연결 | 두 축 교차 대입 전용 compile fixture 없음 |
| R-PROV-06 | `est == base` 사각지대가 `clean` 의 30% | — | 없음. `provenanceLabelFor` 는 금액만 보고 `Provenance.CopiedFromBaseAmount` 를 소비하지 않는다 | 미연결 | 커버리지 밖 코호트 크기 리포트도 없음 |
| R-PROV-07 | 개찰 파생 분모로 굳은 `clean` 3,982행 | — | 없음. ledger 가 지목한 manifest 검증(`fixtures/tools/manifest_contract.py`)은 CI 에서 돌지 않는다 | 미연결 | `money-basis-006`·`UndeclaredProvenanceTest` 는 `Undeclared` 금액의 **산술**만 막는다. 라벨 분류기는 provenance 를 보지 않는다. legacy 행 유입 경로는 현재 코드에 없음(관찰) |
| R-PROV-08 | `0.0` 을 「미상」으로 쓰는 관례 | 타입 + 테스트 | `Carrier.kt` `Fact.Absent`(0 으로 접는 API 없음) · `RegressionExampleTest` 「E-1 부재가 집계 분자·분모 어디에도 0으로 들어가지 않는다」 · fixture `ml-kernel-012`(`test_kernel_golden.py` `test_ml_kernel_012_zero_opened_is_no_observation_not_a_zero_ratio`) | 연결 | — |

### 4. 법정 하한

| ID | 요지 | 형태 | 경로 | 상태 | 잔여·비고 |
| --- | --- | --- | --- | --- | --- |
| R-FLOOR-01 | 지방계약 「명시 제외」가 선언에만 | — | 없음. 법정 tier 표의 런타임 적용 경로 자체가 없다(`FloorRateOrigin.StatutoryTable` 은 저장 codec 에만 쓰인다) | 미연결 | fixture `floor-applicability-001` 은 `insufficient-evidence` 라 미실행. DEC-03(`V2 필수`) 미구현 |
| R-FLOOR-02 | 비국가기관에 국가 tier 일괄 적용 | — | 없음 | 미연결 | R-FLOOR-01 과 같은 빈자리 |
| R-FLOOR-03 | 성립 불가 게시 하한율 `1.00000` | — | 없음. `procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt` `floorRateFrom` 은 밴드 검사 없이 `FloorRate` 를 만들고 `PredictionFacts` 는 `fraction > 1` 만 거른다 | 미연결 | 밴드는 백테스트 한 층(`ml-engine/src/ml_engine/evaluation/backtest/rules.py` `FLOOR_RATE_ABSENT_OR_OUT_OF_BAND`)에만 있다. ledger 의 「세 층 공유」 아님. `KonepsCollectionPolicyData.rangeBands` 는 빈 표(`OPEN-DEC-10`) |
| R-FLOOR-04 | 안전 근거가 「소비자가 X만 한다」 주석 | — | 없음(하한·밴드 값 축) | 미연결 | 같은 형태의 소비자 집합 게이트는 다른 축에만 있다 — `ArchitectureGateTest` 「assemble 커널 호출자는 architecture-policy 허용 목록 밖에 없다」 |
| R-FLOOR-05 | 측정 불가를 `None` 으로, 금지는 문서로만 | 타입 + 테스트 | `decision/src/main/kotlin/bidvector/decision/FloorTypes.kt` `FloorUnmeasurableReason` · `decision/src/test/kotlin/bidvector/decision/FloorShortfallKernelTest.kt` 「위협 모델 (1) — Unmeasurable 은 소진 when 없이 값을 꺼낼 공개 경로가 없다」·「⑥ 149 표본은 Unmeasurable(SampleInsufficient) 이다」 · fixture `floor-shortfall-001` | 연결 | 「판정 불가 ≠ 0%」 렌더링 계약 test 없음(`floor-shortfall-004` 미실행) · 커널은 production 미배선 |
| R-FLOOR-06 | 운영자 하한 override 에 상한 검증 없음 | 계약 + 테스트 | `decision/src/main/kotlin/bidvector/decision/FloorOverrideValidation.kt` `FloorOverrideValidation`·`FloorOverrideOutcome` · `decision/src/test/kotlin/bidvector/decision/FloorOverrideValidationTest.kt` 「밴드보다 높은 override…도 거부된다」 | 연결 | override 입력 표면(API)이 아직 없어 커널 수준 연결 |
| R-FLOOR-07 | 최소 표본 150 의 근거가 통계적 편의 | 계약 + 테스트 | `decision/src/main/kotlin/bidvector/decision/FloorShortfallPolicyData.kt` `minAssessmentSamples`·`minAssessmentSamplesRationale`(비공백 강제) · `FloorShortfallKernelTest` 「⑥ 150 표본은 측정 가능이다 — 경계값」 · fixture `floor-shortfall-001` | 연결 | 판정 결과에 근거 문자열이 실리는지 test 없음 · 커널은 production 미배선 |
| R-FLOOR-08 | 표본 필터 꼬리에 따라 편향 방향 반전 | 계약 + 테스트 | `FloorTypes.kt` `BiasDirection` · `FloorShortfallKernelTest` 「⑧ biasDirection — …」·「위협 모델 (f) — qualifiedDenominator 는 rawCount 빼기 outsideBand 다」 | 연결 | 두 밴드(`denominatorBand`·`biasIndeterminateBand`)가 같은 타입 `AssessmentBand` 다 — ledger 의 「타입으로 구별」 미충족 |

### 5. 면허 자격

| ID | 요지 | 형태 | 경로 | 상태 | 잔여·비고 |
| --- | --- | --- | --- | --- | --- |
| R-QUAL-01 | 같은 fold 를 두 곳에 구현해 과차단 | 타입 + 테스트 | `qualification/src/main/kotlin/bidvector/qualification/LicenseEligibility.kt` `LicenseEligibility` · `LicenseGroupFolding.kt` · `qualification/src/test/kotlin/bidvector/qualification/LicenseEligibilityPropertyTest.kt` · fixture `license-002`~`005` | 연결 | 축이 면허 하나라 「세 축 동일」 property 는 대상 없음 |
| R-QUAL-02 | 같은 결함이 협회 축에 잠재 | — | 없음 | 미연결 | 협회 축이 없고, 축을 더할 때 커널 우회를 컴파일로 막는 장치가 없다(ledger 검증 방법) |
| R-QUAL-03 | 포괄 별칭이 전문분야를 collapse | 계약 + 테스트 | `qualification/src/main/kotlin/bidvector/qualification/LicensePolicy.kt` `LicenseAliasTable`·`LICENSE_QUALIFICATION_POLICY`(정책 데이터, 현재 빈 표) · `qualification/src/test/kotlin/bidvector/qualification/LicenseEligibilityTest.kt` 「별칭 미등재 면허는 원문 정규화 키로 보존되고 collapse되지 않는다」·「정책 version이 판정 봉투에 그대로 실린다」 | 연결 | 포괄어 별칭 집합의 로드 시점 거부 없음(`OPEN-QUAL-07`) |
| R-QUAL-04 | 자격 상세를 잘못된 원천에서 읽어 0건 | 계약 + 테스트 | `adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsLicenseLimitDocumentSource.kt` · `adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsLicenseLimitDocumentSourceTest.kt` 「업종제한이 있으면 정확히 1회 조회하고 LICENSE_LIMIT_DETAIL 로 낸다」 · `adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsOperationDescriptorTest.kt` 「LICENSE_LIMIT_DETAIL 은 inqryDiv 2 이고 bidNtceNo·bidNtceOrd 둘 다 싣는다」 | 연결 | 어댑터는 production 수집 흐름에 아직 배선되지 않음 |
| R-QUAL-05 | 차수 제로패딩을 `int` 로 변환 | 타입 + 테스트 | `shared-kernel/src/main/kotlin/bidvector/sharedkernel/NoticeRound.kt` `NoticeRound` · `procurement/src/test/kotlin/bidvector/procurement/NoticeIdTest.kt` 「NoticeRound 는 제로패딩 3자리 문자열을 원문 그대로 보존한다 — R-QUAL-05」 · `qualification/src/main/kotlin/bidvector/qualification/LicenseInputs.kt` `LmtGrpNo` · fixture `koneps-collection-005`·`012` | 연결 | — |
| R-QUAL-06 | 제외 사유에 보유 면허가 섞임 | 계약 + 테스트 | `LicenseEligibilityPropertyTest` 「missingByGroup은 항상 요구 minus 보유의 부분집합이다 — R-QUAL-06」·「그룹 순서를 바꿔도 verdict는 그대로다」 | 연결 | — |
| R-QUAL-07 | 같은 면허를 판정하는 경로가 둘 | 타입 + 테스트 | `adapters/src/main/kotlin/bidvector/adapters/extraction/ExtractionToQualification.kt`(텍스트 → 같은 입력 타입) · `adapters/src/main/kotlin/bidvector/adapters/qualification/StoredRequirementLicenseGate.kt`(`LicenseEligibility.judge` 하나) · `adapters/src/test/kotlin/bidvector/adapters/extraction/ExtractionToQualificationTest.kt` | 연결 | 판정 함수 둘 금지 architecture test 없음 |

### 6. KONEPS 수집

| ID | 요지 | 형태 | 경로 | 상태 | 잔여·비고 |
| --- | --- | --- | --- | --- | --- |
| R-COL-01 | HTTP 200 + 에러 `resultCode` 가 「데이터 없음」 | 계약 + 테스트 | `procurement/src/main/kotlin/bidvector/procurement/CollectionPolicy.kt` `ResultCodeCategory`·`resultCodeCategories` · `adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsEnvelopeOutcome.kt` `Unclassified` · `adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsOpenApiNoticeSourceTest.kt` 「resultCode 자체가 부재하면 Unclassified 로 비재시도 실패한다」 · fixture `koneps-collection-013` | 연결 | 범주표는 16 코드(`CollectionPolicyTest`), ledger 문면은 17 |
| R-COL-02 | rate-limit 버킷 없음, `unknown` 재시도 가능 | 타입 + 테스트 | `ResultCodeCategory`(`QUOTA_EXCEEDED`·`RETRYABLE`·`NOT_RETRYABLE` 선언) · `adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsGatewayErrorEnvelopeTest.kt` 「429 속도 한도는 그대로 재시도된다 — 일 한도와 다른 축이다」·「일 한도 초과는 즉시 멈춘다」 · `procurement/src/test/kotlin/bidvector/procurement/CollectionAttemptLedgerTest.kt` | 연결 | `unknown` 재시도 여부는 `OPEN-OPS-01` 대기(현재 비재시도) |
| R-COL-03 | 문자열 부분일치 분류가 메시지 변경에 깨짐 | 계약 + 테스트 | `KonepsOpenApiNoticeSourceTest` 「미지 resultCode 는 Unclassified 로 비재시도 실패한다」 · `app/src/test/kotlin/bidvector/app/architecture/TruncationCauseClassificationGateTest.kt` | 연결 | 분류 불가 계수의 리포트 노출 test 는 확인하지 못함 |
| R-COL-04 | 이중 오작동이 무한 루프 → orphan | 계약 + 테스트 | `procurement/src/main/kotlin/bidvector/procurement/Accounting.kt` `TruncationCause.RepeatedPage` · `KonepsOpenApiNoticeSourceTest` 「totalCount 없음 + 같은 페이지 반복이면 유한 페이지에서 truncated 로 끝난다」(상한 5 전 2호출) · `CollectionAttemptLedgerTest` 「절단 사유가 확정 실패와 일시 실패를 가른다」 · fixture `koneps-collection-022` | 연결 | — |
| R-COL-05 | `or` 폴백 사슬로 마감 시각 의미 상실 | 계약 + 테스트 | `procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt`(`DEADLINE_AT` 단일 원천, 폴백 없음) · `procurement/src/test/kotlin/bidvector/procurement/CanonicalizeTest.kt` 「마감일시 필드가 없으면 deadlineAt 은 null 이다 — 지어내지 않는다」 · `procurement/src/test/kotlin/bidvector/procurement/CanonicalizeBlankValuesTest.kt` | 연결 | ledger 문면(출처 동반 타입)과 다른 형태 — 폴백 자체를 없앴다 |
| R-COL-06 | 수집 실패가 합성 공고 저장 + 성공 계수 | 테스트 | `app/src/test/kotlin/bidvector/app/collection/CollectionRunnerTest.kt` 「절단이 있으면 실행이 끝나도 종료 코드는 2 이다」 · `Accounting.kt` `truncated` | 연결 | 합성 출처 운영 write 의 컴파일 차단 없음 — 합성 생성 경로 자체가 없다(COL-10 `폐기`) |
| R-COL-07 | 재개 커서가 `NULL` 을 진행 상태로 겸용 | 타입 + 테스트 | `procurement/src/main/kotlin/bidvector/procurement/CollectionAttemptLedger.kt` `AttemptOutcome` · `workflow/src/test/kotlin/bidvector/workflow/collection/CollectOpeningResultsUseCaseTest.kt` 「원문이 있어도 원장이 미완이라고 하면 다시 부른다」·「원문은 적재됐는데 결말 줄이 없으면 다시 부른다」 | 연결 | 도달 불가 집합 크기 리포트 없음 |
| R-COL-08 | 유한 쿼터를 마감된 공고에 소진 | — | 없음. `procurement/src/main/kotlin/bidvector/procurement/DetailFetch.kt` 게이트에 마감·우선순위 축이 없다 | 미연결 | COL-04 acceptance 「감시조건 매칭 열린 공고가 앞선다」 미구현 |

### 7. 비동기

| ID | 요지 | 형태 | 경로 | 상태 | 잔여·비고 |
| --- | --- | --- | --- | --- | --- |
| R-ASYNC-01 | 임베딩 백필 적체 21,321건 무신호 | — | 없음 | 미연결 | `OPEN-OPS-10` 활성. outbox 에도 깊이 지표 없음 |
| R-ASYNC-02 | 「투입 ≤ 소진」이 주석에만 | — | 없음 | 미연결 | V2 는 주기 생산자 없이 일회 러너다(관찰). 구성 간 불변식 로드 검사는 없다 |
| R-ASYNC-03 | lease 경합 패자 즉시 포기로 카테고리 기아 | 구조 + 테스트 | `app/src/main/kotlin/bidvector/app/collection/CollectionProperties.kt`(`categories` 가 **한 러너의 목록**) · `app/src/test/kotlin/bidvector/app/wiring/CollectionWiringTest.kt` 「once 와 유효한 설정이면 러너가 정확히 하나 있고 업종 소스가 설정 순서대로 조립된다」 · `adapters/src/main/kotlin/bidvector/adapters/snapshot/RunStateLock.kt` + `adapters/src/test/kotlin/bidvector/adapters/snapshot/RunStateLockTest.kt` · `app/src/test/kotlin/bidvector/app/collection/OpeningBudgetE2ETest.kt` 「다른 프로세스가 잠금을 들고 있으면 아무것도 부르지 않고 끝난다」 | 연결 | **r1 정정(verifier R1-M-3)** — 앞 판은 relay 의 advisory lease(`PostgresAdvisoryLockLease` 「종류가 다르면 서로를 막지 않는다」 · `LEASE_BUSY 3`)를 들었으나 그 lease 의 production 사용자는 알림 relay 하나이고 **수집은 실행 상태 디렉터리의 파일 자물쇠**를 쓴다 — 인용 경로를 바꿔도 수집 기제가 그대로여서 연결이 아니었다. 실제 예방은 둘이다: ① 기아를 만든 **형태 자체가 없다**(업종별 작업이 하나의 lease 를 다투는 대신 **한 run 이 업종 목록 전부를 돈다**) ② 같은 디렉터리로 업종별 run 을 띄우면 패자가 사이클을 건너뛰지만 **조용하지 않다**(`ALREADY_RUNNING` exit 3, 호출 0). 잔여: 패자의 bounded retry 없음(다음 cron 주기에 맡김) · 업종별 run 을 띄우는 운영 형태를 막는 게이트는 없다 |
| R-ASYNC-04 | 비 PostgreSQL 에서 lease 무조건 성공 | 테스트 | `PostgresAdvisoryLockLease`(dialect 분기 없음) · `PostgresAdvisoryLockLeaseTest` 「임대를 쥔 동안 두 번째 획득은 Busy 이고 본문을 부르지 않는다」 · Testcontainers 하네스 `adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt`(Docker 부재는 실패) | 연결 | — |
| R-ASYNC-05 | outbox claim `SKIP LOCKED` dialect 분기 | 테스트 | `adapters/src/test/kotlin/bidvector/adapters/event/OutboxClaimConcurrencyTest.kt` 「두 워커가 동시에 claim 하면 같은 행이 두 번 배달되지 않는다 — SKIP LOCKED」(실 PostgreSQL) | 연결 | — |
| R-ASYNC-06 | 주기 메시지 수명 없음, 백로그 무한 증가 | 계약 + 테스트 | `workflow/src/test/kotlin/bidvector/workflow/event/InboxDedupPropertyTest.kt` 「duplicate·out-of-order 수신은 도착 순서와 무관하게 같은 최종 처리 집합…」 · `adapters/src/test/kotlin/bidvector/adapters/e2e/PipelineRedeliveryE2ETest.kt` · `adapters/src/test/kotlin/bidvector/adapters/e2e/PipelineRestartConvergenceE2ETest.kt` · `app/src/test/kotlin/bidvector/app/wiring/OneShotRunnerGuardTest.kt` | 연결 | 메시지 브로커가 없어 「만료 메시지 미소비」는 대상이 없다. 수집 write 쪽 중복 tick test 는 `NoticeVersioningTest` 재시도 append 뿐 |
| R-ASYNC-07 | `id ASC` 선택으로 노후도 상한 기아 | — | 없음 | 미연결 | 노후도 상한을 주장하는 재수집 경로가 아직 없다 |
| R-ASYNC-08 | 관측이 「생산된 행」 기반 | — | 없음 | 미연결 | `RelayExitCodeTest` 「집었는데 하나도 전달하지 못하면 INCOMPLETE 다」는 러너가 돈 경우만 본다. 소비자가 서 있으면 신호가 없다(OPS-04 미구현) |

### 8. ML

| ID | 요지 | 형태 | 경로 | 상태 | 잔여·비고 |
| --- | --- | --- | --- | --- | --- |
| P-ML-01 | (유지할 예방책) 학습·추론이 같은 피처 모듈 import | 게이트 | `ml-engine/pyproject.toml` import-linter 계약 「serving/inference 층 — features·contracts 위로만 의존」·「training/evaluation 층 — features·contracts 위로만 의존」 · `ml-engine/tests/gates/test_import_contracts.py` `test_real_ml_engine_import_contracts_pass` · `ml-engine/tests/registry/test_artifact.py` `test_feature_names_order_mismatch_is_rejected` | 연결 | 피처 정의 이중화 자체를 잡는 검사는 없다(층 계약이 공유 모듈 경유를 강제) |
| R-ML-01 | 학습 코퍼스 69% 가 서빙 밖 모집단 | — | 없음 | 미연결 | 모집단 **선언**은 있다 — `ml-engine/tests/registry/test_artifact.py` `test_undeclared_sample_scope_is_rejected` · `ml-engine/tests/evaluation/test_backtest_sample_scope.py` `test_rows_outside_the_scope_reject_the_snapshot`. 서빙 대조·분포 차이 승격 거부는 없다 |
| R-ML-02 | 피처 커버리지가 백필 진행 상태 | 계약 + 테스트 | `contracts/proto/bidvector/ml/v1/common.proto` `MissingReason`(`NOT_COLLECTED_YET` ≠ `NOT_APPLICABLE`) · `ml-engine/tests/features/test_facts.py` `test_from_proto_missing_reason_unspecified_is_malformed` · `ml-engine/tests/features/test_require_declared.py` `test_require_declared_none_is_undeclared_result_not_default` | 연결 | 커버리지 출처 미선언 필드의 피처 공간 진입을 막는 정적 검사 없음 |
| R-ML-03 | 비율 rolling origin 으로 홀드아웃 100% 겹침 | 테스트 | `ml-engine/src/ml_engine/evaluation/windows.py` `plan_evaluation_windows`·`holdout_overlaps` · `ml-engine/tests/evaluation/test_windows.py` `test_no_fraction_based_split_api_exists`·`test_plan_evaluation_windows_rejects_overlapping_maturity_windows`·`test_week_maturity_contains_excludes_end_boundary` | 연결 | — |
| R-ML-04 | 성숙도 편향 방향 측정 불가로 embargo 회피 | — | 없음. `ml-engine/src/ml_engine/inference/maturity.py` `SettlementObservation` 은 `opened_at`·`settled` 만 갖는다 | 미연결 | `OPEN-ML-04` 결정의 정산 관측 시각 필드 미구현 |
| R-ML-05 | cutoff 가 「개찰 = 라벨 가용」 가정 | — | 없음 | 미연결 | 창 경계·embargo 기준이다(`ml-engine/tests/evaluation/test_backtest_verdict.py` `test_window_history_stops_one_embargo_before_the_window_starts`) — legacy 와 같은 회피 |
| R-ML-06 | 「못 이겼다」와 「못 쟀다」 미구별 | 타입 + 테스트 | `ml-engine/src/ml_engine/evaluation/verdict.py` `NO_EVALUABLE_WINDOW`·`UNDERPOWERED` · `ml-engine/tests/evaluation/test_verdict.py` `test__trial_outcome_underpowered_when_improvement_below_mde`·`test__trial_outcome_failed_when_model_worse_but_evaluable` · `test_backtest_verdict.py` `test_seed_instability_is_detected_when_the_sign_flips` | 연결 | — |
| R-ML-07 | golden 일괄 재생성 명령 존재 | — | 없음 | 미연결 | 구분 자체는 있다 — manifest `classification` 과 conformance 러너의 `authoritative` 필터. 재생성 경로 부재를 잠그는 검사는 없고, `ml-engine/tests/evaluation/_backtest_fixture.py` 는 합성 입력 재생성 진입점을 갖는다 |
| R-ML-08 | 응답 계약 필수 필드가 구현 교집합에서 도출 | 게이트 | `build-logic/src/main/kotlin/bidvector/buildlogic/ContractGateTask.kt`(`buf breaking --against` 승인 태그) · `config/quality/contract-policy.properties` `approved.tag` · `build-logic/src/test/kotlin/bidvector/buildlogic/ContractGateChecksTest.kt` | 연결 | — |
| R-ML-09 | 미학습 공종 가드가 부수 효과로 생김 | 타입 + 테스트 | fixture `ml-kernel-001`·`002` · `ml-engine/tests/inference/golden/test_kernel_golden.py` `test_ml_kernel_001_002_gate_distinguishes_never_trained_from_shallow` · `ml-engine/tests/inference/test_predict.py` `test_segment_availability_zero_rows_is_untrained` | 연결 | — |
