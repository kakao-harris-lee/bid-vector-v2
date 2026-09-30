package bidvector.workflow.collection

import java.security.MessageDigest

private const val HEX_MASK = 0xff

/**
 * 바이트를 소문자 hex 로 — **해시 문자열의 형태를 짓는 자리는 하나다**(vr r4 L-13). 형태가 두 벌이면
 * 한쪽만 고쳐도 아무 데서도 붉어지지 않고, 그 값들은 영속 파일과 레인 간 계약에 실린다.
 */
fun hexOf(bytes: ByteArray): String = bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and HEX_MASK) }

fun sha256Hex(text: String): String =
    hexOf(
        MessageDigest
            .getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)),
    )

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
