package bidvector.adapters.retention

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/** `payload_fields` 투영이 담은 키 — 실제 계약 키 이름(`RawObservation.kt`)을 쓴다. */
private val KEPT = setOf("bidNtceNo", "bidNtceOrd")

/**
 * P-4 — 원문 마스킹은 **allow-list 반전**이다. 담당자 키를 열거하지 않으므로, 상류가 담당자
 * 키를 하나 더 보내도 (계약에 등재되지 않는 한) 자동으로 빠진다 — 아래 「미지 키」 test 가
 * 그 성질을 재는 자리다.
 */
class RawPayloadMaskingTest {
    @Test
    fun `담당자 키는 하나도 남지 않는다`() {
        val payload =
            """{"bidNtceNo":"20260101001","bidNtceOrd":"000","ntceInsttOfclNm":"NAME-FIXTURE",""" +
                """"ntceInsttOfclTelNo":"TEL-FIXTURE","ntceInsttOfclEmailAdrs":"MAIL-FIXTURE",""" +
                """"dminsttOfclNm":"DEMAND-FIXTURE"}"""

        val outcome = maskRawPayload(payload, KEPT)

        val masked = (outcome as PayloadMaskOutcome.Masked).payload
        masked shouldNotContain "Ofcl"
        masked shouldNotContain "FIXTURE"
    }

    @Test
    fun `남길 키는 값까지 그대로 남는다 — 마스킹이 읽는 축을 지우지 않는다`() {
        val outcome = maskRawPayload("""{"bidNtceNo":"20260101001","bidNtceOrd":"000"}""", KEPT)

        outcome shouldBe PayloadMaskOutcome.Masked("""{"bidNtceNo":"20260101001","bidNtceOrd":"000"}""")
    }

    @Test
    fun `등재되지 않은 미지 키는 이름을 몰라도 빠진다 — deny-list 가 아니다`() {
        // 담당자 키 이름을 열거하지 않으므로, 상류가 새로 보낸 임의의 키도 같은 자리에서 빠진다.
        val outcome = maskRawPayload("""{"bidNtceNo":"1","brandNewUpstreamKey":"SOMETHING"}""", KEPT)

        outcome shouldBe PayloadMaskOutcome.Masked("""{"bidNtceNo":"1"}""")
    }

    @Test
    fun `남길 키라도 값이 객체면 남기지 않는다 — 그 안을 이 함수가 알 수 없다`() {
        val outcome = maskRawPayload("""{"bidNtceNo":{"inner":"NAME-FIXTURE"},"bidNtceOrd":"000"}""", KEPT)

        outcome shouldBe PayloadMaskOutcome.Masked("""{"bidNtceOrd":"000"}""")
    }

    @Test
    fun `남길 키라도 값이 배열이면 남기지 않는다`() {
        val outcome = maskRawPayload("""{"bidNtceNo":["NAME-FIXTURE"],"bidNtceOrd":"000"}""", KEPT)

        outcome shouldBe PayloadMaskOutcome.Masked("""{"bidNtceOrd":"000"}""")
    }

    @Test
    fun `숫자·불린·null 은 원문 표기 그대로 남는다`() {
        val outcome = maskRawPayload("""{"bidNtceNo":1.50,"bidNtceOrd":null}""", setOf("bidNtceNo", "bidNtceOrd"))

        outcome shouldBe PayloadMaskOutcome.Masked("""{"bidNtceNo":1.50,"bidNtceOrd":null}""")
    }

    @Test
    fun `판독할 수 없는 원문은 fail-closed — 부분 치환을 하지 않는다`() {
        maskRawPayload("NOT-JSON-AT-ALL", KEPT) shouldBe PayloadMaskOutcome.FailClosed
    }

    @Test
    fun `객체가 아닌 JSON 도 fail-closed`() {
        maskRawPayload("""["bidNtceNo"]""", KEPT) shouldBe PayloadMaskOutcome.FailClosed
    }

    @Test
    fun `깨진 뒤쪽을 가진 원문도 fail-closed — 앞부분만 살려 두지 않는다`() {
        maskRawPayload("""{"bidNtceNo":"1"} trailing garbage""", KEPT) shouldBe PayloadMaskOutcome.FailClosed
    }

    @Test
    fun `남길 키가 하나도 없으면 빈 객체다 — 빈 문자열이 아니다 (payload CHECK)`() {
        val outcome = maskRawPayload("""{"ntceInsttOfclNm":"NAME-FIXTURE"}""", emptySet())

        outcome shouldBe PayloadMaskOutcome.Masked("{}")
    }

    @Test
    fun `fail-closed 대체 값은 빈 문자열이 아니다 — payload CHECK 를 지난다`() {
        PAYLOAD_MASK_FAIL_CLOSED.isNotEmpty() shouldBe true
    }

    @Test
    fun `마스킹은 멱등이다 — 이미 마스킹된 값을 다시 넣어도 같은 값이다`() {
        val once = (maskRawPayload("""{"bidNtceNo":"1","ntceInsttOfclNm":"NAME-FIXTURE"}""", KEPT)
            as PayloadMaskOutcome.Masked).payload

        maskRawPayload(once, KEPT) shouldBe PayloadMaskOutcome.Masked(once)
    }
}
