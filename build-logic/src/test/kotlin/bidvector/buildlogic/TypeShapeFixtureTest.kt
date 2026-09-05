package bidvector.buildlogic

import bidvector.buildlogic.typeshapefixture.ChainLeaf
import bidvector.buildlogic.typeshapefixture.ChainMiddle
import bidvector.buildlogic.typeshapefixture.ChainRoot
import bidvector.buildlogic.typeshapefixture.CrossModuleDerived
import bidvector.buildlogic.typeshapefixture.DelegatingGreeter
import bidvector.buildlogic.typeshapefixture.FakeGradleTask
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
 *
 * D-7(verifier r1 M-2) · verifier r2 H-1 — 상속 깊이는 **이 test 가 `importClasses`로 명시한
 * 집합이거나 루트 패키지(`bidvector.`) 아래** 안에서만 잰다. 둘 다 아닌 클래스
 * (`java.lang.Enum`·Gradle `DefaultTask`)를 만나면 멈춘다.
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
            FakeGradleTask::class.java,
            // `CrossModuleBase`는 일부러 넣지 않는다 — 다른 모듈의 소유 타입(스캔 밖이지만
            // `bidvector.` 아래)을 흉내내야 한다(verifier r2 H-1).
            CrossModuleDerived::class.java,
        )
    private val ownedTypeNames = classes.map { it.name }.toSet()
    private val rootPackagePrefix = "bidvector"

    private fun shapeOf(cls: Class<*>) = classes.get(cls).toTypeShape(ownedTypeNames, rootPackagePrefix)

    @Test
    fun `상속 체인의 깊이가 단계마다 늘어난다`() {
        val root = shapeOf(ChainRoot::class.java).inheritanceDepth
        val middle = shapeOf(ChainMiddle::class.java).inheritanceDepth
        val leaf = shapeOf(ChainLeaf::class.java).inheritanceDepth
        assertTrue(middle > root, "root=$root middle=$middle")
        assertTrue(leaf > middle, "middle=$middle leaf=$leaf")
    }

    @Test
    fun `소유 타입 두 단계 체인은 depth 가 1이다 (D-7)`() {
        // ChainMiddle -> ChainRoot(소유, 계수) -> Object(집합 밖, 정지) = 1.
        assertEquals(1, shapeOf(ChainMiddle::class.java).inheritanceDepth)
    }

    @Test
    fun `인터페이스 둘을 구현하면 interfaceCount 가 2다`() {
        assertEquals(2, shapeOf(TwoInterfaces::class.java).interfaceCount)
    }

    @Test
    fun `enum class 는 java-lang-Enum 이 집합 밖이라 depth 가 0이다 (D-7 정제, 이전 정의는 2)`() {
        assertEquals(0, shapeOf(Status::class.java).inheritanceDepth)
    }

    @Test
    fun `Gradle DefaultTask 를 확장해도 집합 밖이라 depth 가 0이다 (D-7, build-logic 자신의 형태)`() {
        assertEquals(0, shapeOf(FakeGradleTask::class.java).inheritanceDepth)
    }

    @Test
    fun `스캔 집합 밖이라도 루트 패키지 아래면 상속 깊이에 잡힌다 (verifier r2 H-1)`() {
        // CrossModuleDerived 의 상위 CrossModuleBase 는 importClasses 목록에 없다(스캔 밖) —
        // 다른 모듈이 소유한 타입을 흉내낸다. 그래도 `bidvector.` 아래라 계수를 계속해야 한다.
        assertEquals(1, shapeOf(CrossModuleDerived::class.java).inheritanceDepth)
    }

    @Test
    fun `by 위임도 대상 인터페이스가 인터페이스 수에 잡힌다 (preflight 3 미실측 해소)`() {
        assertEquals(1, shapeOf(DelegatingGreeter::class.java).interfaceCount)
    }

    @Test
    fun `by 위임은 상속 체인을 늘리지 않는다`() {
        // 위임은 합성이지 상속이 아니다 — 인터페이스를 직접 구현한 TwoInterfaces 와 같은
        // depth 여야 한다(둘 다 집합 밖 Object 바로 아래라 0).
        assertEquals(
            shapeOf(TwoInterfaces::class.java).inheritanceDepth,
            shapeOf(DelegatingGreeter::class.java).inheritanceDepth,
        )
    }
}
