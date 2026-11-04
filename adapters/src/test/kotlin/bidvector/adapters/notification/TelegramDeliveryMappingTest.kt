package bidvector.adapters.notification

import bidvector.workflow.notification.DeliveryResult
import bidvector.workflow.notification.MaskedTarget
import bidvector.workflow.notification.RejectionReason
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * D-4 결과 어휘 대응의 **순수 판정**. 변이 ①(Unknown → Rejected 바꿔치움)이 직접 치는 자리다 —
 * 배선·서버 없이 인자 하나로 전 갈래를 세운다(`RelayBootDecisionTest` 와 같은 이유).
 *
 * 기대값은 전부 리터럴이다. 표에서 끌어오면 표가 바뀌는 날 기대값도 함께 바뀌어 아무것도 고정하지
 * 않는다.
 */
class TelegramDeliveryMappingTest {
    private val target = MaskedTarget.mask(FAKE_CHAT_ID, LIVE_POLICY)

    private fun resultOf(outcome: TelegramCallOutcome): DeliveryResult =
        telegramDeliveryResult(outcome, SENT_AT, target)

    private fun responded(
        status: Int,
        body: TelegramBodyVerdict = TelegramBodyVerdict.Ok,
    ): DeliveryResult = resultOf(TelegramCallOutcome.Responded(status, body))

    @Test
    fun `2xx 와 ok true 가 함께일 때만 전달이다`() {
        responded(HTTP_OK) shouldBe DeliveryResult.Delivered(SENT_AT, target)
        responded(HTTP_ACCEPTED) shouldBe DeliveryResult.Delivered(SENT_AT, target)
    }

    /** 2xx 라도 본문이 `ok:true` 가 아니거나 해독되지 않으면 **모호**다 — 전달로 읽지 않는다. */
    @Test
    fun `2xx 에 ok 가 아닌 본문이나 해독 불가 본문이면 모호다`() {
        responded(HTTP_OK, TelegramBodyVerdict.NotOk) shouldBe DeliveryResult.Unknown(SENT_AT)
        responded(HTTP_OK, TelegramBodyVerdict.Unreadable) shouldBe DeliveryResult.Unknown(SENT_AT)
    }

    /**
     * 확정 거부 셋. 400(chat not found)·403(차단)은 **대상** 축이고 401(자격 무효)은 **provider**
     * 축이다 — 어휘가 둘뿐이라 둘을 접지 않고 각자에 붙인다.
     */
    @Test
    fun `400 과 403 은 대상 거부, 401 은 provider 거부다`() {
        responded(HTTP_BAD_REQUEST) shouldBe DeliveryResult.Rejected(RejectionReason.InvalidTarget)
        responded(HTTP_FORBIDDEN) shouldBe DeliveryResult.Rejected(RejectionReason.InvalidTarget)
        responded(HTTP_UNAUTHORIZED) shouldBe DeliveryResult.Rejected(RejectionReason.ProviderDeclined)
    }

    /** 그 밖의 4xx 는 「닿았고 확정 거부됐다」이므로 거부다 — 재시도 축이 아니다. */
    @Test
    fun `열거 밖 4xx 도 확정 거부다`() {
        responded(HTTP_NOT_FOUND) shouldBe DeliveryResult.Rejected(RejectionReason.ProviderDeclined)
        responded(HTTP_CONFLICT) shouldBe DeliveryResult.Rejected(RejectionReason.ProviderDeclined)
    }

    /**
     * **429 는 4xx 범위 안이지만 모호다**(D-4) — 범위 판정만 두면 이것이 거부로 접힌다. 이 칸이
     * 없으면 「429 를 Rejected 로」 바꿔치운 변이가 초록이다.
     */
    @Test
    fun `429 는 모호다 — 재시도 없이 격리로 보낸다`() {
        responded(HTTP_TOO_MANY_REQUESTS) shouldBe DeliveryResult.Unknown(SENT_AT)
    }

    @Test
    fun `5xx 와 3xx 는 모호다`() {
        responded(HTTP_SERVER_ERROR) shouldBe DeliveryResult.Unknown(SENT_AT)
        responded(HTTP_BAD_GATEWAY) shouldBe DeliveryResult.Unknown(SENT_AT)
        responded(HTTP_MOVED) shouldBe DeliveryResult.Unknown(SENT_AT)
    }

    /**
     * 송신 전 실패(이름 해소 실패·연결 거부)는 **거부**다. 대상(chat)이 틀린 것이 아니라 provider
     * 쪽에 닿지 못한 것이라 `InvalidTarget` 을 쓰지 않는다 — 두 값 어휘의 한계는 알려진 제한이다.
     */
    @Test
    fun `송신 전 실패는 provider 축 거부다`() {
        TelegramNotSentCause.entries.forEach { cause ->
            resultOf(TelegramCallOutcome.NotSent(cause)) shouldBe
                DeliveryResult.Rejected(RejectionReason.ProviderDeclined)
        }
    }

    /** 모호 사유 전수가 `Unknown` 이다 — 사유가 늘어도 장부 어휘는 하나다. */
    @Test
    fun `모호 사유 전수가 Unknown 이다`() {
        TelegramAmbiguityCause.entries.forEach { cause ->
            resultOf(TelegramCallOutcome.Ambiguous(cause)) shouldBe DeliveryResult.Unknown(SENT_AT)
        }
    }

    /** 상태 판정의 경계 — 199·200·299·300 네 점. 범위를 한 칸 밀면 붉어진다. */
    @Test
    fun `2xx 범위의 경계 네 점`() {
        telegramStatusVerdict(HTTP_OK - 1) shouldBe TelegramStatusVerdict.Ambiguous
        telegramStatusVerdict(HTTP_OK) shouldBe TelegramStatusVerdict.Acknowledged
        telegramStatusVerdict(HTTP_LAST_SUCCESS) shouldBe TelegramStatusVerdict.Acknowledged
        telegramStatusVerdict(HTTP_MOVED) shouldBe TelegramStatusVerdict.Ambiguous
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_ACCEPTED = 202
        const val HTTP_LAST_SUCCESS = 299
        const val HTTP_MOVED = 301
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
        const val HTTP_BAD_GATEWAY = 502
    }
}
