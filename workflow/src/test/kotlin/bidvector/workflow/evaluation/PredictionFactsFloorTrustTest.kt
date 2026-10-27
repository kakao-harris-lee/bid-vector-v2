package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.decision.PublishedFloorExclusion
import bidvector.decision.UnitScore
import bidvector.decision.priority.ScoreFact
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Rate
import bidvector.workflow.prediction.BidPredictionOutcome
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private fun noticeFloor(value: String): FloorRate =
    FloorRate(Rate.ofFraction(BigDecimal(value)), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))

private fun statutoryFloor(value: String): FloorRate =
    FloorRate(Rate.ofFraction(BigDecimal(value)), FloorRateOrigin.StatutoryTable(EffectiveFrom.Initial))

/**
 * DEC-10 ③ — 평가 margin 경로. 투찰가를 내지는 않지만 **verdict 사다리 점수**를 바꾸므로
 * 가격 경로다(D-DEC10-3). 개연 불가는 「하한 미보고」와 **같은 값으로 접히고**(DEC-10
 * acceptance 첫째) **사유가 남는다**(셋째 — 침묵 스킵 금지).
 */
class PredictionFactsFloorTrustTest {
    private val testCapacity = UnitScore(BigDecimal("0.5"))
    private val testPolicies = testResolvedPolicies()

    private fun factsFor(floorRate: FloorRate?): PredictionComponents {
        val notice = testNoticeWithMoney(floorRate = floorRate)
        val baseAmount = requireNotNull(notice.baseAmount).amount
        return predictedFacts(predicted(), baseAmount, notice, testPolicies, testCapacity, emptyMap())
    }

    @Test
    fun `밴드 안 게시값은 점수에 들어가고 제외 사유가 없다`() {
        val facts = factsFor(noticeFloor("0.88"))

        facts.expectedMargin.shouldBeInstanceOf<ScoreFact.Present<UnitScore>>()
        facts.floorExclusion shouldBe null
    }

    @Test
    fun `게시값 부재는 제외가 아니다 — 사유 없이 하한 없는 점수가 난다`() {
        val facts = factsFor(null)

        facts.expectedMargin.shouldBeInstanceOf<ScoreFact.Present<UnitScore>>()
        facts.floorExclusion shouldBe null
    }

    /**
     * R-FLOOR-03 — 라이브 47건의 `1.00000`. 값은 그대로 「하한 없음」으로 접히되 **이제
     * 계수가 생긴다**(D-DEC10-9). 접힌 점수가 부재일 때의 점수와 **같다**는 것이 「동일하게
     * 취급된다」의 실측이다.
     */
    @Test
    fun `성립 불가한 1,00000 은 하한 미보고와 같은 점수로 접히고 사유가 남는다`() {
        val implausible = factsFor(noticeFloor("1.00000"))
        val absent = factsFor(null)

        implausible.expectedMargin shouldBe absent.expectedMargin
        implausible.floorExclusion shouldBe PublishedFloorExclusion.IMPLAUSIBLE
    }

    @Test
    fun `밴드 아래 게시값도 같은 가지로 접히고 사유가 남는다`() {
        val facts = factsFor(noticeFloor("0.2999"))

        facts.expectedMargin shouldBe factsFor(null).expectedMargin
        facts.floorExclusion shouldBe PublishedFloorExclusion.IMPLAUSIBLE
    }

    /**
     * D-DEC10-9 — 앞 판은 `fraction > 1` 만 `DerivationAbsence.FloorRateOutOfRange` 로 걸러
     * **margin 성분을 떨어뜨렸다**. 그 관문의 선언된 목적은 `MarginInputs.init` 의 `require`
     * 가 터지는 것을 막는 것이었고, 게이트가 앞에 서면서 그 사유의 생산자가 0 이 된다.
     * 이제 `> 1` 도 개연 불가의 한 경우로 접히고 **계수된다**.
     */
    @Test
    fun `1 초과 게시값도 개연 불가 한 가지로 접힌다 — 성분 드롭이 아니다`() {
        val facts = factsFor(noticeFloor("1.2"))

        facts.expectedMargin shouldBe factsFor(null).expectedMargin
        facts.floorExclusion shouldBe PublishedFloorExclusion.IMPLAUSIBLE
    }

    @Test
    fun `법정표 출처는 또 다른 사유로 접힌다 — 게이트가 심판이 아닌 값이다`() {
        val facts = factsFor(statutoryFloor("0.88"))

        facts.expectedMargin shouldBe factsFor(null).expectedMargin
        facts.floorExclusion shouldBe PublishedFloorExclusion.ORIGIN_NOT_JUDGED
    }

    @Test
    fun `밴드 안 값과 접힌 값은 서로 다른 점수다 — 접기가 점수를 바꾼다`() {
        val trusted = factsFor(noticeFloor("0.88"))
        val folded = factsFor(noticeFloor("1.00000"))

        (trusted.expectedMargin == folded.expectedMargin) shouldBe false
    }

    /** 예측 자체가 없는 가지에는 하한 제외가 없다 — 두 축을 한 값으로 합치지 않는다. */
    @Test
    fun `예측 미가용 가지는 하한 제외 사유를 싣지 않는다`() {
        val facts = absentPair(MlUnavailableReason.ScoreNotProvided)

        facts.expectedMargin.shouldBeInstanceOf<ScoreFact.Absent>()
        facts.floorExclusion shouldBe null
    }

    /** 신뢰 못 할 예측 응답은 두 성분을 떨어뜨린다 — 그 가지도 하한 제외와 섞이지 않는다. */
    @Test
    fun `신뢰 못 할 예측 응답은 하한 제외 사유 없이 두 성분을 떨어뜨린다`() {
        val notice = testNoticeWithMoney(floorRate = noticeFloor("1.00000"))
        val baseAmount = requireNotNull(notice.baseAmount).amount
        val untrustworthy: BidPredictionOutcome.Predicted = predicted(fitness = BigDecimal("1.5"))

        val facts = predictedFacts(untrustworthy, baseAmount, notice, testPolicies, testCapacity, emptyMap())

        facts.expectedMargin.shouldBeInstanceOf<ScoreFact.Absent>()
        facts.floorExclusion shouldBe null
    }
}
