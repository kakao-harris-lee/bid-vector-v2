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

    @Test
    fun `선언하지 않은 중첩 jar 는 위반이다 — 런타임에 실리면서 판정되지 않던 자리`() {
        val violations =
            nestedArchiveViolations(
                "app",
                mapOf("app.jar" to listOf("adapters.jar", "sneak.jar")),
                setOf("adapters.jar"),
            )

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("sneak.jar"), "$violations")
        assertTrue(violations.single().contains("app.jar"), "어느 아카이브인지가 판정에 들어 있어야 한다")
    }

    @Test
    fun `runtime classpath 가 해석한 jar 만 품으면 위반이 없다`() {
        assertEquals(
            emptyList<String>(),
            nestedArchiveViolations(
                "app",
                mapOf("app.jar" to listOf("adapters.jar", "workflow.jar")),
                setOf("adapters.jar", "workflow.jar", "kotlin-reflect-2.4.10.jar"),
            ),
        )
    }

    @Test
    fun `중첩 jar 가 없으면 위반이 없다 — 열 모듈 중 아홉의 정상 상태`() {
        assertEquals(
            emptyList<String>(),
            nestedArchiveViolations("workflow", mapOf("workflow.jar" to emptyList()), setOf("a.jar")),
        )
    }

    @Test
    fun `runtime 집합이 비면 중첩 jar 전부가 위반이다`() {
        val violations =
            nestedArchiveViolations("app", mapOf("app.jar" to listOf("a.jar", "b.jar")), emptySet())

        assertEquals(2, violations.size, "$violations")
    }

    @Test
    fun `아카이브마다 따로 센다 — 어디에 심었는지가 보여야 한다`() {
        val violations =
            nestedArchiveViolations(
                "app",
                mapOf("app.jar" to listOf("sneak.jar"), "app-plain.jar" to listOf("sneak.jar")),
                emptySet(),
            )

        assertEquals(2, violations.size, "$violations")
        assertTrue(violations.any { it.contains("app-plain.jar") }, "$violations")
    }

    @Test
    fun `면제 접두에 걸리는 중첩 jar 는 위반이 아니다 — Boot 가 넣는 자기 도구 jar`() {
        // 축을 켜자마자 실측으로 나온 자리다: Boot 플러그인이 `spring-boot-jarmode-tools-<버전>.jar` 를
        // BOOT-INF/lib 에 넣는데 그 jar 는 모듈의 runtime classpath 에 없다.
        assertEquals(
            emptyList<String>(),
            nestedArchiveViolations(
                "app",
                mapOf("app.jar" to listOf("spring-boot-jarmode-tools-4.1.1.jar")),
                emptySet(),
                listOf("spring-boot-jarmode-tools"),
            ),
        )
    }

    @Test
    fun `면제는 접두라 버전이 올라도 같은 jar 를 면제한다`() {
        assertEquals(
            emptyList<String>(),
            nestedArchiveViolations(
                "app",
                mapOf("app.jar" to listOf("spring-boot-jarmode-tools-9.9.9.jar")),
                emptySet(),
                listOf("spring-boot-jarmode-tools"),
            ),
        )
    }

    @Test
    fun `면제 접두가 다른 jar 를 덮지 않는다`() {
        val violations =
            nestedArchiveViolations(
                "app",
                mapOf("app.jar" to listOf("sneak.jar", "spring-boot-jarmode-tools-4.1.1.jar")),
                emptySet(),
                listOf("spring-boot-jarmode-tools"),
            )

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("sneak.jar"), "$violations")
    }
}
