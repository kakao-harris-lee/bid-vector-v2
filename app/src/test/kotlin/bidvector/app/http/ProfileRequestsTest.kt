package bidvector.app.http

import bidvector.workflow.profile.TradeName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

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
    private fun detailsWith(tradeName: String): JsonNode =
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
}
