package bidvector.app.http

import io.kotest.matchers.collections.shouldBeEmpty
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
        lines.shouldBeEmpty()

        throttle.admit("10.0.0.7") shouldBe false

        lines.size shouldBe 1
        lines.single() shouldContain "cause=window-limit"
        lines.single() shouldContain "limit=$TEST_SIGN_UP_ATTEMPTS"
        lines.single() shouldContain "tracked-addresses="
        lines.single() shouldNotContain "10.0.0.7"
    }

    /**
     * cr r1 N-3 — **기억이 모자라면 거부한다**(fail-closed). 앞 판은 상한을 넘으면 가장 오래 전에
     * 처음 본 주소를 버렸고, 그 축출이 ⓐ 상한에 걸린 주소의 창을 **지워 풀어 주고** ⓑ 창 안에서
     * 계속 시도하는 주소를 **1순위로** 버렸다(조용한 주소가 더 오래 살았다). 둘 다 상한이 지키려는
     * 방향의 반대다.
     *
     * 지금은 **이미 세고 있는 주소가 자기 창을 잃지 않고**, 모르는 주소가 거부된다.
     */
    @Test
    fun `기억이 찼을 때 상한에 걸린 주소의 창은 지워지지 않는다`() {
        val own = mutableListOf<String>()
        val bounded = throttleWith(trackedAddressLimit = 2, into = own)
        repeat(TEST_SIGN_UP_ATTEMPTS) { bounded.admit("persistent") }
        bounded.admit("persistent") shouldBe false

        // 둘째 주소가 자리를 채우고, 셋째(모르는 주소)는 기억이 없어 거부된다.
        bounded.admit("second") shouldBe true
        bounded.admit("third") shouldBe false

        // 앞 판은 여기서 `true` 였다 — 축출이 창을 지웠기 때문이다.
        bounded.admit("persistent") shouldBe false
        own.last() shouldContain "cause=window-limit"
    }

    /** 모르는 주소의 거부는 **다른 사유**로 적힌다 — 운영자가 둘을 가를 수 있어야 한다. */
    @Test
    fun `기억이 찬 뒤의 새 주소 거부는 사유가 다르다`() {
        val own = mutableListOf<String>()
        val bounded = throttleWith(trackedAddressLimit = 1, into = own)
        bounded.admit("known") shouldBe true

        bounded.admit("stranger") shouldBe false

        own.single() shouldContain "cause=tracked-address-capacity"
        own.single() shouldNotContain "stranger"
    }

    /** 창이 지나 기억이 비면 새 주소가 다시 들어온다 — 거부가 영구하지 않다. */
    @Test
    fun `창이 지나면 기억이 비고 새 주소가 다시 들어온다`() {
        val bounded = throttleWith(trackedAddressLimit = 1, into = mutableListOf())
        bounded.admit("known") shouldBe true
        bounded.admit("stranger") shouldBe false

        clock.advance(TEST_SIGN_UP_WINDOW.toMillis() + 1)

        bounded.admit("stranger") shouldBe true
    }

    private fun throttleWith(
        trackedAddressLimit: Int,
        into: MutableList<String>,
    ): SignUpThrottle =
        SignUpThrottle(
            clock = clock,
            attemptsPerWindow = TEST_SIGN_UP_ATTEMPTS,
            window = TEST_SIGN_UP_WINDOW,
            trackedAddressLimit = trackedAddressLimit,
            log = SignUpThrottleLog { into += it },
        )
}
