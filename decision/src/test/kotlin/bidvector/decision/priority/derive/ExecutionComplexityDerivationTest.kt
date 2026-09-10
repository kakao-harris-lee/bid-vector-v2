package bidvector.decision.priority.derive

import bidvector.decision.UnitScore
import bidvector.decision.priority.closeTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration

/**
 * ④ [deriveExecutionComplexity](scope.md, legacy `scoring.py:306-347`
 * `_estimate_execution_complexity_score`). `budget`·`match`·`capacity` 부재는 재정규화(D-4B5-6),
 * `keywordHits`·`remaining`·`loadRatio` 는 항상 값(우회 (8)).
 */
class ExecutionComplexityDerivationTest {
    private val policy = resolvedTestPolicy()

    @Test
    fun `손계산 — 전 항 Present`() {
        val inputs =
            ComplexityInputs(
                budget = testBaseAmount(600_000_000L),
                keywordHits = KeywordHits(3),
                remaining = Duration.ofHours(10),
                loadRatio = UnitScore(BigDecimal("0.4")),
                match = UnitScore(BigDecimal("0.9")),
                capacity = UnitScore(BigDecimal("0.7")),
            )
        // budget: 6억>=5억→.92 · keyword: min(1,.24+3*.08)=.48 · deadline: 10h∈(6,24]→.78
        // matchFriction=1-.9=.1 · capacityFriction=1-.7=.3
        // score = .92*.30+.48*.25+.78*.15+.4*.10+.1*.10+.3*.10 = .276+.12+.117+.04+.01+.03=.593
        val outcome = deriveExecutionComplexity(inputs, policy)
        outcome.score.value.compareTo(BigDecimal("0.593")) shouldBe 0
        outcome.usedSignals shouldBe
            setOf(
                ComplexitySignal.Budget,
                ComplexitySignal.Keyword,
                ComplexitySignal.Deadline,
                ComplexitySignal.LoadRatio,
                ComplexitySignal.Match,
                ComplexitySignal.Capacity,
            )
    }

    @Test
    fun `budget 부재는 재정규화하고 usedSignals 에서 빠진다`() {
        val inputs =
            ComplexityInputs(
                budget = null,
                keywordHits = KeywordHits(0),
                remaining = null,
                loadRatio = UnitScore(BigDecimal("0.5")),
                match = UnitScore(BigDecimal("0.6")),
                capacity = UnitScore(BigDecimal("0.2")),
            )
        // keyword=.24(0 hits) · deadline: remaining null → noDeadlineComplexity=.3
        // (별도 상수 — band 의 beyondBandsScore .24 와 다른 값)
        // matchFriction=.4 · capacityFriction=.8, budget 제외 재정규화
        // weightSum=.25+.15+.10+.10+.10=.70, num=.24*.25+.3*.15+.5*.10+.4*.10+.8*.10=.06+.045+.05+.04+.08=.275
        // score=.275/.70=0.392857...
        val outcome = deriveExecutionComplexity(inputs, policy)
        outcome.score.value.closeTo(BigDecimal("0.392857")) shouldBe true
        outcome.usedSignals shouldBe
            setOf(
                ComplexitySignal.Keyword,
                ComplexitySignal.Deadline,
                ComplexitySignal.LoadRatio,
                ComplexitySignal.Match,
                ComplexitySignal.Capacity,
            )
    }

    @Test
    fun `match 만 부재해도 재정규화한다`() {
        val inputs =
            ComplexityInputs(
                budget = testBaseAmount(50_000_000L),
                keywordHits = KeywordHits(1),
                remaining = Duration.ofHours(100),
                loadRatio = UnitScore(BigDecimal("0.5")),
                match = null,
                capacity = UnitScore(BigDecimal("0.9")),
            )
        // budget: 5천만 < 1억 → beyond .38 · keyword: .24+.08=.32 · deadline: 100h>72h→beyond .24
        // capacityFriction=1-.9=.1, match 제외 재정규화
        // weightSum=.30+.25+.15+.10+.10=.90, num=.38*.30+.32*.25+.24*.15+.5*.10+.1*.10
        //   =.114+.08+.036+.05+.01=.29, score=.29/.90=0.322222...
        val outcome = deriveExecutionComplexity(inputs, policy)
        outcome.score.value.closeTo(BigDecimal("0.322222")) shouldBe true
        outcome.usedSignals shouldBe
            setOf(
                ComplexitySignal.Budget,
                ComplexitySignal.Keyword,
                ComplexitySignal.Deadline,
                ComplexitySignal.LoadRatio,
                ComplexitySignal.Capacity,
            )
    }

    @Test
    fun `budget match capacity 셋 다 부재하면 keyword deadline loadRatio 셋만 재정규화`() {
        val inputs =
            ComplexityInputs(
                budget = null,
                keywordHits = KeywordHits(2),
                remaining = Duration.ofHours(6),
                loadRatio = UnitScore(BigDecimal("1.0")),
                match = null,
                capacity = null,
            )
        // keyword=.24+.16=.40 · deadline: 6h(경계 포함)→1.0 · loadRatio=1.0
        // weightSum=.25+.15+.10=.50, num=.40*.25+1.0*.15+1.0*.10=.10+.15+.10=.35, score=.35/.50=.70
        val outcome = deriveExecutionComplexity(inputs, policy)
        outcome.score.value.compareTo(BigDecimal("0.70")) shouldBe 0
        outcome.usedSignals shouldBe
            setOf(ComplexitySignal.Keyword, ComplexitySignal.Deadline, ComplexitySignal.LoadRatio)
    }

    @Test
    fun `우회 5 상당 — hits 가 커도 keyword 신호는 1_0 을 넘지 않는다`() {
        val inputs =
            ComplexityInputs(
                budget = null,
                keywordHits = KeywordHits(20),
                remaining = null,
                loadRatio = UnitScore(BigDecimal.ZERO),
                match = null,
                capacity = null,
            )
        // keyword = min(1, .24+20*.08=2.04) = 1.0 · deadline(무마감)=.3 · loadRatio=0
        // weightSum=.25+.15+.10=.50, num=1.0*.25+.3*.15+0*.10=.25+.045=.295, score=.295/.50=.59
        val outcome = deriveExecutionComplexity(inputs, policy)
        outcome.score.value.compareTo(BigDecimal("0.59")) shouldBe 0
    }
}
