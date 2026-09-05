package bidvector.buildlogic

import bidvector.buildlogic.typeshapefixture.ChainLeaf
import bidvector.buildlogic.typeshapefixture.ChainMiddle
import bidvector.buildlogic.typeshapefixture.ChainRoot
import bidvector.buildlogic.typeshapefixture.DelegatingGreeter
import bidvector.buildlogic.typeshapefixture.Status
import bidvector.buildlogic.typeshapefixture.TwoInterfaces
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `JavaClass.inheritanceDepth()`·`interfaceCount()`(`TypeShape.kt`)가 **실제 컴파일된
 * Kotlin 바이트코드**에서 옳게 재는지 고정한다 — preflight §3의 미실측(`by` 위임)을 닫고,
 * `qualityBaseline`과 `typeShapeGate`가 같은 함수를 쓰므로 이 test 가 둘 모두를 검증한다.
 */
class TypeShapeFixtureTest {
    private val classes =
        ClassFileImporter().importClasses(
            ChainRoot::class.java,
            ChainMiddle::class.java,
            ChainLeaf::class.java,
            TwoInterfaces::class.java,
            Status::class.java,
            DelegatingGreeter::class.java,
        )

    private fun shapeOf(cls: Class<*>) = classes.get(cls).toTypeShape()

    @Test
    fun `상속 체인의 깊이가 단계마다 늘어난다`() {
        val root = shapeOf(ChainRoot::class.java).inheritanceDepth
        val middle = shapeOf(ChainMiddle::class.java).inheritanceDepth
        val leaf = shapeOf(ChainLeaf::class.java).inheritanceDepth
        assertTrue(middle > root, "root=$root middle=$middle")
        assertTrue(leaf > middle, "middle=$middle leaf=$leaf")
    }

    @Test
    fun `인터페이스 둘을 구현하면 interfaceCount 가 2다`() {
        assertEquals(2, shapeOf(TwoInterfaces::class.java).interfaceCount)
    }

    @Test
    fun `enum class 는 java-lang-Enum 상속으로 depth 가 2다`() {
        assertEquals(2, shapeOf(Status::class.java).inheritanceDepth)
    }

    @Test
    fun `by 위임도 대상 인터페이스가 인터페이스 수에 잡힌다 (preflight 3 미실측 해소)`() {
        assertEquals(1, shapeOf(DelegatingGreeter::class.java).interfaceCount)
    }

    @Test
    fun `by 위임은 상속 체인을 늘리지 않는다`() {
        // 위임은 합성이지 상속이 아니다 — 인터페이스를 직접 구현한 TwoInterfaces 와 같은
        // depth 여야 한다(둘 다 Any 바로 아래).
        assertEquals(
            shapeOf(TwoInterfaces::class.java).inheritanceDepth,
            shapeOf(DelegatingGreeter::class.java).inheritanceDepth,
        )
    }
}
