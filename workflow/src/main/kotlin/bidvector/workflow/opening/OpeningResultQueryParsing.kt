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
 */
fun parseOpeningResultQuery(
    noticeNumber: String?,
    noticeRound: String?,
): OpeningResultQueryRequest {
    rejectionOf(noticeNumber, noticeRound)?.let { return OpeningResultQueryRequest.Rejected(it) }
    val number = runCatching { NoticeNumber.of(requireNotNull(noticeNumber)) }.getOrNull()
        ?: return OpeningResultQueryRequest.Rejected(OpeningResultQueryRejection.NOTICE_NUMBER_INVALID)
    val round = runCatching { NoticeRound.of(requireNotNull(noticeRound)) }.getOrNull()
        ?: return OpeningResultQueryRequest.Rejected(OpeningResultQueryRejection.NOTICE_ROUND_INVALID)
    return OpeningResultQueryRequest.Accepted(NoticeId(number, round))
}

/** 값 객체에 닿기 전에 끊는 사유들 — 순서가 곧 우선순위다(부재 → 제어 문자 → 길이). */
private fun rejectionOf(
    noticeNumber: String?,
    noticeRound: String?,
): OpeningResultQueryRejection? =
    when {
        noticeNumber == null -> OpeningResultQueryRejection.NOTICE_NUMBER_MISSING
        noticeRound == null -> OpeningResultQueryRejection.NOTICE_ROUND_MISSING
        noticeNumber.hasControlCharacter() || noticeRound.hasControlCharacter() ->
            OpeningResultQueryRejection.TEXT_CONTROL_CHARACTER

        noticeNumber.length > MAX_NOTICE_KEY_LENGTH || noticeRound.length > MAX_NOTICE_KEY_LENGTH ->
            OpeningResultQueryRejection.TEXT_TOO_LONG

        else -> null
    }
