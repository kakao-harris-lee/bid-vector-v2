package bidvector.procurement

import bidvector.sharedkernel.NoticeRound

/**
 * 공고번호(`bidNtceNo`) — KONEPS 의 권위 유니크 키(COL-05). 정규화 규칙은 이 값 객체가
 * 소유한다(legacy `normalize_notice_number` 를 호출부마다 부르는 형태를 채택하지 않는다).
 *
 * 정규화: 앞뒤 공백 제거 하나뿐이다 — legacy 불변식(`source_url` 정규화가 `bidNtceNo`/
 * `bidNtceOrd` 만 남기므로 source_url 동일 ⇒ notice_number 동일)은 이 값이 **원문 그대로**
 * 유지될 때만 성립한다. 자릿수·구분자 형식을 임의로 바꾸면 그 불변식이 깨진다.
 */
data class NoticeNumber(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "NoticeNumber는 빈 문자열일 수 없다" }
        require(value == value.trim()) { "NoticeNumber는 정규화된(trim) 형태여야 한다: '$value'" }
    }

    companion object {
        /** 원문 공고번호 문자열에서 정규화한다 — 정규화 규칙은 여기 하나뿐이다(COL-05). */
        fun of(raw: String): NoticeNumber = NoticeNumber(raw.trim())
    }
}

/**
 * 공고 식별자 — 공고번호와 차수의 쌍(③, COL-05). 같은 공고번호라도 차수(재공고)가 다르면
 * 다른 공고다(`data-dictionary.md` §2.2.1 `Renoticed`).
 */
data class NoticeId(
    val number: NoticeNumber,
    val round: NoticeRound,
)
