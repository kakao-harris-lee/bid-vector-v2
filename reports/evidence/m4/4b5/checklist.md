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
| 6 | `floor = 1` 분모 0 | `MarginDerivation.floorHeadroomOf`의 `floor.compareTo(ONE) == 0 -> recommended` 분기 — `MarginInputs.init`(verifier r1 F-1)이 `floor ≤ 1`을 보장해 이 분기 밖은 항상 `0 < floor < 1`이다 | `ExpectedMarginDerivationTest`("floor 1 이면 분모 0 이라 headroom 을 rec 로 되돌린다", F-1 경계·회귀 test 다섯) |
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

## verifier r1 반영(not-ready → 수정 라운드 1, 재작업 1/5)

| finding | 처분 |
| --- | --- |
| **F-1 (high)** ③ 가 상한 없는 `Rate` 를 clamp 하지 않아 `floor>1`에서 headroom 이 뒤집힘 | 채택 (a) — `MarginInputs.init` 에서 `recommendedRate`·`floorRate`·`predictedRate` 를 `≤ 1`로 닫는다(`DerivationInputs.kt`). 판정층 짝은 상류(2B `D-2B-8`·4D-1 `ContractViolation`). `ExpectedMarginDerivationTest` 경계(1.0 통과·1.0000001 거부) + 우회 재현(floor 1.2 거부) + 회귀(rec 0.7·floor 0.9 legacy 일치, 0.445) 다섯 |
| **F-2 (medium)** 출하 `budgetCaptureRounding` 대조 test 없음(6→2 변이 생존) | `DerivationPolicyDataTest`에 출하 인스턴스 `scaleDigits`·`mode` 직접 대조 test 추가 + `BudgetCaptureDerivationTest`에 정밀도 민감 표본(333,333,000/1,000,000,000 = 0.333333, scale 2 면 0.33 으로 달라짐) 추가 |
| **F-3 (medium)** `floorHeadroomOf` KDoc 「legacy 와 사실상 같다」가 사실과 다름 | KDoc 정정(`MarginDerivation.kt`) — 실측 차(0.7225 대 0.5325) 명시, 「거동은 scope.md ③ 의 의도된 선택」으로 재서술. `policy-values.md` §3 「의도된 갈림」에도 등재 |
| **F-4 (medium, 장부)** rollback.md 목록·계수 낡음, capability-map.md 누락 | rollback.md 「대상 파일 목록」·restore 명령에 `docs/discovery/capability-map.md` 추가, `git diff --name-status` 재산출(D/M 갱신) |
| **F-5 (low, 장부)** commands.md 의 S-0 선언 head 가 도달 불가(rebase 뒤 amend) | commands.md 의 해당 절을 실제 조상 SHA(`e47370e`, 이번 라운드 head)로 정정 |
| **F-6 (low)** `renormalizedWeightedSum`↔4B-4 `weightedScoreOf` 구조 중복 | 이번 라운드 수정 대상 아님(4B-4 파일 편집 금지) — 알려진 제한에 등재, 4B-6 소관 |
| **F-7 (low)** legacy 2자리 반올림 미재현 | `policy-values.md` §3 「의도된 갈림」에 등재(의도된 재설계, 값 손실 미실측) |
| **F-8 (low, 계약)** D-4B5-3 「경계 의미는 같다」 부정확 | scope.md 는 팀장(세션 모델) 소유라 이 레인이 수정하지 않는다 — **팀장이 커밋 `9817dda`로 직접 정정, 닫힘** |

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
- **`renormalizedWeightedSum`(`MarginDerivation.kt`)과 4B-4 `weightedScoreOf`
  (`PriorityComposition.kt`)가 구조적으로 중복**(verifier r1 F-6) — 같은 알고리즘,
  `cpdCheck` 는 토큰 문턱 아래라 통과한다. 4B-4 파일 편집 금지가 이 slice의 계약이라
  이번 라운드에서 합치지 않는다 — 4B-6 이 공용 커널로 올리거나 4B-4 함수를 `internal`
  승격할 때 처리(계약 갱신 필요).
- **D-4B5-3(「시간 밴드 경계 의미는 같다」) 문면이 부정확했다**(verifier r1 F-8) — legacy
  는 `deadline_hours_remaining: int` (정수 절사) 를 받아 6.9h 가 `6` → `≤6` 밴드로
  들어가지만, V2 `Duration` 은 6.9h 그대로 비교해 `>6h` 밴드로 떨어진다. **팀장이
  `scope.md` D-4B5-3 을 직접 정정했다**(커밋 `9817dda`, 문면 소유가 세션 모델이라 이
  레인은 수정하지 않았다) — 「경계 의미는 legacy 와 같지 않다 · 잘림을 재현하지 않는다
  (intentional-redesign)」로 갱신. **닫힘.**

## 재작업

1/5(verifier r1 not-ready → 수정 반영).

## 사용자 승인

(대기)
