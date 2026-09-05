package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 상속 깊이·인터페이스 수 래칫의 순수 함수 음성·양성 — D-3(증가 금지). [TypeShape] 값만으로
 * 성립하므로 Gradle·바이트코드 없이 테스트가 붙는다. 바이트코드에서 실제로 그 값을 재는
 * 로직([JavaClass.inheritanceDepth]·[JavaClass.interfaceCount])의 정확성은
 * [TypeShapeFixtureTest]가 실제 컴파일된 fixture로 고정한다.
 */
class TypeShapeRatchetPolicyTest {
    private fun policy(
        maxDepth: Int = 2,
        maxInterfaces: Int = 1,
    ) = TypeShapeRatchetPolicy(
        mapOf(
            "ratchet.type.inheritance-depth.max" to maxDepth.toString(),
            "ratchet.type.interfaces.max" to maxInterfaces.toString(),
        ),
    )

    @Test
    fun `baseline 과 정확히 같은 값은 위반이 아니다`() {
        val shapes = listOf(TypeShape("p.C", inheritanceDepth = 2, interfaceCount = 1))
        assertEquals(emptyList(), policy().violations(shapes))
    }

    @Test
    fun `상속 깊이가 baseline 을 넘으면 잡는다`() {
        val shapes = listOf(TypeShape("p.Deep", inheritanceDepth = 3, interfaceCount = 0))
        val violations = policy().violations(shapes)
        assertTrue(violations.single().contains("p.Deep"), violations.toString())
        assertTrue(violations.single().contains("상속 깊이"), violations.toString())
    }

    @Test
    fun `구현 인터페이스 수가 baseline 을 넘으면 잡는다`() {
        val shapes = listOf(TypeShape("p.Wide", inheritanceDepth = 1, interfaceCount = 2))
        val violations = policy().violations(shapes)
        assertTrue(violations.single().contains("p.Wide"), violations.toString())
        assertTrue(violations.single().contains("구현 인터페이스"), violations.toString())
    }

    @Test
    fun `한 타입이 두 축 모두 넘으면 둘 다 잡는다`() {
        val shapes = listOf(TypeShape("p.Both", inheritanceDepth = 5, interfaceCount = 3))
        val violations = policy().violations(shapes)
        assertEquals(2, violations.size, violations.toString())
    }

    @Test
    fun `baseline 을 낮추면 전엔 통과하던 타입도 잡힌다`() {
        val shapes = listOf(TypeShape("p.C", inheritanceDepth = 2, interfaceCount = 1))
        assertTrue(policy(maxDepth = 1).violations(shapes).isNotEmpty())
    }
}
