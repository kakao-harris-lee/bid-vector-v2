package bidvector.app.http

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

private val START: Instant = Instant.parse("2026-11-21T00:00:00Z")

/**
 * 남용 상한(M15/15B)의 **순수 측정** — HTTP 없이 계수·창·주소 분리만 본다. endpoint 쪽의 429 매핑은
 * `AccountSignUpEndpointTest` 가 잰다.
 */
class SignUpThrottleTest {
    private val clock = FixedClock(START)
    private val throttle = SignUpThrottle(clock, TEST_SIGN_UP_POLICY)

    @Test
    fun `상한까지는 받고 그 다음부터 거부한다`() {
        repeat(TEST_SIGN_UP_POLICY.signUpsPerAddress) { throttle.admit("10.0.0.1") shouldBe true }

        throttle.admit("10.0.0.1") shouldBe false
        throttle.admit("10.0.0.1") shouldBe false
    }

    @Test
    fun `다른 주소는 따로 센다 — 한 주소가 다 쓴다고 전부 막히지 않는다`() {
        repeat(TEST_SIGN_UP_POLICY.signUpsPerAddress) { throttle.admit("10.0.0.1") }

        throttle.admit("10.0.0.2") shouldBe true
    }

    @Test
    fun `창이 지나면 다시 열린다 — 창 안의 기록만 센다`() {
        repeat(TEST_SIGN_UP_POLICY.signUpsPerAddress) { throttle.admit("10.0.0.1") }
        throttle.admit("10.0.0.1") shouldBe false

        clock.advance(TEST_SIGN_UP_POLICY.signUpWindow.toMillis() + 1)

        throttle.admit("10.0.0.1") shouldBe true
    }

    /**
     * 경계의 **정확한 자리** — 창 길이에서 한 칸 모자라면 아직 막혀 있고, 창 길이 그대로 지나면
     * 열린다(술어가 `!at.isAfter(now - window)` 라 **딱 창 길이 된 기록은 잊는다**). 두 쪽을 함께
     * 재는 이유는 경계가 한 칸 밀리는 변경이 한쪽 단언만으로는 조용하기 때문이다.
     */
    @Test
    fun `창의 경계는 딱 창 길이에서 열린다 — 한 칸 전까지는 막혀 있다`() {
        repeat(TEST_SIGN_UP_POLICY.signUpsPerAddress) { throttle.admit("10.0.0.1") }

        clock.advance(TEST_SIGN_UP_POLICY.signUpWindow.toMillis() - 1)
        throttle.admit("10.0.0.1") shouldBe false

        clock.advance(1)
        throttle.admit("10.0.0.1") shouldBe true
    }

    /**
     * 거부는 **창을 밀지 않는다** — 밀면 상한이 「창 동안 조용히 있어야 풀리는」 다른 규칙이 되고,
     * 계약은 그것을 말하지 않았다. 거부를 쌓은 뒤에도 첫 기록 기준으로 창이 지나면 열린다.
     */
    @Test
    fun `거부는 창을 밀지 않는다`() {
        repeat(TEST_SIGN_UP_POLICY.signUpsPerAddress) { throttle.admit("10.0.0.1") }
        clock.advance(TEST_SIGN_UP_POLICY.signUpWindow.toMillis() / 2)
        repeat(5) { throttle.admit("10.0.0.1") shouldBe false }

        clock.advance(TEST_SIGN_UP_POLICY.signUpWindow.toMillis() / 2)

        throttle.admit("10.0.0.1") shouldBe true
    }
}
