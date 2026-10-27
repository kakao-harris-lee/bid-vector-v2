package bidvector.workflow.opening

import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.notice.hasControlCharacter

/**
 * 조회 질의의 **순수 파싱**(M7/7C⑤-a, D-7C5-5) — 전송 층은 원문 문자열만 받고 검증을 여기로
 * 넘긴다(7C① 과 같은 규율: 프레임워크의 타입 변환에 맡기면 형식 오류가 500 으로 새거나
 * 조용히 강제 변환된다).
 *
 * 거부 사유는 **열거**다 — 응답에는 실리지 않지만(어느 입력이 틀렸는지 싣지 않는다) 진단과
 * test 가 그 어휘로 말한다.
 */
sealed interface OpeningResultQueryRequest {
    data class Accepted(
        val id: NoticeId,
    ) : OpeningResultQueryRequest

    data class Rejected(
        val reason: OpeningResultQueryRejection,
    ) : OpeningResultQueryRequest
}

enum class OpeningResultQueryRejection {
    NOTICE_NUMBER_MISSING,
    NOTICE_NUMBER_INVALID,
    NOTICE_ROUND_MISSING,
    NOTICE_ROUND_INVALID,
    TEXT_TOO_LONG,
    TEXT_CONTROL_CHARACTER,
}

/**
 * 전송 층이 받아들이는 공고 키 길이 상한 — **도메인 규칙이 아니라 전송 가드**다(7C① 의 텍스트
 * 칸 상한과 같은 자리·같은 수). 공고번호 형식 자체는 값 객체가 진다.
 */
const val MAX_NOTICE_KEY_LENGTH = 200

/**
 * 두 칸을 공고 하나로 — **둘 다 필수**다(없는 칸을 기본값으로 접지 않는다: 차수를 `"000"` 으로
 * 접으면 1차 공고의 답이 다른 차수의 질문에 돌아간다, R-QUAL-05 와 같은 축).
 *
 * 공고번호는 수집이 저장할 때 쓰는 **같은 정규화**(`NoticeNumber.of`)를 지난다 — 조회가 다른
 * 정규화를 쓰면 저장된 키를 못 찾는다. 차수는 제로패딩 3자리만 받는다(값 객체의 형식 규칙 —
 * 조회가 관대해지지 않는다).
 *
 * **거부 사유는 [rejectionOf] 한 자리가 센다**(값 객체 실패 둘까지). 그래서 이 함수의 출구가
 * 하나이고, 「거부가 없다」는 곧 두 값 객체가 섰다는 뜻이다 — [requireNotNull] 이 그 구조적
 * 사실을 적는다(사유 열거에 `number == null`·`round == null` 이 있으므로 둘 다 null 이 아니다).
 */
fun parseOpeningResultQuery(
    noticeNumber: String?,
    noticeRound: String?,
): OpeningResultQueryRequest {
    val number = noticeNumber?.let { raw -> runCatching { NoticeNumber.of(raw) }.getOrNull() }
    val round = noticeRound?.let { raw -> runCatching { NoticeRound.of(raw) }.getOrNull() }
    val id = if (number != null && round != null) NoticeId(number, round) else null
    return when (val rejection = rejectionOf(noticeNumber, noticeRound, number, round)) {
        null -> OpeningResultQueryRequest.Accepted(requireNotNull(id))
        else -> OpeningResultQueryRequest.Rejected(rejection)
    }
}

/**
 * 값 객체에 닿기 전에 끊는 사유들 — 순서가 곧 우선순위다(부재 → 제어 문자 → 길이).
 *
 * 술어를 먼저 **이름 붙여** 둔다: 두 칸을 함께 보는 조건이라 분기 줄에 그대로 쓰면 읽는 사람이
 * 「무엇을 보는 조건인가」를 매번 다시 읽어야 한다. 널 검사 앞에서 계산해도 안전하다
 * (`hasControlCharacter` 는 수신자 널을 스스로 접고, 길이는 널이면 0 으로 센다).
 */
private fun rejectionOf(
    noticeNumber: String?,
    noticeRound: String?,
    number: NoticeNumber?,
    round: NoticeRound?,
): OpeningResultQueryRejection? {
    val hasControlCharacter = noticeNumber.hasControlCharacter() || noticeRound.hasControlCharacter()
    val exceedsLength =
        (noticeNumber?.length ?: 0) > MAX_NOTICE_KEY_LENGTH || (noticeRound?.length ?: 0) > MAX_NOTICE_KEY_LENGTH
    return when {
        noticeNumber == null -> OpeningResultQueryRejection.NOTICE_NUMBER_MISSING
        noticeRound == null -> OpeningResultQueryRejection.NOTICE_ROUND_MISSING
        hasControlCharacter -> OpeningResultQueryRejection.TEXT_CONTROL_CHARACTER
        exceedsLength -> OpeningResultQueryRejection.TEXT_TOO_LONG
        number == null -> OpeningResultQueryRejection.NOTICE_NUMBER_INVALID
        round == null -> OpeningResultQueryRejection.NOTICE_ROUND_INVALID
        else -> null
    }
}
