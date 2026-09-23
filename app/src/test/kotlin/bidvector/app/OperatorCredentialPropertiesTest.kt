package bidvector.app

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * verifier r4 실측 반영 — [OperatorCredentialProperties]가 `data class`였을 때
 * `toString()`이 원문을 그대로 냈다(자격증명 원문이 Spring의 기동 실패·바인딩 오류·
 * actuator 환경 노출 로그로 새는 경로). `OperatorCredentialTest`와 같은 형태로 잠근다.
 */
class OperatorCredentialPropertiesTest {
    @Test
    fun `toString 은 원문 값을 담지 않는다`() {
        val secret = "operator-secret-value-that-must-not-leak"
        val properties = OperatorCredentialProperties(secret)

        properties.toString().contains(secret) shouldBe false
        properties.toString().contains("bidvector.app.OperatorCredentialProperties") shouldBe true
    }
}
