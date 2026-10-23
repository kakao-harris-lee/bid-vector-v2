package bidvector.app.http

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * **요청 값이 도메인 규칙을 어기면 400 이다 — 500 이 아니다**(M7/7B, D-7B-2 작업 중 발견).
 *
 * 값 객체의 `require` 가 던지는 `IllegalArgumentException` 은 기본 처리기에서 500 이 된다.
 * 요청 판독이 그것을 [InvalidEditRequestException](400)으로 옮기는지를 자리마다 잰다 — 차수
 * 형식 · 증빙 참조 길이 · 메모 길이 · 추첨번호 부호. 정상 값은 그대로 통과한다(양성 대조).
 */
class BidCaseRequestsTest {
    private val mapper = JsonMapper.builder().build()

    private fun json(text: String): JsonNode = mapper.readTree(text)

    @Test
    fun `차수 형식이 틀리면 400 이다`() {
        shouldThrow<InvalidEditRequestException> {
            BidCaseRequests.openCase(json("""{"noticeNumber":"R7B-REQ","noticeRound":"00","bidKind":"REAL"}"""))
        }
    }

    @Test
    fun `정상 차수는 그대로 읽힌다 — 양성 대조`() {
        val (notice, _) =
            BidCaseRequests.openCase(json("""{"noticeNumber":"R7B-REQ","noticeRound":"000","bidKind":"REAL"}"""))

        notice.noticeRound.value shouldBe "000"
    }

    @Test
    fun `증빙 참조 · 메모 길이 상한과 추첨번호 부호를 어기면 400 이다`() {
        val tooLongRef = "r".repeat(EVIDENCE_LIMIT + 1)
        val tooLongMemo = "m".repeat(MEMO_LIMIT + 1)

        listOf(
            """"evidenceRef":"$tooLongRef"""",
            """"memo":"$tooLongMemo"""",
            """"drawNumbers":[0]""",
        ).forEach { extra ->
            shouldThrow<InvalidEditRequestException> { BidCaseRequests.submission(json(record(extra))) }
        }
    }

    @Test
    fun `상한 안의 기록은 그대로 읽힌다 — 양성 대조`() {
        val ok = "r".repeat(EVIDENCE_LIMIT)

        val read = BidCaseRequests.submission(json(record(""""evidenceRef":"$ok","drawNumbers":[3]""")))

        read.drawNumbers shouldBe listOf(3)
    }

    private fun record(extra: String): String =
        """{"amountWon":1000000,"vatTreatment":"INCLUSIVE","submittedAt":"2026-10-23T00:00:00Z",$extra}"""

    private companion object {
        /**
         * `EvidenceRef` · `BidMemo` 의 길이 상한(도메인 companion 이 private 이라 값을 옮겨 적는다).
         * 도메인이 상한을 올리면 「넘는 값」이 정상이 되어 위 거부 test 가 붉어진다 — 어긋남이 숨지 않는다.
         */
        const val EVIDENCE_LIMIT = 128
        const val MEMO_LIMIT = 2000
    }
}
