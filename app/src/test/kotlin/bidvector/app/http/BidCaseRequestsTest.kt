package bidvector.app.http

import bidvector.bidding.BidMemo
import bidvector.bidding.EvidenceRef
import bidvector.bidding.SkipReason
import bidvector.bidding.TransitionReason
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
        val tooLongRef = "r".repeat(EvidenceRef.MAX_LENGTH + 1)
        val tooLongMemo = "m".repeat(BidMemo.MAX_LENGTH + 1)

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
        val ok = "r".repeat(EvidenceRef.MAX_LENGTH)

        val read = BidCaseRequests.submission(json(record(""""evidenceRef":"$ok","drawNumbers":[3]""")))

        read.drawNumbers shouldBe listOf(3)
    }

    private fun record(extra: String): String =
        """{"amountWon":1000000,"vatTreatment":"INCLUSIVE","submittedAt":"2026-10-23T00:00:00Z",$extra}"""

    /**
     * 요청 문서의 `maxLength` ↔ 도메인 상한 상수, 등식(D-7B-5 C3-M2) — 입찰 건 요청 셋의 문자열 상한 전수를 문서에서 읽는다.
     * 문서와 도메인 중 한쪽만 바뀌면 붉다(생성 타입은 W-5 가 문서와 바이트로 묶는다).
     */
    @Test
    fun `입찰 건 요청 문서의 문자열 상한은 도메인 상한 상수와 같다`() {
        @Suppress("UNCHECKED_CAST")
        val schemas = (loadOpenApiSpec()["components"] as Map<String, Any?>)["schemas"] as Map<String, Any?>
        val documented =
            BID_CASE_REQUEST_SCHEMAS
                .flatMap { name ->
                    @Suppress("UNCHECKED_CAST")
                    val properties = (schemas.getValue(name) as Map<String, Any?>)["properties"] as Map<String, Any?>
                    properties.mapNotNull { (property, schema) ->
                        @Suppress("UNCHECKED_CAST")
                        ((schema as Map<String, Any?>)["maxLength"] as Int?)?.let { "$name.$property" to it }
                    }
                }.toMap()

        documented shouldBe
            mapOf(
                "SkipRequest.reason" to SkipReason.MAX_LENGTH,
                "RecordBidRequest.evidenceRef" to EvidenceRef.MAX_LENGTH,
                "RecordBidRequest.memo" to BidMemo.MAX_LENGTH,
                "CorrectOutcomeRequest.reason" to TransitionReason.MAX_LENGTH,
            )
    }

    private companion object {
        val BID_CASE_REQUEST_SCHEMAS = listOf("SkipRequest", "RecordBidRequest", "CorrectOutcomeRequest")
    }
}
