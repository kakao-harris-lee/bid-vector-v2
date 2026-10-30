package bidvector.workflow.result

import bidvector.settlement.AwardMatchingPolicyData
import bidvector.settlement.AwardWinnerTradeName
import bidvector.settlement.OperatorTradeName
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Provenance
import bidvector.workflow.opening.OpeningProgress
import bidvector.workflow.opening.OpeningResultFigures
import bidvector.workflow.opening.OpeningResultView
import bidvector.workflow.opening.ProvisionalRankOne
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 대사 판정의 **구성**(M7/7C⑤-b, D-7C5B-7 · 레인 정정 2026-10-31: 사유는 여덟이 아니라 **열**) —
 * SET-01 커널은 낙찰·패찰과 사유 넷만 낸다. 유찰·재입찰과 개찰 축 여섯은 커널 밖이라 이 함수가
 * **조립**한다. mock 없이 값으로만 잰다.
 *
 * 여기서 잠기는 것: 판정 불가 사유 **열이 각각 도달 가능**하다는 것(열거를 손으로 들지 않고
 * [ReconciliationBlocker.entries] 로 센다 — 사유를 더하면 이 test 가 먼저 붉는다) · 유찰·재입찰이
 * 판정 불가로 접히지 않는다는 것 · 개찰 축이 커널 축보다 **먼저**라는 것.
 */
class ReconcileBidTest {
    private val policy = AwardMatchingPolicyData(listOf("주식회사", "(주)"))
    private val mine = OperatorTradeName.Declared("(주)가상해양기술")
    private val sameCompany = AwardWinnerTradeName.Published("주식회사 가상해양기술")
    private val otherCompany = AwardWinnerTradeName.Published("(주)가상토목건설")

    private fun figures(confirmedAward: AwardAmount? = null) =
        OpeningResultFigures(
            participantCount = 7,
            actualOpeningAt = Instant.parse("2026-10-30T05:00:00Z"),
            confirmedAward = confirmedAward,
            provisionalRankOne = ProvisionalRankOne.NotObserved,
            observedAt = Instant.parse("2026-10-30T06:00:00Z"),
        )

    private fun observed(progress: OpeningProgress) = OpeningResultView.Observed(progress, figures())

    private fun verdict(
        opening: OpeningResultView,
        operator: OperatorTradeName = mine,
        winner: AwardWinnerTradeName = sameCompany,
    ) = reconcileBid(opening, operator, winner, policy)

    /**
     * 사유마다 그것을 내는 입력 — `when` 이 **소진**이라 사유를 더하면 이 함수가 컴파일되지 않고,
     * [ReconciliationBlocker.entries] 를 도는 test 가 그 입력이 정말 그 사유를 내는지 잰다.
     * 둘이 함께라야 「더했는데 아무도 안 본다」가 막힌다.
     */
    private fun inputFor(
        blocker: ReconciliationBlocker,
    ): Triple<OpeningResultView, OperatorTradeName, AwardWinnerTradeName> =
        when (blocker) {
            ReconciliationBlocker.NOTICE_ABSENT -> {
                Triple(OpeningResultView.NoticeAbsent, mine, sameCompany)
            }

            ReconciliationBlocker.NOT_OPENED_YET -> {
                Triple(OpeningResultView.NotOpenedYet, mine, sameCompany)
            }

            ReconciliationBlocker.NOT_COLLECTED -> {
                Triple(OpeningResultView.NotCollected, mine, sameCompany)
            }

            ReconciliationBlocker.OBSERVED_NOT_PROMOTED -> {
                Triple(OpeningResultView.ObservedNotPromoted, mine, sameCompany)
            }

            ReconciliationBlocker.PROGRESS_UNRECOGNIZED -> {
                Triple(observed(OpeningProgress.UNRECOGNIZED), mine, sameCompany)
            }

            ReconciliationBlocker.PROGRESS_ABSENT -> {
                Triple(observed(OpeningProgress.ABSENT), mine, sameCompany)
            }

            ReconciliationBlocker.OPERATOR_NAME_NOT_DECLARED -> {
                Triple(observed(OpeningProgress.OPENED), OperatorTradeName.NotDeclared, sameCompany)
            }

            ReconciliationBlocker.OPERATOR_NAME_NORMALIZES_TO_EMPTY -> {
                Triple(observed(OpeningProgress.OPENED), OperatorTradeName.Declared("(주)"), sameCompany)
            }

            ReconciliationBlocker.WINNER_NAME_NOT_PUBLISHED -> {
                Triple(observed(OpeningProgress.OPENED), mine, AwardWinnerTradeName.NotPublished)
            }

            ReconciliationBlocker.WINNER_NAME_NORMALIZES_TO_EMPTY -> {
                Triple(observed(OpeningProgress.OPENED), mine, AwardWinnerTradeName.Published("(주)"))
            }
        }

    @Test
    fun `판정 불가 사유 열이 각각 도달 가능하다`() {
        ReconciliationBlocker.entries.size shouldBe 10
        ReconciliationBlocker.entries.forEach { blocker ->
            val (opening, operator, winner) = inputFor(blocker)
            reconcileBid(opening, operator, winner, policy) shouldBe ReconciliationVerdict.Undecidable(blocker)
        }
    }

    /**
     * 「등재 밖」과 「부재」를 접지 않는다 — 7C⑤-a 가 가른 둘(`OpeningProgress` KDoc)을 대사
     * 사유에서 도로 접으면 「상류 어휘가 늘었다」가 보이지 않는다.
     */
    @Test
    fun `진행구분 등재 밖과 부재는 서로 다른 사유다`() {
        val unrecognized = verdict(observed(OpeningProgress.UNRECOGNIZED))
        val absent = verdict(observed(OpeningProgress.ABSENT))
        (unrecognized == absent) shouldBe false
    }

    @Test
    fun `유찰과 재입찰은 판정 불가가 아니라 자기 결말이다`() {
        verdict(observed(OpeningProgress.FAILED_BID)) shouldBe ReconciliationVerdict.FailedBid
        verdict(observed(OpeningProgress.REBID)) shouldBe ReconciliationVerdict.Rebid
    }

    /** 유찰이면 상호를 보지 않는다 — 상호가 미설정이어도 결말은 유찰이다. */
    @Test
    fun `유찰은 상호 미설정에도 유찰이다`() {
        verdict(observed(OpeningProgress.FAILED_BID), operator = OperatorTradeName.NotDeclared) shouldBe
            ReconciliationVerdict.FailedBid
    }

    @Test
    fun `개찰완료 — 정규화 뒤 상호가 같으면 낙찰, 다르면 패찰이다`() {
        verdict(observed(OpeningProgress.OPENED), winner = sameCompany) shouldBe ReconciliationVerdict.Won
        verdict(observed(OpeningProgress.OPENED), winner = otherCompany) shouldBe ReconciliationVerdict.Lost
    }

    /**
     * **낙찰자 상호 미공개는 패찰이 아니다** — SET-01 acceptance 의 3-state 가 이 조립을 지나서도
     * 살아 있는지 잰다(접히면 분모가 오염된다).
     */
    @Test
    fun `낙찰자 상호 미공개가 패찰로 접히지 않는다`() {
        val undecidable = verdict(observed(OpeningProgress.OPENED), winner = AwardWinnerTradeName.NotPublished)
        (undecidable == ReconciliationVerdict.Lost) shouldBe false
    }

    /**
     * 개찰 축이 커널 축보다 **먼저**다 — 아직 개찰 전인 건에 「상호 미설정」을 사유로 내면
     * 운영자가 상호를 채워도 답이 달라지지 않는데 채우라고 말하는 셈이 된다.
     */
    @Test
    fun `개찰 축이 커널 축보다 먼저다`() {
        verdict(OpeningResultView.NotOpenedYet, operator = OperatorTradeName.NotDeclared) shouldBe
            ReconciliationVerdict.Undecidable(ReconciliationBlocker.NOT_OPENED_YET)
        verdict(OpeningResultView.ObservedNotPromoted, winner = AwardWinnerTradeName.NotPublished) shouldBe
            ReconciliationVerdict.Undecidable(ReconciliationBlocker.OBSERVED_NOT_PROMOTED)
    }

    /** 운영자 축이 결과 축보다 먼저다(legacy 순서 — 커널이 이미 지키는 것이 조립 뒤에도 남는가). */
    @Test
    fun `둘 다 없으면 운영자 축 사유가 먼저다`() {
        verdict(
            observed(OpeningProgress.OPENED),
            operator = OperatorTradeName.NotDeclared,
            winner = AwardWinnerTradeName.NotPublished,
        ) shouldBe ReconciliationVerdict.Undecidable(ReconciliationBlocker.OPERATOR_NAME_NOT_DECLARED)
    }

    /** 확정 낙찰가가 있어도 판정은 **상호로만** 선다 — 금액은 대사에 들어오지 않는다(SET-01). */
    @Test
    fun `확정 낙찰가가 있어도 상호가 다르면 패찰이다`() {
        val award = AwardAmount(1_000_000L, Currency.KRW, Provenance.DerivedFromOpening)
        reconcileBid(
            OpeningResultView.Observed(OpeningProgress.OPENED, figures(confirmedAward = award)),
            mine,
            otherCompany,
            policy,
        ) shouldBe ReconciliationVerdict.Lost
    }
}
