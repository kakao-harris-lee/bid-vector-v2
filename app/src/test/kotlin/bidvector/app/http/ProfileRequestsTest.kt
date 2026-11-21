package bidvector.app.http

import bidvector.workflow.profile.BusinessRegistrationNumber
import bidvector.workflow.profile.TradeName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

/**
 * 이름 거부 규칙의 **코드포인트 축**(M7/7C④ 2b r3) — 짝을 이룬 대리는 통과하고 짝 없는 대리는 400 이다.
 *
 * 왜 이 자리인가: 규칙은 `Char` 가 아니라 **코드포인트** 단위로 돈다. `Char` 하나씩 보면 이모지 ·
 * 희귀 한자처럼 짝을 이룬 대리의 두 조각이 각각 `SURROGATE` 로 읽혀 **정상 입력이 400 이 된다**.
 * 거부해야 하는 것은 짝 없는 대리다. 그 둘을 가르는 것이 `index += Character.charCount(…)` 한 줄이고,
 * 이 test 가 그 줄을 잠근다(변이 MS-pair: `+= 1` 로 바꾸면 첫 test 가 붉다).
 *
 * HTTP 를 거치지 않고 요청 판독을 직접 부른다 — 짝 없는 대리를 JSON 문면으로 보내면 거부가 **내 규칙**
 * 인지 **파서**인지 갈리지 않는다(초록의 이유가 둘이 된다). 노드를 손으로 지어 그 모호함을 없앤다.
 */
class ProfileRequestsTest {
    private val mapper = JsonMapper.builder().build()

    /** 상호 하나만 담은 사업 정보 본문 — JSON 문면을 거치지 않고 값을 그대로 싣는다. */
    private fun detailsWith(tradeName: String): ObjectNode =
        mapper.createObjectNode().apply {
            put("tradeName", tradeName)
            putNull("headquartersRegion")
            put("naraRegistration", "UNANSWERED")
        }

    @Test
    fun `짝을 이룬 대리가 든 이름은 통과한다 — 이모지 한 자`() {
        val withEmoji = "합성😀상호"

        val details = ProfileRequests.businessDetails(detailsWith(withEmoji))

        details.tradeName shouldBe TradeName(withEmoji)
    }

    @Test
    fun `짝 없는 대리는 400 이다`() {
        shouldThrow<InvalidEditRequestException> {
            ProfileRequests.businessDetails(detailsWith("합성\uD800상호"))
        }
    }

    /** 폭 0 문자만으로 된 이름 — 눈에 보이지 않는 상호가 저장되지 않는다(서식 Cf). */
    @Test
    fun `보이지 않는 문자만으로 된 이름은 400 이다`() {
        shouldThrow<InvalidEditRequestException> {
            ProfileRequests.businessDetails(detailsWith("​​"))
        }
    }

    /** 사업자번호만 바꾼 사업 정보 본문 — 키를 **싣지 않는** 경우는 [detailsWith] 가 맡는다. */
    private fun detailsWithNumber(raw: String?): JsonNode =
        detailsWith("합성상호").apply {
            if (raw == null) putNull("businessRegistrationNumber") else put("businessRegistrationNumber", raw)
        }

    /**
     * M15/15C — **받는 꼴 둘이 같은 값으로 들어온다.** 요청 판독이 스스로 하이픈을 지우면
     * 정규화가 두 자리가 되므로, 여기서는 「값 타입이 낸 것을 그대로 싣는가」만 잰다.
     */
    @Test
    fun `사업자번호 — 숫자 10자리와 3-2-5 표기가 같은 값으로 들어온다`() {
        val expected = BusinessRegistrationNumber("1234567890")

        ProfileRequests.businessDetails(detailsWithNumber("1234567890")).businessRegistrationNumber shouldBe expected
        ProfileRequests.businessDetails(detailsWithNumber("123-45-67890")).businessRegistrationNumber shouldBe expected
    }

    /** 키가 없거나 `null` 이면 「비워 둠」 — 선택 칸이므로 그 부재가 400 이 아니다. */
    @Test
    fun `사업자번호 — 키가 없거나 null 이면 미설정이다`() {
        ProfileRequests.businessDetails(detailsWith("합성상호")).businessRegistrationNumber shouldBe null
        ProfileRequests.businessDetails(detailsWithNumber(null)).businessRegistrationNumber shouldBe null
    }

    /**
     * 꼴이 어긋나면 **400** 이다(500 이 아니다) — 값 타입의 `require` 가 `requestValue` 를 지나
     * 요청 오류로 옮겨지는지를 잰다. 공백뿐인 값도 「지우기」로 접지 않는다(상호와 같은 규율).
     */
    @Test
    fun `사업자번호 — 꼴이 어긋나거나 공백뿐이면 400 이다`() {
        listOf("123456789", "12345678901", "1-2-3-4-5-6-7-8-9-0", "123456789O", "   ", "")
            .forEach { raw ->
                shouldThrow<InvalidEditRequestException> {
                    ProfileRequests.businessDetails(detailsWithNumber(raw))
                }
            }
    }

    /** 거부 문면에 입력값이 실리지 않는다 — 400 본문·로그 어느 쪽으로도 새지 않게(P4). */
    @Test
    fun `사업자번호 — 거부 문면이 입력값을 싣지 않는다`() {
        val failure =
            shouldThrow<InvalidEditRequestException> {
                ProfileRequests.businessDetails(detailsWithNumber("777-77-77777"))
            }

        failure.message.orEmpty() shouldNotContain "77777"
    }
}
