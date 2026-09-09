package bidvector.decision

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private val TEST_VERSION = PolicyVersion(EffectiveFrom.Initial, "test-verdict-ladder")

/** 조사 §2.4 legacy 값(legacy-behavior, test 정책에만 — `OPEN-4B1-LADDER-THRESHOLDS`). */
private fun legacyPolicy(): Resolution.Resolved<VerdictLadderPolicyData> =
    Resolution.Resolved(
        VerdictLadderPolicyData(
            capacityHoldPriorityThreshold = BigDecimal("0.8"),
            bidNowThreshold = BigDecimal("0.7"),
            reviewThreshold = BigDecimal("0.45"),
            forceBidProbabilityThreshold = BigDecimal("0.8"),
            forceBidMatchedThreshold = BigDecimal("0.7"),
        ),
        TEST_VERSION,
    )

private fun score(value: String): UnitScore = UnitScore(BigDecimal(value))

private fun inputOf(
    priority: String?,
    probability: String? = null,
    matched: String? = null,
    currentActiveBids: Int = 0,
    maxActiveBids: Int = 10,
): LadderInput =
    LadderInput(
        priorityScore = priority?.let(::score),
        probabilityScore = probability?.let(::score),
        matchedScore = matched?.let(::score),
        currentActiveBids = currentActiveBids,
        maxActiveBids = maxActiveBids,
    )

/**
 * scope.md ①②, 조사 §2.2 분기 전수 — 사다리 네 분기(설계 검토 (1) — 소진 `when`,
 * 모든 분기가 결과를 명시 반환한다. legacy 의 부작위 skip(N-3)을 뒤집는다).
 */
class VerdictLadderTest {
    @Test
    fun `① capacity-hold — 진행중 입찰이 한도에 닿고 priority 가 임계 미만이면 Skip(CapacityHold)`() {
        val input = inputOf(priority = "0.5", currentActiveBids = 2, maxActiveBids = 2)

        val verdict = VerdictLadder.judge(input, legacyPolicy())

        verdict.shouldBeInstanceOf<Verdict.Skip>()
        verdict.reason shouldBe SkipReason.CapacityHold
    }

    @Test
    fun `② priority 승격 — priority 가 bidNowThreshold 이상이면 BidNow(PriorityAboveBidNowThreshold)`() {
        val input = inputOf(priority = "0.75")

        val verdict = VerdictLadder.judge(input, legacyPolicy())

        verdict.shouldBeInstanceOf<Verdict.BidNow>()
        val reason = verdict.reasons.single()
        reason.shouldBeInstanceOf<BidNowReason.PriorityAboveBidNowThreshold>()
        reason.priority shouldBe BigDecimal("0.75")
        reason.threshold shouldBe BigDecimal("0.7")
    }

    @Test
    fun `② force-bid 우회 — priority 낮아도 probability·matched 가 임계 이상이면 BidNow(ForceBidOverride)`() {
        // 조사 §5.1 실측 — priority in [0.45, 0.70) 인데도 force-bid 로 승격.
        val input = inputOf(priority = "0.5", probability = "0.85", matched = "0.75")

        val verdict = VerdictLadder.judge(input, legacyPolicy())

        verdict.shouldBeInstanceOf<Verdict.BidNow>()
        val reason = verdict.reasons.single()
        reason.shouldBeInstanceOf<BidNowReason.ForceBidOverride>()
        reason.probability shouldBe BigDecimal("0.85")
        reason.matched shouldBe BigDecimal("0.75")
    }

    @Test
    fun `③ review 밴드 — priority 가 review-bidNow 구간이고 force-bid 도 확정적으로 거짓이면 Review(PriorityInReviewBand)`() {
        // force-bid 는 priority 와 무관하다(조사 §5.1 — "regardless of the priority
        // score") — review/skip 로 떨어지려면 probability·matched 가 「없다」가 아니라
        // 「임계 미만으로 확정됐다」여야 한다. 그래서 이 test 는 둘 다 낮은 값으로 준다.
        val input = inputOf(priority = "0.5", probability = "0.1", matched = "0.1")

        val verdict = VerdictLadder.judge(input, legacyPolicy())

        verdict.shouldBeInstanceOf<Verdict.Review>()
        val reason = verdict.reasons.single()
        reason.shouldBeInstanceOf<ReviewReason.PriorityInReviewBand>()
        reason.priority shouldBe BigDecimal("0.5")
    }

    @Test
    fun `④ 기본 — 위 셋 다 거짓이면 Skip(LowPriority)`() {
        // ③과 같은 이유로 probability·matched 를 확정적으로 낮게 준다.
        val input = inputOf(priority = "0.3", probability = "0.1", matched = "0.1")

        val verdict = VerdictLadder.judge(input, legacyPolicy())

        verdict.shouldBeInstanceOf<Verdict.Skip>()
        verdict.reason shouldBe SkipReason.LowPriority
    }

    @Test
    fun `순서 민감도(1D ProvenanceRules 관례) — capacity-hold 와 force-bid 조건이 동시에 참이면 capacity-hold 가 이긴다`() {
        // 조사 §2.1 실측: elif 라 branch 1 이 참이면 branch 2 는 평가조차 안 된다.
        val input =
            inputOf(priority = "0.5", probability = "0.9", matched = "0.9", currentActiveBids = 2, maxActiveBids = 2)

        val verdict = VerdictLadder.judge(input, legacyPolicy())

        verdict.shouldBeInstanceOf<Verdict.Skip>()
        verdict.reason shouldBe SkipReason.CapacityHold
    }

    @Test
    fun `④ ML 부재 — priorityScore 가 없으면 어떤 분기도 평가하지 않고 Review(MlUnavailable)`() {
        val input = inputOf(priority = null)

        val verdict = VerdictLadder.judge(input, legacyPolicy())

        verdict.shouldBeInstanceOf<Verdict.Review>()
        val reason = verdict.reasons.single()
        reason.shouldBeInstanceOf<ReviewReason.MlUnavailable>()
    }

    @Test
    fun `④ ML 부재 — priority 는 있으나 force-bid 에 필요한 probability·matched 가 없으면 Review(MlUnavailable)`() {
        // priority 는 review 밴드 안이라(0.5) capacity-hold·priority 승격 둘 다 아니고,
        // force-bid 를 평가하려는데 probability·matched 가 없다.
        val input = inputOf(priority = "0.5", probability = null, matched = null)

        val verdict = VerdictLadder.judge(input, legacyPolicy())

        verdict.shouldBeInstanceOf<Verdict.Review>()
        val reason = verdict.reasons.single()
        reason.shouldBeInstanceOf<ReviewReason.MlUnavailable>()
    }

    @Test
    fun `④ ML 부재라도 priority 만으로 이미 BidNow 가 확정되면 probability·matched 부재는 묻지 않는다`() {
        val input = inputOf(priority = "0.9", probability = null, matched = null)

        val verdict = VerdictLadder.judge(input, legacyPolicy())

        verdict.shouldBeInstanceOf<Verdict.BidNow>()
        verdict.reasons.single().shouldBeInstanceOf<BidNowReason.PriorityAboveBidNowThreshold>()
    }
}
