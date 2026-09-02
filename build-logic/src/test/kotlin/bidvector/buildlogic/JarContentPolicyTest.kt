package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 아카이브 판정. **패키지 접두를 맞춘 침입이 통과하지 않는다**는 것이 이 판정의 요점이라,
 * 각 단언은 「이름은 맞는데 그 산출물이 아닌」 형태를 하나씩 고정한다.
 */
class JarContentPolicyTest {
    private val policy = JarContentPolicy("bidvector/settlement")
    private val verified = mapOf("bidvector/settlement/Anchor.class" to "hash-a")

    private fun entry(
        name: String,
        digest: String = "hash-a",
        kotlinOrigin: Boolean = true,
    ) = JarClassEntry(name, digest, kotlinOrigin)

    @Test
    fun `게이트를 통과한 그 바이트면 위반이 없다`() {
        assertEquals(emptyList(), policy.violations(listOf(entry("bidvector/settlement/Anchor.class")), verified))
    }

    @Test
    fun `소유 패키지에 이름을 맞춰도 검증 집합에 없으면 잡는다`() {
        val violations = policy.violations(listOf(entry("bidvector/settlement/Sneak.class")), verified)
        assertEquals(1, violations.size, "실제: $violations")
        assertTrue(violations.single().contains("게이트를 거치지 않은"), violations.single())
    }

    @Test
    fun `같은 이름이라도 내용이 다르면 잡는다`() {
        val violations =
            policy.violations(listOf(entry("bidvector/settlement/Anchor.class", digest = "hash-b")), verified)
        assertTrue(violations.single().contains("내용이 다르다"), violations.single())
    }

    @Test
    fun `검증 집합에 있어도 원산지가 없으면 잡는다`() {
        val violations =
            policy.violations(listOf(entry("bidvector/settlement/Anchor.class", kotlinOrigin = false)), verified)
        assertTrue(violations.single().contains("kotlin.Metadata"), violations.single())
    }

    @Test
    fun `소유 밖 클래스는 그 사유로 잡는다`() {
        val violations = policy.violations(listOf(entry("evil/Sneak.class")), verified)
        assertTrue(violations.single().contains("소유 밖"), violations.single())
    }

    @Test
    fun `원산지 표식을 상수 풀 어디에서든 찾는다`() {
        val marker = "kotlin/Metadata".toByteArray(Charsets.US_ASCII)
        assertTrue((ByteArray(37) + marker + ByteArray(11)).hasKotlinMetadata())
        assertFalse("kotlin/Metadat".toByteArray(Charsets.US_ASCII).hasKotlinMetadata(), "부분 일치는 표식이 아니다")
        assertFalse(ByteArray(3).hasKotlinMetadata(), "표식보다 짧은 입력")
    }
}
