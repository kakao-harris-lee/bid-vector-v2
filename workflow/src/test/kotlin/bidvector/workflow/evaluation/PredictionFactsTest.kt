package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.decision.UnitScore
import bidvector.decision.priority.PRIORITY_POLICY
import bidvector.decision.priority.ScoreFact
import bidvector.decision.priority.derive.DERIVATION_POLICY
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * verifier r1 F-1 — [predictedFacts]가 `MarginInputs.init`의 세 술어(recommendedRate·
 * predictedRate·floorRate 각 `fraction ≤ 1`)를 **호출 전에** 전부 판정하는지 직접 잰다.
 * `analyze()` 전체를 거치면 `MlAnalysisOutcome.Analyzed`가 budgetCapture·expectedMargin
 * 성분을 노출하지 않아 이 관문의 개별 성패를 볼 수 없다 — `internal` 함수를 같은 패키지
 * test 가 직접 부른다(init ≠ gate: 값 타입의 `require`는 마지막 안전판이지 이 관문이 아니다).
 */
class PredictionFactsTest {
    private val testCapacity = UnitScore(BigDecimal("0.5"))

    private val testPolicies: ResolvedPolicies by lazy {
        val version = PolicyVersion(EffectiveFrom.Initial, "test")
        ResolvedPolicies(
            opportunity = OPPORTUNITY_POLICY.entries.single().second,
            opportunityVersion = version,
            derivation = Resolution.Resolved(DERIVATION_POLICY.entries.single().second, version),
            priority = PRIORITY_POLICY.entries.single().second,
        )
    }

    @Test
    fun `candidates base 1 0 은 통과하고 1 0000001 은 두 성분 Absent(ContractViolation) 이며 예외가 없다`() {
        val notice = testNoticeWithMoney()
        val baseAmount = requireNotNull(notice.baseAmount).amount

        val atBoundary =
            predicted(
                conservative = Rate.ofFraction(BigDecimal("0.95")),
                base = Rate.ofFraction(BigDecimal("1.0")),
                aggressive = Rate.ofFraction(BigDecimal("1.05")),
            )
        val overBoundary =
            predicted(
                conservative = Rate.ofFraction(BigDecimal("1.0")),
                base = Rate.ofFraction(BigDecimal("1.0000001")),
                aggressive = Rate.ofFraction(BigDecimal("1.1")),
            )

        val (atBudget, atMargin) =
            shouldNotThrowAny { predictedFacts(atBoundary, baseAmount, notice, testPolicies, testCapacity) }
        atBudget.shouldBePresent()
        atMargin.shouldBePresent()

        val (overBudget, overMargin) =
            shouldNotThrowAny { predictedFacts(overBoundary, baseAmount, notice, testPolicies, testCapacity) }
        overBudget shouldBe ScoreFact.Absent(MlUnavailableReason.ContractViolation)
        overMargin shouldBe ScoreFact.Absent(MlUnavailableReason.ContractViolation)
    }

    @Test
    fun `floorRate 1 0 은 margin 통과, 1 0000001 은 margin 만 Absent(InvalidRequest) — budgetCapture 는 무관`() {
        val floorAtBoundary =
            FloorRate(Rate.ofFraction(BigDecimal("1.0")), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))
        val floorOverBoundary =
            FloorRate(Rate.ofFraction(BigDecimal("1.0000001")), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))
        val noticeAtBoundary = testNoticeWithMoney(floorRate = floorAtBoundary)
        val noticeOverBoundary = testNoticeWithMoney(floorRate = floorOverBoundary)
        val baseAmount = requireNotNull(noticeAtBoundary.baseAmount).amount
        val validPrediction = predicted()

        val (atBudget, atMargin) =
            shouldNotThrowAny {
                predictedFacts(validPrediction, baseAmount, noticeAtBoundary, testPolicies, testCapacity)
            }
        atBudget.shouldBePresent()
        atMargin.shouldBePresent()

        val (overBudget, overMargin) =
            shouldNotThrowAny {
                predictedFacts(validPrediction, baseAmount, noticeOverBoundary, testPolicies, testCapacity)
            }
        overBudget.shouldBePresent()
        overMargin shouldBe ScoreFact.Absent(MlUnavailableReason.InvalidRequest)
    }

    private fun ScoreFact<UnitScore>.shouldBePresent() {
        check(this is ScoreFact.Present) { "Present 가 아니다: $this" }
    }
}
