package bidvector.app.wiring

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * D-6A2a-13 ①(privacy-gate r1 L-2) — [PersistenceProperties] 가 `data class` 였을 때
 * `toString()` 이 DB 자격 값을 그대로 냈다. Boot 의 바인딩 실패 분석기는 실패한 속성의 값을
 * 문면에 낸다 — 미래 변경이 이 타입에 형식 검증을 붙이면 그 값이 기동 실패 로그(운영 배치에서는
 * **영구 로그**)로 간다. [bidvector.app.OperatorCredentialProperties] 와 같은 규율로 잠근다.
 */
class PersistencePropertiesTest {
    @Test
    fun `toString 은 자격 값을 담지 않는다`() {
        val credential = "persistence-credential-that-must-not-leak"
        val properties =
            PersistenceProperties(
                jdbcUrl = "jdbc:postgresql://192.0.2.1:5432/bidvector",
                username = "bidvector_admin",
                credential = credential,
            )

        properties.toString().contains(credential) shouldBe false
        properties.toString().contains("bidvector.app.wiring.PersistenceProperties") shouldBe true
    }
}
