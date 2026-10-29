package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.decision.priority.derive.DerivationAbsence
import bidvector.sharedkernel.ReasonCode
import bidvector.workflow.embedding.EmbeddingUnavailableReason
import bidvector.workflow.embedding.EmbeddingVector
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 브리지 사유 매핑(scope.md ④) 10 소진·[DerivationAbsence] 매핑·재정규화(D-4B6B-2) 고정.
 */
class EmbeddingBridgeTest {
    @Test
    fun `EmbeddingUnavailableReason 10 가지가 모두 같은 이름의 MlUnavailableReason 으로 옮겨진다`() {
        bridgeEmbeddingReason(EmbeddingUnavailableReason.CircuitOpen) shouldBe MlUnavailableReason.CircuitOpen
        bridgeEmbeddingReason(EmbeddingUnavailableReason.DeadlineExceeded) shouldBe MlUnavailableReason.DeadlineExceeded
        bridgeEmbeddingReason(EmbeddingUnavailableReason.RetryBudgetExhausted) shouldBe
            MlUnavailableReason.RetryBudgetExhausted
        bridgeEmbeddingReason(EmbeddingUnavailableReason.TransportFailed) shouldBe MlUnavailableReason.TransportFailed
        bridgeEmbeddingReason(EmbeddingUnavailableReason.ReleaseMismatch) shouldBe MlUnavailableReason.ReleaseMismatch
        bridgeEmbeddingReason(EmbeddingUnavailableReason.ContractViolation) shouldBe
            MlUnavailableReason.ContractViolation
        bridgeEmbeddingReason(EmbeddingUnavailableReason.UnsupportedSchema) shouldBe
            MlUnavailableReason.UnsupportedSchema
        bridgeEmbeddingReason(EmbeddingUnavailableReason.UnsupportedRelease) shouldBe
            MlUnavailableReason.UnsupportedRelease
        bridgeEmbeddingReason(EmbeddingUnavailableReason.InvalidRequest) shouldBe MlUnavailableReason.InvalidRequest
        bridgeEmbeddingReason(EmbeddingUnavailableReason.ModelNotReady) shouldBe MlUnavailableReason.ModelNotReady
    }

    @Test
    fun `DerivationAbsence 다섯 전부 ScoreNotProvided — 하한율 사유는 DEC-10 게이트가 가져갔다`() {
        bridgeDerivationAbsence(DerivationAbsence.BaseAmountMissing) shouldBe MlUnavailableReason.ScoreNotProvided
        bridgeDerivationAbsence(DerivationAbsence.RecommendationMissing) shouldBe MlUnavailableReason.ScoreNotProvided
        bridgeDerivationAbsence(DerivationAbsence.WorkloadNotCollected) shouldBe MlUnavailableReason.ScoreNotProvided
        bridgeDerivationAbsence(DerivationAbsence.MarketAverageMissing) shouldBe MlUnavailableReason.ScoreNotProvided
        bridgeDerivationAbsence(DerivationAbsence.MoneyArithmeticUnmeasurable(ReasonCode.AMOUNT_OVERFLOW)) shouldBe
            MlUnavailableReason.ScoreNotProvided
    }

    /**
     * D-DEC10-9 — `FloorRateOutOfRange` 는 어휘에서 사라졌다. 그 사유의 선언된 목적은
     * `MarginInputs.init` 의 `require` 를 피하는 것이었고, 지금은 DEC-10 게이트가 가격 경로
     * 앞에서 판정하며 제외를 **계수 어휘**(`PublishedFloorExclusion`)로 낸다.
     *
     * 리플렉션으로 하위 타입을 세지 않는다(그 API 는 이 모듈 classpath 에 없다). 두 축이 함께
     * 센다 — [allDerivationAbsences] 의 **소진 `when`** 은 항이 생기면 컴파일을 멈추고, 아래
     * **크기 단언**은 그 `when` 가지만 더하고 목록에 넣지 않는 변경을 잡는다.
     */
    @Test
    fun `DerivationAbsence 어휘 전수가 ScoreNotProvided 다 — 새 항은 컴파일을 멈춘다`() {
        val reasons = allDerivationAbsences()

        // r1 CR L-3 — `when` 가지만 더하고 목록에 안 넣는 변경을 이 수가 잡는다(앞 판 KDoc 이
        // 「숫자를 세는 단언」을 가리켰는데 그 단언이 없었다).
        reasons.size shouldBe 5
        reasons.forEach { reason -> bridgeDerivationAbsence(reason) shouldBe MlUnavailableReason.ScoreNotProvided }
    }

    @Test
    fun `재정규화 — 거친 ε(0 009) 벡터도 UnitVector 로 성립하고 차원이 보존된다`() {
        // norm = 1.009, EmbeddingVector.COARSE_NORM_EPSILON(0.01) 이내라 생성은 성공한다.
        val vector = EmbeddingVector(listOf(1.009f))

        val unitVector = vector.toUnitVector(BigDecimal("0.0001"))

        unitVector.values.size shouldBe vector.values.size
        val norm = sqrt(unitVector.values.sumOf { it.toDouble() * it.toDouble() })
        (abs(norm - 1.0) <= 0.0001) shouldBe true
    }

    @Test
    fun `재정규화 — 다차원 벡터도 코사인 정밀도 ε 안으로 들어온다`() {
        val raw = listOf(0.6f, 0.8f * 1.008f)
        val vector = EmbeddingVector(raw)

        val unitVector = vector.toUnitVector(BigDecimal("0.0001"))

        val norm = sqrt(unitVector.values.sumOf { it.toDouble() * it.toDouble() })
        (abs(norm - 1.0) <= 0.0001) shouldBe true
        unitVector.values.size shouldBe 2
    }
}

/**
 * `DerivationAbsence` 전수. `when` 이 소진이라 **항이 추가되면 이 함수가 컴파일되지 않는다** —
 * 그것이 이 목록의 요점이고, 숫자를 세는 단언은 그 뒤의 부수적인 확인이다.
 */
private fun allDerivationAbsences(): List<DerivationAbsence> {
    val reasons =
        listOf(
            DerivationAbsence.BaseAmountMissing,
            DerivationAbsence.RecommendationMissing,
            DerivationAbsence.WorkloadNotCollected,
            DerivationAbsence.MarketAverageMissing,
            DerivationAbsence.MoneyArithmeticUnmeasurable(ReasonCode.AMOUNT_OVERFLOW),
        )
    reasons.forEach { reason ->
        when (reason) {
            DerivationAbsence.BaseAmountMissing,
            DerivationAbsence.RecommendationMissing,
            DerivationAbsence.WorkloadNotCollected,
            DerivationAbsence.MarketAverageMissing,
            is DerivationAbsence.MoneyArithmeticUnmeasurable,
            -> Unit
        }
    }
    return reasons
}
