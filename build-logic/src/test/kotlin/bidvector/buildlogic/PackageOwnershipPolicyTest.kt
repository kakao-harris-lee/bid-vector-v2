package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Codex 3차 #1 이 든 두 우회를 고정한다. 둘 다 **클래스가 자기 패키지를 스스로 선언한다**는
 * 전제를 깨는 형태이고, 기존 위반 fixture 는 전부 자기 세그먼트를 옳게 선언했으므로
 * 그 전제를 시험하지 않았다.
 */
class PackageOwnershipPolicyTest {
    private val policy = PackageOwnershipPolicy("bidvector")

    @Test
    fun `선언 없는 패키지를 잡는다`() {
        val violations = policy.violations("decision", listOf(""))
        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("기본 패키지"), violations.single())
    }

    @Test
    fun `남의 세그먼트 참칭을 잡는다`() {
        val violations = policy.violations("decision", listOf("bidvector.app.sneaky"))
        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("bidvector.app.sneaky"), violations.single())
    }

    @Test
    fun `자기 패키지와 그 하위는 통과한다`() {
        assertTrue(policy.violations("decision", listOf("bidvector.decision", "bidvector.decision.floor")).isEmpty())
    }

    @Test
    fun `하이픈 모듈 이름은 세그먼트에서 하이픈을 뺀다`() {
        assertAll(
            { assertEquals("bidvector.sharedkernel", policy.ownedPackage("shared-kernel")) },
            { assertTrue(policy.violations("shared-kernel", listOf("bidvector.sharedkernel")).isEmpty()) },
            { assertTrue(policy.violations("shared-kernel", listOf("bidvector.shared")).isNotEmpty()) },
        )
    }

    @Test
    fun `접두사가 겹치는 남의 패키지를 통과시키지 않는다`() {
        assertTrue(policy.violations("app", listOf("bidvector.application")).isNotEmpty())
    }
}
