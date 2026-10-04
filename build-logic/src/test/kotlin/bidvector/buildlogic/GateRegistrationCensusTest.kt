package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-6G2g-9 (B-5 다) · D-6G2g-13 ① — 등재 등식의 **순수 코어**. task 배선과 분리해 인라인 입력으로
 * 잰다(`TestShapesTest` 관례).
 *
 * 등식은 **`모집단 ∖ 제외 == 등재`** 이고 양방향이다. 모집단은 소스 파일 이름이 아니라 컴파일된
 * 클래스이고(파일명 술어는 한 파일에 test 클래스가 둘이면 둘째를 놓친다 — 저장소 실례 아홉),
 * 제외는 손으로 적은 이름이 아니라 **build 사실**(Gradle `Test.filter` 의 제외 패턴 · 클래스에 붙은
 * 실행 조건 애노테이션)에서만 나온다(운영자 결정 B-3 (나)).
 *
 * 제외 자신도 등식의 한 변이다 — build 사실이 제외를 **늘리면** 선언과 어긋나 붉어진다. 선언은
 * 제외의 **출처가 아니라 래칫**이다: 선언에만 있는 이름은 아무것도 제외하지 못한다(한 방향으로만 안전).
 */
class GateRegistrationCensusTest {
    // ---- 모집단 — 컴파일된 클래스, 접은 이름 ----

    @Test
    fun `test 메서드를 가진 최상위 클래스만 모집단이다`() {
        val census =
            census(
                facts("p.FooTest", methods = setOf(TEST)),
                facts("p.Helper", methods = emptySet()),
                facts("p.BarKt", methods = emptySet()),
            )

        assertEquals(setOf("p.FooTest"), census.population)
    }

    @Test
    fun `한 파일의 test 클래스 둘은 둘 다 모집단이다 — 파일명 술어의 구멍`() {
        val census =
            census(
                facts("p.SampleListFileTest", methods = setOf(TEST)),
                facts("p.FileSampleListLedgerTest", methods = setOf(TEST)),
            )

        assertEquals(setOf("p.SampleListFileTest", "p.FileSampleListLedgerTest"), census.population)
    }

    @Test
    fun `중첩 클래스의 test 는 바깥 클래스로 접힌다`() {
        val census = census(facts("p.OuterTest\$Inner", methods = setOf(TEST)))

        assertEquals(setOf("p.OuterTest"), census.population)
    }

    @Test
    fun `메타 애노테이션으로 TestTemplate 인 것도 test 메서드다 — ParameterizedTest`() {
        val census =
            census(
                facts("p.ParamTest", methods = setOf("org.junit.jupiter.params.ParameterizedTest")),
                meta = mapOf("org.junit.jupiter.params.ParameterizedTest" to setOf(TEMPLATE)),
            )

        assertEquals(setOf("p.ParamTest"), census.population)
    }

    @Test
    fun `추상 상위에서 물려받은 test 도 모집단이다 — 구체 하위`() {
        val census =
            census(
                facts("p.BaseTest", methods = setOf(TEST), isAbstract = true),
                facts("p.ConcreteTest", superTypes = listOf("p.BaseTest")),
            )

        assertEquals(setOf("p.ConcreteTest"), census.population)
    }

    /** 추상 클래스는 인스턴스화되지 않는다 — JUnit 도 돌리지 않으므로 등재를 요구하면 안 된다. */
    @Test
    fun `test 를 선언한 추상 클래스는 모집단 밖이다`() {
        val census = census(facts("p.BaseTest", methods = setOf(TEST), isAbstract = true))

        assertEquals(emptySet<String>(), census.population)
    }

    /** 상위가 조회 자리에서 풀리지 않으면 그 가지는 끝난다 — 모집단 밖이고 등재돼 있으면 잉여로 붉는다. */
    @Test
    fun `풀리지 않는 상위는 모집단을 늘리지 않는다`() {
        val census = census(facts("p.ConcreteTest", superTypes = listOf("other.UnknownBase")))

        assertEquals(emptySet<String>(), census.population)
    }

    // ---- 제외 — build 사실에서만 ----

    @Test
    fun `Gradle 제외 패턴에 걸린 클래스는 제외다`() {
        val census =
            census(
                facts("p.CrossLangSmokeTest", methods = setOf(TEST)),
                facts("p.KeepTest", methods = setOf(TEST)),
                excludePatterns = setOf("*CrossLangSmokeTest"),
            )

        assertEquals(setOf("p.CrossLangSmokeTest"), census.excluded)
    }

    @Test
    fun `실행 조건 애노테이션이 붙은 클래스는 제외다`() {
        val census =
            census(
                facts("p.RealServerIntegrationTest", methods = setOf(TEST), classes = setOf(ENABLED_IF)),
                excludePatterns = emptySet(),
            )

        assertEquals(setOf("p.RealServerIntegrationTest"), census.excluded)
    }

    /**
     * `@Disabled` 는 **제외가 아니다.** 제외로 치면 게이트 test 에 그 한 줄을 붙이는 것이 등재에서
     * 빼는 길이 되고, 그러면 `gateExecutionGate` 의 「건너뛰었다」 판정도 함께 사라진다. 등재에 남겨야
     * 그 판정이 붉는다.
     */
    @Test
    fun `Disabled 는 제외가 아니다 — 게이트를 끄는 길이 되지 않게`() {
        val census =
            census(facts("p.FooTest", methods = setOf(TEST), classes = setOf("org.junit.jupiter.api.Disabled")))

        assertEquals(emptySet<String>(), census.excluded)
        assertEquals(setOf("p.FooTest"), census.population)
    }

    // ---- 등식 — 양방향 ----

    @Test
    fun `모집단에서 제외를 뺀 것이 등재와 같으면 위반이 없다`() {
        val census =
            census(
                facts("p.FooTest", methods = setOf(TEST)),
                facts("p.SmokeTest", methods = setOf(TEST)),
                excludePatterns = setOf("*SmokeTest"),
            )

        val violations =
            GateRegistration.violations(
                census,
                registered = setOf("p.FooTest"),
                declaredExcluded = setOf("p.SmokeTest"),
            )

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `등재에서 한 줄을 빼면 누락으로 붉는다`() {
        val census = census(facts("p.FooTest", methods = setOf(TEST)))

        val violations = GateRegistration.violations(census, registered = emptySet(), declaredExcluded = emptySet())

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("p.FooTest"), "$violations")
    }

    @Test
    fun `소스에 없는 이름이 등재에 있으면 잉여로 붉는다`() {
        val census = census(facts("p.FooTest", methods = setOf(TEST)))

        val violations =
            GateRegistration.violations(
                census,
                registered = setOf("p.FooTest", "p.GhostTest"),
                declaredExcluded = emptySet(),
            )

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("p.GhostTest"), "$violations")
    }

    /** 제외가 선언보다 늘면 붉는다 — 게이트 test 에 조건 애노테이션을 붙여 등재에서 빼는 길을 막는다. */
    @Test
    fun `선언되지 않은 제외가 생기면 붉는다`() {
        val census = census(facts("p.FooTest", methods = setOf(TEST), classes = setOf(ENABLED_IF)))

        val violations = GateRegistration.violations(census, registered = emptySet(), declaredExcluded = emptySet())

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("p.FooTest"), "$violations")
    }

    /** 선언에만 있는 이름은 아무것도 제외하지 못한다 — 선언은 출처가 아니라 래칫이다. */
    @Test
    fun `선언에만 있고 build 사실이 없는 제외는 붉는다`() {
        val census = census(facts("p.FooTest", methods = setOf(TEST)))

        val violations =
            GateRegistration.violations(
                census,
                registered = setOf("p.FooTest"),
                declaredExcluded = setOf("p.GhostTest"),
            )

        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().contains("p.GhostTest"), "$violations")
    }

    private companion object {
        const val TEST = "org.junit.jupiter.api.Test"
        const val TEMPLATE = "org.junit.jupiter.api.TestTemplate"
        const val ENABLED_IF = "org.junit.jupiter.api.condition.EnabledIfSystemProperty"

        fun facts(
            binaryName: String,
            methods: Set<String> = emptySet(),
            classes: Set<String> = emptySet(),
            superTypes: List<String> = emptyList(),
            isAbstract: Boolean = false,
        ) = TestClassFacts(binaryName, methods, classes, superTypes, isAbstract)

        fun census(
            vararg classes: TestClassFacts,
            excludePatterns: Set<String> = emptySet(),
            meta: Map<String, Set<String>> = emptyMap(),
        ) = GateRegistration.census(
            classes.toList(),
            TestDiscoveryVocabulary(
                annotations = setOf(TEST, "org.junit.jupiter.api.TestFactory", TEMPLATE),
                conditionPackages = setOf("org.junit.jupiter.api.condition"),
            ),
            excludePatterns,
            metaAnnotations = { meta[it].orEmpty() },
            superFacts = { name -> classes.firstOrNull { it.binaryName == name } },
        )
    }
}
