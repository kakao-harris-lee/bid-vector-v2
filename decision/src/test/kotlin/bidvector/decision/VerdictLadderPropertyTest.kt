package bidvector.decision

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private val VERSION = PolicyVersion(EffectiveFrom.Initial, "test-verdict-ladder-property")
private val POLICY: Resolution.Resolved<VerdictLadderPolicyData> =
    Resolution.Resolved(
        VerdictLadderPolicyData(
            capacityHoldPriorityThreshold = BigDecimal("0.8"),
            bidNowThreshold = BigDecimal("0.7"),
            reviewThreshold = BigDecimal("0.45"),
            forceBidProbabilityThreshold = BigDecimal("0.8"),
            forceBidMatchedThreshold = BigDecimal("0.7"),
        ),
        VERSION,
    )

private val unitFraction: Arb<BigDecimal> = Arb.bigDecimal(BigDecimal.ZERO, BigDecimal.ONE)
private val maybeScore: Arb<UnitScore?> = Arb.choice(unitFraction.map(::UnitScore), arbitrary { null })

/**
 * scope.md ④, 설계 검토 (4) 7 — 「부재에서 `BidNow` 로 가는 경로가 타입에 없다」의 negative
 * property. priority 가 없으면(또는 priority 는 있지만 force-bid 평가에 필요한
 * probability·matched 중 하나라도 없으면서 priority 단독으로는 아직 BidNow 가 확정되지
 * 않으면) 어떤 조합에서도 `BidNow`가 나오지 않는다.
 */
class VerdictLadderPropertyTest {
    @Test
    fun `priorityScore 가 없으면 어떤 다른 입력 조합에서도 BidNow 가 나오지 않는다`() {
        runBlocking {
            checkAll(maybeScore, maybeScore, Arb.int(0, 20), Arb.int(0, 20)) { probability, matched, current, max ->
                val input = LadderInput(null, probability, matched, current, max)

                val verdict = VerdictLadder.judge(input, POLICY)

                verdict.shouldBeInstanceOf<Verdict.Review>()
                verdict.reasons.single().shouldBeInstanceOf<ReviewReason.MlUnavailable>()
            }
        }
    }

    @Test
    fun `priority 가 review 밴드 안이고 probability·matched 중 하나라도 없으면 BidNow 가 나오지 않는다`() {
        // whichAbsent: 0=probability 만 결측, 1=matched 만 결측, 2=둘 다 결측.
        runBlocking {
            checkAll(
                Arb.bigDecimal(BigDecimal("0.45"), BigDecimal("0.6999")),
                Arb.int(0, 2),
                unitFraction,
            ) { priority, whichAbsent, otherValue ->
                val other = UnitScore(otherValue)
                val probability = if (whichAbsent == 1) other else null
                val matched = if (whichAbsent == 0) other else null
                val input = LadderInput(UnitScore(priority), probability, matched, 0, 10)

                val verdict = VerdictLadder.judge(input, POLICY)

                verdict.shouldBeInstanceOf<Verdict.Review>()
                verdict.reasons.single().shouldBeInstanceOf<ReviewReason.MlUnavailable>()
            }
        }
    }
}
