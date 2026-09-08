package bidvector.adapters.extraction

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * verifier r2 N-1 — F-2 가 세운 불변식(`httpRequestTimeout > callTimeout`)의 회귀 방지.
 * 이 `require` 하나가 F-2(같은 시한 경합) 해소의 유일한 메커니즘이었는데, 그것을 재는
 * test 가 없어 통째로 지워도(변이 M3, verifier r2 실측) extraction 전건이 통과했다.
 */
class ExtractionPolicyDataTest {
    @Test
    fun `httpRequestTimeout 이 callTimeout 과 같으면 생성이 실패한다`() {
        shouldThrow<IllegalArgumentException> {
            testExtractionPolicy(callTimeout = Duration.ofMillis(300), httpRequestTimeout = Duration.ofMillis(300))
        }
    }

    @Test
    fun `httpRequestTimeout 이 callTimeout 보다 작으면 생성이 실패한다`() {
        shouldThrow<IllegalArgumentException> {
            testExtractionPolicy(callTimeout = Duration.ofMillis(300), httpRequestTimeout = Duration.ofMillis(100))
        }
    }
}
