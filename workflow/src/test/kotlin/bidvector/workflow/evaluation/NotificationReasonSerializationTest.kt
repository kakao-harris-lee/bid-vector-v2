package bidvector.workflow.evaluation

import bidvector.decision.BidNowReason
import bidvector.decision.MlUnavailableReason
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private const val ML_UNAVAILABLE_REASON_COUNT = 11

/**
 * scope.md 우회 4 — [BidNowReason][bidvector.decision.BidNowReason]의 `toString()`은
 * `NotificationRequestedPayload.bidNowReasons`에 그대로 실려 outbox에 영속된다(sink
 * KDoc). 그 직렬화가 바뀌어도(필드명 변경 등) 아무것도 안 붉으면 옛 outbox 행은 조용히
 * 다른 형식을 이는 채 남는다 — 이 함수가 그 축을 축어로 잠근다. **`else` 없는 소진
 * `when`** 이라 `BidNowReason`에 새 하위 타입이 생기면 이 함수부터 컴파일이 깨진다.
 */
internal fun expectedBidNowReasonToString(reason: BidNowReason): String =
    when (reason) {
        is BidNowReason.PriorityAboveBidNowThreshold -> {
            "PriorityAboveBidNowThreshold(priority=0.9, threshold=0.5)"
        }

        is BidNowReason.ForceBidOverride -> {
            "ForceBidOverride(probability=0.95, matched=0.95, probabilityThreshold=0.9, matchedThreshold=0.9)"
        }
    }

/**
 * scope.md 우회 4 — [MlUnavailableReason]의 `toString()`은
 * `NotificationEvidencePayload.NotPredicted.reason`에 그대로 실린다. 전부 `data object`
 * 라 실제로 "새는" 값은 없지만(클래스명=값) 이름이 바뀌면 영속 행의 뜻이 조용히
 * 바뀐다. **`else` 없는 소진 `when`** — 새 사유가 추가되면 이 함수부터 컴파일이 깨진다.
 */
internal fun expectedMlUnavailableReasonToString(reason: MlUnavailableReason): String =
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

private val ALL_ML_UNAVAILABLE_REASONS =
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

/**
 * **outbox 에 영속되는 사유 문자열의 축어 잠금**(scope.md 우회 4) — `OutboxNotificationRequestPort`
 * 가 두 열거의 `toString()` 을 payload 에 그대로 싣기 때문에, 그 형식이 조용히 바뀌면 옛 행이
 * 다른 형식을 인 채 남는다.
 *
 * `OutboxNotificationRequestPortTest` 에서 **갈라낸** 파일이다(파일 500줄 한도). 기계적 분할이
 * 아니라 축이 하나다: 위 두 잠금 함수와 아래 두 test 가 같은 사실(「직렬화 형식」)을 본다.
 * 그 함수들은 투영 test 도 쓰므로 `internal` 로 둔다 — 같은 패키지에서 한 자리가 정본이다.
 */
class NotificationReasonSerializationTest {
    /**
     * scope.md 우회 4 — `BidNowReason` 두 case 의 `toString()` 직렬화를 축어로 잠근다.
     * `expectedBidNowReasonToString`의 소진 `when`이 `BidNowReason`에 새 하위 타입이
     * 생기는 순간 컴파일을 깬다(이 test 파일 자체가 컴파일 안 됨) — 리스트를 갱신하지
     * 않아도 걸린다.
     */
    @Test
    fun `BidNowReason 두 case 의 직렬화가 축어로 고정된다 — 우회 4, 소진 when`() {
        bidNowVerdict().reasons.single().toString() shouldBe "PriorityAboveBidNowThreshold(priority=0.9, threshold=0.5)"
        forceBidOverrideVerdict().reasons.single().toString() shouldBe
            "ForceBidOverride(probability=0.95, matched=0.95, probabilityThreshold=0.9, matchedThreshold=0.9)"

        // 잠금 함수 자신도 같은 값을 낸다는 것을 재확인 — 함수와 literal 이 갈라지면
        // 다음에 sink test 가 잠금 함수만 보고 안심하는 것을 막는다.
        bidNowVerdict().reasons.single().let { it.toString() shouldBe expectedBidNowReasonToString(it) }
        forceBidOverrideVerdict().reasons.single().let { it.toString() shouldBe expectedBidNowReasonToString(it) }
    }

    /**
     * scope.md 우회 4 — `MlUnavailableReason` 열한 case 전수. `expectedMlUnavailableReasonToString`
     * 의 소진 `when`이 새 사유가 추가되는 순간 컴파일을 깬다.
     */
    @Test
    fun `MlUnavailableReason 전 case 의 직렬화가 축어로 고정된다 — 우회 4, 소진 when`() {
        ALL_ML_UNAVAILABLE_REASONS.size shouldBe ML_UNAVAILABLE_REASON_COUNT
        ALL_ML_UNAVAILABLE_REASONS.forEach { reason ->
            reason.toString() shouldBe expectedMlUnavailableReasonToString(reason)
        }
    }
}
