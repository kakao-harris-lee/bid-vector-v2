package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.decision.priority.UnitVector
import bidvector.decision.priority.derive.DerivationAbsence
import bidvector.workflow.embedding.EmbeddingUnavailableReason
import bidvector.workflow.embedding.EmbeddingVector
import java.math.BigDecimal
import kotlin.math.sqrt

/*
 * 4B-6b 브리지(scope.md ④) — 서로 다른 사유 축(임베딩·파생)을 조합기의 단일 어휘
 * (MlUnavailableReason)로 옮기고, 전송 안전판 벡터(EmbeddingVector)를 코사인 정밀
 * 벡터(UnitVector)로 재정규화한다. internal 순수 함수만 — 조합기(OpportunityAnalysis)
 * 밖에서 이 변환을 우회할 자리가 없다.
 */

/**
 * [EmbeddingUnavailableReason](10) → [MlUnavailableReason] 이름 1:1(scope.md ④, D-4B6B-1
 * 계열과 같은 소진 `when` 규율 — `else` 없음, 사유가 늘면 컴파일이 잡는다).
 */
internal fun bridgeEmbeddingReason(reason: EmbeddingUnavailableReason): MlUnavailableReason =
    when (reason) {
        EmbeddingUnavailableReason.CircuitOpen -> MlUnavailableReason.CircuitOpen
        EmbeddingUnavailableReason.DeadlineExceeded -> MlUnavailableReason.DeadlineExceeded
        EmbeddingUnavailableReason.RetryBudgetExhausted -> MlUnavailableReason.RetryBudgetExhausted
        EmbeddingUnavailableReason.TransportFailed -> MlUnavailableReason.TransportFailed
        EmbeddingUnavailableReason.ReleaseMismatch -> MlUnavailableReason.ReleaseMismatch
        EmbeddingUnavailableReason.ContractViolation -> MlUnavailableReason.ContractViolation
        EmbeddingUnavailableReason.UnsupportedSchema -> MlUnavailableReason.UnsupportedSchema
        EmbeddingUnavailableReason.UnsupportedRelease -> MlUnavailableReason.UnsupportedRelease
        EmbeddingUnavailableReason.InvalidRequest -> MlUnavailableReason.InvalidRequest
        EmbeddingUnavailableReason.ModelNotReady -> MlUnavailableReason.ModelNotReady
    }

/**
 * [DerivationAbsence] → [MlUnavailableReason](scope.md ④) — fact 부재는 전부
 * `ScoreNotProvided`(값이 아직 없다는 사실), 단 [DerivationAbsence.FloorRateOutOfRange]만
 * `InvalidRequest`(호출부가 준 값 자체가 계약 범위 밖이라는 다른 사실).
 */
internal fun bridgeDerivationAbsence(reason: DerivationAbsence): MlUnavailableReason =
    when (reason) {
        DerivationAbsence.BaseAmountMissing,
        DerivationAbsence.RecommendationMissing,
        DerivationAbsence.WorkloadNotCollected,
        DerivationAbsence.MarketAverageMissing,
        is DerivationAbsence.MoneyArithmeticUnmeasurable,
        -> MlUnavailableReason.ScoreNotProvided

        DerivationAbsence.FloorRateOutOfRange -> MlUnavailableReason.InvalidRequest
    }

/**
 * [EmbeddingVector](L2 정규화, ε 0.01 거친 전송 안전판) → [UnitVector](코사인 정밀도, ε는
 * 정책값) — L2 norm으로 재정규화한다(D-4B6B-2). 재정규화 없이는 [EmbeddingVector]의 거친
 * ε가 [UnitVector]의 정밀 ε(기본 0.0001)를 만족하지 못할 수 있다. 차원은 그대로 옮긴다 —
 * 차원 불일치 판정은 이 함수가 아니라 [bidvector.decision.priority.SemanticMatch.of]가 진다.
 */
internal fun EmbeddingVector.toUnitVector(normEpsilon: BigDecimal): UnitVector {
    val norm = sqrt(values.sumOf { component -> component.toDouble() * component.toDouble() })
    val normalized = values.map { component -> BigDecimal.valueOf(component.toDouble() / norm) }
    return UnitVector(normalized, normEpsilon)
}
