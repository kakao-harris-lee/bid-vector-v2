# M4/4B-5 checklist

base_sha: `9eddf7525b74f89ce279acbb3adf4948f05c25f1`
head_sha: (리뷰 요청 시점의 `git rev-parse HEAD`)

## 우회 ↔ 코드 ↔ 실측 대응표(scope.md 「우회 후보(≥5)」)

| # | 우회 | 차단 코드 | 실측 |
| --- | --- | --- | --- |
| 1 | base 없음 → 0.5(legacy sentinel) | `deriveBudgetCapture`의 `base == null \|\| base.export().won <= 0L → Absent(BaseAmountMissing)` | `BudgetCaptureDerivationTest`(base null·0 이하 둘) |
| 2 | 밴드 경계 6h 정확히·6h+1ns | `Ladder.Ascending`/`Descending`의 `<=`/`>=` 포함 비교 | `BandTest`(오름·내림 각 경계 전수)·`UrgencyDerivationTest`(8 경계)·`ExecutionComplexityDerivationTest`(6h 경계) |
| 3 | 합 ≠ 1 표 | `DerivationPolicyData.init`의 `requireFullWeightMap`(scale 4 비교) | `DerivationPolicyDataTest`(marginWeights·complexityWeights 각 합≠1 생성 실패) |
| 4 | 밴드 역순·중복 상한 정책 | `Ladder.Ascending`/`Descending.init`의 `requireStrictlyOrdered`(엄격 단조) | `BandTest`(역순·중복 생성 실패) |
| 5 | `hits` 음수 | `KeywordHits.init`(`count >= 0`) | `ExecutionComplexityDerivationTest`(hits=20 상한 test — 음수 자체는 타입 `init`으로 생성 단계 차단, 별도 test 불필요·`KeywordHits` 값 타입 자체가 우회를 없앤다) |
| 6 | `floor = 1` 분모 0 | `MarginDerivation.floorHeadroomOf`의 `floor.compareTo(ONE) == 0 -> recommended` 분기 | `ExpectedMarginDerivationTest`("floor 1 이면 분모 0 이라 headroom 을 rec 로 되돌린다") |
| 7 | `alignmentTolerance` 등 리터럴 | 전부 `DerivationPolicyData` 필드(main 에 숫자 리터럴 없음 — `verifier grep` 대상) | `DerivationPolicyDataTest`(constant range 위반 생성 실패 다섯) |
| 8 | ④ 전 항 부재 | `ComplexityInputs.keywordHits`·`remaining`·`loadRatio` 는 non-null(타입이 「전부 부재」를 구성 불가로 닫는다) | `ExecutionComplexityDerivationTest`("budget match capacity 셋 다 부재하면 keyword deadline loadRatio 셋만") — 그 셋조차 항상 present 임을 보인다 |
| 9 | `Money` 를 `BigDecimal` 로 꺼내 나눔 | `deriveBudgetCapture`는 `bidRateAgainst`(1B)만 쓴다 — main 에 `Money` 타입에 대한 직접 나눗셈 없음(④ budget 은 `.export().won`을 밴드 **비교**에만 쓰고 나눗셈하지 않는다, 아래 「값 획득 축」 참고) | `BudgetCaptureDerivationTest`(1B 산술 왕복·VAT 불일치·미선언 출처 — `bidRateAgainst` 내부 전건이 그대로 도달함을 확인) |

## 값 획득 축(D-4B5-4 판단 — ④ budget 밴드는 왜 「Money 산술」이 아닌가)

`ComplexityInputs.budget: BaseAmount?` 의 밴드 비교(`budgetSignalOf`)는 `Money.export().won`
(공개 read API)로 얻은 `Long` 매그니튜드를 정책 `Long` 임계(`500_000_000` 등)와 비교한다.
이것이 우회 (9)의 대상이 되는 "두 `Money` 를 산술로 섞는 것"과 다른 이유:

- 두 `Money` 값을 서로 비교/스케일하는 것이 아니라 **하나의 `Money` 를 고정 정책 임계와**
  비교한다 — `bidvector.decision.FloorTypes.AssessmentBand.contains(fraction)`(기존
  4B-1 코드, `Rate.fraction` 을 고정 경계와 비교)와 같은 축이다.
- vat/provenance 안전검사(`compareKnownVat`)는 두 `Money` 를 산술로 섞을 때만 의미가
  있다 — 정책 임계는 애초에 `Money` 가 아니라 순수 크기(출처·과세 개념이 없다)라
  vat/provenance 전건을 걸 대상 자체가 없다.
- 나눗셈이 없다(비교만) — D-4B5-4 가 금지한 것은 "직접 나눗셈"이다.

이 판단은 verifier 표적 항목이다 — 동의하지 않으면 `budget` 을 4B-6 이 `Long`(won)으로
미리 변환해 넘기는 계약으로 되돌릴 수 있다(타입만 바뀌고 로직은 동일).

## D-4B5-4 판단 → 계약 갱신 2026-09-10 #1 — ⑤ deriveCompetitiveness 제외(`OPEN-4B5-COMPETITIVENESS`)

구현 레인이 D-4B5-4 「없으면 멈추고 보고」대로 멈춘 실측(`Basis` enum·`MoneyArithmetic.kt`
전체 `operator fun` 목록·`compareKnownVat` 시그니처)을 팀장이 계약 갱신으로 확정했다:

- `Basis`(BASE_AMOUNT·ESTIMATED·YEGA·BID·ALLOCATED_BUDGET·AWARD) 에 "시장평균" 축이
  없다 — `marketAverage` 를 `Money` 로 타입화할 생성 경로 자체가 없다.
- `Money` 스케일 곱 연산은 `BaseAmount.times(BidRate): UnroundedBidAmount` 하나뿐이다
  (`grep -rn "operator fun" shared-kernel/src/main/kotlin` 결과 1건).
- `compareKnownVat` 는 오버로드 여섯이 전부 **같은 타입** 쌍만 받는다(basis 혼입을
  막기 위한 설계, `Money.kt` KDoc) — 밴드 임계처럼 "정책 비율로 스케일한 상대 비교"에는
  구조적으로 맞지 않는다.
- **뿌리는 더 깊다**(팀장 계약 갱신) — V2 는 「투찰 시장 평균」을 수집·정의한 적이
  없다(capability-map 에 그 축이 없고, legacy 는 예산을 sentinel 로 썼다). 산술의
  부재가 아니라 **fact 의 부재**다.

처분: `deriveCompetitiveness` 는 만들지 않는다. `DerivationPolicyData` 에도
competitiveness 밴드·비율 필드를 두지 않는다(소비자 없는 필드 금지). ⑤ 는
`workloadNotCollected`(⑦)와 같은 축의 상수 `competitivenessNotCollected(): DerivationOutcome<UnitScore>
= Absent(MarketAverageMissing)` 하나만 둔다(`WorkloadDerivationTest`). `capability-map.md`
§14 등재는 팀장(세션 모델) 소관 — `docs/discovery/capability-map.md` 는 이 slice의
in_scope 밖이라(scope.md) 이 레인이 직접 편집하지 않는다.

## `init` ↔ 판정층 짝 (설계 검토 대응)

| 값 타입 `init` | 판정층 짝 |
| --- | --- |
| `Band`(값 자체는 제약 없음, 순서는 `Ladder` 가 짊어짐) | `Ladder.init`(단조)이 밴드 조합 자체를 판정 — 타입 쌍이 같은 파일 안에서 닫힌다 |
| `KeywordHits.count >= 0` | 문자열 매칭(카운트 산출)은 4B-6 — 그 카운터가 이 하한을 지키는지는 4B-6 책임(`checklist` 알려진 제한으로 아래 명시) |
| `DerivationPolicyData`(가중치 합·범위·단조) | `DERIVATION_POLICY.resolve(...)` 출하 인스턴스 자신이 이 `init`을 통과해야 컴파일·런타임 둘 다 서므로 짝이 같은 파일(`DerivationPolicyDataTest`)에 있다 |

## 알려진 제한

- **⑤ `deriveCompetitiveness` 미구현**(위 계약 갱신 #1) — `OPEN-4B5-COMPETITIVENESS`(scope.md
  신설, capability-map §14 등재는 팀장 소관). 시장 평균 fact 의 정의·수집(M3 후속 —
  개찰 결과 집계 축)이 먼저 있어야 이 함수가 의미를 갖는다.
- **fact 획득·`Clock`·문자열 매칭·workload 집계·추천가 산출은 4B-6 소관** — 이 slice는
  이미 계산된 값(`Duration?`·`Money?`·`UnitScore?`·`KeywordHits`)만 받는다.
- **`KeywordHits.count` 하한만 이 slice가 검증**한다 — 실제 카운트 산식(키워드 14개
  목록)은 4B-6 이 짓고, 그 카운터가 이 `init` 을 지키는지는 4B-6 evidence 가 확인한다.
- **corpus 없음** — `OPEN-4B5-CORPUS`(밴드·합성 전수 표의 corpus 승격, 병합 뒤 curator).
- **정책 값 승인 대기** — `OPEN-4B5-POLICY-VALUES`(`policy-values.md`), §4
  `budgetCaptureRounding` 은 legacy 값이 아니라 구조적 placeholder(정밀도 민감도
  실측 없음).
- **`budgetCaptureRounding` 스케일(6)의 근거가 실측이 아니다** — 위와 같음, 승인
  대상.

## 재작업

0/5.

## 사용자 승인

(대기)
