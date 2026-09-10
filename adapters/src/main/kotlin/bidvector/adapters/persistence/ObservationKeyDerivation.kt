package bidvector.adapters.persistence

import bidvector.procurement.ObservationKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RowDiscriminator
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
     * 그대로 넘긴다 — `raw_observation.payload_fields`에 저장되는 등재분 투영과 키 유도
     * 재료가 한 몸이다(이중 계산·이중 정의를 피한다). `observation.sourceText`(원문, F-7
     * 운영자 결정)도 재료에 더한다 — 등재분이 같아도 원문이 다르면(예: 미등재 필드만 바뀜)
     * 다른 관측으로 본다. 원문이 없으면 빈 문자열로 접는다(등재분만으로 유도하던 기존
     * 동작과 하위호환).
     *
     * `rowDiscriminator`(M3/3E 신설, D-3E-1a (a)) — 값이 있으면 그 값, 부재·공백이면 응답 안
     * 위치가 재료 마지막 칸에 더해진다(`OPEN-3B2-STORAGE-ROW-KEY-COLLISION`). `null`(기본값,
     * 행 구별이 필요 없는 오퍼레이션)이면 이 칸은 빈 문자열이라 **기존 키 유도와 동치**다 —
     * 목록 오퍼레이션의 기존 raw 행 키는 이 확장으로 바뀌지 않는다(상대적 동등/비동등만
     * test가 보므로 절대 해시 값 자체가 바뀌는 것은 무해하다).
     */
    fun of(
        observation: RawNoticeObservation,
        canonicalPayload: String,
        rowDiscriminator: RowDiscriminator? = null,
    ): ObservationKey {
        val material =
            listOf(
                observation.sourceEndpoint.name,
                observation.observedAt.toString(),
                canonicalPayload,
                observation.sourceText ?: "",
                rowDiscriminator.materialToken(),
            ).joinToString(separator = SEPARATOR)
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(StandardCharsets.UTF_8))
        return ObservationKey(digest.toHexString())
    }

    /**
     * `Identified`/`Positional`을 서로 다른 접두로 갈라 재료에 싣는다 — 접두가 없으면
     * `Identified("5")`와 `Positional(5)`가 우연히 같은 문자열이 되어 서로 다른 두 사유(값
     * 대 위치)가 재료 층에서 다시 접힌다.
     */
    private fun RowDiscriminator?.materialToken(): String =
        when (this) {
            null -> ""
            is RowDiscriminator.Identified -> "id:$value"
            is RowDiscriminator.Positional -> "pos:$ordinal"
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
