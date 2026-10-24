package bidvector.workflow.prediction

import bidvector.decision.menu.BidStance
import bidvector.sharedkernel.Rate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private fun fraction(value: String): Rate = Rate.ofFraction(BigDecimal(value))

/**
 * **legacy 브리지 표를 선언 데이터로 둔다**(`app/ai/price_prediction/guardrails_apply.py:191-199`
 * 의 `_CANDIDATE_LABEL_TO_SCENARIO_STANCE`). 구현에서 베낀 기대값이 아니라 **1차 출처**다.
 */
private val LEGACY_BRIDGE: Map<String, BidStance> =
    mapOf(
        "conservative" to BidStance.Aggressive,
        "base" to BidStance.Recommended,
        "aggressive" to BidStance.Safe,
    )

/**
 * ML 후보 셋 ↔ 메뉴 태도 셋의 **라벨 대응**(M6/G1-DEC05).
 *
 * **라운드 1 의 이 test 가 결함을 고정했다.** 앞 판은 oracle 을 **제 구현에서 끌어와**
 * (`conservative → safe`) 그대로 단언했고, 그래서 반전을 잡는 대신 **반전을 잠갔다**.
 * 구현에서 끌어온 oracle 은 구현을 확인할 뿐 검증하지 못한다. 그래서 지금 oracle 은 둘 다
 * 구현 독립이다:
 * - **도메인 성질** — 투찰율이 높을수록 낙하에 안전하므로 `safe` 의 율이 가장 크고
 *   `aggressive` 의 율이 가장 작다(legacy `app/ai/bid_target.py` docstring: *aggressive =
 *   band floor, most competitive, 낙하 위험 · safe = band ceiling, safe from 낙하*).
 * - **1차 출처 표** — [LEGACY_BRIDGE].
 */
class StanceRateMappingTest {
    @Test
    fun `도메인 성질 — 안전이 가장 높고 공격이 가장 낮다`() {
        val candidates =
            BidRateCandidates(
                conservative = fraction("0.90"),
                base = fraction("0.93"),
                aggressive = fraction("0.96"),
            )

        val stances = candidates.toStanceRates()

        (stances.rateOf(BidStance.Safe) > stances.rateOf(BidStance.Recommended)) shouldBe true
        (stances.rateOf(BidStance.Recommended) > stances.rateOf(BidStance.Aggressive)) shouldBe true
    }

    @Test
    fun `1차 출처 표 — 계약 라벨이 legacy 브리지가 적은 태도로 간다`() {
        val byLabel =
            mapOf(
                "conservative" to fraction("0.90"),
                "base" to fraction("0.93"),
                "aggressive" to fraction("0.96"),
            )
        val candidates =
            BidRateCandidates(
                conservative = byLabel.getValue("conservative"),
                base = byLabel.getValue("base"),
                aggressive = byLabel.getValue("aggressive"),
            )

        val stances = candidates.toStanceRates()

        LEGACY_BRIDGE.forEach { (label, stance) ->
            stances.rateOf(stance) shouldBe byLabel.getValue(label)
        }
    }

    /** 두 축의 순서 불변식은 **방향이 반대**다 — 변환이 그 뒤집음을 수행한다. */
    @Test
    fun `역순 후보 셋은 애초에 만들어지지 않는다`() {
        shouldThrow<IllegalArgumentException> {
            BidRateCandidates(
                conservative = fraction("0.96"),
                base = fraction("0.93"),
                aggressive = fraction("0.90"),
            )
        }
    }
}
