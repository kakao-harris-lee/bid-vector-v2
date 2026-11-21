package bidvector.app

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.Instant

private val FIXED: Instant = Instant.parse("2026-11-21T00:00:00Z")

/**
 * 출하 상한의 **값과 배선**(M15/15B 수정 라운드 2 — verifier r1 M-r1-1 · cr r1 N-7).
 *
 * 앞 판에는 이 자리가 없었다. `HttpTestApplication` 이 자기 상수로 [bidvector.app.http.SignUpThrottle]
 * 을 따로 만들어 꽂으므로, **출하 빈과 설정 타입을 지나는 test 가 하나도 없었다** — verifier 가
 * `attemptsPerWindow = properties.attemptsPerHour` 를 `Int.MAX_VALUE` 로 바꾸는 변이를 심었더니
 * `:app:test` 1106 건이 그대로 초록이었다(출하 조립에서 상한이 사라지는데 아무도 붉지 않았다).
 *
 * 앞 판에서 그 수를 잠그던 것은 `SignUpPolicyDataTest` 의 「출하 정책이 계약의 값으로 해소된다」
 * 였는데, 수를 정책에서 설정으로 옮기면서 그 단언이 **대체 없이** 사라졌다. 이 class 가 그 자리다.
 *
 * 재는 것은 셋이다: ① 설정 타입의 기본값과 가드 ② 설정 키 이름 ③ **출하 빈 메서드가 그 값을
 * 상한으로 흘리는가**(N+1 번째 거부). ③ 이 그 변이를 죽인다.
 */
class SignUpThrottleWiringTest {
    @Test
    fun `설정 기본값이 계약 갱신의 값이다 — 시간당 30 · 창 1시간 · 추적 주소 10000`() {
        val properties = SignUpThrottleProperties()

        properties.attemptsPerHour shouldBe 30
        properties.trackedAddressLimit shouldBe 10_000
        properties.window shouldBe Duration.ofHours(1)
    }

    /** 기본값 오타(0·음수)가 조용히 나가지 않는다 — 설정 객체가 서지 않으면 기동이 실패한다. */
    @Test
    fun `성립하지 않는 설정은 객체가 서지 않는다`() {
        shouldThrow<IllegalArgumentException> { SignUpThrottleProperties(attemptsPerHour = 0) }
        shouldThrow<IllegalArgumentException> { SignUpThrottleProperties(trackedAddressLimit = 0) }
    }

    /**
     * 키 이름은 **운영자가 쓰는 문자열**이다 — 바뀌면 운영 설정이 조용히 무시된다(바인딩은 없는
     * 키에 아무 말도 하지 않는다). 그래서 접두를 애너테이션에서 직접 읽어 못박는다.
     */
    @Test
    fun `설정 접두가 bidvector signup 이다`() {
        val annotation = SignUpThrottleProperties::class.java.getAnnotation(ConfigurationProperties::class.java)

        requireNotNull(annotation) { "설정 타입에 @ConfigurationProperties 가 없다" }.prefix shouldBe "bidvector.signup"
    }

    /**
     * **배선이 상한을 흘린다**(verifier r1 M-r1-1 의 변이가 죽는 자리). 출하 빈 메서드를 그대로
     * 불러 상한만큼 받고 그 다음이 거부되는지 본다 — 배선이 설정 값을 쓰지 않으면(상수·
     * `Int.MAX_VALUE`) 그 거부가 서지 않아 이 단언이 붉는다.
     */
    @Test
    fun `출하 빈이 설정의 상한을 그대로 쓴다 — N 번째까지 받고 N+1 번째를 거부한다`() {
        val throttle = BidVectorApplication().signUpThrottle({ FIXED }, SignUpThrottleProperties(attemptsPerHour = 3))

        repeat(3) { throttle.admit("10.0.0.1") shouldBe true }
        throttle.admit("10.0.0.1") shouldBe false
    }

    /** 기본값으로 조립해도 같은 성질이다 — 30 번째까지 받고 31 번째를 거부한다. */
    @Test
    fun `기본 설정으로 조립한 출하 빈은 31 번째를 거부한다`() {
        val throttle = BidVectorApplication().signUpThrottle({ FIXED }, SignUpThrottleProperties())

        repeat(30) { throttle.admit("10.0.0.2") shouldBe true }
        throttle.admit("10.0.0.2") shouldBe false
    }
}
