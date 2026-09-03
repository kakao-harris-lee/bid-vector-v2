package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 아카이브 판정. **패키지 접두를 맞춘 침입이 통과하지 않는다**는 것이 이 판정의 요점이라,
 * 각 단언은 「이름은 맞는데 그 산출물이 아닌」 형태를 하나씩 고정한다.
 *
 * 원산지 층은 **`SourceFile` 이 게이트를 통과한 소스 이름 집합에 드는가**다 — 애노테이션은
 * 소스에 한 줄로 붙어 위조되므로 앵커가 되지 못한다.
 */
class JarContentPolicyTest {
    private val policy = JarContentPolicy("bidvector/settlement", setOf("Anchor.kt"))
    private val verified = mapOf("bidvector/settlement/Anchor.class" to "hash-a")

    private fun entry(
        name: String,
        digest: String = "hash-a",
        sourceFile: String? = "Anchor.kt",
    ) = JarClassEntry(name, digest, sourceFile)

    @Test
    fun `게이트를 통과한 그 바이트면 위반이 없다`() {
        assertEquals(emptyList(), policy.violations(listOf(entry("bidvector/settlement/Anchor.class")), verified))
    }

    @Test
    fun `소유 패키지에 이름을 맞춰도 검증 집합에 없으면 잡는다`() {
        val violations = policy.violations(listOf(entry("bidvector/settlement/Sneak.class")), verified)
        assertTrue(violations.single().contains("게이트를 거치지 않은"), violations.single())
    }

    @Test
    fun `같은 이름이라도 내용이 다르면 잡는다`() {
        val violations =
            policy.violations(listOf(entry("bidvector/settlement/Anchor.class", digest = "hash-b")), verified)
        assertTrue(violations.single().contains("내용이 다르다"), violations.single())
    }

    @Test
    fun `포함 관계를 통과해도 SourceFile 이 게이트 밖이면 잡는다`() {
        val violations =
            policy.violations(listOf(entry("bidvector/settlement/Anchor.class", sourceFile = "Sneak.java")), verified)
        assertTrue(violations.single().contains("Sneak.java"), violations.single())
    }

    @Test
    fun `SourceFile 이 아예 없으면 잡는다`() {
        val violations =
            policy.violations(listOf(entry("bidvector/settlement/Anchor.class", sourceFile = null)), verified)
        assertTrue(violations.single().contains("SourceFile 이 없다"), violations.single())
    }

    @Test
    fun `소유 밖 클래스는 그 사유로 잡는다`() {
        val violations = policy.violations(listOf(entry("evil/Sneak.class")), verified)
        assertTrue(violations.single().contains("소유 밖"), violations.single())
    }
}
