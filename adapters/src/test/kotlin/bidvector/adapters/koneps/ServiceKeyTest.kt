package bidvector.adapters.koneps

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/** D-3B-5 — 서비스 키가 `toString()`·로그·예외 메시지에 원문으로 나오지 않는다. */
class ServiceKeyTest {
    @Test
    fun `toString 은 원문을 노출하지 않는다`() {
        val key = ServiceKey.of("super-secret-key-value")

        key.toString() shouldBe "ServiceKey(***)"
        key.toString() shouldNotContain "super-secret-key-value"
    }

    @Test
    fun `urlEncoded 는 URL 질의에 안전한 형태를 낸다`() {
        val key = ServiceKey.of("abc+def/ghi==")

        key.urlEncoded shouldNotContain "+def/ghi=="
    }

    @Test
    fun `빈 문자열은 거부된다`() {
        runCatching { ServiceKey.of("") }.isFailure shouldBe true
    }
}
