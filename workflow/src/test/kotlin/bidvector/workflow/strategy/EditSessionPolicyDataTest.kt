package bidvector.workflow.strategy

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * verifier M-2 — 우회 (4)(timeout 을 0/음수/무한으로)의 유일한 차단인 두 `require` 를
 * 직접 단언한다. `ExtractionPolicyDataTest`(M3/3C verifier r2 N-1)와 같은 계보 —
 * `beginSession`/`expireIfDue` 위 test 는 정상 범위 값만 써 이 불변식을 「간접 확인」
 * 한다고 적혔지만 실측상 간접 확인도 성립하지 않았다(두 `require` 를 `require(true)` 로
 * 바꿔도 `:workflow:test` exit 0).
 */
class EditSessionPolicyDataTest {
    @Test
    fun `timeoutWindow 가 0 이면 생성이 실패한다`() {
        shouldThrow<IllegalArgumentException> {
            EditSessionPolicyData(Duration.ZERO)
        }
    }

    @Test
    fun `timeoutWindow 가 음수면 생성이 실패한다`() {
        shouldThrow<IllegalArgumentException> {
            EditSessionPolicyData(Duration.ofMinutes(-1))
        }
    }

    @Test
    fun `timeoutWindow 가 상한(24시간)을 넘으면 생성이 실패한다`() {
        shouldThrow<IllegalArgumentException> {
            EditSessionPolicyData(Duration.ofHours(24).plusSeconds(1))
        }
    }

    @Test
    fun `timeoutWindow 가 상한(24시간)과 같으면 생성이 성립한다 — 경계는 포함`() {
        EditSessionPolicyData(Duration.ofHours(24))
    }
}
