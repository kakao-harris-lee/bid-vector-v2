package bidvector.workflow.collection

import java.security.MessageDigest

private const val HEX_MASK = 0xff

fun sha256Hex(text: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and HEX_MASK) }

/**
 * 공고 하나의 익명 키(D-6G-2) — `sha256("<공고번호>/<차수>")`. **salt 가 없다**: 표본 선택
 * 술어가 같은 값을 다시 계산할 수 있어야 하고(재현), 공고번호 자체는 공개값이다. 이 해시가
 * 지우는 것은 비밀이 아니라 **조인 키**다 — 비식별의 대상은 공고가 아니라 투찰자다.
 */
@ConsistentCopyVisibility
data class NoticeKeyHash private constructor(
    val value: String,
) {
    companion object {
        fun of(
            noticeNumber: String,
            noticeRound: String,
        ): NoticeKeyHash = NoticeKeyHash(sha256Hex("$noticeNumber/$noticeRound"))

        /** 이미 계산된 해시를 되읽는다(표본 목록 파일) — 형태만 검사하고 값을 지어내지 않는다. */
        fun ofHex(raw: String): NoticeKeyHash {
            require(HEX_SHAPE.matches(raw)) { "공고 키 해시 형태가 아니다" }
            return NoticeKeyHash(raw)
        }

        private val HEX_SHAPE = Regex("[0-9a-f]{64}")
    }
}
