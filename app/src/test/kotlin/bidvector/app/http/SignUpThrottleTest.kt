package bidvector.app.http

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Instant

private val START: Instant = Instant.parse("2026-11-21T00:00:00Z")

/**
 * 남용 상한(M15/15B)의 **순수 측정** — HTTP 없이 계수·창·주소 분리·거부 로그만 본다. endpoint 쪽의
 * 429 매핑과 「상한이 KDF 앞에 선다」는 `AccountSignUpEndpointTest` 가 잰다.
 */
class SignUpThrottleTest {
    private val clock = FixedClock(START)
    private val lines = mutableListOf<String>()
    private val throttle =
        SignUpThrottle(
            clock = clock,
            attemptsPerWindow = TEST_SIGN_UP_ATTEMPTS,
            window = TEST_SIGN_UP_WINDOW,
            trackedAddressLimit = TEST_TRACKED_ADDRESS_LIMIT,
            log = SignUpThrottleLog { lines += it },
        )

    @Test
    fun `상한까지는 받고 그 다음부터 거부한다`() {
        repeat(TEST_SIGN_UP_ATTEMPTS) { throttle.admit("10.0.0.1") shouldBe true }

        throttle.admit("10.0.0.1") shouldBe false
        throttle.admit("10.0.0.1") shouldBe false
    }

    @Test
    fun `다른 주소는 따로 센다 — 한 주소가 다 쓴다고 전부 막히지 않는다`() {
        repeat(TEST_SIGN_UP_ATTEMPTS) { throttle.admit("10.0.0.1") }

        throttle.admit("10.0.0.2") shouldBe true
    }

    @Test
    fun `창이 지나면 다시 열린다 — 창 안의 기록만 센다`() {
        repeat(TEST_SIGN_UP_ATTEMPTS) { throttle.admit("10.0.0.1") }
        throttle.admit("10.0.0.1") shouldBe false

        clock.advance(TEST_SIGN_UP_WINDOW.toMillis() + 1)

        throttle.admit("10.0.0.1") shouldBe true
    }

    /**
     * 경계의 **정확한 자리** — 창 길이에서 한 칸 모자라면 아직 막혀 있고, 창 길이 그대로 지나면
     * 열린다(술어가 `!at.isAfter(now - window)` 라 **딱 창 길이 된 기록은 잊는다**). 두 쪽을 함께
     * 재는 이유는 경계가 한 칸 밀리는 변경이 한쪽 단언만으로는 조용하기 때문이다.
     */
    @Test
    fun `창의 경계는 딱 창 길이에서 열린다 — 한 칸 전까지는 막혀 있다`() {
        repeat(TEST_SIGN_UP_ATTEMPTS) { throttle.admit("10.0.0.1") }

        clock.advance(TEST_SIGN_UP_WINDOW.toMillis() - 1)
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
        repeat(TEST_SIGN_UP_ATTEMPTS) { throttle.admit("10.0.0.1") }
        clock.advance(TEST_SIGN_UP_WINDOW.toMillis() / 2)
        repeat(5) { throttle.admit("10.0.0.1") shouldBe false }

        clock.advance(TEST_SIGN_UP_WINDOW.toMillis() / 2)

        throttle.admit("10.0.0.1") shouldBe true
    }

    /**
     * code-review r1 H-1 — 거부가 **아무 신호도 남기지 않으면** 운영자는 「가입이 안 된다」가 전역
     * 잠금인지 고장인지 구별할 수 없다. 그 줄이 서는 것과, 거기에 **식별 값이 없는 것**을 함께 잰다.
     */
    @Test
    fun `거부는 로그 한 줄을 남기고 그 줄에 주소가 없다`() {
        repeat(TEST_SIGN_UP_ATTEMPTS) { throttle.admit("10.0.0.7") }
        lines.shouldBeEmptyList()

        throttle.admit("10.0.0.7") shouldBe false

        lines.size shouldBe 1
        lines.single() shouldContain "limit=$TEST_SIGN_UP_ATTEMPTS"
        lines.single() shouldContain "tracked-addresses="
        lines.single() shouldNotContain "10.0.0.7"
    }

    /**
     * code-review r1 LOW — 창 안에 시도를 가진 주소가 상한을 넘으면 **가장 오래 전에 처음 본
     * 주소부터 버린다.** 오늘은 프록시 뒤라 키가 하나뿐이지만, 키 축이 IP 로 바뀌는 날 이 상한이
     * 없으면 맵이 창 길이만큼 자란다.
     */
    @Test
    fun `추적 주소가 상한을 넘으면 가장 오래된 주소부터 버린다`() {
        val bounded =
            SignUpThrottle(
                clock = clock,
                attemptsPerWindow = TEST_SIGN_UP_ATTEMPTS,
                window = TEST_SIGN_UP_WINDOW,
                trackedAddressLimit = 2,
                log = SignUpThrottleLog { lines += it },
            )
        repeat(TEST_SIGN_UP_ATTEMPTS) { bounded.admit("first") }
        bounded.admit("first") shouldBe false

        // 둘째·셋째 주소가 들어오면 맨 처음 주소의 기록이 밀려난다.
        bounded.admit("second") shouldBe true
        bounded.admit("third") shouldBe true

        bounded.admit("first") shouldBe true
    }

    private fun List<String>.shouldBeEmptyList() {
        size shouldBe 0
    }
}
