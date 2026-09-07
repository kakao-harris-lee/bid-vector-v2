package bidvector.procurement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * `ObservationKey`는 값만 나른다 — 유도 규칙은 어댑터 소유다(verifier r1 F-2 뒤 정정,
 * `RawObservationStore.kt` KDoc). 이 test는 값 타입 자체의 불변식만 잰다.
 */
class ObservationKeyTest {
    @Test
    fun `ObservationKey 는 빈 문자열을 거부한다`() {
        shouldThrow<IllegalArgumentException> { ObservationKey("") }
    }

    @Test
    fun `ObservationKey 는 공백만 있는 문자열도 거부한다`() {
        shouldThrow<IllegalArgumentException> { ObservationKey("   ") }
    }

    @Test
    fun `같은 값의 ObservationKey 는 구조적으로 같다`() {
        ObservationKey("abc") shouldBe ObservationKey("abc")
    }

    @Test
    fun `다른 값의 ObservationKey 는 다르다`() {
        (ObservationKey("abc") == ObservationKey("xyz")) shouldBe false
    }
}
