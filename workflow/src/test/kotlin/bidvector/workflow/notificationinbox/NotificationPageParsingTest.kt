package bidvector.workflow.notificationinbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * M7/7C③ — 페이지 질의의 **순수 파싱**. 운영자가 보낸 문자열이 조용히 강제 변환되지 않는다는
 * 것을 잰다(7C① 과 같은 규율 · 같은 원시 함수를 쓴다).
 */
class NotificationPageParsingTest {
    @Test
    fun `값이 없으면 기본 페이지다`() {
        accepted(NotificationPageParameters()) shouldBe
            NotificationPageRequest(
                page = 0,
                size = NotificationInboxLimits.DEFAULT_PAGE_SIZE,
                unreadOnly = false,
            )
    }

    @Test
    fun `정수 두 칸과 boolean 한 칸을 읽는다`() {
        accepted(NotificationPageParameters(page = "2", size = "5", unreadOnly = "true")) shouldBe
            NotificationPageRequest(page = 2, size = 5, unreadOnly = true)
    }

    @Test
    fun `offset 은 페이지와 크기의 곱이다 — Int 곱셈으로 넘치지 않는다`() {
        val request = NotificationPageRequest(page = Int.MAX_VALUE, size = 100, unreadOnly = false)

        request.offset shouldBe Int.MAX_VALUE.toLong() * 100L
    }

    @Test
    fun `형식이 틀린 정수 칸은 거부다 — 조용히 0 이 되지 않는다`() {
        rejected(NotificationPageParameters(page = "x")) shouldBe NotificationPageRejection.PAGE_NOT_INTEGER
        rejected(NotificationPageParameters(size = "x")) shouldBe NotificationPageRejection.SIZE_NOT_INTEGER
    }

    /** 7C① 이 한 번 겪은 갈림 — `toIntOrNull` 은 이 둘을 받아들인다. 같은 원시 함수를 쓰므로 막힌다. */
    @Test
    fun `앞뒤 공백과 더하기 부호는 정수 형태가 아니다`() {
        rejected(NotificationPageParameters(page = " 1")) shouldBe NotificationPageRejection.PAGE_NOT_INTEGER
        rejected(NotificationPageParameters(size = "+1")) shouldBe NotificationPageRejection.SIZE_NOT_INTEGER
    }

    @Test
    fun `음수 페이지는 거부다`() {
        rejected(NotificationPageParameters(page = "-1")) shouldBe NotificationPageRejection.PAGE_NEGATIVE
    }

    @Test
    fun `크기 0 과 상한 초과는 거부다 — 조용히 절삭하지 않는다`() {
        rejected(NotificationPageParameters(size = "0")) shouldBe NotificationPageRejection.SIZE_OUT_OF_RANGE
        rejected(
            NotificationPageParameters(size = "${NotificationInboxLimits.MAX_PAGE_SIZE + 1}"),
        ) shouldBe NotificationPageRejection.SIZE_OUT_OF_RANGE
    }

    @Test
    fun `상한 자체는 받는다 — 경계값`() {
        accepted(
            NotificationPageParameters(size = "${NotificationInboxLimits.MAX_PAGE_SIZE}"),
        ).size shouldBe NotificationInboxLimits.MAX_PAGE_SIZE
    }

    /**
     * **이 slice 가 7C① 과 갈리는 자리** — `toBoolean()` 은 알 수 없는 값을 `false` 로 접는다.
     * 그러면 `unreadOnly=yes` 를 보낸 호출자가 전체 목록을 받고도 안읽음만 걸렀다고 믿는다.
     */
    @Test
    fun `boolean 이 아닌 값은 거부다 — 조용히 false 가 되지 않는다`() {
        listOf("yes", "1", "on", "참", "TRUE1").forEach { raw ->
            rejected(NotificationPageParameters(unreadOnly = raw)) shouldBe
                NotificationPageRejection.UNREAD_ONLY_NOT_BOOLEAN
        }
    }

    @Test
    fun `boolean 두 리터럴은 대소문자를 무시한다`() {
        accepted(NotificationPageParameters(unreadOnly = "TRUE")).unreadOnly shouldBe true
        accepted(NotificationPageParameters(unreadOnly = "False")).unreadOnly shouldBe false
    }

    /** 형식 검사가 범위 검사보다 앞이다 — 규칙표의 순서가 곧 우선순위다. */
    @Test
    fun `형식과 범위가 둘 다 틀리면 형식 거부가 먼저다`() {
        rejected(NotificationPageParameters(size = "x", page = "-1")) shouldBe
            NotificationPageRejection.PAGE_NEGATIVE
    }

    /**
     * 파싱을 지나지 않고 지은 요청은 **생성자가** 막는다 — 검증된 값만 포트에 닿는다는 것을
     * 타입이 진다(파싱 경로가 하나라는 말의 뒷면).
     */
    @Test
    fun `검증 밖의 요청은 지어지지 않는다`() {
        shouldThrow<IllegalArgumentException> {
            NotificationPageRequest(page = -1, size = 10, unreadOnly = false)
        }
        shouldThrow<IllegalArgumentException> {
            NotificationPageRequest(page = 0, size = 0, unreadOnly = false)
        }
        shouldThrow<IllegalArgumentException> {
            NotificationPageRequest(
                page = 0,
                size = NotificationInboxLimits.MAX_PAGE_SIZE + 1,
                unreadOnly = false,
            )
        }
    }

    private fun accepted(parameters: NotificationPageParameters): NotificationPageRequest =
        when (val result = parseNotificationPageParameters(parameters)) {
            is NotificationPageResult.Accepted -> result.request
            is NotificationPageResult.Rejected -> error("받아들여야 하는 질의가 거부됐다: ${result.reason}")
        }

    private fun rejected(parameters: NotificationPageParameters): NotificationPageRejection =
        when (val result = parseNotificationPageParameters(parameters)) {
            is NotificationPageResult.Accepted -> error("거부해야 하는 질의가 통과했다: ${result.request}")
            is NotificationPageResult.Rejected -> result.reason
        }
}
