package bidvector.app.collection

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * M9/portal-02 D-3 — mock KONEPS 의 라우팅은 **정확 일치**다. 조달청이 오퍼레이션명 뒤에 `02` 를
 * 붙인 개편에서 접두·접미 술어는 조용히 빗나간다: `…CnstwkBsisAmount02` 는 접미 `BsisAmount` 에
 * 더는 맞지 않지만 접두 `getBidPblancListInfo` 에는 여전히 맞아, 기초금액 축 요청이 **공고 목록
 * 응답**을 200 으로 받는다. 그 오라우팅은 호출 계수를 통째로 옮기므로 E2E 가 재려던 것이 사라진다.
 *
 * 그래서 두 축을 여기서 **거동으로** 잠근다 — 표에 있는 이름은 자기 축으로만 가고(ⓐ), 표에 없는
 * 이름은 404 이며 닫을 때 실패로 올라온다(ⓑ). 오퍼레이션 이름은 설정 기본 표에서 가져온다:
 * 리터럴로 적으면 그 자리가 다음 개편에서 한쪽만 바뀌는 자리가 된다(문면 자체는
 * `KonepsEndpointDefaultsTest` 가 잠근다).
 */
class MockKonepsRoutingTest {
    private val constructionNoticeList = KonepsEndpointProperties().operations.getValue("construction").path
    private val constructionBaseAmount =
        KonepsOpeningEndpointProperties().operations.getValue("construction").baseAmountPath

    /**
     * 표에 **없는** 세 이름. 둘은 등재하지 않은 업종(물품)의 공고 목록·기초금액이고 — 접두
     * `getBidPblancListInfo` 를 공유하므로 접두 라우팅이면 둘 다 공고 목록 응답을 200 으로 받는다 —
     * 셋째는 **개편 전 옛 이름**이다.
     *
     * **옛 이름이 목록에 있어야 하는 이유**(v L-1): 물품 둘만 두면 「표가 `02` 를 떼고도 받아준다」는
     * 느슨함이 잡히지 않는다. `mockKonepsRouteOf` 를 `ROUTES[op] ?: ROUTES[op + "02"]` 로 바꾸고
     * 배선을 옛 이름으로 되돌리면 그 판이 **전부 초록**이 된다 — 전환이 안 됐는데 초록인 자리다.
     * 옛 이름을 미등재로 못 박으면 그 변이가 여기서 붉다.
     */
    private val unroutedOperations =
        listOf(
            "getBidPblancListInfoThng",
            "getBidPblancListInfoThngBsisAmount02",
            "getBidPblancListInfoCnstwk",
        )

    @Test
    fun `표에 있는 이름은 자기 축으로만 간다 — 공고 목록과 기초금액이 접두를 공유해도 갈린다`() {
        MockOpeningKonepsHttp(noticesPerSlot = 1, bidderName = "SYN-라우팅").use { mock ->
            get(mock, constructionNoticeList, "inqryDiv=2&inqryBgnDt=20260603")
            get(mock, constructionBaseAmount, "bidNtceNo=OPEN-ROUTE-0001&bidNtceOrd=000")

            mock.noticeListCalls shouldContainExactly listOf(constructionNoticeList)
            mock.baseAmountNotices shouldContainExactly listOf("OPEN-ROUTE-0001")
            mock.unroutedCalls.shouldHaveSize(0)
        }
    }

    @Test
    fun `표에 없는 이름은 404 이고 닫을 때 실패로 올라온다 — 조용히 다른 축의 응답을 내지 않는다`() {
        val mock = MockOpeningKonepsHttp(noticesPerSlot = 1, bidderName = "SYN-라우팅")
        unroutedOperations.forEach { operation ->
            status(mock, operation, "inqryDiv=2&inqryBgnDt=20260603") shouldBe HTTP_NOT_FOUND
        }

        mock.unroutedCalls shouldContainExactly unroutedOperations
        mock.noticeListCalls.shouldHaveSize(0)
        shouldThrowAny { mock.close() }
    }

    private fun get(
        mock: MockOpeningKonepsHttp,
        operation: String,
        query: String,
    ) {
        status(mock, operation, query) shouldBe HTTP_OK
    }

    private fun status(
        mock: MockOpeningKonepsHttp,
        operation: String,
        query: String,
    ): Int =
        HttpClient
            .newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("${mock.baseUrl}/$operation?$query")).GET().build(),
                HttpResponse.BodyHandlers.discarding(),
            ).statusCode()
}

private const val HTTP_OK = 200
private const val HTTP_NOT_FOUND = 404
