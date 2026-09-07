package bidvector.adapters.persistence

import bidvector.procurement.ObservationKey
import bidvector.procurement.RawNoticeObservation
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * [ObservationKey]의 실제 유도 지점(verifier r1 F-2 뒤 신설) — `MessageDigest`(SHA-256)를
 * `RawObservationStore.kt` KDoc이 procurement가 가질 수 없다고 적은 바로 그 이유(domain
 * 허용 목록 밖)로 여기(adapters)에 둔다. `ObservationPayloadCodec.encode`가 이미 만드는
 * **정본 직렬화**(계약 등재 필드 전체, `raw_observation.payload`에 그대로 저장되는 것과
 * 같은 문자열)에 sourceEndpoint·observedAt을 더해 해시한다 — 저장되는 payload와 키가
 * 유도되는 재료가 같은 문자열이라 「같은 payload인데 다른 키」·「다른 payload인데 같은 키」
 * 가 (해시 충돌을 제외하면) 구조적으로 생기지 않는다.
 *
 * SHA-256(256비트)은 `Object.hashCode()`류(32비트, `"Aa"`·`"BB"`가 `String.hashCode()`에서
 * 충돌하는 실물이 F-2였다)와 달리 실무에서 우연 충돌을 기대할 수 없는 자리수다.
 */
internal object ObservationKeyDerivation {
    private const val SEPARATOR = "|"

    /**
     * `canonicalPayload`는 [ObservationPayloadCodec.encode]가 낸 **같은** 문자열을 호출부가
     * 그대로 넘긴다 — `raw_observation.payload`에 저장되는 정본과 키 유도 재료가 한 몸이다
     * (이중 계산·이중 정의를 피한다).
     */
    fun of(
        observation: RawNoticeObservation,
        canonicalPayload: String,
    ): ObservationKey {
        val material =
            listOf(observation.sourceEndpoint.name, observation.observedAt.toString(), canonicalPayload)
                .joinToString(separator = SEPARATOR)
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(StandardCharsets.UTF_8))
        return ObservationKey(digest.toHexString())
    }

    private fun ByteArray.toHexString(): String {
        val chars = CharArray(size * HEX_CHARS_PER_BYTE)
        for (i in indices) {
            val byteValue = this[i].toInt() and BYTE_MASK
            chars[i * HEX_CHARS_PER_BYTE] = HEX_DIGITS[byteValue shr NIBBLE_BITS]
            chars[i * HEX_CHARS_PER_BYTE + 1] = HEX_DIGITS[byteValue and NIBBLE_MASK]
        }
        return String(chars)
    }

    private const val HEX_DIGITS = "0123456789abcdef"

    /** 바이트 하나 = 16진수 두 글자. */
    private const val HEX_CHARS_PER_BYTE = 2

    /** 부호 있는 `Byte`를 부호 없는 0~255 정수로 펴는 마스크. */
    private const val BYTE_MASK = 0xFF

    /** 바이트를 상위·하위 니블로 가르는 비트 폭(4비트 = 16진수 한 자리). */
    private const val NIBBLE_BITS = 4
    private const val NIBBLE_MASK = 0x0F
}
