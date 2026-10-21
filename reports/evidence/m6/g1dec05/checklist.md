# M6/G1-DEC05 — checklist (투찰가 메뉴 DEC-05 + binding 제약 기록 DEC-02)

판정 대상 SHA: (Phase 2 에서 고정)

## Phase 1 — 설계 (코드 0 · Gradle 0)

읽은 것: `scope.md`(정본) · `CLAUDE.md` · `.claude/rules/kotlin-service.md` ·
`docs/discovery/capability-map.md` DEC-01·02·03·04·05·10·11·12 · `reports/evidence/m6/6e1/acceptance-trace.md`
머리(술어 ②)와 DEC-01·02·04·05·10 행 · `reports/evidence/m7/7a/m6-condition3-adjustment-proposal.md` G1·G3 표 ·
`milestone-6.md` 완료 조건 아래 결정 블록 · lane-discipline 메모.

읽은 코드(경로는 전부 worktree 루트 기준):
`shared-kernel/src/main/kotlin/bidvector/sharedkernel/{Money,MoneyArithmetic,Rate,RateArithmetic,Carrier,Derivation,Policy,PolicyTable}.kt` ·
`decision/src/main/kotlin/bidvector/decision/{FloorTypes,FloorShortfallKernel,FloorShortfallPolicyData,FloorOverrideValidation}.kt` ·
`workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionOutcome.kt` ·
`workflow/src/main/kotlin/bidvector/workflow/evaluation/{OpportunityAnalysis,OpportunityPolicyData,PredictionFacts,Ports,CandidateEvaluation}.kt` ·
`app/src/main/kotlin/bidvector/app/wiring/EvaluationWiring.kt` · `app/src/main/kotlin/bidvector/app/http/EvaluationDryRunController.kt` ·
`procurement/src/main/kotlin/bidvector/procurement/{NoticeFacts,Canonicalize}.kt` ·
`decision/build.gradle.kts` · `config/quality/{architecture-policy,size-policy,gate-tests}.properties`.

### 0. 착수 조사가 바꾼 전제 셋 (설계보다 먼저)

**조사 1 — 「하한」은 금액 축에서 성립하지 않는다.** 게시 낙찰하한율(`FloorRate`)은 KDoc·
`capability-map.md` DEC-04 분류 근거가 모두 **예정가격 기준 율**이라고 적는다. 추천 투찰율은
**기초금액 기준**이다. 예정가격은 개찰 전 추첨이라 V2 에 값이 없고, 값이 있어도
`YegaAmount.vatTreatment` 가 `UNKNOWN` 고정이라 예정가를 낀 산술은 전부 `Unmeasurable` 이다
(`Money.kt`). 그래서 **게시 하한율 × 기초금액 = 원 단위 하한**은 basis 가 섞인 계산이고,
그것이 바로 DEC-12 A5·DEC-02 「legacy 형태 처리」가 폐기한 형태다.

V2 가 이미 가진 정직한 다리는 하나뿐이다 — **임계 사정률 = 투찰율 ÷ 하한율**
(`RateArithmetic.criticalAssessmentRate`). 하한 미달 ⟺ 실현 사정률 > 임계. 즉 **게시 하한은
하드 clamp 가 아니라 율 축의 리스크**이고, 그 측정은 이미 ⓐ 인 DEC-04 커널
(`measureFloorShortfall`)이 한다. 이 사실이 아래 (a) 의 「리스크 노트」와 (e) 의 값 질문을
동시에 정한다.

**조사 2 — binding 의 자리는 shared-kernel 이 아니라 이 커널이라고 코드가 이미 적어 뒀다.**
`Carrier.kt` 의 `ROUNDED_BELOW_FLOOR` KDoc: *「"어느 제약에 binding됐는지" 추적은 이 모듈의
순수 반올림 함수가 아니라 그 알고리즘을 구현하는 쪽의 몫이다」*. 따라서 binding 기록은
`decision` 에 두고 `roundedWith` 의 반환 타입은 건드리지 않는 것이 기존 선언과 정합한다.

**조사 3 — 메뉴 후보 비율의 출처는 오늘 ML 하나뿐이고, 그 ML 은 출하에서 고정 미가용이다.**
`BidRateCandidates(conservative, base, aggressive)` 를 만드는 자리는 어댑터 매핑
(`adapters.ml.mapSuccess`) 뿐이고, `conservative`·`aggressive` 의 **production 소비자는 0**
이다(실측: `.base` 만 `PredictionFacts.kt:102·104` 가 읽는다. 타입 KDoc 자신이 「현재 소비자는
0」이라 적는다). 그리고 출하 조립은 `EvaluationWiring.mlAnalysisPort()` 가
`UnavailableMlAnalysis()` **고정**이다(`OPEN-ML-ANALYSIS-WIRING`, D-6A3-2). ML 없이 후보 셋을
낼 수 있는 V2 데이터 축은 없다 — 발주처 밴드(legacy 모드 1)에 대응하는 타입·정책·수집 축이
저장소에 0 이고(`OPEN-4B5-COMPETITIVENESS` 가 「시장 평균 fact 를 정의·수집한 적이 없다」를
이미 등재한다), 공사 floor 앵커(legacy 모드 2)는 조사 1 때문에 **앵커 자체가 금액 축에서
서지 않는다**.

### (a) 타입·커널 스케치 — 이름·모듈·가시성

모듈은 `decision`(ADR 0006 D-2 — 「법정 하한, 추천 후보 평가, reason code」), 새 패키지
`bidvector.decision.menu`. 선례는 같은 모듈의 `bidvector.decision.priority` 이고,
`architecture-policy.properties` 의 허용 목록은 접두 `bidvector.decision` 이라 하위 패키지가
따로 등재를 요구하지 않는다(실측). `decision` 의 의존은 `shared-kernel` 하나다
(`decision/build.gradle.kts`) — 메뉴가 `procurement.Notice` 를 직접 보지 않는다는 뜻이고,
입력은 호출부가 값으로 푼다.

| 파일 | 타입 | 가시성 | 요지 |
|---|---|---|---|
| `menu/BidStance.kt` | `enum class BidStance { Safe, Recommended, Aggressive }` | public | 태도 어휘. **라벨 대응을 한 자리에 선언**: `BidStance.rateOf(candidates: BidRateCandidates): Rate` — `Safe→conservative` · `Recommended→base` · `Aggressive→aggressive`. 이 함수가 두 라벨 체계의 **유일한** 변환 자리다(두 자리에 두면 갈린다). |
| `menu/BidMenuDisclosure.kt` | `enum class BidMenuDisclosure { YegaDrawnPrivately, NotAutoSubmitted }` | public | 공통 고지 **두 문장의 자리**. 한국어 완성 문장을 도메인이 들지 않는다(DEC-12 A1 — legacy 가 문구를 영속해 감사 기록이 틀린 채 남았다). 문장 렌더링은 표면(7C ②) 몫. `Offered` 가 두 값 **전부**를 싣는 것을 `init` 이 강제한다. |
| `menu/BidMenuGrounds.kt` | `data class BidMenuGrounds(bidRate: BidRate, floorRate: FloorRate, criticalAssessmentRate: Derived<AssessmentRate>, baseProvenance: Provenance, policyVersion: PolicyVersion)` | public(생성자 public) | 근거. **위조가 구조로 막힌다** — `Derived`·`BidRate` 의 생성자가 shared-kernel `internal` 이라 `decision` 밖에서 지어낼 수 없고, `criticalAssessmentRate` 는 `RateArithmetic` 만 만든다. |
| `menu/BidMenuRisk.kt` | `sealed interface BidMenuRisk { data class ShortfallFrequency(val judgement: FloorShortfallJudgement) ; data class NotMeasured(val reason: FloorUnmeasurableReason) }` | public | 리스크 노트. **DEC-04 커널의 출력을 그대로 싣는다**(새 축을 만들지 않는다). 낙찰 확률 칸 없음 — 이 타입에 `probability`·`winRate` 류 필드가 없다는 것이 DEC-04 정직 명세의 집행이다. |
| `menu/BindingConstraint.kt` | `sealed interface BindingConstraint { data object Unconstrained ; data object BandLower ; data object BandUpper ; data object PublishedFloor ; data class Provisional(val kind: ProvisionalConstraint) }` | public | **DEC-02 의 빈 자리.** 제약마다 **다른 값**이라 「제약 하나를 지워도 결과가 같다」가 성립할 수 없다. `Provisional` 은 예산 상한·계약 역전(`OPEN-DEC-03` 재결정 대상)의 자리이고 **세지 않는다** — 타입 이름이 그 잠정성을 진술한다. |
| `menu/BidMenuOption.kt` | `@ConsistentCopyVisibility data class BidMenuOption internal constructor(stance, amount: Derived<BidAmount>, binding: BindingConstraint, risk: BidMenuRisk, grounds: BidMenuGrounds)` | 생성자 `internal` | **근거 없는 옵션을 만들 수 없다** — 다섯 인자 전부 필수(기본값 0), 생성자는 `internal`, 유일한 생성 경로는 `composeBidMenu`. `decision` 밖에서는 읽기만 된다. |
| `menu/BidMenuOutcome.kt` | `sealed interface BidMenuOutcome` — `Offered internal constructor(safe, recommended, aggressive: BidMenuOption, disclosures: Set<BidMenuDisclosure>, policyVersion)` · `BandCollapsed(lower: Rate, upper: Rate, cause: CollapseCause)` · `Unmeasurable(reason: MenuUnmeasurableReason)` · `SourceAbsent(reason: MenuSourceAbsence)` | `Offered` 만 `internal` 생성자 | 네 갈래 sealed, `when` 전수(`else` 금지). **옵션 셋은 정확히 셋이고 리스트가 아니다**(`BidRateCandidates` 관례) — 「2안만 있는 정상」이 표현 불가능하다. 실패 셋은 공개 생성자(「사유 있는 실패는 어디서든」 — `FloorShortfall.Unmeasurable`·`FloorOverrideOutcome.Rejected` 관례). |
| `menu/BidMenuPolicyData.kt` | `data class BidMenuPolicyData(floorComparison: FloorBoundComparison, amountRounding: RoundingPolicy, criticalRateRounding: RoundingPolicy)` + `val BID_MENU_POLICY: EffectiveDatedPolicy<BidMenuPolicyData>` | public | `*PolicyData` 관례(`FloorShortfallPolicyData`·`PriorityPolicyData`). **이 타입은 값을 지어내지 않는다** — (e) 참조. legacy 매직 셋(`_BASE_ADJUSTMENT`·`_DISPERSION_REFERENCE`·`_MAX_ADJUSTMENT`)은 채택하지 않는다(DEC-05 「legacy 형태 처리」). |
| `menu/BidMenuKernel.kt` | `fun composeBidMenu(input: BidMenuInput, policy: Resolution.Resolved<BidMenuPolicyData>): BidMenuOutcome` + `data class BidMenuInput(base: BaseAmount, candidates: BidRateCandidates, publishedFloor: FloorRate?, samples: List<AssessmentRate>, shortfallPolicy: Resolution.Resolved<FloorShortfallPolicyData>)` | public | 순수 함수. port·Clock·전역 0. 금액은 `(base * BidRate.recommended(rate)).roundedWith(...)` 로만 만든다 — `decision` 은 `Derived`·`Measurement.Measured` 를 지어낼 수 없으므로(shared-kernel `internal` 생성자) **메뉴의 금액은 반드시 승인된 산술을 거친 값**이다. |

**커널의 순서 고정**(중첩 `if` 대신 단계 함수 — `OpportunityAnalysis.Step` 관례):
① 출처 전건(`publishedFloor == null` → `SourceAbsent`) → ② 율 축 제약 적용(하한 bound 와
후보 밴드) → ③ 밴드 붕괴 판정(상한 ≤ 하한 → `BandCollapsed`) → ④ 태도별 금액 반올림
(`roundedWith(policy.amountRounding, floor = <그 옵션의 하한 금액>)`) → ⑤ 리스크 노트
(DEC-04 커널) → ⑥ 조립.

**②의 하한 bound 를 어디서 얻는가 — 이것이 이 slice 의 중심 설계 질문이다(조사 1).**
금액 축 하한을 쓰려면 basis 를 섞어야 하므로, 쓰지 않는다. 대신 **율 축**에서 쓴다:
임계 사정률 `투찰율 ÷ 하한율` 이 1 미만이면 그 후보는 **어떤 실현 사정률에서도**(사정률 ≥ 1)
하한 미달이다. 즉 하드 제약은 **투찰율 ≥ 게시 하한율**(⟺ 임계 사정률 ≥ 1) 하나로 쓸 수 있고,
그 경계 비교 어휘(`>` 인가 `≥` 인가)만 정책값이다(`ShortfallComparison` 과 같은 꼴). 이 규칙은
**새 수치를 요구하지 않는다** — 1 은 사정률의 정의상 동일점이지 튜닝 상수가 아니다. 다만
「하한율을 투찰율 하한으로 그대로 읽는다」는 **해석 선택**이므로 운영자 승인 대상이다((e)).

④의 `floor` 인자는 그 옵션 자신의 **하한 bound × 기초금액**이다 — 두 값이 같은 기초금액
basis 라 섞이지 않는다. `roundedWith` 가 `ROUNDED_BELOW_FLOOR` 로 막으면 그 옵션은
`Unmeasurable` 이 아니라 **binding = `PublishedFloor`** 로 하한 bound 자체를 채택한 값으로
올라간다(내림 잔차가 하한을 밑도는 경로가 닫힌다).

**알려진 구조 제약 하나**: `BaseAmount.won` 과 `UnroundedBidAmount.raw` 가 shared-kernel
`internal` 이라 `decision` 은 「하한율 × 기초금액」의 `BigDecimal` 을 **만들 수 없다**
(`Money.export()` 는 공개지만 그것으로 산술하면 basis 방어를 스스로 뚫는 것이다). 따라서
④를 그대로 쓰려면 shared-kernel 에 **좁은 추가 하나**가 필요하다. 두 안을 팀장 판단으로 올린다:
- **(안 A, 추천)** `@ConsistentCopyVisibility data class AppliedFloor internal constructor(internal val raw: BigDecimal)` +
  `fun BaseAmount.appliedFloorFrom(rate: BidRate): AppliedFloor`, 그리고 `roundedWith` 의
  `floor: BigDecimal?` → `floor: AppliedFloor?`. **하한은 같은 basis 의 곱에서만 나온다**가
  타입으로 닫힌다 — 지금은 임의의 `BigDecimal` 이 하한 자리에 들어간다. 비용: `ArithmeticTest`
  와 `app/src/test/.../ProvenanceFloorExecutors.kt`(conformance)의 호출부 갱신.
- **(안 B, 최소)** `internal` 을 건드리지 않고 `fun BaseAmount.unroundedFloorFor(rate: BidRate): BigDecimal`
  만 추가. 호출부 갱신 0 이지만 하한 자리의 구멍(임의 `BigDecimal`)은 그대로 남는다.

### (b) 메뉴가 production 경로 어디에서 만들어지는가 — **오늘 소비자가 없다**

출하 조립(`app/src/main/kotlin/bidvector/app/wiring/**`)의 HTTP 표면은 넷이다 —
`/api/evaluation-dry-runs`(`EvaluationDryRunController`) · `/api/strategy` 읽기·편집 ·
세션. 그중 금액을 내는 것은 **하나도 없다**: dry-run 응답(`EvaluationDryRunResponse`)은
공고 ID 배열과 건수뿐인 평탄 스칼라이고(D-6A3-6), 투찰가 칸이 없다.

금액을 **계산**하는 production 코드는 저장소 전체에서 한 자리뿐이다 —
`workflow/.../evaluation/PredictionFacts.kt:124` 의 `budgetCaptureFact`. 그런데 ① 그 값은
밖으로 나가지 않고 `budgetCapture` **점수**의 중간값으로만 쓰이고 ② 그 함수를 품은
`OpportunityAnalysis` 는 출하 조립에 **배선되지 않는다**(`EvaluationWiring` 이
`UnavailableMlAnalysis` 고정, 의존 게이트가 그 사실을 잠근다) ③ 그 호출은 `floor` 인자를 주지
않는다(기본 `null`) — 즉 DEC-02 둘째 bullet 의 하한 보장이 **출하 경로에서 한 번도 발화하지
않는다**.

그러므로 이 slice 안에서 메뉴가 설 production 자리는 없다. 7C ② endpoint 는 M7 몫이고,
`app/**` 출하 조립과 `openapi/**`·`web/**` 는 계약 out_scope 다. **계약 갱신으로 자리를 열
것을 권하지 않는다** — 엔드포인트를 열어도 (c) 때문에 값이 서지 않아 「미가용을 돌려주는
표면」이 하나 더 생길 뿐이다.

### (c) 출하 정책 값으로 메뉴 값의 출처가 서는가 — **후보 비율만 서지 않는다**

| 메뉴 입력 | 출하에서 서는가 | 근거 |
|---|---|---|
| 기초금액 `BaseAmount` | **선다** | `Notice.baseAmount`(`ResolvedBaseAmount`) — DEC-01 이 ⓐ 이고 해석 순서가 `KONEPS_COLLECTION_POLICY` 출하 값이다 |
| 게시 하한율 `FloorRate` | **선다** | `Canonicalize.floorRateFrom` → `notice.floor_rate_fraction` 영속 → `NoticeRow` 복원. DEC-10 신뢰 게이트는 아직 없다(별도 G1 slice) — 이 slice 는 게이트 전/후를 가르는 타입 자리를 침범하지 않는다 |
| 반올림 정책 | **선다(승인 대기)** | `OPPORTUNITY_POLICY.recommendedAmountRounding = RoundingPolicy(0, HALF_UP)`. 단 `OPEN-DIC-10`(모드 값)이 미결이라 메뉴 정책도 같은 상태로 둔다 |
| 하한 미달 표본 | **서지 않는다(부분)** | `FloorShortfallPolicyData` 의 **출하 정본 표가 없다**(실측: `FLOOR_SHORTFALL_POLICY` 같은 `EffectiveDatedPolicy` 선언이 저장소에 0, 소비자는 conformance 실행기와 kernel test 뿐). 표본 공급 port 도 메뉴 경로에 없다 |
| **후보 비율 셋** | **서지 않는다** | 유일한 출처가 ML 응답이고 출하는 `UnavailableMlAnalysis` 고정(조사 3). ML 없이 서는 대체 출처 **없음** — 발주처 밴드 축 0, 공사 floor 앵커는 조사 1 로 금액 축에서 불성립 |

즉 **ML-01(ML 제품 배선)이 닫히기 전에는 메뉴가 production 에서 `SourceAbsent` 말고 다른 값을
낼 수 없다.** 이것은 이 slice 가 고칠 수 있는 종류의 빈자리가 아니다.

### (d) ⓐ 인가 ⓒ 인가 — **둘 다 ⓒ(부분 측정)** 로 남는다

추적표 머리의 술어 ② 는 「그 bullet 의 거동이 **production 배선과 출하 정책 값에서 성립**」
이고, 규칙 문단은 *「출하 조립이 그 포트를 자리지킴으로 고정하면 운영자는 그 거동을 보지
못한다」* 를 ⓐ 근거가 아니라고 못박는다(`ML-01`·`ML-02`·`ML-04` 가 그 이유로 ⓒ 다).
메뉴의 값 출처가 정확히 그 포트다.

- **DEC-05 → ⓒ(부분 측정, 강해짐).** 두 무조건 bullet(①근거 필수 ②밴드 붕괴 비위장)은 타입·
  커널에서 **구조로** 성립하고 새 test 가 전수로 잰다. 그러나 production 배선에 메뉴를 만드는
  자리가 없고((b)) 후보 비율의 출하 출처가 없다((c)) — 술어 ②·③ 이 빈다.
  빈 bullet: **없음(구현)** / 빈 것은 **배선과 값 출처**. 홀더는 `OPEN-ML-ANALYSIS-WIRING`(ML-01)
  + 표면은 M7 7C ②.
- **DEC-02 → ⓒ(부분 측정, 강해짐).** 첫 bullet(binding 기록)은 이 slice 가 **구현에 없다 →
  있다** 로 바꾸지만, 그 결과를 내는 경로가 출하에 없으므로 ⓐ 가 아니다. 둘째 bullet(반올림
  하한 보장)은 `ArithmeticTest` 가 재되 **출하 경로의 유일한 `roundedWith` 호출이 `floor=null`**
  이라 술어 ② 가 빈다. 셋째 bullet(basis 교차 min/max 금지)은 `CompileFailureHarnessTest`(1·11)가
  컴파일 단계에서 재므로 배선과 무관하게 성립한다.

**의심되면 ⓒ** 규칙을 적용했고, 과소 쪽을 택했다. 이 판정이 맞다면 추적표 집계는
ⓐ 16 그대로이고 두 행의 본문·「부분 측정(강함)」 표식과 머리 열거만 바뀐다(표식 집합 등식 `E-24`
동반 갱신).

### (e) 새 정책 수치 값이 필요한가 — **수치는 0, 해석 결정 하나**

메뉴가 ML 의 후보 **셋을 그대로** 태도 셋에 대응시키므로(조사 3 의 라벨 대응),
legacy 의 매직 셋에 해당하는 **스프레드 수치가 필요 없다**. 필요한 것은 값이 아니라 선언이다:

1. **`floorComparison`(해석 결정, 운영자)** — 「게시 하한율을 투찰율의 하한으로 그대로 읽는다」
   를 채택하는가, 채택하면 경계 등가(`투찰율 == 하한율`)를 수용인가 미달인가. 근거:
   조사 1 — 금액 축 변환은 basis 를 섞으므로 불가, 율 축 비교만 정직하다. 선례:
   `ShortfallComparison`(`StrictlyGreater` 초기값, decision 28).
2. **`amountRounding`·`criticalRateRounding` 의 모드** — 새 값이 아니라 **기존 미결**
   `OPEN-DIC-10` 의 같은 자리다. 이 slice 는 슬롯만 두고 값을 지어내지 않는다(test 정책에만
   실제 값).
3. (참고) `FloorShortfallPolicyData` 의 **출하 정본 표 부재**((c))는 이 slice 가 만들지 않는다 —
   DEC-04 행의 production 칸이 그 표를 이름으로 적는데 저장소에 그 선언이 없다는 **실측**은
   팀장에게 별도로 보고한다(추적표 DEC-04 행의 사실 문제이지 이 slice 의 산출물이 아니다).

### (f) 계약 산출물 7 — G3 OPEN 묶음 둘의 등재 목록 초안

등재 자리: `docs/discovery/capability-map.md` §14.3 (C) 표(최근 slice 들이 신설 행을 더하는 자리).
목록 정본은 7A 조정안 G3 표.

- **`OPEN-M6-RESIDUAL-OPS`** (M6/G1-DEC05 신설) — 운영 축 12 + 전략 축 4:
  `STR-04` · `STR-09` · `STR-10` · `NOTI-01` · `OPS-02` · `OPS-03` · `OPS-04` · `OPS-05` ·
  `OPS-07` · `OPS-08` · `OPS-09` · `OPS-10` · `OPS-11` · `OPS-12` · `OPS-13` (15)
- **`OPEN-M6-RESIDUAL-ML-LIFECYCLE`** (M6/G1-DEC05 신설) — ML 수명주기·분류 version:
  `ML-05` · `ML-07` · `ML-08` · `SET-06` · `DEC-08` (5)
- 두 묶음에 들지 않는 G3 넷: `ML-02` · `ML-04`(G1 의 ML 배선 slice 가 acceptance 에 재판정을
  넣는다 — 결정 블록 문면) · `QUAL-04` · `COL-07`(`OPEN-DEC-10` 이 이미 홀더). → 15 + 5 + 4 = **24**,
  조정안 G3 계와 일치.
- 활성 OPEN 개수 줄(§12 머리 「활성 45건」)을 +2 로 맞춘다.

### 팀장 결정을 받을 것 (Phase 2 착수 전)

1. (d) 의 ⓒ/ⓒ 판정을 그대로 가는가.
2. (a) 의 shared-kernel 추가 — **안 A(타입 있는 하한, 추천)** 인가 안 B(최소)인가, 아니면 ④를
   포기하고 하한 보장을 메뉴 밖에 두는가.
3. (e) 1 의 해석 결정을 운영자에게 올릴 것인가, 아니면 슬롯만 두고 값 미정으로 둘 것인가.
4. `FloorShortfallPolicyData` 출하 정본 표 부재(추적표 DEC-04 production 칸과 어긋남)의 처분.

### 병렬 레인 충돌 고지

`reports/evidence/m6/6e1/acceptance-trace.md` 는 M7/7C-1 레인도 편집한다(실측:
`2620afe7 docs(m7-7c1): 추적표 STR-16 …`). 같은 파일의 다른 행이라 hunk 는 겹치지 않지만
rollback 의 hunk 목록은 실측 HEAD 에서 기계 산출한다. `w-c03`(COL-03) 과의 겹침:
이 slice 의 in_scope 는 `decision/**`·`shared-kernel/**`·`workflow/**` 의 prediction 주변이고
`workflow/collection`·`procurement` 는 건드리지 않는다 — 현재 겹침 0.

---

## Phase 2 — 구현과 실측

팀장 결정(2026-10-21): ⓒ/ⓒ 유지 · 안 A 채택 · 해석은 슬롯만 + 새 OPEN · DEC-04 행 정정 + ⓐ 전수 훑기.

### D-G1DEC05-1 — 추적표 DEC-04 행 정정 (팀장 지시)

`FLOOR_SHORTFALL_POLICY` 는 저장소에 없다. ⓐ 16 행의 production 칸이 인용한 이름을 전수로 훑었다
(`*/src/main` + `config/quality` + `app/src/main/resources`, 고정 문자열 검색). 부재는 **둘**이다.

| 행 | 인용 이름 | 실재 | 처분 |
|---|---|---|---|
| **DEC-04** | `FLOOR_SHORTFALL_POLICY` | **없음** | ⓐ → **ⓒ**(술어 ② 미성립). `FloorShortfallPolicyData` 의 소비자는 conformance 실행기와 kernel test 뿐 |
| **STR-03** | `VERDICT_LADDER_POLICY` | **없음** | **이름 오기** — 실체는 `VerdictLadderPolicyData`(`reviewThreshold <= bidNowThreshold` 를 `init` 이 거부)이고 출하 배선은 `EVALUATION_LADDER_POLICY_SLOT` → `EvaluateCandidatesUseCase` 다. 거동이 실재하므로 **판정을 건드리지 않았다**(지시대로 보고만) |
| 나머지 14 행의 인용 이름 42 | — | 전부 실재 | 처분 없음 |

훑기 범위의 한계: 고정 문자열 일치라 **이름이 맞는지**만 보고 **그 이름이 그 bullet 을 실제로 재는지**는
보지 않는다. 후자는 행마다 읽어야 하는 일이고 이 slice 의 지시 범위 밖이다.

### D-G1DEC05-2 — `AppliedFloor`(안 A) 와 그 파급

`roundedWith(floor: BigDecimal?)` → `roundedWith(floor: AppliedFloor?)`. 생성 경로는
`BaseAmount.appliedFloorFrom(BidRate)` 하나다. **팀장이 예상한 `app` conformance 갱신은 필요 없었다** —
실측: `roundedWith` 호출부는 저장소 전체에서 production 1(`PredictionFacts.kt`, 하한 인자 없음) ·
shared-kernel test 2 · decision test 1(하한 인자 없음)이고 `app/**` 에는 **0** 이다
(`ProvenanceFloorExecutors` 는 하한 미달 커널을 쓰지 ..roundedWith.. 를 쓰지 않는다). 그래서 in_scope 의
`app` 확장은 **쓰지 않았다**.

### (2b) 값 획득 축 — 새 public 표면이 밖에 허락하는 것

| 새 public 표면 | 밖에서 할 수 있는 것 | 할 수 없는 것 |
|---|---|---|
| `BidStance` · `StanceRates` | 태도 셋 구성·조회 | 순서를 깨뜨린 셋 구성(`init` 이 거부) |
| `BidMenuGrounds` | 근거 **읽기**, 그리고 구성 | 근거를 지어 **옵션으로 승격** — 옵션 생성자가 `internal` |
| `BidMenuOption` | 다섯 칸 읽기 | **생성·`copy`**(`internal` + `@ConsistentCopyVisibility`) |
| `BidMenuOutcome` 네 갈래 | `when` 전수 소비, 실패 셋 구성 | `Offered` 생성·`copy` |
| `BindingConstraint`·`BidMenuDisclosure`·`MenuUnmeasurableReason`·`MenuSourceAbsence` | 구성·비교 | — (사유·어휘 값이라 공개가 맞다) |
| `BidMenuInput`·`BidMenuPolicyData`·`FloorRateReading`·`FloorBoundComparison` | 입력·정책 구성 | — |
| `composeBidMenu` | 메뉴 산출 | — |
| `AppliedFloor` | 하한 값 전달 | **생성**(`internal`) — `appliedFloorFrom` 만이 경로 |
| `BaseAmount.appliedFloorFrom` | 같은 basis 하한 산출 | 다른 basis 입력(서명이 `BidRate` 만 받는다) |

**근거 없이 옵션을 만들 수 있는 public 경로 = 0.** 금액을 산술 없이 실을 경로도 0이다 —
`Derived`·`Measurement.Measured` 의 생성자가 `shared-kernel` `internal` 이라 `decision` 조차 지어낼 수 없다.
두 폐쇄는 `MenuCompileFailureHarnessTest` 가 **모듈 밖 컴파일 단위**에서 실측한다(같은 모듈 test 는
`internal` 을 그냥 부를 수 있어 폐쇄를 재지 못한다).

### 변이 표 (전부 구현 커밋 `e88c069b` 뒤, 각 변이는 `git diff --numstat` 으로 적용 확인)

| # | 변이 | numstat | 결과 | 잡은 자리 |
|---|---|---|---|---|
| M1 | 옵션 생성자 전면 개방(`@ConsistentCopyVisibility` 제거 + `internal` 제거) | 1/2 | **RED** | `MenuCompileFailureHarnessTest` fixture 1 |
| M1′ | 생성자만 `public`(주석 유지) | 1/1 | **RED** — 단 `-Werror`(`ConsistentCopyVisibility is redundant`)가 **컴파일에서 먼저** 잡는다. test 까지 가지 않는다 | `:decision:compileKotlin` |
| M2 | 밴드 붕괴 판정을 항상 `null` 로 | 1/1 | **RED** | `BidMenuKernelTest` 밴드 붕괴 2 |
| M3 | `PublishedFloorBound` → `CandidateRate`(두 제약을 한 값으로) | 3/3 | **RED** | `BidMenuKernelTest` binding 2 |
| M4 | 적용 하한을 반올림에 넘기지 않음(`floor = null`) | 1/1 | **RED** | `BidMenuKernelTest` 반올림 1 |
| M5 | 하한 타입을 날 `BigDecimal?` 로 되돌림 | 4/4 | **RED** — 단 `:shared-kernel:compileTestKotlin` 이 **먼저** 잡는다(`ArithmeticTest` 호출부). fixture 14 는 돌지 못한다 | 컴파일 |
| M5′ | `BigDecimal?` 오버로드를 **더해** 구멍만 연다(기존 호출부 무손상) | 5/0 | **RED** | `CompileFailureHarnessTest` fixture 14 |
| M6 | 정책 값을 커널 상수로 되돌림(`floorRateReading` 무시) | 1/1 | **RED** | `BidMenuPolicyDataTest` 1 + `BidMenuKernelTest` 2 |
| 통제군 | 주석 한 줄(거동 0) | 1/0 | **GREEN** | — |

M1′·M5 를 표에 남기는 이유: 「변이가 RED 였다」와 「그 test 가 RED 를 냈다」는 다른 주장이다. 둘은
test 가 아니라 컴파일이 잡았고, M1·M5′ 가 그 test 들의 비공허성을 따로 실측한다.

### 판정

- **DEC-05 → ⓒ(부분 측정, 강함)** · **DEC-02 → ⓒ(부분 측정, 강함)** · **DEC-04 → ⓒ(부분 측정, 강함, 정정)**.
- 집계 ⓐ **16 → 15**, 성립 범위 **18 → 17/61**. 완료 조건 3 의 결론은 바뀌지 않는다.
- DEC-05·DEC-02 를 「홀더 없는 미구현 17」에서 뺐다(홀더를 달았다) — 남은 **15**.

### 알려진 제한

1. **메뉴는 production 에서 아직 돌지 않는다.** 소비자 0 · 후보 비율 출처 0. ⓐ 는 ML-01(ML 배선)과
   M7 7C ②(표면)가 닫힌 뒤에 재판정할 일이다.
2. **출하 정본이 `FloorRateReading.Undecided` 라, 오늘 production 값에서는 밴드 붕괴·하한 binding 이
   발화하지 않는다.** 그 두 상태는 test 정책 위에서만 관측된다 — 그래서 ⓒ 다. `BidMenuPolicyDataTest`
   가 이 사실 자체를 고정하므로, 운영자가 읽기를 정하면 그 test 가 먼저 깨져 갱신을 강제한다.
3. `BidMenuOutcome.Offered.disclosures` 는 늘 전수라 **값으로는 정보를 나르지 않는다**. 그 자리를 둔 이유는
   고지 의무가 값과 함께 표면 경계까지 이동하게 하려는 것이고, `init` 이 부분 집합을 거부한다.
4. DEC-04 의 빈자리(출하 정책 표)는 이 slice 가 **만들지 않았다** — 값이 운영자 승인 대상이고
   (`minAssessmentSamples` 150 의 근거 문자열 포함) 이 slice 의 산출물이 아니다.
