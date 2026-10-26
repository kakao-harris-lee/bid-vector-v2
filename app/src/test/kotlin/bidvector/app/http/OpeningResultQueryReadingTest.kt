package bidvector.app.http

import bidvector.app.wiring.OpeningResultQuery
import bidvector.workflow.opening.NoticeOpeningFacts
import bidvector.workflow.opening.OPENING_PROGRESS_POLICY
import bidvector.workflow.opening.ReadOpeningResult
import bidvector.workflow.strategy.Clock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-10-26T00:00:00Z")

/**
 * 질의 **읽기 자체가 깨지는** 입력을 이 endpoint 가 어떻게 다루는가 — HTTP 왕복으로는 잴 수 없는
 * 자리다(cr r1 · vr r1 L-6).
 *
 * 공용 읽기 자리([readDeclaredValues])가 실패를 **삼켜 빈 값으로** 돌려주면, 이 endpoint 는 필수 칸이
 * 둘이라 여전히 400 을 낸다 — 같은 상태 코드·같은 고정 문구라서 응답만 보면 삼킴과 정상 거부를 구별할
 * 수 없다. 그래서 거동의 차이를 **원인 사슬**에서 잰다: 읽기가 던진 실패가 거부 예외의 `cause` 로
 * 남아야 한다. 삼키는 변이에서 이 단언이 깨진다.
 *
 * Spring 컨텍스트를 띄우지 않는다 — 컨트롤러는 질의 하나를 받는 평범한 객체이고, 조회기는 공고 부재를
 * 내는 이중체로 족하다(이 test 의 주제는 응답 값이 아니라 **읽기 실패의 전달**이다).
 */
class OpeningResultQueryReadingTest {
    private val controller =
        OpeningResultController(
            OpeningResultQuery(
                ReadOpeningResult({ NoticeOpeningFacts.NoticeAbsent }, Clock { NOW }, OPENING_PROGRESS_POLICY),
            ),
        )

    @Test
    fun `질의 읽기가 깨지면 거부로 바꾸되 원인을 잃지 않는다`() {
        val broken = IllegalStateException("질의 해석 실패(서블릿 컨테이너)")
        val request =
            object : MockHttpServletRequest("GET", "/api/opening-results") {
                override fun getParameterValues(name: String): Array<String>? = throw broken
            }

        val rejection = shouldThrow<InvalidOpeningResultQueryException> { controller.read(request) }

        rejection.cause shouldBe broken
    }

    @Test
    fun `읽기가 멀쩡하면 같은 경로가 통상 거부로 간다 — 통제군`() {
        val request = MockHttpServletRequest("GET", "/api/opening-results")

        val rejection = shouldThrow<InvalidOpeningResultQueryException> { controller.read(request) }

        rejection.cause shouldBe null
    }
}
