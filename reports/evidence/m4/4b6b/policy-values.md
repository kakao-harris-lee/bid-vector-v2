# M4/4B-6b 정책 값 — OPEN-4B6B-POLICY-VALUES(**사용자 승인 2026-09-12 — 종결**)

정본은 이 문서다 — `OpportunityPolicyData`의 4B-6b 신규 슬롯 값을 바꾸려면 이 문서를
먼저 갱신한다(3A `KONEPS_COLLECTION_POLICY`·4B-4/4B-5 관례).

## §1 예산(D-4B6B-5)

| 슬롯 | 값 | 근거 |
| --- | --- | --- |
| `embeddingBudget` | `Duration.ofSeconds(2)` | ADR 0010 D-1 "보수적 상한 + 측정 의무" — 4D-2 `EmbedTextPort` 단일 호출의 초기 추정치. 실측 갱신은 4D-2 운영 데이터 축적 이후(활성 유지, 4B-4 `normEpsilon` 관례와 같은 성질) |
| `predictionBudget` | `Duration.ofSeconds(3)` | 같은 근거 — 4D-1 `BidPredictionPort`는 GBM 추론이라 임베딩보다 약간 더 준다(추정, 실측 아님) |

## §2 release 선택자·목적함수(D-4B6B-5)

| 슬롯 | 값 | 근거 |
| --- | --- | --- |
| `releaseSelector` | `ModelReleaseSelector.LatestPromoted` | 임베딩·예측 두 호출에 같은 값을 넘겨야 D-4B6B-3(release 일관성)이 실제로 의미가 있다. `Exact`(특정 release 고정)는 이 slice가 쓸 이유가 없다(운영 배선의 몫) |
| `objective` | `OptimizationObjective.SCENARIO_TRIPLE` | 계약(2B)이 지원하는 값이 이것뿐이다(`OPEN-2B-OBJECTIVE-VALUES` 미결) |

## §3 카테고리 offset(D-4B6B-5)

| 슬롯 | 값 | 근거 |
| --- | --- | --- |
| `categoryOffset` | `BigDecimal.ZERO` | STR-05(카테고리별 override 산식)는 이 slice 밖 — 값을 지어내지 않고 0(무보정)으로 고정한다. `PriorityPolicyData.categoryOffsetMin/Max`([-0.20, 0.20])는 4B-4가 이미 승인했고 0은 그 범위 안이라 `SemanticMatch.of`가 항상 통과시킨다 |

## §4 recommendedAmountRounding(D-4B6B-6 구현에 필요, 계약 착수 문서 밖 추가)

| 슬롯 | 값 | 근거 |
| --- | --- | --- |
| `recommendedAmountRounding` | `RoundingPolicy(scaleDigits = 0, mode = RoundingMode.HALF_UP)` | `baseAmount × BidRate(candidates.base)`를 `BidAmount`로 반올림하는 데 shared-kernel `MoneyArithmetic.roundedWith`가 `Resolution.Resolved<RoundingPolicy>`를 요구한다. `scaleDigits=0`은 `data-dictionary.md` §1.1 정의 ①(금액은 원 단위 정수)이 이미 닫은 구조적 사실이라 값이 아니다. `mode`는 `RoundingPolicy` KDoc이 명시한 열린 정책값(`OPEN-DIC-10`)이라 `HALF_UP`을 코드 리터럴로 두지 않고 이 슬롯에 둔다 — `DerivationPolicyData.budgetCaptureRounding`·decision `ProvenancePolicyData.integerRoundingMode`와 같은 값(사실상 코드베이스 전역 관례). **`budgetCaptureRounding`(scale 6)를 재사용하지 않는다** — `divideForRate`(`MoneyArithmetic.kt`)가 실제로는 고정 `MathContext(20)`만 쓰고 `RoundingPolicy.scaleDigits/mode`를 그 나눗셈에 쓰지 않는다(policyVersion 라벨링에만 쓰인다, 조사 실측) — 재사용하면 의미가 다른 자리에 값을 얹는 것이라 별도 슬롯을 새로 둔다 |

## 해소 조건

`OPEN-4B6B-POLICY-VALUES`는 위 넷(예산 둘·selector/objective·categoryOffset)의 값
자체가 실측(4D-1/4D-2 운영 SLA·STR-05 산식 확정)으로 갱신될 때 닫힌다. §4는 계약
착수 문서가 예견하지 못한 기술적 필수값이라 별도 결정 — HALF_UP·scale 0 조합은
바뀔 여지가 낮다(구조적 사실 + 코드베이스 전역 관례)고 판단해 승인 대기에 포함은
하되 우선순위는 낮다.

## 사용자 승인

**2026-09-12 — `OPEN-4B6B-POLICY-VALUES` 종결.** slice 4B-6b 종결 승인과 함께 받았다(§1 예산 2s/3s · §2 `LatestPromoted`·`SCENARIO_TRIPLE` ·
§3 offset 0 · §4 반올림 scale 0 HALF_UP). 예산 둘은 초기 추정치로 4D-1·4D-2 운영 실측 뒤 갱신 대상(활성 유지). 정본은 이 문서 —
evidence `reports/evidence/m4/4b6b/checklist.md` 「사용자 승인」 절.
