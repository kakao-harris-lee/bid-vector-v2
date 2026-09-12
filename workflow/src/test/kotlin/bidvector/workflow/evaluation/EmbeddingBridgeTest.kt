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
    fun `DerivationAbsence 넷은 ScoreNotProvided, FloorRateOutOfRange 만 InvalidRequest`() {
        bridgeDerivationAbsence(DerivationAbsence.BaseAmountMissing) shouldBe MlUnavailableReason.ScoreNotProvided
        bridgeDerivationAbsence(DerivationAbsence.RecommendationMissing) shouldBe MlUnavailableReason.ScoreNotProvided
        bridgeDerivationAbsence(DerivationAbsence.WorkloadNotCollected) shouldBe MlUnavailableReason.ScoreNotProvided
        bridgeDerivationAbsence(DerivationAbsence.MarketAverageMissing) shouldBe MlUnavailableReason.ScoreNotProvided
        bridgeDerivationAbsence(DerivationAbsence.MoneyArithmeticUnmeasurable(ReasonCode.AMOUNT_OVERFLOW)) shouldBe
            MlUnavailableReason.ScoreNotProvided
        bridgeDerivationAbsence(DerivationAbsence.FloorRateOutOfRange) shouldBe MlUnavailableReason.InvalidRequest
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
