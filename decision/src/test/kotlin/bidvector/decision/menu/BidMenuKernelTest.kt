package bidvector.decision.menu

import bidvector.decision.FloorShortfall
import bidvector.decision.FloorUnmeasurableReason
import bidvector.sharedkernel.ReasonCode
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.RoundingMode

/**
 * 투찰가 메뉴 커널 회귀(`capability-map.md` DEC-05 + DEC-02 무조건 acceptance).
 *
 * 이 class 가 재는 bullet 과 그 자리:
 * - DEC-05 ① 「값만 있고 근거 없는 옵션을 만들 수 없다」 → 아래 `근거` test 둘 +
 *   [MenuCompileFailureHarnessTest] fixture 1(모듈 밖 생성 폐쇄, 유일한 기계적 증명).
 * - DEC-05 ② 「밴드 붕괴가 '정상 3안'으로 위장되지 않는다」 → `밴드 붕괴` test 셋.
 * - DEC-02 ① 「어느 제약에 binding 됐는지가 결과에 실린다」 → `binding` test 넷
 *   (**네 상태가 서로 다른 값이다** — 하나를 지우면 다른 하나와 구별되지 않아 RED).
 * - DEC-02 ② 「반올림이 하한 아래로 내려가지 않는다」 → `반올림` test 둘.
 */
class BidMenuKernelTest {
    @Test
    fun `게시 하한율이 없으면 판정 불가가 아니라 값 출처 부재다`() {
        val outcome = composeBidMenu(testInput(publishedFloorRate = null), testMenuPolicy())

        outcome shouldBe BidMenuOutcome.SourceAbsent(MenuSourceAbsence.PublishedFloorRateAbsent)
    }

    @Test
    fun `근거 — 정상 3안의 옵션마다 태도·리스크 노트·근거가 실린다`() {
        val outcome = composeBidMenu(testInput(), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        listOf(offered.safe, offered.recommended, offered.aggressive).forEach { option ->
            option.grounds.publishedFloorRate shouldBe testFloorRate("0.88")
            option.grounds.baseProvenance shouldBe testBase().provenance
            option.grounds.policyVersion shouldBe MENU_TEST_VERSION
            option.grounds.bidRate.rate shouldBe option.grounds.bidRate.rate
        }
        offered.safe.stance shouldBe BidStance.Safe
        offered.recommended.stance shouldBe BidStance.Recommended
        offered.aggressive.stance shouldBe BidStance.Aggressive
    }

    @Test
    fun `근거 — 근거의 투찰율은 그 옵션이 실제로 쓴 효력 투찰율이다`() {
        val rates = testRates("0.80", "0.93", "0.96")
        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        // safe 는 하한(0.88)에 걸려 올라갔다 — 근거가 후보값 0.80 이 아니라 효력값 0.88 을 말한다.
        offered.safe.grounds.bidRate.rate shouldBe fraction("0.88")
        offered.recommended.grounds.bidRate.rate shouldBe fraction("0.93")
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
        offered.aggressive.binding shouldBe BindingConstraint.CandidateRate
    }

    @Test
    fun `binding — 하한 아래 후보는 하한이 값을 정하고 그 사실이 결과에 실린다`() {
        val rates = testRates("0.80", "0.93", "0.96")
        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.safe.binding shouldBe BindingConstraint.PublishedFloorBound
        offered.recommended.binding shouldBe BindingConstraint.CandidateRate
    }

    /** 경계 등가 — 값이 제약 **위에 올라앉아** 있으므로 「제약이 안 걸렸다」가 아니다. */
    @Test
    fun `binding — 하한과 정확히 같은 후보도 하한이 값을 정한 것이다`() {
        val rates = testRates("0.88", "0.93", "0.96")
        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.safe.binding shouldBe BindingConstraint.PublishedFloorBound
    }

    /**
     * **미결을 「제약 없음」으로 위장하지 않는다**(fail-closed, `OPEN-DEC05-FLOOR-RATE-READING`).
     * 이 값이 [BindingConstraint.CandidateRate] 와 **같은 값이면** 운영자는 「하한을 재서 안
     * 걸렸다」와 「하한을 재지 못했다」를 구별할 수 없다 — 둘을 한 값으로 접는 변이가 여기서 RED 다.
     */
    @Test
    fun `binding — 하한율 읽기가 미정이면 제약 없음이 아니라 미정으로 실린다`() {
        val outcome = composeBidMenu(testInput(), testMenuPolicy(reading = FloorRateReading.Undecided))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        offered.safe.binding shouldBe BindingConstraint.FloorRateReadingUndecided
        offered.recommended.binding shouldBe BindingConstraint.FloorRateReadingUndecided
        offered.aggressive.binding shouldBe BindingConstraint.FloorRateReadingUndecided
    }

    @Test
    fun `binding — 네 상태가 서로 다른 값이라 제약을 구별한다`() {
        val distinct =
            setOf(
                BindingConstraint.CandidateRate,
                BindingConstraint.PublishedFloorBound,
                BindingConstraint.FloorRateReadingUndecided,
            )

        distinct shouldHaveSize 3
    }

    @Test
    fun `밴드 붕괴 — 꼭대기 후보조차 하한 아래면 정상 3안이 아니다`() {
        val rates = testRates("0.70", "0.75", "0.80")
        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))

        outcome shouldBe BidMenuOutcome.BandCollapsed(lowerBound = fraction("0.88"), upperBound = fraction("0.80"))
    }

    @Test
    fun `밴드 붕괴 — 경계 등가는 정책이 가른다`() {
        val rates = testRates("0.80", "0.84", "0.88")

        val atOrAboveOutcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))
        val strictOutcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = strictlyAbove()))

        atOrAboveOutcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        strictOutcome shouldBe BidMenuOutcome.BandCollapsed(fraction("0.88"), fraction("0.88"))
    }

    /**
     * 허용 구간이 열려 있으면 **최소 원소가 없다** — 엡실론을 지어내 올리지 않고 사유 있는
     * 실패를 낸다(값을 지어내지 않는다는 규율의 이 자리 집행).
     */
    @Test
    fun `판정 불가 — 열린 하한에서는 하한 아래 후보를 올릴 자리가 없다`() {
        val rates = testRates("0.88", "0.93", "0.96")
        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = strictlyAbove()))

        outcome shouldBe
            BidMenuOutcome.Unmeasurable(MenuUnmeasurableReason.FloorBoundNotRepresentable(BidStance.Safe))
    }

    /**
     * DEC-02 ② — 내림 잔차가 적용 하한을 밑돌면 **조용히 성공하지 않는다**. 기초금액
     * 1,000,001 × 0.88 = 880,000.88 이고 `DOWN` 은 880,000 으로 내려 하한 미만이다.
     */
    @Test
    fun `반올림 — 내림 잔차가 적용 하한을 밑돌면 사유 있는 실패다`() {
        val rates = testRates("0.88", "0.93", "0.96")
        val input = testInput(base = testBase(1_000_001L), rates = rates)

        val outcome = composeBidMenu(input, testMenuPolicy(reading = atOrAbove(), mode = RoundingMode.DOWN))

        outcome shouldBe
            BidMenuOutcome.Unmeasurable(
                MenuUnmeasurableReason.AmountUnmeasurable(BidStance.Safe, ReasonCode.ROUNDED_BELOW_FLOOR),
            )
    }

    /** 하한 **위** 값이 반올림으로 조금 내려가는 것은 위반이 아니다 — 하한 자신이 기준이기 때문이다. */
    @Test
    fun `반올림 — 하한 위 후보는 내림이 있어도 성립한다`() {
        val rates = testRates("0.93", "0.94", "0.96")
        val input = testInput(base = testBase(1_000_001L), rates = rates)

        val outcome = composeBidMenu(input, testMenuPolicy(reading = atOrAbove(), mode = RoundingMode.DOWN))

        outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
    }

    /**
     * 리스크 노트는 DEC-04 커널의 판정 봉투 그대로다 — 표본이 문턱 미만이면 빈도를 지어내지
     * 않고 사유 있는 측정 불가가 실린다(정직 명세 2).
     */
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
        val rates = testRates("0.88", "0.99", "1.10")
        val outcome = composeBidMenu(testInput(rates = rates), testMenuPolicy(reading = atOrAbove()))

        val offered = outcome.shouldBeInstanceOf<BidMenuOutcome.Offered>()
        // 임계 사정률 = 투찰율 ÷ 하한율. 0.99 ÷ 0.88 = 1.125
        offered.recommended.risk.critical.value.rate shouldBe fraction("1.125")
    }
}
