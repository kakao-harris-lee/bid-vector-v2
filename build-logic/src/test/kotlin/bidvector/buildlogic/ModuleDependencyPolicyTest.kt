package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 경계 판정의 **음성** 쪽. Codex #2 가 지적한 구멍이 여기 고정된다 — 참조 없이 **선언만** 된
 * 업무 모듈 사이의 project 의존은 architecture test 가 볼 수 없으므로 이 판정이 유일한 그물이다.
 *
 * 실제 정책 파일을 읽는다. 규칙을 테스트 안에 다시 적으면 정책이 바뀔 때 테스트가 낡은 규칙을
 * 지키게 되고, 그러면 통과가 아무것도 뜻하지 않는다.
 */
class ModuleDependencyPolicyTest {
    private val policy = ModuleDependencyPolicy.load(File(REAL_POLICY))

    private fun projectDeps(vararg paths: String) = ResolvedDependencies(emptyMap(), paths.toSet())

    @Test
    fun `업무 모듈은 서로를 참조할 수 없다`() {
        val violations = policy.violations("qualification", projectDeps(":decision"))
        assertEquals(1, violations.size, "위반 하나가 나와야 한다: $violations")
        assertTrue(violations.single().contains(":decision"), violations.single())
    }

    @Test
    fun `공유 domain 모듈은 참조할 수 있다`() {
        assertTrue(policy.violations("qualification", projectDeps(":shared-kernel")).isEmpty())
    }

    @Test
    fun `shared-kernel 은 어떤 모듈도 참조할 수 없다`() {
        assertAll(
            { assertTrue(policy.allowedProjectDependencies("shared-kernel").isEmpty()) },
            { assertTrue(policy.violations("shared-kernel", projectDeps(":procurement")).isNotEmpty()) },
        )
    }

    @Test
    fun `application 은 domain 을 참조하고 adapters 를 참조하지 못한다`() {
        assertAll(
            { assertTrue(policy.violations("workflow", projectDeps(":decision", ":shared-kernel")).isEmpty()) },
            { assertTrue(policy.violations("workflow", projectDeps(":adapters")).isNotEmpty()) },
            { assertTrue(policy.violations("workflow", projectDeps(":app")).isNotEmpty()) },
        )
    }

    @Test
    fun `아래 층은 위 층을 참조하지 못한다`() {
        assertAll(
            { assertTrue(policy.violations("adapters", projectDeps(":workflow", ":decision")).isEmpty()) },
            { assertTrue(policy.violations("adapters", projectDeps(":app")).isNotEmpty()) },
            { assertTrue(policy.violations("app", projectDeps(":adapters", ":workflow", ":settlement")).isEmpty()) },
        )
    }

    @Test
    fun `domain 모듈만 금지 group 판정을 받는다`() {
        val spring = ResolvedDependencies(mapOf("org.springframework:spring-core" to "7.0.9"), emptySet())
        assertAll(
            { assertTrue(policy.violations("decision", spring).isNotEmpty()) },
            { assertTrue(policy.violations("app", spring).isEmpty()) },
        )
    }

    @Test
    fun `금지 목록이 가족마다 여러 좌표를 든다`() {
        val families = listOf("org.springframework", "jakarta.persistence", "com.fasterxml.jackson", "org.apache.kafka")
        assertAll(
            families.map { family ->
                { assertTrue(policy.forbiddenGroups.any { it.startsWith(family) }, "$family 가 없다") }
            },
        )
    }

    private companion object {
        /** 빌드가 절대 경로를 넘긴다 — 작업 디렉터리에 기대지 않는다. */
        val REAL_POLICY: String =
            System.getProperty("bidvector.architecture.policy")
                ?: error("시스템 속성 'bidvector.architecture.policy' 가 없다 — 빌드가 넘긴다")
    }
}
