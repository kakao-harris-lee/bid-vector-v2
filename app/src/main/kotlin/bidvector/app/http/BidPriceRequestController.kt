package bidvector.app.http

import bidvector.adapters.evaluation.InvalidEvaluationRequestException
import bidvector.app.wiring.BidPriceRequestFactory
import bidvector.app.wiring.BidPriceRequestOutcome
import bidvector.app.wiring.NoticeNotFoundException
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.NoticeRound
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

/**
 * 투찰가 요청 — 공고 **단건**·운영자 요청 평가(M7/7C ②). 컨트롤러는
 * `factory.forRequest(...)` 와 그 run 의 `evaluate()` 만 부른다 — 판정·조립을 복제하지 않고
 * 포트를 직접 쥐지 않는다(`EvaluationDryRunController` 와 같은 규율).
 *
 * **POST 다.** ① 이 요청의 종착점이 알림함 한 항목 기록(R-4)이라 쓰기를 갖게 될 자리다 —
 * GET 으로 출하한 뒤 바꾸면 이미 출하된 계약을 깨야 한다 ② CSRF 요구가 **구성으로** 선다:
 * `BrowserSessionAuthenticator` 의 면제 집합이 `GET`·`HEAD`·`OPTIONS` 셋뿐이라 POST 는
 * 요구 쪽에 자동으로 놓인다(열거를 더하지 않는다) ③ 브라우저·프록시의 prefetch·재시도가
 * 평가를 임의로 발화시키지 않는다.
 *
 * **공고 식별자는 본문이다**(경로가 아니다) — 경로 매개변수로 두면 퍼센트 인코딩 축이 하나 더
 * 생기고, 전 요청 audit 의 경로 칸 어휘가 공고마다 달라진다.
 *
 * **`@RequestBody` 가 [JsonNode] 다**(D-6A3-19) — Jackson 의 기본 강제 변환이 `123`(수)을
 * 공고번호로, `"3"`(문자열)을 여력 수로 조용히 받아들이지 못하게 원시 트리로 받아
 * [parseBidPriceRequest] 가 **명시** 검증한다.
 */
@RestController
class BidPriceRequestController(
    private val factory: BidPriceRequestFactory,
) {
    @PostMapping("/api/bid-price-requests")
    fun request(
        @RequestBody body: JsonNode,
    ): BidPriceRequestResponse {
        val request = parseBidPriceRequest(body)
        return when (val outcome = factory.forRequest(request.noticeId, request.currentActiveBids)) {
            // 표면이 404 로 옮긴다 — 오류 **형태**를 만드는 자리는 `ErrorBody.kt` 하나다.
            BidPriceRequestOutcome.NoticeNotFound -> throw NoticeNotFoundException()
            is BidPriceRequestOutcome.Prepared -> {
                val evaluation = runBlocking { outcome.run.evaluate() }
                bidPriceRequestResponseOf(outcome.run, evaluation, request.currentActiveBids)
            }
        }
    }
}

/** 검증을 지난 요청 — 생성 경로는 [parseBidPriceRequest] 하나다. */
internal data class BidPriceRequest(
    val noticeId: NoticeId,
    val currentActiveBids: Int,
)

/**
 * 요청 본문의 **명시** 형식 검증. 어긋나면 전부 [InvalidEvaluationRequestException](400
 * `INVALID_REQUEST`, 예외 메시지는 응답에 실리지 않는다 — `ErrorMapping` 이 고정 문구로만
 * 옮긴다)이다. 어느 입력이 틀렸는지 응답에 싣지 않는 것은 7C ① 검색과 같은 관례다.
 *
 * 값 객체의 `init`(공백 금지·차수 3자리)에 검증을 맡기지 않는다 — 그러면 형식 오류가
 * `IllegalArgumentException` 으로 새어 500 경로로 간다. 여기서 먼저 판정해 400 으로 옮긴다.
 */
internal fun parseBidPriceRequest(body: JsonNode): BidPriceRequest {
    if (!body.isObject) {
        throw InvalidEvaluationRequestException("요청 본문이 JSON object 가 아니다")
    }
    val number = body.bidPriceText("noticeNumber")
    val round = body.bidPriceText("noticeRound")
    if (!NOTICE_ROUND_FORMAT.matches(round)) {
        throw InvalidEvaluationRequestException("noticeRound 는 제로패딩 3자리 숫자여야 한다")
    }
    val normalizedNumber =
        NoticeNumber.ofOrNull(number)
            ?: throw InvalidEvaluationRequestException("noticeNumber 가 공고번호로 서지 않는다")
    return BidPriceRequest(
        noticeId = NoticeId(normalizedNumber, NoticeRound.of(round)),
        currentActiveBids = parseCurrentActiveBids(body),
    )
}

/**
 * 문자열 칸 하나 — 누락·`null`·비문자열·공백·제어문자·길이 상한 초과를 거부한다. 제어문자를
 * 거부하는 이유는 7C ① 과 같다(로그·audit 의 행 경계를 입력이 흔들지 못하게 한다).
 */
private fun JsonNode.bidPriceText(key: String): String {
    val node = get(key)
    if (node == null || node.isNull || !node.isString) {
        throw InvalidEvaluationRequestException("$key 는 문자열이어야 한다")
    }
    val value = node.stringValue()
    if (value.isBlank()) {
        throw InvalidEvaluationRequestException("$key 는 빈 문자열일 수 없다")
    }
    if (value.length > MAX_IDENTIFIER_LENGTH) {
        throw InvalidEvaluationRequestException("$key 가 길이 상한을 넘었다")
    }
    if (value.any { it.isISOControl() }) {
        throw InvalidEvaluationRequestException("$key 에 제어 문자가 있다")
    }
    return value
}

/**
 * 식별자 칸의 **형식** 상한 — 어떤 업무 판단도 이 수에 걸려 있지 않다(7C ① 의 질의 길이
 * 상한과 같은 종류). 계약 문서의 `maxLength` 와 같은 값이고 계약 test 가 그 등식을 잰다.
 */
internal const val MAX_IDENTIFIER_LENGTH = 200

private val NOTICE_ROUND_FORMAT = Regex("\\d{3}")
