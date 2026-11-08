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
    private val policy =
        JarContentPolicy(
            "bidvector/settlement",
            setOf("Anchor.kt"),
            classRoots = listOf("BOOT-INF/classes/"),
            externalClassRoots = listOf("org/springframework/boot/loader/"),
        )
    private val verified = mapOf("bidvector/settlement/Anchor.class" to "hash-a")

    private fun entry(
        name: String,
        digest: String = "hash-a",
        sourceFile: String? = "Anchor.kt",
    ) = JarClassEntry(name, digest, sourceFile)

    private fun JarContentPolicy.violations(
        entries: List<JarClassEntry>,
        verified: Map<String, String>,
    ) = judge(entries, verified).violations

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

    // ---- 배포물 축(`OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT`) ----

    @Test
    fun `bootJar 접두 밑의 그 바이트는 위반이 없다 — 접두를 벗기고 판정한다`() {
        val entries = listOf(entry("BOOT-INF/classes/bidvector/settlement/Anchor.class"))

        assertEquals(emptyList(), policy.violations(entries, verified))
    }

    @Test
    fun `bootJar 접두 밑에 심은 비-검증 클래스는 잡는다 — 접두가 판정을 느슨하게 하지 않는다`() {
        val entries = listOf(entry("BOOT-INF/classes/bidvector/settlement/Sneak.class"))

        assertTrue(policy.violations(entries, verified).single().contains("게이트를 거치지 않은"))
    }

    @Test
    fun `bootJar 접두 밑의 소유 밖 클래스도 잡는다`() {
        val entries = listOf(entry("BOOT-INF/classes/evil/Sneak.class"))

        assertTrue(policy.violations(entries, verified).single().contains("소유 밖"))
    }

    @Test
    fun `launcher 접두는 면제하되 수를 센다 — 그 바이트는 이 저장소의 소스가 아니다`() {
        val verdict =
            policy.judge(
                listOf(
                    entry("org/springframework/boot/loader/launch/JarLauncher.class", sourceFile = null),
                    entry("BOOT-INF/classes/bidvector/settlement/Anchor.class"),
                ),
                verified,
            )

        assertEquals(emptyList(), verdict.violations)
        assertEquals(1, verdict.exempt)
        assertEquals(1, verdict.judged, "면제는 판정 수에 들지 않는다 — 공허 판정을 공허로 세기 위해")
    }

    @Test
    fun `면제 접두 밖의 비-소유 클래스는 면제가 있어도 그대로 위반이다`() {
        val entries = listOf(entry("org/evil/Sneak.class"))

        assertTrue(policy.violations(entries, verified).single().contains("소유 밖"))
    }

    @Test
    fun `면제만 남은 아카이브는 판정 수가 0 이다 — 비-공허 단언의 입력`() {
        val verdict =
            policy.judge(
                listOf(entry("org/springframework/boot/loader/launch/JarLauncher.class", sourceFile = null)),
                verified,
            )

        assertEquals(0, verdict.judged)
        assertEquals(emptyList(), verdict.violations)
    }

    @Test
    fun `아카이브 입력이 없으면 비-공허 단언이 붉는다`() {
        val failure = archiveEmptinessFailure("app", archiveCount = 0, judged = 0)

        assertTrue(failure != null && failure.contains("app"), "$failure")
    }

    @Test
    fun `판정할 class 엔트리가 0 이면 비-공허 단언이 붉는다 — 면제만 남은 아카이브`() {
        val failure = archiveEmptinessFailure("app", archiveCount = 2, judged = 0)

        assertTrue(failure != null, "면제 엔트리만 남은 아카이브도 같은 공허다")
    }

    @Test
    fun `아카이브와 판정 엔트리가 있으면 비-공허 단언은 조용하다`() {
        assertEquals(null, archiveEmptinessFailure("app", archiveCount = 2, judged = 558))
    }
}
