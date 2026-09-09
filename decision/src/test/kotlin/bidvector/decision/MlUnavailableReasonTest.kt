package bidvector.decision

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * M4/4D-1 — `MlUnavailableReason`(D-4D-6) 값이 늘 때 이 `when`이 컴파일을 깨뜨리는 것이
 * 회귀 방지다(1C 관례, scope.md 「decision」 절). 값 자체의 의미는 KDoc(`ReviewReason.kt`)이
 * 진다 — 이 test는 소진성만 고정한다.
 */
class MlUnavailableReasonTest {
    @Test
    fun `MlUnavailableReason 의 when 은 열 값을 소진한다`() {
        val values: List<MlUnavailableReason> =
            listOf(
                MlUnavailableReason.ScoreNotProvided,
                MlUnavailableReason.DeadlineExceeded,
                MlUnavailableReason.CircuitOpen,
                MlUnavailableReason.RetryBudgetExhausted,
                MlUnavailableReason.TransportFailed,
                MlUnavailableReason.ModelNotReady,
                MlUnavailableReason.ReleaseMismatch,
                MlUnavailableReason.ContractViolation,
                MlUnavailableReason.UnsupportedSchema,
                MlUnavailableReason.UnsupportedRelease,
                MlUnavailableReason.InvalidRequest,
            )

        val labels =
            values.map { reason ->
                when (reason) {
                    MlUnavailableReason.ScoreNotProvided -> "ScoreNotProvided"
                    MlUnavailableReason.DeadlineExceeded -> "DeadlineExceeded"
                    MlUnavailableReason.CircuitOpen -> "CircuitOpen"
                    MlUnavailableReason.RetryBudgetExhausted -> "RetryBudgetExhausted"
                    MlUnavailableReason.TransportFailed -> "TransportFailed"
                    MlUnavailableReason.ModelNotReady -> "ModelNotReady"
                    MlUnavailableReason.ReleaseMismatch -> "ReleaseMismatch"
                    MlUnavailableReason.ContractViolation -> "ContractViolation"
                    MlUnavailableReason.UnsupportedSchema -> "UnsupportedSchema"
                    MlUnavailableReason.UnsupportedRelease -> "UnsupportedRelease"
                    MlUnavailableReason.InvalidRequest -> "InvalidRequest"
                }
            }

        labels.toSet().size shouldBe values.size
    }
}
