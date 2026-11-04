package bidvector.adapters.notification

import bidvector.workflow.notification.DeliveryResult
import bidvector.workflow.notification.MaskedTarget
import bidvector.workflow.notification.RejectionReason
import java.time.Instant

/** 응답 본문의 `ok` 한 칸만 읽은 결과 — 본문의 나머지(`result.chat.id` 등)는 어디로도 옮기지 않는다. */
internal enum class TelegramBodyVerdict {
    Ok,
    NotOk,
    Unreadable,
}

/** 요청이 Telegram 에 **닿지 않았음이 확정**인 사유(D-4 「송신 전 실패」). */
internal enum class TelegramNotSentCause {
    NameNotResolved,
    ConnectionRefused,
}

/** 받았는지 **모르는** 사유(D-4). 어느 것도 재발송하지 않는다 — at-most-once. */
internal enum class TelegramAmbiguityCause {
    Timeout,
    ConnectionBroken,
    Interrupted,
    TransportFailure,
}

/**
 * 호출 한 번의 관측 — **결과 어휘가 아니다**. 어휘로 옮기는 것은 [telegramDeliveryResult] 하나뿐이고,
 * 그래서 「무엇을 보았는가」와 「장부에 무엇을 적는가」가 따로 변이될 수 있다(변이 ① 의 표적).
 */
internal sealed interface TelegramCallOutcome {
    data class Responded(
        val status: Int,
        val body: TelegramBodyVerdict,
    ) : TelegramCallOutcome

    data class NotSent(
        val cause: TelegramNotSentCause,
    ) : TelegramCallOutcome

    data class Ambiguous(
        val cause: TelegramAmbiguityCause,
    ) : TelegramCallOutcome
}

/** 상태 코드의 판정 — 범위 **구성**이고, 모르는 코드의 기본값은 전달도 거부도 아닌 모호다. */
internal enum class TelegramStatusVerdict {
    Acknowledged,
    TargetRefused,
    ProviderRefused,
    Ambiguous,
}

/**
 * **429 가 4xx 범위보다 먼저다**(D-4) — 범위만 보면 이것이 확정 거부로 접히고, 그러면 받았을지도
 * 모르는 메시지가 `FAILED` 로 기록된다. 400(chat not found)·403(차단)은 대상 축, 401(자격 무효)은
 * provider 축이고, 열거 밖 4xx 도 「닿았고 확정 거부됐다」라 거부다. 1xx·3xx·5xx 는 모호다.
 */
internal fun telegramStatusVerdict(status: Int): TelegramStatusVerdict =
    when {
        status in TelegramHttpStatus.SUCCESS_RANGE -> TelegramStatusVerdict.Acknowledged
        status == TelegramHttpStatus.TOO_MANY_REQUESTS -> TelegramStatusVerdict.Ambiguous
        status == TelegramHttpStatus.BAD_REQUEST -> TelegramStatusVerdict.TargetRefused
        status == TelegramHttpStatus.FORBIDDEN -> TelegramStatusVerdict.TargetRefused
        status in TelegramHttpStatus.CLIENT_ERROR_RANGE -> TelegramStatusVerdict.ProviderRefused
        else -> TelegramStatusVerdict.Ambiguous
    }

/**
 * D-4 결과 어휘 대응의 전부 — 순수 함수다(설정도 비밀값도 받지 않는다). 그래서 거부 메시지가 값을
 * 담을 수 없고, 전 갈래를 인자 하나로 세울 수 있다.
 *
 * 전달은 **2xx 와 `ok:true` 가 함께일 때만**이다. 2xx 에 `ok:false` 거나 본문을 해독하지 못하면
 * 모호다 — 「상태 코드가 좋았다」만으로 전달을 선언하면 장부가 거짓이 될 수 있다.
 */
internal fun telegramDeliveryResult(
    outcome: TelegramCallOutcome,
    at: Instant,
    target: MaskedTarget,
): DeliveryResult =
    when (outcome) {
        // 대상(chat)이 틀린 것이 아니라 provider 쪽에 닿지 못했다 — `InvalidTarget` 은 chat 축의
        // 뜻이라 쓰지 않는다. 어휘가 두 값뿐인 한계는 알려진 제한이다.
        is TelegramCallOutcome.NotSent -> DeliveryResult.Rejected(RejectionReason.ProviderDeclined)
        is TelegramCallOutcome.Ambiguous -> DeliveryResult.Unknown(at)
        is TelegramCallOutcome.Responded -> respondedResult(outcome, at, target)
    }

private fun respondedResult(
    responded: TelegramCallOutcome.Responded,
    at: Instant,
    target: MaskedTarget,
): DeliveryResult =
    when (telegramStatusVerdict(responded.status)) {
        TelegramStatusVerdict.Acknowledged -> acknowledgedResult(responded.body, at, target)
        TelegramStatusVerdict.TargetRefused -> DeliveryResult.Rejected(RejectionReason.InvalidTarget)
        TelegramStatusVerdict.ProviderRefused -> DeliveryResult.Rejected(RejectionReason.ProviderDeclined)
        TelegramStatusVerdict.Ambiguous -> DeliveryResult.Unknown(at)
    }

private fun acknowledgedResult(
    body: TelegramBodyVerdict,
    at: Instant,
    target: MaskedTarget,
): DeliveryResult =
    when (body) {
        TelegramBodyVerdict.Ok -> DeliveryResult.Delivered(at, target)
        TelegramBodyVerdict.NotOk -> DeliveryResult.Unknown(at)
        TelegramBodyVerdict.Unreadable -> DeliveryResult.Unknown(at)
    }

/** 프로토콜 상수 — 정책 값이 아니라 HTTP·Bot API 의 사실이다(versioned policy 데이터 아님). */
private object TelegramHttpStatus {
    val SUCCESS_RANGE = 200..299
    val CLIENT_ERROR_RANGE = 400..499
    const val BAD_REQUEST = 400
    const val FORBIDDEN = 403
    const val TOO_MANY_REQUESTS = 429
}
