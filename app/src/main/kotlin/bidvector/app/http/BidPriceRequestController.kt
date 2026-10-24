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
 * [parseBidPriceNoticeId] 와 [parseCurrentActiveBids] 가 **명시** 검증한다.
 */
@RestController
class BidPriceRequestController(
    private val factory: BidPriceRequestFactory,
) {
    @PostMapping("/api/bid-price-requests")
    fun request(
        @RequestBody body: JsonNode,
    ): BidPriceRequestResponse {
        val noticeId = parseBidPriceNoticeId(body)
        val currentActiveBids = parseCurrentActiveBids(body)
        return when (val outcome = factory.forRequest(noticeId, currentActiveBids)) {
            // 표면이 404 로 옮긴다 — 오류 **형태**를 만드는 자리는 `ErrorBody.kt` 하나다.
            BidPriceRequestOutcome.NoticeNotFound -> throw NoticeNotFoundException()
            is BidPriceRequestOutcome.Prepared -> {
                val evaluation = runBlocking { outcome.run.evaluate() }
                bidPriceRequestResponseOf(outcome.run, evaluation, currentActiveBids)
            }
        }
    }
}
/**
 * 요청 본문의 **명시** 형식 검증. 어긋나면 전부 [InvalidEvaluationRequestException](400
 * `INVALID_REQUEST`, 예외 메시지는 응답에 실리지 않는다 — `ErrorMapping` 이 고정 문구로만
 * 옮긴다)이다. 어느 입력이 틀렸는지 응답에 싣지 않는 것은 7C ① 검색과 같은 관례다.
 *
 * 값 객체의 `init`(공백 금지·차수 3자리)에 검증을 맡기지 않는다 — 그러면 형식 오류가
 * `IllegalArgumentException` 으로 새어 500 경로로 간다. 여기서 먼저 판정해 400 으로 옮긴다.
 *
 * **검증을 지난 요청을 담는 보유 타입을 두지 않는다**(값 둘을 따로 낸다) — `app.http` 의 주입
 * 표면 게이트는 생성자 매개변수를 「주입받는다」로 읽어서, 도메인 타입을 안은 작은 보유 class 가
 * 주입 허용 목록을 넓히라고 요구한다(실측으로 걸렸다). 호출부가 값 둘을 그대로 들고 간다.
 */
internal fun parseBidPriceNoticeId(body: JsonNode): NoticeId {
    rejectBidPriceRequestIf(!body.isObject) { "요청 본문이 JSON object 가 아니다" }
    val number = body.bidPriceText("noticeNumber")
    val round = body.bidPriceText("noticeRound")
    rejectBidPriceRequestIf(!NOTICE_ROUND_FORMAT.matches(round)) { "noticeRound 는 제로패딩 3자리 숫자여야 한다" }
    val normalizedNumber =
        NoticeNumber.ofOrNull(number)
            ?: rejectBidPriceRequest { "noticeNumber 가 공고번호로 서지 않는다" }
    return NoticeId(normalizedNumber, NoticeRound.of(round))
}

/**
 * 문자열 칸 하나 — 누락·`null`·비문자열·공백·제어문자·길이 상한 초과를 거부한다. 제어문자를
 * 거부하는 이유는 7C ① 과 같다(로그·audit 의 행 경계를 입력이 흔들지 못하게 한다).
 */
private fun JsonNode.bidPriceText(key: String): String {
    val node = get(key)
    rejectBidPriceRequestIf(node == null || node.isNull || !node.isString) { "$key 는 문자열이어야 한다" }
    val value = checkNotNull(node).stringValue()
    rejectBidPriceRequestIf(value.isBlank()) { "$key 는 빈 문자열일 수 없다" }
    rejectBidPriceRequestIf(value.length > MAX_IDENTIFIER_LENGTH) { "$key 가 길이 상한을 넘었다" }
    rejectBidPriceRequestIf(value.any { it.isISOControl() }) { "$key 에 제어 문자가 있다" }
    return value
}

/**
 * 거부의 **유일한 자리**. 검증마다 `throw` 를 쓰면 함수 하나가 throw 넷을 들고(detekt `ThrowsCount`),
 * 더 중요하게는 거부가 여러 자리로 흩어져 「어떤 입력이 어떤 코드로 거부되는가」가 한눈에 안 보인다.
 * 메시지는 진단용이고 **응답에 실리지 않는다**(`ErrorMapping` 이 고정 문구로만 옮긴다) — 지연 평가로
 * 받아 통과하는 경로에서 문자열을 만들지도 않는다.
 */
private fun rejectBidPriceRequest(message: () -> String): Nothing =
    throw InvalidEvaluationRequestException(message())

private fun rejectBidPriceRequestIf(
    rejected: Boolean,
    message: () -> String,
) {
    if (rejected) rejectBidPriceRequest(message)
}

/**
 * 식별자 칸의 **형식** 상한 — 어떤 업무 판단도 이 수에 걸려 있지 않다(7C ① 의 질의 길이
 * 상한과 같은 종류). 계약 문서의 `maxLength` 와 같은 값이고 계약 test 가 그 등식을 잰다.
 */
internal const val MAX_IDENTIFIER_LENGTH = 200

private val NOTICE_ROUND_FORMAT = Regex("\\d{3}")
