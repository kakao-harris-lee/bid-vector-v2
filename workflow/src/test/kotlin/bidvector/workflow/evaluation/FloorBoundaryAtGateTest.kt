package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.decision.PublishedFloorExclusion
import bidvector.decision.PublishedFloorTrust
import bidvector.decision.UnitScore
import bidvector.decision.priority.ScoreFact
import bidvector.decision.trustPublishedFloorRate
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Rate
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * **경계 셋(`0.0` · `1.0` · `1.2`)을 게이트 수준에서 잰다**(운영자 결정 2026-10-28 — D-DEC10-24).
 *
 * 왜 더하는가 — 4B-5 의 커널 test 셋은 같은 세 값으로 `floorHeadroomOf` 의 특례 분기와
 * `MarginInputs.init` 의 `≤ 1` 술어를 **모듈 안에서** 재고, 그 자리에서는 「게이트가 이 값을
 * 어떻게 처분하는가」를 잴 수 없다. DEC-10 게이트가 개연 밴드(`0.30`–`0.995`)를 세운 뒤로 세
 * 값은 **가격 경로에 도달하지 못하는데**, 커널 test 의 초록만으로는 그 사실이 보이지 않는다 —
 * 통과하는 test 가 production 에서 일어날 수 없는 상태를 재고 있어도 초록은 같다. 그래서 같은
 * 값을 **게이트에 넣어** ① 거부되는지 ② margin 이 떨어지는지를 여기서 잰다.
 *
 * 커널 test 셋은 **지우지도 옮기지도 않았다**(D-DEC10-24) — `decision` 모듈 안에서는 그 값으로
 * 함수를 부를 수 있으므로 그쪽 방어층도 살아 있다. 4B-5 의 evidence 는 건드리지 않았고, 이
 * 파일이 생긴 사실은 이 slice 의 `scope.md`·`checklist.md` 가 적는다.
 */
class FloorBoundaryAtGateTest {
    private val testCapacity = UnitScore(BigDecimal("0.5"))
    private val testPolicies = testResolvedPolicies()

    private fun noticeFloorOf(value: String): FloorRate =
        FloorRate(Rate.ofFraction(BigDecimal(value)), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))

    private fun verdictFor(value: String): PublishedFloorTrust =
        trustPublishedFloorRate(noticeFloorOf(value), testPolicies.floorTrust)

    private fun factsFor(floorRate: FloorRate?): PredictionComponents {
        val notice = testNoticeWithMoney(floorRate = floorRate)
        val baseAmount = requireNotNull(notice.baseAmount).amount
        return predictedFacts(predicted(), baseAmount, notice, testPolicies, testCapacity, emptyMap())
    }

    // ---- ① 세 값은 게이트에서 거부된다 ----

    @Test
    fun `하한 0 은 밴드 아래라 개연 불가다`() {
        verdictFor("0.0").shouldBeInstanceOf<PublishedFloorTrust.Implausible>()
    }

    @Test
    fun `하한 1 은 밴드 위라 개연 불가다`() {
        verdictFor("1.0").shouldBeInstanceOf<PublishedFloorTrust.Implausible>()
    }

    @Test
    fun `1 을 넘는 하한도 개연 불가다`() {
        verdictFor("1.2").shouldBeInstanceOf<PublishedFloorTrust.Implausible>()
    }

    // ---- ② 그래서 margin 에서 떨어진다 ----

    /**
     * 커널 test 「일반식 등식 — floor 0 은 특례 분기 없이 rec 를 낸다」가 재는 것은 **하한이
     * 쓰였는데 결과가 rec 였다**는 등식이다. 게이트 뒤의 같은 값은 **하한이 쓰이지 않는다** —
     * 점수가 「하한 미보고」와 같고 사유가 남는다. 두 주장이 다르다는 것이 이 파일을 더한 이유다.
     */
    @Test
    fun `하한 0 — margin 은 하한 미보고와 같은 값으로 접히고 사유가 남는다`() {
        val facts = factsFor(noticeFloorOf("0.0"))

        facts.expectedMargin shouldBe factsFor(null).expectedMargin
        facts.floorExclusion shouldBe PublishedFloorExclusion.IMPLAUSIBLE
    }

    /**
     * 커널 test 「분모 0 가드」의 게이트 쪽 짝. 분모 0 가드는 **모듈 안 방어층으로 남아 있고**,
     * 여기서 잠그는 것은 그 값이 가격 경로에 **들어오지 못한다**는 사실이다.
     */
    @Test
    fun `하한 1 — margin 은 하한 미보고와 같은 값으로 접히고 사유가 남는다`() {
        val facts = factsFor(noticeFloorOf("1.0"))

        facts.expectedMargin shouldBe factsFor(null).expectedMargin
        facts.floorExclusion shouldBe PublishedFloorExclusion.IMPLAUSIBLE
    }

    /**
     * 커널 test 「최후 방어선 — `floorRate` 가 1 을 넘으면 생성 실패」의 게이트 쪽 짝. 게이트
     * 뒤에서는 **생성 실패가 아니라** 성분 드롭이다(D-DEC10-12 — 사용자 승인 거동 D-4B6-4 유지).
     * `MarginInputs.init` 의 `≤ 1` require 는 모듈 안 최후 방어선으로 남는다(D-DEC10-9).
     */
    @Test
    fun `1 을 넘는 하한 — margin 성분이 떨어지고 그래도 계수된다`() {
        val facts = factsFor(noticeFloorOf("1.2"))

        facts.expectedMargin shouldBe ScoreFact.Absent(MlUnavailableReason.InvalidRequest)
        facts.floorExclusion shouldBe PublishedFloorExclusion.IMPLAUSIBLE
    }

    // ---- 양성 대조 — 위 단언들이 공허하지 않다 ----

    /**
     * 「접힌다」가 의미를 가지려면 **접히지 않는 값**이 다른 점수여야 한다. 밴드 안 값이
     * 통과하고 그 점수가 하한 미보고와 다름을 같은 자리에서 잰다 — 이것이 없으면 위 네 단언은
     * 「하한이 아무 영향도 없다」와 구별되지 않는다.
     */
    @Test
    fun `양성 대조 — 밴드 안 하한은 통과하고 미보고와 다른 점수를 낸다`() {
        verdictFor("0.88").shouldBeInstanceOf<PublishedFloorTrust.Trusted>()

        val trusted = factsFor(noticeFloorOf("0.88"))
        trusted.floorExclusion shouldBe null
        trusted.expectedMargin shouldNotBe factsFor(null).expectedMargin
    }
}
