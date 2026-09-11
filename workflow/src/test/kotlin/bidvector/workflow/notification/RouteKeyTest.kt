package bidvector.workflow.notification

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

private const val SLUG_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_-"
private const val DIGIT_ALPHABET = "0123456789"

/**
 * scope.md ③, 설계 검토 (1) — `RouteKey`는 허용 모양 하나만 받는다(`^[a-z][a-z0-9_-]{0,80}$`).
 * 숫자로 시작·`:`·대문자·`@`·공백·81자↑는 전부 생성 실패다 — 무엇이 위험한지 몰라도
 * 슬러그가 아니면 못 들어온다(우회 (7)).
 */
class RouteKeyTest {
    @Test
    fun `슬러그 모양은 생성에 성공하고 값을 그대로 보존한다`() {
        val key = RouteKey("owner-42_ops")

        key.value shouldBe "owner-42_ops"
    }

    @Test
    fun `숫자로 시작하면 거부된다 — 원문 chat id 모양`() {
        shouldThrow<IllegalArgumentException> { RouteKey("42-owner") }
    }

    @Test
    fun `콜론을 담으면 거부된다`() {
        shouldThrow<IllegalArgumentException> { RouteKey("chat:12345") }
    }

    @Test
    fun `대문자를 담으면 거부된다`() {
        shouldThrow<IllegalArgumentException> { RouteKey("Owner") }
    }

    @Test
    fun `골뱅이를 담으면 거부된다 — 메일 주소 모양`() {
        shouldThrow<IllegalArgumentException> { RouteKey("ops@example.com") }
    }

    @Test
    fun `공백을 담으면 거부된다`() {
        shouldThrow<IllegalArgumentException> { RouteKey("ops team") }
    }

    @Test
    fun `빈 문자열은 거부된다`() {
        shouldThrow<IllegalArgumentException> { RouteKey("") }
    }

    @Test
    fun `81자는 상한 경계값으로 통과한다`() {
        RouteKey("a" + "b".repeat(80))
    }

    @Test
    fun `82자는 상한을 넘어 거부된다`() {
        shouldThrow<IllegalArgumentException> { RouteKey("a" + "b".repeat(81)) }
    }

    @Test
    fun `소문자로 시작하고 슬러그 알파벳으로만 구성된 임의 문자열은 항상 생성에 성공한다 — property`() {
        runBlocking {
            checkAll(Arb.string(0..80, SLUG_ALPHABET)) { tail ->
                RouteKey("a$tail")
            }
        }
    }

    @Test
    fun `숫자만으로 구성된 임의 문자열은 항상 거부된다 — property`() {
        runBlocking {
            checkAll(Arb.string(1..15, DIGIT_ALPHABET)) { digits ->
                shouldThrow<IllegalArgumentException> { RouteKey(digits) }
            }
        }
    }

    // ---- PR #5 게이트 시정(privacy-gate) — 거부 메시지가 원문을 싣지 않는다 ----
    // 이 타입의 KDoc 은 「원문 식별자(채팅id·봇비밀값·메일주소)는 이 shape 를 통과하지
    // 못한다」고 방어를 선언한다 — 그 값이 거부 경로의 예외 메시지로 새면 그 선언이
    // 무효가 된다. 아래는 그 세 가지 원문 모양 각각으로 실제 유출이 없음을 단언한다.

    @Test
    fun `숫자로 시작하는 원문(채팅id 모양)은 예외 메시지에 나타나지 않는다`() {
        val chatId = "820394857123"
        val exception = shouldThrow<IllegalArgumentException> { RouteKey(chatId) }
        exception.message.orEmpty() shouldNotContain chatId
    }

    @Test
    fun `콜론을 담은 원문(봇 비밀값 모양)은 예외 메시지에 나타나지 않는다`() {
        val botSecret = "bot:AAHx9secretTokenValue12345"
        val exception = shouldThrow<IllegalArgumentException> { RouteKey(botSecret) }
        exception.message.orEmpty() shouldNotContain botSecret
    }

    @Test
    fun `골뱅이를 담은 원문(메일 주소 모양)은 예외 메시지에 나타나지 않는다`() {
        val email = "ops-lead@example.com"
        val exception = shouldThrow<IllegalArgumentException> { RouteKey(email) }
        exception.message.orEmpty() shouldNotContain email
    }
}
