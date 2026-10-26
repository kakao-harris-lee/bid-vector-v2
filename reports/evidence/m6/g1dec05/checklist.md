# M6/G1-DEC05 — checklist (투찰가 메뉴 DEC-05 + binding 제약 기록 DEC-02)

**실측 기준 SHA: `63a207cd`** — acceptance · 변이 · rollback 이 **모두 이 SHA** 에서 돌았다
(r3 일괄). 복원 경로(코드 · test · `config/quality` · 공유 장부)를 건드린 **마지막** 커밋이고,
그 뒤 커밋은 `reports/evidence/m6/g1dec05/**` 뿐이라 **복원 경로가 그 사이에 움직이지 않는다**.

**판정 SHA(동결 HEAD)는 동결 보고에 적는다.** 실측 기준 SHA 와 다른 것이 정상이다(evidence
커밋은 언제나 뒤에 온다). 검증자가 볼 등식은 「실측 HEAD == 판정 SHA」가 아니라
`git diff --name-only 63a207cd..<판정 SHA> -- <rollback 이 되돌리는 경로들>` 이 **빈 출력**인지다.

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
| `BidMenuOutcome` **다섯** 갈래 | `when` 전수 소비, 실패 셋 구성 | `Offered`·`PartiallyOffered` 생성·`copy` |
| `BidMenuOutcome.PartiallyOffered`(라운드 2) | `offered`·`withheld` 맵 **읽기만** — 반환된 맵은 가둔 사본이라 캐스팅해 바꾸면 던진다(r2 M-4) | **생성·`copy`**(생성자 비공개 + `@ConsistentCopyVisibility`) — fixture 3 이 모듈 밖에서 실측 · **사후 변조**는 `BidMenuTypesTest` 가 실측 |
| `CollapseCause` 둘(라운드 2) | 붕괴 사유 구성·비교 | — (사유 값이라 공개가 맞다) |
| `BidMenuOutcome.BandCollapsed(cause, collapsedStances)` | 구성(실패 셋) | 빈 태도 집합(`init` 거부) |
| `BidMenuOutcome.Unmeasurable(withheld)` | 구성(실패 셋) | 태도 전수가 아닌 보류(`init` 거부 — 「일부 보류」는 `PartiallyOffered` 의 자리다) |
| `BindingConstraint`(**네 값** — 금액 축 `PublishedFloorRounding` 추가, r3 N-1)·`BidMenuDisclosure`·`MenuSourceAbsence` | 구성·비교 | — (사유·어휘 값이라 공개가 맞다) |
| `MenuUnmeasurableReason.CollapsedWithOtherStances` | `stance`·`amountWon`·`otherStances` **읽기** | **생성·`copy`**(생성자 비공개 + `@ConsistentCopyVisibility`) · `otherStances` **사후 변조**(가둔 사본, r3 N-2) — `withheld` 맵이 `unmodifiableMap` 이어도 **그 값 안의 집합**은 캐스팅 한 줄로 비울 수 있었다 |
| `BidMenuInput`·`BidMenuPolicyData`·`FloorRateReading`·`FloorBoundComparison` | 입력·정책 구성 | — |
| `composeBidMenu` | 메뉴 산출 | — |
| `AppliedFloor` | 하한 값 전달 | **생성**(`internal`) — `appliedFloorFrom` 만이 경로 |
| `BaseAmount.appliedFloorFrom` | 같은 basis 하한 산출 | **날 `BigDecimal` 을 하한 자리에 넣는 것**(r2 L-1 정정 — 막는 것은 **타입**이지 basis 가 아니다. `BidRate.recommended(floorRate.rate)` 한 줄이면 예정가 basis 율도 들어가고, 커널 자신이 `appliedFloorOf` 에서 그렇게 한다 — 결정된 읽기라 정당하다) |

**근거 없이 옵션을 만들 수 있는 public 경로 = 0.** 금액을 산술 없이 실을 경로도 0이다 —
`Derived`·`Measurement.Measured` 의 생성자가 `shared-kernel` `internal` 이라 `decision` 조차 지어낼 수 없다.
두 폐쇄는 `MenuCompileFailureHarnessTest` 가 **모듈 밖 컴파일 단위**에서 실측한다(같은 모듈 test 는
`internal` 을 그냥 부를 수 있어 폐쇄를 재지 못한다).

**결과 그래프의 컬렉션은 전부 읽기 전용이다(r2 M-4 · r3 N-2).** `Offered.disclosures` · `PartiallyOffered.offered`·`withheld` · `BandCollapsed.collapsedStances` · `Unmeasurable.withheld` · `CollapsedWithOtherStances.otherStances` **여섯 다** 생성 시 복사해 가둔다 — 「생성자가 닫혀 있다」와 「나온 값을 바꿀 수 없다」는 **다른 주장**이고, 앞 판은 앞쪽만 재고 있었다. 가둔 뷰만으로는 부족한 자리(원본 참조가 `equals`·`hashCode` 에 남는 표류)도 함께 닫았다.

**옵션이 밖으로 나가는 경로의 전수 점검(라운드 2 기준).** `BidMenuOption` 을 싣는 결과는
`Offered` 와 `PartiallyOffered` **둘뿐**이고 둘 다 생성자가 `internal` 이다. 옵션을 담지 않는 세
갈래(`BandCollapsed`·`Unmeasurable`·`SourceAbsent`)는 공개 생성자지만 **옵션을 실을 칸이 없다**.
그래서 위 폐쇄는 라운드 2 가 낸 새 조립 경로에서도 유지되고, **그 유지가 추론이 아니라 fixture
1·2·3 의 실측**이다. `AmountDecision`·`Step`·`OptionContext`·`EffectiveRate` 는 `internal` 이라
표면이 아니다.

### 수정 라운드 2 — finding 전수 대응표

리뷰 원문 둘(code-reviewer `REQUEST_CHANGES` 8건 · verifier `changes-needed`)의 **전건**이다.
번호는 원문 번호다(cr 은 HIGH-1~3 · MEDIUM-4~6 · LOW-7~8, verifier 는 H-1 · M-1 · L-1~3).
verifier H-1 = cr HIGH-1, L-1 = MEDIUM-5, L-2 = MEDIUM-4 로 같은 자리다.

| finding | 무엇이 틀렸나 | 지금 막는 자리 |
|---|---|---|
| **cr HIGH-1** · v H-1 | 태도 라벨 반전 — `conservative`→`safe` 로 이어 운영자가 보는 「안전」이 셋 중 **낙하 위험이 가장 큰** 후보가 됐다 | `StanceRateMappingTest` 둘(도메인 성질 · legacy 브리지 표 — oracle 둘 다 **구현 독립**) · `BidMenuKernelTest` 「태도 축」 둘 · `StanceRates.init` 방향 반전 |
| **cr HIGH-2** | 후보 밴드 자신의 붕괴 미측정 — 값이 같은 두 옵션이 「정상 3안」으로 나갔다 | `CollapseCause.DuplicateEffectiveRates` + `BidMenuKernelTest` 밴드 붕괴 다섯(읽기 미정 · clamp 수렴 · 전수 겹침 포함). 이 축은 하한과 무관해 **읽기 미정에서도 판정**한다 |
| **cr HIGH-3** (ⅰ) | 한 태도의 실패가 메뉴 전체를 죽였다 | `PartiallyOffered`(타입 분리, `Offered` 는 셋 전부일 때만) + `BidMenuKernelTest` 「부분 보류」 + `MenuCompileFailureHarnessTest` fixture 3 |
| **cr HIGH-3** (ⅱ) | 하한 미달이 보류로 흘러 **하한에 걸린 옵션이 늘 사라졌다**(`DOWN`·`HALF_*` 가 경계에서 1원 아래로) | 제약을 만족하는 **최소 정수로 올림** + binding 을 하한으로 + `BidMenuKernelTest` 반올림 셋(리뷰어 수치 예 1,234,567 × 0.87995 포함) |
| **cr MEDIUM-4** · v L-2 | 항상 통과하는 test 둘 — 자기 비교, 그리고 상수 `setOf` 크기만 세어 **커널을 부르지 않는** test | 효력 투찰율 기대값으로 바꾸고, binding 구별은 **세 시나리오를 실제로 돌려 관측된** 값 집합으로 |
| **cr MEDIUM-5** · v L-1 | `AppliedFloor` 가 「같은 기초금액」을 보장한다고 KDoc 이 과대 진술 | KDoc 을 **basis 축 한정**으로 낮춤 + capability-map DEC-02 절 + 알려진 제한 3 |
| **cr MEDIUM-6** | 게시 하한 유래 값이 `BidRateOrigin.Recommended` 로 라벨된다 | `BidRateOrigin` 은 **바꾸지 않고**(지시) KDoc 에 「자리채움이고 값의 이유는 `binding` 이 유일 출처」 + 알려진 제한 4 |
| **cr LOW-7** | `StanceRateMappingTest` 에 독립 oracle 이 없어 **반전을 초록으로 통과**시켰다 | oracle 을 도메인 성질 + 1차 출처 표 둘로 교체(위 HIGH-1 과 같은 자리) |
| **cr LOW-8** | 출하 값에서 `Offered` 가 하한을 전혀 적용하지 않고 나간다 | 알려진 제한 2 + 7C ② 계약 조건(「`FloorRateReadingUndecided` binding 이 붙은 옵션을 추천으로 띄우지 않는다」) |
| **v M-1** | 「fail-closed」가 과장 — 미결에서도 결과가 그대로 나간다 | 문면을 **표식형**으로 낮춤(거동 불변, 지시). `BidMenuPolicyData` KDoc + **capability-map OPEN 행**(장부에도 같은 과장이 남아 있었다) |
| **v L-3** | 추적표 집계 숫자 · checklist 판정 SHA 줄 미기입 | 집계는 머지 재산출로 ⓐ 16 · ⓑ 2 · ⓒ 43(아래 문서 등식) · 판정 SHA 줄은 이 문서 머리 |

**레인 재개가 추가로 잡은 셋**(리뷰에 없던 것 — 앞 레인이 끊긴 자리):
1. **하한 올림 test 의 기대값이 틀렸다** — 1,086,362 로 적혀 있었고 옳은 값은 **1,086,358**
   (1,234,567 × 0.87995 = 1,086,357.23165 → `DOWN` 1,086,357 은 하한 미만 → 하한 이상 최소 정수
   1,086,358). 손으로 센 근거를 KDoc 에 적었다.
2. **`PartiallyOffered` 의 생성 폐쇄가 측정되지 않았다** — `Offered` 에만 음성 fixture 가 있어
   「같은 기법이니 닫혀 있다」는 추론만 있었다. fixture 3 + 비공허성 오타 변이를 쌍으로 넣었다.
3. **capability-map DEC-05 절이 반전된 라벨 대응을 그대로 들고 있었다** — 다음 소비자(7C ②)가
   그 문장을 근거로 같은 결함을 재생산할 자리였다. `ktlint` 5건도 같은 성질이다(라운드 2 가
   lint 를 돌리지 못한 채 끊겼다).

### 승인 전 일괄(D-G1DEC05-3) — r2 finding 대응표

r2 는 **verifier ready-for-review · code-reviewer approve**(blocker·high 0)였고, 아래는 차단이
아닌 등재 항목의 처분이다. 번호는 verifier 원문(M-1~M-5 · L-1~L-5)이고 cr 쪽 같은 자리를 함께 적는다.

| finding | 무엇이 문제였나 | 처분 |
|---|---|---|
| **M-1**(cr M-1) | 부분 붕괴가 **겹치지 않은 세 번째 옵션까지** 버린다(탐침 P5) | 겹친 태도만 `CollapsedWithOtherStances` 로 보류, 나머지는 제시. 전부 겹쳤을 때만 `BandCollapsed`. 대조표 **P-22** 에 등재 |
| **M-2**(cr M-2) | 하한 **위** 후보가 올림을 타면 binding 이 `PublishedFloorBound` 로 바뀌고 **그 경로에 test 가 없다**(변이 N1 이 GREEN) | 올림이 binding 을 덮지 않는다(`effective.binding` 전달). P2 모양 회귀 test 추가 — N1 이 이 test 에서 RED |
| **M-3** | 중복 판정이 **율 축**에만 있어 금액이 같은 두 옵션이 「정상 3안」으로 나간다(탐침 P1) | 판정 축을 **반올림 뒤 금액**으로. `DuplicateEffectiveRates` → `DuplicateEffectiveAmounts`. 대조표 **P-23** 에 등재 |
| **M-4**(cr M-3) | 결과 타입의 컬렉션이 **사후 변조**된다 — 「고지 전수」 불변식 포함(탐침 P3·P4) | 네 결과 타입이 **생성자를 `private` 으로 내리고 companion `invoke` 가 가둔 사본을 primary 자리에** 넣는다(③′). (2b) 표의 「맵 읽기만」도 고쳤다 |
| **M-4 보강**(팀장 지적, ③′) | 가둔 뷰만 파생하면 **반만 닫힌다** — data class 의 `equals`·`hashCode` 가 primary 의 **원본 참조**를 읽어, 생성 뒤 원본이 바뀌면 공개 뷰는 그대로인데 해시가 움직인다. 게다가 `BandCollapsed`·`Unmeasurable` 은 public 생성자에 애너테이션이 없어 **`copy` 가 모듈 밖에 열려 있었다** | 사본을 primary 자리에 넣어 표류를 없애고, `@ConsistentCopyVisibility` 를 네 타입 전부에 달아 `copy` 를 닫았다. **RED 를 먼저 봤다** — clean 빌드에서 225 중 1 실패(그 drift test), 해시가 `-1858671038` → `-1003534475` 로 움직였다. 봉쇄는 **fixture 4**(음성 + 오타 변이)가 모듈 밖 컴파일 단위에서 잰다 |
| **M-5** | 후보 율의 **출처가 근거에 없다** — 상수 율로 「근거를 갖춘」 메뉴를 만들 수 있다 | 출처 축을 만들지 않는다. 알려진 제한 9 + `OPEN-DEC05-STANCE-RATE-PROVENANCE` 등재(`ML-01` 때 닫는다) |
| **L-1** | (2b) 표 `appliedFloorFrom` 행의 「다른 basis 입력 불가」가 과장 | 타입이 닫는 것은 **날 `BigDecimal`** 이지 basis 가 아니라고 고쳤다 |
| **L-2** | 변이 표 R2-M1 의 「잡은 자리」가 실측과 다르다 | `StanceRateMappingTest` **둘뿐**으로 고쳤다(커널 test 는 `toStanceRates` 를 거치지 않는다) |
| **L-3** | 추적표 DEC-05 행에 「`E-24` 양쪽 22」가 남았다 | **23** 으로 고쳤다 |
| **L-4**(cr L-2) | `capability-map.md` §12 신설 사유 단락이 낡았다(「fail-closed」 · 「3건 · 45→48」) | **표식형**으로 고치고 신설 건수를 현재(5건)로 맞췄다 |
| **L-5**(cr L-1) | `amountStep` KDoc 의 「후보 비율에 더 가깝게 남는다」가 거짓 · DEC-02 행이 점수 축 OPEN 의 ⓐ 전환 영향을 적지 않았다 | KDoc 을 `ceil(U) = ceil(F)` 증명으로 고쳤다 · DEC-02 행에 **「막지 않는다」와 그 이유**를 적었다 |
| **cr L-3** | `PartiallyOffered` 의 `init` 불변식 **여섯이 하나도 재지지 않는다** | 여섯을 각각 `shouldThrow` 로 재는 test 추가(팀장 목록 밖 항목 — M-4 와 합쳐 닫았다) |

**이 일괄이 커널 거동을 바꾼다**(M-1 · M-2 · M-3) — 그래서 표적 재검증(r3)이 붙는다.

**측정 전 낡은 `.class` 대조를 절차로 넣었다.** 증분 빌드의 `build/classes` 에 **소스에서 지운 타입의
`.class` 가 남아** test 가 옛 정의를 보고 가짜 RED 를 내는 일이 다른 레인에서 나왔다(CI 는 새 checkout 이라
없다). 이 라운드가 `CollapseCause.DuplicateEffectiveRates` 를 `…Amounts` 로 **이름을 바꿨으므로** 그 자리다 —
실제로 `CollapseCause$DuplicateEffectiveRates.class`(10-22 자)가 남아 있었다. **첫 대조는 그것을 놓쳤다**:
중첩 class 는 `Outer$Inner.class` 인데 `$` 앞 마디(`CollapseCause`)만 소스와 대조했고 그 이름은 지금도
선언돼 있어 통과로 나왔다. **마디를 전부 보도록** 고쳐야 걸린다. 지금은 단계 6 이 `clean` 뒤에 이 대조를
돌리고 **「소스에 없는 class 0」을 본 뒤에야** 측정을 믿는다.

### 승인 전 일괄 실측 — 실측 기준 `e28ab66f`

**acceptance** — `clean` 뒤 `check --continue` **exit 0** · **CI 명령 그대로 `check` exit 0**.
HEAD 양끝 불변 · `git status --porcelain` 양끝 **빈 출력** · **`:leakPatternGate`·`:contractGate`
executed**(UP-TO-DATE 아님).

**낡은 `.class` 대조 0** — `clean` 뒤 중첩 마디까지 전수로. 타입 이름을 바꾼 라운드라 반드시
보는 자리다(`DuplicateEffectiveRates` → `…Amounts`).

| # | 변이 | numstat | 결과 | 실패 수 | 무엇을 잠그는가 |
|---|---|---|---|---|---|
| **기준선** | 변이 0 | 0/0 | **GREEN** | 0 | 변이 전 clone 이 초록 |
| **N1** | 올림 경로가 율 축 값을 덮어쓴다(r2 M-2 재현) | 1/1 | **RED** | 2 | 두 축을 섞지 않는다 |
| **M3P** | 경계 등가의 binding 을 `CandidateRate` 로 | 1/1 | **RED** | 2 | 율 축 제약 구별 |
| **C1** | 금액 겹침 판정을 못 찾게 한다 | 1/1 | **RED** | 8 | M-1·M-3 방어 |
| **F1** | `invoke` 의 복사를 뺀다 | 1/1 | **RED** | 2 | ③′ 원본 표류 |
| **A1** | 금액 축 variant 를 `CandidateRate` 로 되돌림 | 1/1 | **RED** | 2 | **r3 N-1** 재귀속 |
| **A2** | `otherStances` 가둠 제거 | 1/1 | **RED** | 1 | **r3 N-2** |
| **U2** | `Unmeasurable` 의 생성자·`copy` 공개 | 1/2 | **RED** | 2 | **r3 N-3** 게이트 술어 |
| **R2-M1** | `toStanceRates` 라벨 축 반전 | 2/2 | **RED** | 2 | 라벨 축 |
| **R2-M8** | `StanceRates` 불변식 방향 반전 | 1/1 | **RED** | 30 + 2 | 순서 불변식 |
| **통제군** | 주석 한 줄(거동 0) | 1/0 | **GREEN** | 0 | — |

**아홉 전부 적용·RED · 기준선과 통제군 GREEN · `APPLY-FAILS=0`.** 전부 바꿔치기이고
`numstat` 으로 적용을 확인한 뒤에만 돌렸다. test task 마다 `--rerun` 이라 통제군의 초록은
**cache 적중이 아니라 실제 실행**이다. 필터는 모듈 셋 전체다.

**「적용 실패」가 「RED」로 보이던 구멍을 닫았다.** 첫 실행에서 **N1·C1 둘이 `PATTERN 0 != 1`
로 적용되지 않았다** — 이번 라운드가 고친 바로 그 두 줄(`promotedBinding` 도입 ·
`firstOrNull` → `filter`)을 변이 패턴이 그대로 가리키고 있었기 때문이다. **수정 라운드가 변이
목록을 낡게 만든다.** 게다가 `apply … && run_case …` 구조라 `apply` 가 실패하면 `run_case` 가
호출되지 않아 그 안의 `NOT-APPLIED` 가드가 발화할 수 없었고, 스크립트 종료 코드는 마지막
명령(통제군 GREEN)이라 **0** 이었다. 지금은 적용 실패를 세어 `APPLY-FAILS=N` 을 찍고 0 이
아니면 **exit 2** 다. 위 표는 **패턴을 현행화해 전부 다시 돌린 한 벌**이다(일곱 + 둘로 나누어
적지 않는다).

**rollback ①~⑦ 전부 초록** — 복원 기준 **병합된 main `46ac0949`**(고정), 버릴 clone.

| 단계 | 결과 |
|---|---|
| ① 복원 목록 ⊇ 변경 경로(evidence 제외) | **OK — 누락 0** (A 30 · M 7 · 계 37) |
| ② `git restore --source=46ac0949` + 새 경로 삭제 | restore exit 0 · delete exit 0 |
| ③ 되돌린 경로의 `git diff` | **OK — 0 bytes** |
| ④ `compileKotlin compileTestKotlin` | **exit 0** |
| ⑤ `:decision:test :shared-kernel:test :workflow:test` | **exit 0** |
| ⑥ `sizeGate gateRegistrationGate leakPatternGate` | **exit 0** |
| ⑦ 트리 복귀 | **OK — porcelain 빈 출력 · HEAD 불변** |

**완료는 종료 코드가 아니라 계수로 센다**: 내 표지 넷(menu 디렉터리 · `AppliedFloor.kt` ·
복원 경로 안 `G1-DEC05` · 전 트리 `AppliedFloor`) **전부 0**, 남의 표지 둘(SET-01 OPEN id
**5 → 5** · COL-03 의 `PromoteOpeningResultsUseCase.kt` **보존**) **그대로**.

**④⑤⑥ 을 생략하지 않는 이유**: ③ 은 **복원한 경로 집합만** 기준과 대조하므로 목록에서
빠진 파일은 정의상 검사 밖이고 구조적으로 0 줄이 나온다 — 그 누락은 ④ 컴파일만 본다.
① 의 ⊇ 등식을 **되돌리기 전에** 먼저 돌리는 것도 같은 이유다. **갈음은 「HEAD 초록」이 아니라
트리 동일성으로만 한다** — ③ 의 빈 출력이 그 동일성이다.

**캐시 정직성**: ④⑤ 는 상당수가 FROM-CACHE·UP-TO-DATE 다. 공허한 초록이 **아니다** — 캐시
키가 맞았다는 것은 되돌린 트리의 입력이 slice 이전 상태와 **바이트 동일**이라는 뜻이고
rollback 이 증명하려는 것이 그 동일성이다. 다만 「test 가 다시 돌았다」고는 적지 않는다.
⑥ 의 `gateRegistrationGate` 초록은 **되돌린 `gate-tests.properties` 가 되돌린 test 클래스
모집단과 양방향으로 맞는다**는 뜻이라 게이트 등재의 복원 완전성을 잰다.

**이 라운드가 쓴 선행 패스**: 비싼 clean `check`(23분) 로 형식 오류를 발견하는 일이 반복돼,
체인 전에 **빠른 정적 게이트**(세 모듈 × `ktlintCheck`·`detekt`·`sizeGate`·`typeShapeGate`·
`testShapeGate`·`gateRegistrationGate`, `--continue`)를 두고 **붉은 것을 한 번에** 보게 했다.
체인은 **ACC 가 붉으면 그 자리에서 멈춘다** — 붉은 기준선 위의 변이는 아무것도 재지 못하는데
앞 회차는 그 위에서 변이 6분·rollback 25분을 돌려 **락만 잡았다**.

### Phase 1 약속 ↔ 출하 실물 대조표 (D-G1DEC05-5, 전수)

축은 **약속 하나**다 — 파일 축은 쓰지 않는다(파일을 합치고 나누는 것은 설계 판단이 아니라
배치이고, 그 축으로 세면 이탈이 아닌 것이 이탈로 보인다). 범주 넷:
**① 약속대로** · **② 선언하고 이탈** · **③ 무선언 이탈** · **④ 약속 자체가 틀림**.

| # | Phase 1 약속 | 출하 실물 | 범주 | 사유 |
|---|---|---|---|---|
| P-1 | `BidStance{Safe,Recommended,Aggressive}` 어휘 | 같다 | ① | — |
| P-2 | 라벨 대응 `Safe→conservative`·`Aggressive→aggressive` | `conservative→Aggressive`·`aggressive→Safe` | **④** | 약속이 축을 뒤집어 적었다. 투찰율이 낮을수록 공격이다(1차 출처 셋). 라운드 1 이 **약속을 그대로 구현해** cr HIGH-1 이 됐다 |
| P-3 | 변환 자리 = `decision` 의 `BidStance.rateOf(candidates: BidRateCandidates)` | `workflow` 의 `BidRateCandidates.toStanceRates()` + `decision` 의 `StanceRates.rateOf(stance)` | **④** | 약속이 **같은 문서 두 단락 위의 의존 규칙과 모순**이다(`decision` 의 의존은 `shared-kernel` 하나라 `BidRateCandidates` 를 볼 수 없다). 「한 자리에 선언」 의도는 유지했다 |
| P-4 | `BidMenuDisclosure` 두 값, `Offered` 가 전수를 싣고 `init` 이 강제 | 같다 | ① | — |
| P-5 | `BidMenuGrounds` 5 칸(`criticalAssessmentRate` 포함) | 4 칸(그 칸 없음) | ② | 같은 사실이 `risk` 의 `FloorShortfallJudgement.critical` 에 `Derived` 로 이미 실린다. 타입 KDoc 이 선언한다 |
| P-6 | `BidMenuRisk` sealed 둘 | **그 타입이 없다** — `risk: FloorShortfallJudgement` 직접 | **③** | 저장소 전체 참조 0. `FloorShortfallJudgement` 가 이미 측정/판정불가를 가르는 sealed 봉투라 감싸면 같은 축이 둘이 된다. 판단은 맞지만 **어디에도 적지 않았다** |
| P-7 | `BindingConstraint` 5 변종 | 3 변종(`CandidateRate`·`PublishedFloorBound`·`FloorRateReadingUndecided`) | ② | 밴드 상·하한 두 변종은 하한을 **율 축 하나**로 쓰기로 한 뒤 쓸 자리가 없어졌고(조사 1), `Provisional` 은 Phase 1 자신이 「세지 않는다」고 적은 `OPEN-DEC-03` 몫이다. `FloorRateReadingUndecided` 는 약속에 없던 **추가**이고 사유는 (e) 가 적는다 |
| P-8 | `BidMenuOption` 다섯 인자 필수 + `internal` 생성자 | 같다 | ① | — |
| P-9 | `BidMenuOutcome` sealed **넷** | sealed **다섯**(`PartiallyOffered` 추가) | ② | 라운드 2 가 cr HIGH-3 으로 갈랐고 D-G1DEC05-4 가 승인했다 |
| P-10 | `BandCollapsed(lower, upper, **cause: CollapseCause**)` | 라운드 1: `(lowerBound, upperBound)` — **`cause` 없음** / 라운드 2: `(cause, collapsedStances)` | **③** → ②(라운드 2 수리) | 약속에 있던 `cause` 를 라운드 1 이 **적지 않고 뺐다**. 그 결과 붕괴 출처가 하나로 뭉쳐 **cr HIGH-2 가 바로 그 빈 자리**다 — 약속을 지켰으면 그 finding 이 없었다 |
| P-11 | `BidMenuPolicyData` 3 칸 | 2 칸(`floorRateReading`·`amountRounding`) | `floorComparison`→`floorRateReading` 은 ② / **`criticalRateRounding` 삭제는 ③** | 읽기 미결을 타입으로 표현한 것은 (e) 가 선언한다. `criticalRateRounding` 은 **사유 없이 사라졌다** — 메뉴가 `shortfallPolicy` 를 DEC-04 커널에 그대로 넘기고 그 정책이 이미 그 슬롯을 소유하므로 두면 같은 정책이 두 축에 생기는 것이 맞지만, 그 판단을 적지 않았다 |
| P-12 | `composeBidMenu` 순수 함수 · port·Clock·전역 0 | 같다 | ① | — |
| P-13 | `BidMenuInput(base, **candidates: BidRateCandidates**, …)` | `BidMenuInput(base, rates: StanceRates, …)` | **④** | P-3 과 같은 모순(의존 규칙) |
| P-14 | 커널 순서 ①출처→②율 축→③붕괴→④금액→⑤리스크→⑥조립 | ①출처→②효력율→③붕괴 **둘**→④금액·리스크→⑤조립 **세 갈래** | ② | 라운드 2 가 늘렸다(D-G1DEC05-4 승인) |
| P-15 | ④의 `ROUNDED_BELOW_FLOOR` 는 보류가 아니라 **하한 bound 채택 값으로 올라간다**(「그 경로가 닫힌다」) | 라운드 1: 보류(`Halt`) / 라운드 2: 최소 정수 올림 | **③** → ①(라운드 2 복귀) | 라운드 1 이 약속을 **선언 없이 뒤집었고** 그것이 cr HIGH-3 이다. 게다가 `ArithmeticTest` KDoc 이 그 뒤집힌 거동을 acceptance 의 정답처럼 **사후 정당화**했다(그 문장은 D-G1DEC05-5 로 삭제) |
| P-16 | 안 A — `roundedWith(floor: AppliedFloor?)`, 비용은 `ArithmeticTest` + `app` conformance 갱신 | `app` 갱신 **0** | ② | 호출부 실측에서 `app/**` 의 `roundedWith` 호출이 0 이었다(D-G1DEC05-2 가 선언) |
| P-17 | (b) 이 slice 에 production 배선·표면을 만들지 않는다 | 만들지 않았다 | ① | 소비자 0 을 추적표 행이 적는다 |
| P-18 | (c) 값 출처 표 — 후보 비율만 서지 않는다 | 그대로 | ① | — |
| P-19 | (d) DEC-05·DEC-02 를 ⓒ 로 둔다 | ⓒ·ⓒ + `DEC-04` 정정 | ① | 자기 slice 에 불리한 정정을 포함한다 |
| P-20 | (e) 신설 **수치 0** | `OPEN-DEC05-FLOOR-RATE-READING` 신설 · 수치 0 — 단 라운드 2 가 `FLOOR_PROMOTION_ROUNDING`(scale 0 · `CEILING`) 추가 | ② | 올림은 **정수 도메인에서 「하한 이상」 제약의 정의**이지 운영자가 고를 튜닝이 아니라고 보고 정책 슬롯을 두지 않았다. **재리뷰 표적**(판단이 갈릴 수 있다 — 아래 표적 목록) |
| P-22 | (설계 검토 (1)) 「밴드 붕괴는 선택지가 서지 않는 상태」 — 붕괴는 **메뉴가 서지 않는 경우**의 이름이다 | 라운드 2: 둘만 겹쳐도 메뉴 전체를 `BandCollapsed` 로 돌려 **멀쩡한 세 번째 옵션까지 버렸다** / r2 일괄: 겹친 태도만 보류하고 나머지는 제시 | **③** → ①(r2 수리) | 「한 태도를 못 낸다고 나머지를 버리지 않는다」는 `PartiallyOffered` 의 원칙을 붕괴 축에만 적용하지 않은 것이고, **선언이 없었다**(verifier·cr 공통 M-1). 지금은 전부 겹쳤을 때만 붕괴다 |
| P-23 | (설계 검토 (0)) 「값이 같은 두 옵션은 선택지가 아니다」 | 라운드 2: **율 축**으로만 판정 / r2 일괄: **반올림 뒤 금액** 축으로 판정 | ② | 운영자가 보고 제출하는 것은 금액이다. 율 차이가 1/기초금액 미만이면 두 옵션은 같은 원화가 되고, 하한 올림 경로는 결과가 언제나 `ceil(적용 하한)` 이라 **율 축에서는 겹침이 보이지 않는다**(r2 M-3) |
| P-21 | (f) G3 OPEN 묶음 둘 등재 + 활성 개수 줄 조정 | 등재 · 머지 시점에 **50** 으로 합산 | ① | 합산 규약은 양쪽 장부가 스스로 적어 둔 것을 따랐다 |

**읽는 법 — ④ 넷 중 셋(P-2·P-3·P-13)이 뿌리 하나다.** Phase 1 이 태도 축의 방향과 모듈 경계를
동시에 틀리게 적었고, 라운드 1 이 그 약속을 **충실히 구현해** cr HIGH-1 이 됐다. 즉 이 slice 의
가장 큰 결함은 「약속을 어긴 것」이 아니라 **「약속을 지킨 것」**이다. 그래서 다음 slice 가 재야
할 것은 「약속을 지켰는가」가 아니라 **「약속이 1차 출처와 맞는가」를 Phase 1 에서 한 번 더 재는
것**이다.

**③ 넷 중 둘(P-10·P-15)은 약속을 적지 않고 뺐더니 그 자리가 정확히 리뷰 finding 이 됐다** —
약속이 맞았고 이탈이 틀렸다. 나머지 둘(P-6·P-11)은 판단이 맞지만 선언이 없었다.

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

**위 표는 라운드 1 실측이고, 대상 코드가 라운드 2 에 바뀐 행이 있다**(M2·M4 가 가리킨 붕괴 판정과
반올림 경로). 그래서 라운드 2 의 방어는 아래에서 **다시 전수로** 쟀다. M1·M3·M5·M5′·M6 이 가리킨
구조(옵션 생성자 폐쇄 · binding 구별 · 하한 타입 · 정책 데이터)는 라운드 2 에 바뀌지 않았다.

### 변이 표 — 라운드 2 (clone `756cf042`, 2026-10-24 23:19~23:24)

버릴 clone 에서 돌렸다. 각 변이는 **바꿔치기**이고(`numstat` 의 `+`·`-` 가 둘 다 0 이 아니다)
`git diff --numstat` 으로 적용을 확인한 뒤에만 test 를 돌렸다. test task 마다 `--rerun` 을 붙였다 —
UP-TO-DATE·FROM-CACHE 로 건너뛴 초록은 실행이 아니다. 필터는 모듈 셋 전체(`:decision:test`
`:workflow:test` `:shared-kernel:test`)다 — 좁은 `--tests` 필터는 **방어 부재와 미측정을 같은
값으로** 만든다.

| # | 변이 | numstat | 결과 | 잡은 자리 |
|---|---|---|---|---|
| **기준선** | 변이 0 | 0/0 | **GREEN** | 변이 전 clone 이 초록임을 먼저 재지 않으면 뒤의 RED 가 변이 탓인지 알 수 없다 |
| R2-M1 | `toStanceRates` 를 라운드 1 의 반전으로 되돌림 | 2/2 | **RED**(7) | `StanceRateMappingTest` **둘뿐**(r2 L-2 정정 — 커널 test 는 `toStanceRates` 를 거치지 않아 이 변이에 반응하지 않는다. verifier 재실측에서 실패 2) |
| R2-M2 | `floorAboveBand` 의 꼭대기를 `safe` → `aggressive` 로 오독 | 1/1 | **RED**(17) | 밴드 붕괴 · 태도 축 · 부분 보류 |
| R2-M3 | 효력율 중복 붕괴 판정 제거(`duplicateRates` 미호출) | 1/1 | **RED**(13) | 밴드 붕괴 셋(중복·clamp 수렴·전수 겹침) |
| R2-M4 | 부분 제시 제거 — 보류가 있으면 메뉴 전체를 죽인다(라운드 1 거동) | 1/5 | **RED**(23) | 부분 보류 + `Unmeasurable.init` 전수 불변식 |
| R2-M5 | 하한 올림 제거(밑돌면 보류) | 1/1 | **RED**(7) | 반올림 셋 |
| R2-M6 | 올림 모드를 `CEILING` → `DOWN`(최소 정수가 아닌 올림) | 1/1 | **RED**(7) | 반올림 — 올린 값이 하한 미만으로 남는다 |
| R2-M7 | `PartiallyOffered` 생성자 전면 개방 | 1/2 | **RED**(7) | `MenuCompileFailureHarnessTest` fixture 3 |
| R2-M8 | `StanceRates` 순서 불변식 방향 반전 | 1/1 | **RED**(35) | `BidMenuTypesTest` + 두 축의 생성 지점 전부 |
| **통제군** | 주석 한 줄(거동 0) | 1/0 | **GREEN**(0) | — |

괄호 안은 로그의 실패 줄 수다. **여덟 전부 RED · 기준선과 통제군 GREEN.** 통제군이 `1/0`
(순수 추가)인 것은 의도다 — 통제군은 거동을 바꾸지 않는 변경이 초록임을 보이는 자리이고,
`--rerun` 이 붙어 **cache 적중이 아니라 실제 실행된** 초록이다.

R2-M2 를 표에 남기는 이유: HIGH-1 의 라벨 수정은 **붕괴 술어의 꼭대기 선택까지 파급**한다
(`rates.safe` 가 꼭대기다). 라벨만 고치고 이 자리를 두면 붕괴를 놓치거나 반대로 오탐하는데,
그 파급이 실제로 test 로 잡히는지를 이 변이가 잰다(17줄 — 한 자리의 오독이 세 축을 동시에 깬다).

### 판정

- **DEC-05 → ⓒ(부분 측정, 강함)** · **DEC-02 → ⓒ(부분 측정, 강함)** · **DEC-04 → ⓒ(부분 측정, 강함, 정정)**.
- 두 행은 **ⓐ 술어 ② 의 선언 예외**로 장부에 등재했다(운영자 결정 2026-10-24, docs PR #88) —
  판정 근거는 「커널·구조 성립 + 7C ② 화면의 『아직 산출 안 함』 상태 표시가 test 로 잠김」이고,
  값 출처 축과 그 값에서만 생기는 축은 `ML-01` 과 함께 **6G 백테스트 판정 뒤**다. 선언 예외는
  ⓐ 로 세지 않으므로 **두 행은 ⓒ 로 남고 수치는 움직이지 않는다**. **ⓐ 전환은 7C ② 장부 커밋**
  이 한다 — 이 slice 가 하지 않는다.
- 집계는 **머지 시점에 재산출**했다: 병합된 main 이 ⓐ 17 · ⓑ 2 · ⓒ 42 였고(7C①b 의 `STR-07`
  승격) 이 레인의 자기 증감 **−1**(`DEC-04` 한 행)을 더해 **ⓐ 16 · ⓑ 2 · ⓒ 43 · 성립 18/61**.
  축별 표는 두 레인이 서로 다른 행(`STR`·`DEC`)을 고쳐 자동 병합됐고 **그 합이 머리 숫자와
  같다**(기계 확인). 완료 조건 3 의 결론은 바뀌지 않는다.
- `DEC-05`·`DEC-02` 를 「홀더 없는 미구현」에서 뺐다(홀더를 달았다).
- **「부분 측정(강함)」 표식은 유지**다(D-G1DEC05-6). D-G1DEC05-4 는 이 표식을 **떼는** 판정을
  승인했는데 그 승인은 **bullet ② 가 비어 있던 라운드 1 전제**에서 나왔고, 라운드 2 가 그 bullet 을
  채워 전제가 바뀌었다. `E-24` 는 **양쪽 23** 으로 고정한다(머지 전 이 레인 22 + 병합된
  `SET-01` 1 — 아래 「문서 등식」의 경위).

### 알려진 제한

1. **메뉴는 production 에서 아직 돌지 않는다.** 소비자 0 · 후보 비율 출처 0. **ⓐ 전환은 7C ②
   상태 test 가 선 뒤 7C ② 장부 커밋이 하고, 값 출처 축은 6G 백테스트 판정 뒤다**(운영자 결정
   2026-10-24, PR #88) — 이 slice 가 재판정하지 않는다.
2. **출하 정본이 `FloorRateReading.Undecided` 라, 오늘 production 값에서는 하한 유래 밴드 붕괴와
   하한 binding 이 발화하지 않는다.** 그 두 상태는 test 정책 위에서만 관측된다 —
   `BidMenuPolicyDataTest` 가 이 사실 자체를 고정하므로, 운영자가 읽기를 정하면 그 test 가 먼저
   깨져 갱신을 강제한다. **단 효력율 중복 붕괴는 읽기 미정에서도 발화한다**(라운드 2 가 넓힌
   자리 — 그 축은 하한과 무관하다).
   - 운영자는 **2026-10-22 에 이 읽기를 `AsBidRateLowerBound(AtOrAbove)` 로 결정했다.** 이 slice 는
     **적용하지 않는다**(출하 정본은 `Undecided` 그대로) — 적용은 7C ② 몫이다.
   - **미결 표식은 출력을 막지 않는다**(verifier M-1 · cr LOW-8). `FloorRateReadingUndecided`
     binding 이 붙은 옵션도 그대로 나간다 — **「`Offered` = 제약 검토 완료」가 아니다.** 그 표식을
     읽고 처분하는 것은 표면의 의무이고 **7C ② 계약 조건**이다: 「이 binding 이 붙은 옵션을
     추천으로 띄우지 않는다」. **그리고 `PublishedFloorRounding` 이 붙은 옵션은 「하한이 금액을
     정했다」로 표시한다**(r3 N-1) — 율은 여유가 있었지만 제출 금액은 하한이 정한 값이다.
3. `BidMenuOutcome.Offered.disclosures` 는 늘 전수라 **값으로는 정보를 나르지 않는다**. 그 자리를 둔 이유는
   고지 의무가 값과 함께 표면 경계까지 이동하게 하려는 것이고, `init` 이 부분 집합을 거부한다.
4. DEC-04 의 빈자리(출하 정책 표)는 이 slice 가 **만들지 않았다** — 값이 운영자 승인 대상이고
   (`minAssessmentSamples` 150 의 근거 문자열 포함) 이 slice 의 산출물이 아니다.
5. **`AppliedFloor` 는 「같은 공고」를 지지 않는다**(cr MEDIUM-5). 보장 범위는 **basis 축**이고,
   다른 공고의 `BaseAmount` 로 만든 하한을 넘겨도 타입은 막지 못한다. 메뉴 경로에서는 커널이
   한 `BidMenuInput.base` 만 쓰는 것으로 성립한다 — 표면 경계의 그 전건은 7C ② 계약 조건이다.
6. **`BidMenuGrounds.bidRate.origin` 은 값의 출처가 아니다**(cr MEDIUM-6). `BidRateOrigin.Recommended`
   는 산술 진입에 필요한 **자리채움**이고, 하한에 걸려 올라간 값도 읽기 미정으로 제약 없이 지나온
   값도 같은 `origin` 을 단다. **값의 이유는 `binding` 이 유일한 출처다.** 지시대로 shared-kernel
   `BidRateOrigin` 에 variant 를 더하지 않았다(그 타입은 관측값/추천값을 가르는 다른 축이다).
7. **D-G1DEC05-1 의 이름 실재 훑기 모집단은 착수 base 의 ⓐ 16 행이다.** 머지로 들어온 7C①b 의
   `STR-07` 은 그 시점에 ⓐ 가 아니었으므로 **이름 실재 확인을 받지 않았다** — 그 행의 인용 이름
   확인은 이 slice 의 범위 밖이다.
8. **`OPEN-DEC02-SCORE-AXIS-FLOOR` 는 이 slice 가 닫지 않는다.** 점수 경로의 `roundedWith` 가
   하한 인자를 주지 않는 것은 **메뉴 부재와 무관**하다(그 경로에 `AppliedFloor` 를 만드는 자리가
   애초에 없다 — 실측). 그리고 그 자리는 하한이 적용될 자리가 아니다: budgetCapture 는
   `기초금액 × 비율 → 반올림 → bidRateAgainst → clamp01` 의 왕복이라 그 `BidAmount` 은 비율을
   점수로 바꾸는 중간값이고 제출·표시·영속되지 않는다. clamp 를 넣으면 하한 미달 예측이 「하한에서
   투찰한 것」으로 점수화돼 신호가 왜곡된다. 점수 의미를 바꾸는 도메인 결정이라 **DEC-10 착수
   계약에서 함께 판정**한다.

9. **후보 율의 출처가 근거에 없다**(r2 M-5 · `OPEN-DEC05-STANCE-RATE-PROVENANCE`).
   `StanceRates` 는 아무 `Rate` 나 받는 public 생성자이고 `BidMenuGrounds` 는 그 율이 **ML
   release 에서 왔는지 손으로 넣은 상수인지**를 싣지 않는다. 그래서 legacy 매직 스프레드 같은
   상수로도 **「근거를 갖춘」 완전한 `Offered`** 가 나온다 — 착수 계약 위협 모델 (0) 첫 줄
   (「근거 없는 숫자가 운영자에게 추천으로 가는 것」)의 **입력 경계**다.
   - **이 slice 는 출처 축을 새로 만들지 않는다**(D-G1DEC05-3 단계 4). 닫는 자리는 `ML-01`
     (ML 배선)이고, 그 slice 가 율의 출처를 실제로 들고 오는 자리다.
   - 지금 해가 없는 이유는 **소비자가 0 이고 화면 표시가 「아직 산출 안 함」 선언 예외**이기
     때문이지 타입이 막아서가 아니다 — 그 구분을 적어 둔다.
   - 7C ② 계약 조건: 율의 ML release 출처를 근거와 함께 표시한다.

### r3 일괄 — finding 대응표

r3 는 **verifier ready-for-review · cr approve**(blocker·high 0). 아래는 차단이 아닌 등재
항목의 처분이고, 번호는 verifier 원문(N-1~N-4 · L-N1~L-N3)과 cr(M1~M3 · L1~L4)이다.

| finding | 무엇이 문제였나 | 처분 |
|---|---|---|
| **N-1**(cr M1) | 올림이 발화하면 **금액은 하한이 정했는데** binding 이 `CandidateRate`(「걸린 제약이 없다」) — 하한을 빼면 1,086,358 → 1,086,357 로 **값이 달라진다** | 금액 축 variant `PublishedFloorRounding` 신설(**D-G1DEC05-7** — 두 축이 함께 걸리면 율 축이 이긴다). 「제약을 지우면 결과가 바뀐다」 대조 test 추가 · 변이 **A1** 이 RED |
| **N-2**(cr M2) | `CollapsedWithOtherStances.otherStances` 만 `frozen()` 밖 — `withheld` 가 `unmodifiableMap` 이어도 **그 값 안의 집합**은 캐스팅 한 줄로 비워진다 | ③′ 와 같은 처방(비공개 생성자 + `invoke` 의 `frozen`). 사후 변조 test · 변이 **A2** |
| **N-3**(cr L1) | fixture 4 가 **한 파일에 copy 둘**이라 「어느 하나라도 막히면 통과」 — `Offered`·`PartiallyOffered` 는 대상 밖이었다(**게이트 술어 결함**) | 타입마다 음성 fixture 하나씩 **다섯**(`4a`~`4e`). verifier 변이 **U2** 가 RED 인지 실측. 동결 뒤 **표적 재검증 대상** |
| **N-4**(cr M3) | `scope.md` 의 base·rollback 기준이 **두 세대 낡음**(`fc15975c`·`bee7c36f`) — 적힌 대로 복원하면 남의 머지분이 사라진다 | `46ac0949`(머지 `20b7129a`)로 재고정 · 계약 갱신 이력 셋 |
| **cr L2** | `경계 등가는 정책이 가른다` 가 `cause` 를 단언하지 않아 두 붕괴 사유를 구별하지 않음 | 두 줄에 `cause` 단언(`DuplicateEffectiveAmounts(880,000)` vs `FloorAboveBand`) |
| **cr L3** | `collidingStances` 의 `firstOrNull` 이 「태도 셋」 전제에 기대고 그 전제가 **주석으로만** 닫힘 | **전수 수집**(`filter`+`flatMap`) + 「전수가 접혔으면 금액은 하나」를 `require` 로 실행 시점에 |
| **cr L4** | 계약 갱신 이력에 r3 새 파일 ↔ in_scope 대조 없음 | 2026-10-26 ② 로 등재(범위 밖 0) |
| **L-N1** | 낡은 KDoc 셋(`AmountDecision` · 순서 주석 · fixture 4) | 전부 갱신 |
| **L-N2** | `OPEN-DEC05-STANCE-RATE-PROVENANCE` 의 역방향 포인터 부재 | `ML-01` 쪽에 달았다 — 닫는 자리에서 보이지 않으면 유예가 유실된다 |
| **L-N3** | 앞 라운드 rollback·acceptance 절이 현행 절과 **나란히** 남음 | 「대체됨」 한 줄로 접었다. 지속적 **방법** 근거(④⑤⑥ 를 생략하지 않는 이유 · 캐시 정직성 · 트리 동일성 갈음)는 현행 절로 옮겼다 — 그것은 라운드 이력이 아니다 |

### 재리뷰 표적 (레인이 스스로 올리는 것)

1. **P-20 — `FLOOR_PROMOTION_ROUNDING` 과 「(e) 신설 수치 0」의 관계**(D-G1DEC05-6). 라운드 2 가
   `RoundingPolicy(MONEY_AXIS_SCALE_DIGITS, CEILING)` 을 추가하고 **정책 슬롯을 두지 않았다** —
   근거는 「원 단위 정수 도메인에서 『하한 이상』 제약을 만족하는 최소 원소가 곧 올림이므로 제약의
   정의이지 운영자가 고를 튜닝이 아니다」이고 `OPEN-DIC-10`(자연 반올림 모드)과 다른 축이라는 것이다.
   **판단이 갈릴 수 있는 자리**이므로 표적으로 올린다.
2. **checklist Phase 1 서술 ↔ 출하 코드**(D-G1DEC05-5) — 위 대조표 21행이 그 대조이고, 그중
   **③ 넷 · ④ 넷**이 어긋난 자리다. 특히 ④ 셋(P-2·P-3·P-13)은 **약속 자체가 1차 출처와 달랐고
   라운드 1 이 그 약속을 지켜 결함이 됐다**.
3. **라운드 1 변이 표의 「대상 코드 불변」 주장** — M1·M3·M5·M5′·M6 을 라운드 2 에 다시 돌리지
   않고 「그 구조가 바뀌지 않았다」로 갈음했다. 그 갈음이 맞는지는 코드 대조로만 확인했다.

### 추적표 `STR-03` — 이름 정정(판정 불변)

`STR-03` 의 production 칸이 인용한 `VERDICT_LADDER_POLICY` 는 **이름 오기**다. 실체는
`bidvector.decision.VerdictLadderPolicyData` 의 `init`(`reviewThreshold <= bidNowThreshold` 거부)과
출하 선언 `bidvector.workflow.evaluation.EVALUATION_LADDER_POLICY_SLOT`(`LadderPolicySlot`) →
`EvaluateCandidatesUseCase` 다. 거동이 실재하므로 **판정은 건드리지 않고 이름만 고쳤다** — 집계는
`DEC-04` 한 행의 결과다.

- **D-G1DEC05-1 의 `app` 조항**: 쓰지 않았다(위 D-G1DEC05-2 실측). 계약 쪽 삭제는 팀장 소관.

### 앞 라운드 실측 절 — **대체됨**

r2 의 acceptance(`756cf042`)·문서 등식·rollback(실측 HEAD `fdf9a277` · 복원 기준 `fc15975c`)
절은 **위 「승인 전 일괄 실측」이 대체한다**(r3 L-N3). 같은 주장을 두 기준에서 적으면 어느 쪽이
현행인지 읽는 사람이 판단해야 하고, 라운드 이력 절은 evidence 규율이 금지한다. 현행 수치는
전부 **실측 기준 `63a207cd` · 복원 기준 `46ac0949`** 한 벌이다.
