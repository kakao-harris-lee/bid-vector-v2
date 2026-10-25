package bidvector.decision.menu

import bidvector.decision.FloorShortfall
import bidvector.decision.FloorUnmeasurableReason
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.export
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.RoundingMode

/**
 * 투찰가 메뉴 커널 회귀(`capability-map.md` DEC-05 + DEC-02 무조건 acceptance).
 *
 * 재는 bullet 과 그 자리:
 * - DEC-05 ① 「근거 없는 옵션을 만들 수 없다」 → `근거` test 셋 + [MenuCompileFailureHarnessTest]
 *   fixture 1(모듈 밖 생성 폐쇄, 유일한 기계적 증명).
 * - DEC-05 ② 「밴드 붕괴가 '정상 3안'으로 위장되지 않는다」 → `밴드 붕괴` test 다섯
 *   (**하한 유래**와 **후보 밴드 자신의 중복** 둘 다 — 라운드 2 HIGH-2).
 * - DEC-02 ① 「어느 제약에 binding 됐는지가 결과에 실린다」 → `binding` test 넷.
 * - DEC-02 ② 「반올림이 하한 아래로 내려가지 않는다」 → `반올림` test 셋(라운드 2 HIGH-3 —
 *   밑돌면 **보류가 아니라 올린다**).
 * - 태도 축의 방향(라운드 2 HIGH-1) → `태도 축` test 둘.
 */
class BidMenuKernelTest {
    @Test
    fun `게시 하한율이 없으면 판정 불가가 아니라 값 출처 부재다`() {
        val outcome = composeBidMenu(testInput(publishedFloorRate = null), testMenuPolicy())

        outcome shouldBe BidMenuOutcome.SourceAbsent(MenuSourceAbsence.PublishedFloorRateAbsent)
    }

    /**
     * 투찰율이 **높을수록 안전**이다 — 임계 사정률(투찰율 ÷ 하한율)이 클수록 실현 사정률이
     * 임계를 넘기 어려워 낙하 여유가 크다. 라벨 축을 뒤집는 변이가 여기서 RED 다.
     */
    @Test
    fun `태도 축 — 안전이 공격보다 임계 사정률도 투찰율도 크다`() {
        val outcome = composeBidMenu(testInput(), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.safe.risk.critical.value.rate shouldBeGreaterThan offered.aggressive.risk.critical.value.rate
        offered.safe.grounds.bidRate.rate shouldBeGreaterThan offered.aggressive.grounds.bidRate.rate
    }

    @Test
    fun `태도 축 — 하한에 걸려 올라가는 쪽은 가장 낮은 공격이다`() {
        val rates = testRates(aggressive = "0.80", recommended = "0.93", safe = "0.96")

        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.aggressive.binding shouldBe BindingConstraint.PublishedFloorBound
        offered.aggressive.grounds.bidRate.rate shouldBe fraction("0.88")
        offered.safe.binding shouldBe BindingConstraint.CandidateRate
    }

    @Test
    fun `근거 — 옵션마다 그 태도의 효력값이 근거로 실린다`() {
        val rates = testRates(aggressive = "0.90", recommended = "0.93", safe = "0.96")

        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.aggressive.grounds.bidRate.rate shouldBe fraction("0.90")
        offered.recommended.grounds.bidRate.rate shouldBe fraction("0.93")
        offered.safe.grounds.bidRate.rate shouldBe fraction("0.96")
    }

    @Test
    fun `근거 — 하한 출처·기초금액 출처·정책 version 이 함께 실린다`() {
        val outcome = composeBidMenu(testInput(), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.safe.grounds.publishedFloorRate shouldBe testFloorRate("0.88")
        offered.safe.grounds.baseProvenance shouldBe testBase().provenance
        offered.safe.grounds.policyVersion shouldBe MENU_TEST_VERSION
    }

    @Test
    fun `근거 — 하한에 걸린 옵션의 근거는 후보값이 아니라 효력값을 말한다`() {
        val rates = testRates(aggressive = "0.80", recommended = "0.93", safe = "0.96")

        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.aggressive.grounds.bidRate.rate shouldBe fraction("0.88")
    }

    @Test
    fun `공통 고지 두 자리가 정상 3안에 전수로 실린다`() {
        val outcome = composeBidMenu(testInput(), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.disclosures shouldBe BidMenuDisclosure.entries.toSet()
        offered.disclosures shouldHaveSize 2
    }

    @Test
    fun `binding — 하한 위 후보는 후보 비율이 값을 정한다`() {
        val outcome = composeBidMenu(testInput(), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.safe.binding shouldBe BindingConstraint.CandidateRate
        offered.recommended.binding shouldBe BindingConstraint.CandidateRate
    }

    /** 경계 등가 — 값이 제약 **위에 올라앉아** 있으므로 「제약이 안 걸렸다」가 아니다. */
    @Test
    fun `binding — 하한과 정확히 같은 후보도 하한이 값을 정한 것이다`() {
        val rates = testRates(aggressive = "0.88", recommended = "0.93", safe = "0.96")

        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.aggressive.binding shouldBe BindingConstraint.PublishedFloorBound
    }

    /**
     * **미결을 「제약 없음」으로 위장하지 않는다**(`OPEN-DEC05-FLOOR-RATE-READING`).
     * 이 값이 [BindingConstraint.CandidateRate] 와 같아지면 운영자는 「하한을 재서 안 걸렸다」와
     * 「하한을 재지 못했다」를 구별할 수 없다 — 둘을 한 값으로 접는 변이가 여기서 RED 다.
     */
    @Test
    fun `binding — 하한율 읽기가 미정이면 제약 없음이 아니라 미정으로 실린다`() {
        val outcome = composeBidMenu(testInput(), testMenuPolicy(reading = FloorRateReading.Undecided))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.safe.binding shouldBe BindingConstraint.FloorRateReadingUndecided
        offered.aggressive.binding shouldBe BindingConstraint.FloorRateReadingUndecided
    }

    /**
     * (라운드 2 MEDIUM-4) — 앞 판은 상수 셋을 `setOf` 에 넣고 크기만 셌다. 그것은 **커널을
     * 부르지 않아** 아무것도 재지 않는다. 세 시나리오를 실제로 돌려 **관측된** binding 이
     * 서로 다름을 잰다.
     */
    @Test
    fun `binding — 세 시나리오가 서로 다른 제약을 관측한다`() {
        val aboveFloor = composeBidMenu(testInput(), testMenuPolicy(reading = atOrAbove()))
        val onFloor =
            composeBidMenu(
                testInput(rates = testRates(aggressive = "0.80", recommended = "0.93", safe = "0.96")),
                testMenuPolicy(reading = atOrAbove()),
            )
        val undecided = composeBidMenu(testInput(), testMenuPolicy(reading = FloorRateReading.Undecided))

        val observed =
            setOf(
                aboveFloor.shouldBeInstanceOf<BidMenuOutcome.Offered>().aggressive.binding,
                onFloor.shouldBeInstanceOf<BidMenuOutcome.Offered>().aggressive.binding,
                undecided.shouldBeInstanceOf<BidMenuOutcome.Offered>().aggressive.binding,
            )

        observed shouldHaveSize 3
    }

    @Test
    fun `밴드 붕괴 — 꼭대기 후보조차 하한 아래면 정상 3안이 아니다`() {
        val rates = testRates(aggressive = "0.70", recommended = "0.75", safe = "0.80")

        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))

        val collapsed = outcome.shouldBeInstanceOf<BidMenuOutcome.BandCollapsed>()
        collapsed.cause shouldBe CollapseCause.FloorAboveBand(fraction("0.88"), fraction("0.80"))
        collapsed.collapsedStances shouldBe BidStance.entries.toSet()
    }

    @Test
    fun `밴드 붕괴 — 경계 등가는 정책이 가른다`() {
        val rates = testRates(aggressive = "0.80", recommended = "0.84", safe = "0.88")

        val atOrAboveOutcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))
        val strictOutcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = strictlyAbove()))

        atOrAboveOutcome.shouldBeInstanceOf<BidMenuOutcome.BandCollapsed>()
        strictOutcome.shouldBeInstanceOf<BidMenuOutcome.BandCollapsed>()
    }

    /**
     * (r2 M-1) — 겹친 태도만 접고 **멀쩡한 세 번째 옵션은 살린다.** 라운드 2 초판은 겹침을
     * 보면 메뉴 전체를 `BandCollapsed` 로 돌려 유효한 safe 까지 버렸다.
     */
    @Test
    fun `부분 붕괴 — 겹친 둘만 보류되고 겹치지 않은 옵션은 제시된다`() {
        val rates = testRates(aggressive = "0.93", recommended = "0.93", safe = "0.96")

        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = FloorRateReading.Undecided))

        val partial = outcome.shouldBeInstanceOf<BidMenuOutcome.PartiallyOffered>()
        partial.offered.keys shouldBe setOf(BidStance.Safe)
        partial.withheld[BidStance.Aggressive] shouldBe
            MenuUnmeasurableReason.CollapsedWithOtherStances(
                stance = BidStance.Aggressive,
                amountWon = 930_000L,
                otherStances = setOf(BidStance.Recommended),
            )
        partial.withheld[BidStance.Recommended] shouldBe
            MenuUnmeasurableReason.CollapsedWithOtherStances(
                stance = BidStance.Recommended,
                amountWon = 930_000L,
                otherStances = setOf(BidStance.Aggressive),
            )
    }

    /** verifier 탐침 P5 — clamp 가 둘을 하한으로 모아도 세 번째는 살아남는다. */
    @Test
    fun `부분 붕괴 — clamp 가 둘을 모아도 꼭대기 옵션은 제시된다`() {
        val rates = testRates(aggressive = "0.80", recommended = "0.85", safe = "0.96")

        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))

        val partial = outcome.shouldBeInstanceOf<BidMenuOutcome.PartiallyOffered>()
        partial.offered.keys shouldBe setOf(BidStance.Safe)
        partial.withheld.keys shouldBe setOf(BidStance.Aggressive, BidStance.Recommended)
    }

    /**
     * verifier 탐침 P1(**출하 정본** `Undecided` · `HALF_UP`) — 율이 달라도 **반올림 뒤 금액**이
     * 같으면 선택지가 아니다. 율 축으로만 재면 이 입력이 「정상 3안」으로 나갔다(r2 M-3).
     * 1,234,567 × 0.93 = 1,148,147.31 · × 0.9300001 = 1,148,147.4334567 — 둘 다 1,148,147 원이다.
     */
    @Test
    fun `부분 붕괴 — 율이 달라도 반올림 뒤 금액이 같으면 접힌다`() {
        val rates = testRates(aggressive = "0.93", recommended = "0.9300001", safe = "0.96")
        val input = testInput(base = testBase(1_234_567L), rates = rates)

        val outcome = composeBidMenu(input, testMenuPolicy(reading = FloorRateReading.Undecided))

        val partial = outcome.shouldBeInstanceOf<BidMenuOutcome.PartiallyOffered>()
        partial.offered.keys shouldBe setOf(BidStance.Safe)
        partial.withheld.keys shouldBe setOf(BidStance.Aggressive, BidStance.Recommended)
        (partial.withheld.getValue(BidStance.Aggressive) as MenuUnmeasurableReason.CollapsedWithOtherStances)
            .amountWon shouldBe 1_148_147L
    }

    @Test
    fun `밴드 붕괴 — 셋이 전부 같은 금액이면 전수가 붕괴로 실린다`() {
        val rates = testRates(aggressive = "0.93", recommended = "0.93", safe = "0.93")

        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = FloorRateReading.Undecided))

        val collapsed = outcome.shouldBeInstanceOf<BidMenuOutcome.BandCollapsed>()
        collapsed.cause shouldBe CollapseCause.DuplicateEffectiveAmounts(930_000L)
        collapsed.collapsedStances shouldBe BidStance.entries.toSet()
    }

    /**
     * 리뷰어 수치 예 — 기초금액 1,234,567 · 하한율 0.87995 면 하한이 소수이고 `DOWN` 이 그
     * 아래로 떨어진다. 라운드 1 은 이 옵션을 **보류**했다(하한에 걸린 옵션이 늘 사라졌다).
     * 지금은 제약을 만족하는 **최소 정수**로 올라가고 binding 이 하한을 가리킨다.
     *
     * 기대값은 손으로 센다 — 1,234,567 × 0.87995 = **1,086,357.23165** 이므로 `DOWN` 은
     * 1,086,357(하한 미만)이고 하한 이상인 최소 정수는 **1,086,358** 이다. 구현에서 끌어온
     * 수가 아니다(라운드 2 의 `StanceRateMappingTest` 교훈 — oracle 은 구현 독립이어야 한다).
     */
    @Test
    fun `반올림 — 하한 경계에서 내림이 밑돌면 보류가 아니라 올린다`() {
        val rates = testRates(aggressive = "0.87995", recommended = "0.93", safe = "0.96")
        val input = testInput(base = testBase(1_234_567L), rates = rates, publishedFloorRate = testFloorRate("0.87995"))

        val outcome = composeBidMenu(input, testMenuPolicy(reading = atOrAbove(), mode = RoundingMode.DOWN))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.aggressive.binding shouldBe BindingConstraint.PublishedFloorBound
        offered.aggressive.amount.value
            .export()
            .won shouldBe 1_086_358L
    }

    /**
     * verifier 탐침 P2 — 하한 **위** 후보가 원 단위 올림만 타는 경로(r2 M-2). 값을 정한 것은
     * 여전히 후보 비율이므로 binding 은 [BindingConstraint.CandidateRate] 로 남아야 한다.
     * 앞 판은 올림 경로에서 무조건 `PublishedFloorBound` 를 달아, 「하한에 걸렸거나 정확히 그
     * 위」라는 그 값의 뜻과 어긋나는 옵션을 만들었다. 변이 N1 이 이 test 에서만 RED 다.
     *
     * 1,234,567 × 0.8799501 = 1,086,357.3551067 → `HALF_UP` 1,086,357 은 적용 하한
     * 1,086,357.23165 **미만**이라 올림이 발화하고 결과는 `ceil(하한)` = 1,086,358 이다.
     */
    @Test
    fun `binding — 하한 위 후보가 올림만 타면 후보 비율이 값을 정한 것으로 남는다`() {
        val rates = testRates(aggressive = "0.8799501", recommended = "0.93", safe = "0.96")
        val input = testInput(base = testBase(1_234_567L), rates = rates, publishedFloorRate = testFloorRate("0.87995"))

        val outcome = composeBidMenu(input, testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.aggressive.binding shouldBe BindingConstraint.CandidateRate
        offered.aggressive.amount.value
            .export()
            .won shouldBe 1_086_358L
        offered.aggressive.grounds.bidRate.rate shouldBe fraction("0.8799501")
    }

    @Test
    fun `반올림 — 하한 위 후보는 내림이 있어도 후보 비율이 값을 정한다`() {
        val rates = testRates(aggressive = "0.93", recommended = "0.94", safe = "0.96")
        val input = testInput(base = testBase(1_000_001L), rates = rates)

        val outcome = composeBidMenu(input, testMenuPolicy(reading = atOrAbove(), mode = RoundingMode.DOWN))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.aggressive.binding shouldBe BindingConstraint.CandidateRate
    }

    /** 올림 대상은 하한 미달뿐이다 — 다른 사유(출처 미선언)는 올려도 해결되지 않으므로 보류다. */
    @Test
    fun `반올림 — 하한 미달이 아닌 사유는 올리지 않고 보류한다`() {
        val outcome = composeBidMenu(testInput(base = undeclaredBase()), testMenuPolicy(reading = atOrAbove()))

        val unmeasurable = outcome.shouldBeInstanceOf<BidMenuOutcome.Unmeasurable>()
        unmeasurable.withheld shouldContainKey BidStance.Safe
        unmeasurable.withheld[BidStance.Safe] shouldBe
            MenuUnmeasurableReason.AmountUnmeasurable(BidStance.Safe, ReasonCode.UNDECLARED_PROVENANCE)
    }

    /**
     * 허용 구간이 열려 있으면 하한 아래 후보는 올릴 자리가 없다(최소 원소 없음). 그 태도
     * 하나만 보류되고 **나머지는 그대로 제시된다** — 라운드 1 은 메뉴 전체를 죽였다(HIGH-3).
     */
    @Test
    fun `부분 보류 — 한 태도를 못 내도 나머지는 제시되고 사유가 실린다`() {
        val rates = testRates(aggressive = "0.88", recommended = "0.93", safe = "0.96")

        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = strictlyAbove()))

        val partial = outcome.shouldBeInstanceOf<BidMenuOutcome.PartiallyOffered>()
        partial.offered.keys shouldBe setOf(BidStance.Recommended, BidStance.Safe)
        partial.withheld[BidStance.Aggressive] shouldBe
            MenuUnmeasurableReason.FloorBoundNotRepresentable(BidStance.Aggressive)
        partial.disclosures shouldBe BidMenuDisclosure.entries.toSet()
    }

    @Test
    fun `리스크 노트 — 표본 부족은 빈도 영이 아니라 사유 있는 측정 불가다`() {
        val outcome = composeBidMenu(testInput(samples = testSamples("1.00", "1.01")), testMenuPolicy(atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        val result = offered.recommended.risk.result
        val unmeasurable = result.shouldBeInstanceOf<FloorShortfall.Unmeasurable>()
        unmeasurable.reason.shouldBeInstanceOf<FloorUnmeasurableReason.SampleInsufficient>()
    }

    @Test
    fun `리스크 노트 — 임계 사정률이 그 옵션의 효력 투찰율에서 나온다`() {
        val rates = testRates(aggressive = "0.88", recommended = "0.99", safe = "1.10")

        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        // 임계 사정률 = 투찰율 ÷ 하한율. 0.99 ÷ 0.88 = 1.125
        offered.recommended.risk.critical.value.rate shouldBe fraction("1.125")
    }
}
