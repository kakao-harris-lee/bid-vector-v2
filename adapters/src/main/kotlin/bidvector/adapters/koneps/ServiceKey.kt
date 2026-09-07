package bidvector.adapters.koneps

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * KONEPS 인증 서비스 키(D-3B-5) — 로그·예외 메시지·`toString()` 에 원문이 노출되지 않는다
 * (secret 스캔 대상, evidence 규격). URL 질의 인코딩은 [urlEncoded] 가 한 곳에서 한다 —
 * resultCode `30`(등록되지 않은 서비스 키)의 실제 원인 후보 중 하나가 「URL 미인코딩」이다
 * (`policy-values.md` §3.2).
 */
class ServiceKey private constructor(
    private val raw: String,
) {
    val urlEncoded: String
        get() = URLEncoder.encode(raw, StandardCharsets.UTF_8)

    override fun toString(): String = "ServiceKey(***)"

    override fun equals(other: Any?): Boolean = other is ServiceKey && raw == other.raw

    override fun hashCode(): Int = raw.hashCode()

    companion object {
        fun of(raw: String): ServiceKey {
            require(raw.isNotBlank()) { "ServiceKey는 빈 문자열일 수 없다" }
            return ServiceKey(raw)
        }
    }
}
