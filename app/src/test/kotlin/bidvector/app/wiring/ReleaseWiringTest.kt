package bidvector.app.wiring

import bidvector.adapters.persistence.ReleaseIdentifier
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext

/**
 * M9/OPS-10 — 「**값이 없으면 기동이 실패한다**」를 재는 음성 대조(verifier M-2 · cr OQ-2).
 *
 * 다른 배선 test 들은 `ReleaseIdentifier` 빈을 **직접 꽂으므로** 이 경로를 지나지 않고, 출하 조립
 * test 들은 속성을 전부 **준다** — 즉 「기본값 없음」이라는 약속을 재는 자리가 어디에도 없었다.
 * 기본값이 되살아나면(옛 `unversioned`) 이 test 가 먼저 붉어진다.
 *
 * **실패의 자리를 특정하지 않는다** — Boot 의 값 객체 바인딩과 `ReleaseIdentifier.require` 중
 * 어느 쪽이 먼저 끊는지는 Boot 판에 따라 다를 수 있고, 약속은 「뜨지 않는다」이므로 그 사실만
 * 단언한다(메시지는 키 이름을 담는지만 본다).
 */
class ReleaseWiringTest {
    @Test
    fun `릴리스 식별자 속성이 없으면 조립이 기동하지 않는다`() {
        val failure = bootFailure()

        failure shouldNotBe null
        failureText(failure) shouldContain "bidvector.release"
    }

    @Test
    fun `빈 문자열도 거부된다 — 값이 있는 척하는 설정이 통과하지 않는다`() {
        val failure = bootFailure("bidvector.release.sha=")

        failure shouldNotBe null
    }

    @Test
    fun `값을 주면 그 값이 빈으로 선다 — 양성 대조`() {
        AnnotationConfigApplicationContext().use { context ->
            TestPropertyValues.of("bidvector.release.sha=wiring-positive-release").applyTo(context)
            context.register(ReleaseWiring::class.java)
            context.refresh()

            context.getBean(ReleaseIdentifier::class.java) shouldBe ReleaseIdentifier("wiring-positive-release")
        }
    }

    private fun bootFailure(vararg properties: String): Throwable? {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*properties).applyTo(context)
        context.register(ReleaseWiring::class.java)
        val failure = runCatching { context.refresh() }.exceptionOrNull()
        runCatching { context.close() }
        return failure
    }

    private fun failureText(failure: Throwable?): String =
        generateSequence(failure) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
}
