package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TestShapes.extract` 의 음성 — 설계 검토 `_workspace/harness-test-shape/02_design-review.md`
 * A-2·A-3·「미달 후보」. 정책은 인라인으로 구성한다(순수 함수, task 배선과 분리 — `PublicApiTypesTest`
 * 관례). 2B 원 사례(`= runBlocking { … shouldBe … }`)가 위반 V1 이다.
 */
class TestShapesTest {
    private val policy =
        TestShapePolicy(
            methodAnnotations = setOf("Test", "ParameterizedTest", "RepeatedTest", "TestTemplate"),
            factoryAnnotations = setOf("TestFactory"),
            forbidSuspend = true,
            forbidPrivate = true,
        )

    // ---- 위반 4종 — 반환 타입/본문 형태 ----

    @Test
    fun `V1 식 본문 test 는 위반이다 — 2B 원 사례`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @Test\n    fun x() = runBlocking { shouldBe(1) }\n}\n",
                policy,
            )
        assertEquals(1, violations.size, "$violations")
        assertTrue(violations.single().functionName == "x")
    }

    @Test
    fun `V2 명시 Unit 반환 식 본문도 위반이다 — 추론에 기대는 형태 자체를 막는다`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @Test\n    fun x(): Unit = runBlocking { shouldBe(1) }\n}\n",
                policy,
            )
        assertEquals(1, violations.size, "$violations")
    }

    @Test
    fun `V3 블록 본문이지만 명시 반환 타입이 Unit 이 아니면 위반이다`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @Test\n    fun x(): Int {\n        return 1\n    }\n}\n",
                policy,
            )
        assertEquals(1, violations.size, "$violations")
    }

    @Test
    fun `V4 제네릭 비-Unit 명시 반환도 위반이다`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @Test\n    fun x(): List<Int> {\n        return emptyList()\n    }\n}\n",
                policy,
            )
        assertEquals(1, violations.size, "$violations")
    }

    // ---- 정당 3종 ----

    @Test
    fun `L1 블록 본문 반환 타입 없음은 정당하다`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @Test\n    fun x() {\n        val y = 1\n    }\n}\n",
                policy,
            )
        assertTrue(violations.isEmpty(), "$violations")
    }

    @Test
    fun `L2 블록 본문 명시 Unit 반환은 정당하다`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @Test\n    fun x(): Unit {\n        val y = 1\n    }\n}\n",
                policy,
            )
        assertTrue(violations.isEmpty(), "$violations")
    }

    @Test
    fun `L3 다른 test-메서드 어노테이션도 같은 규칙을 받는다`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @ParameterizedTest\n    fun x() {\n        val y = 1\n    }\n}\n",
                policy,
            )
        assertTrue(violations.isEmpty(), "$violations")
    }

    // ---- factory ----

    @Test
    fun `F1 TestFactory 는 반환 타입 미명시가 정당하다`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @TestFactory\n    fun x() = listOf(dynamicTest(\"a\") { })\n}\n",
                policy,
            )
        assertTrue(violations.isEmpty(), "$violations")
    }

    @Test
    fun `F2 TestFactory 는 명시 비-Unit 반환도 정당하다`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @TestFactory\n    fun x(): List<DynamicTest> = listOf(dynamicTest(\"a\") { })\n}\n",
                policy,
            )
        assertTrue(violations.isEmpty(), "$violations")
    }

    @Test
    fun `F3 TestFactory 가 명시 Unit 반환이면 위반이다 — 반대 규칙`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @TestFactory\n    fun x(): Unit {\n        val y = 1\n    }\n}\n",
                policy,
            )
        assertEquals(1, violations.size, "$violations")
    }

    // ---- suspend ----

    @Test
    fun `S1 suspend test 메서드는 블록 본문이어도 위반이다 — forbid-suspend`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @Test\n    suspend fun x() {\n        val y = 1\n    }\n}\n",
                policy,
            )
        assertEquals(1, violations.size, "$violations")
    }

    @Test
    fun `S2 forbid-suspend 가 꺼져 있으면 같은 함수가 정당하다`() {
        val relaxed = policy.copy(forbidSuspend = false)
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @Test\n    suspend fun x() {\n        val y = 1\n    }\n}\n",
                relaxed,
            )
        assertTrue(violations.isEmpty(), "$violations")
    }

    // ---- private ----

    @Test
    fun `P1 private test 메서드는 블록 본문이어도 위반이다 — forbid-private`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @Test\n    private fun x() {\n        val y = 1\n    }\n}\n",
                policy,
            )
        assertEquals(1, violations.size, "$violations")
    }

    @Test
    fun `P2 forbid-private 가 꺼져 있으면 같은 함수가 정당하다`() {
        val relaxed = policy.copy(forbidPrivate = false)
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @Test\n    private fun x() {\n        val y = 1\n    }\n}\n",
                relaxed,
            )
        assertTrue(violations.isEmpty(), "$violations")
    }

    // ---- 대상 밖 ----

    @Test
    fun `test-메서드 어노테이션이 없으면 대상이 아니다`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    fun x() = 1\n}\n",
                policy,
            )
        assertTrue(violations.isEmpty(), "$violations")
    }

    @Test
    fun `여러 위반이 있으면 각각 보고한다`() {
        val violations =
            TestShapes.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    @Test\n    fun a() = runBlocking { }\n\n    @Test\n    fun b(): Int {\n        return 1\n    }\n}\n",
                policy,
            )
        assertEquals(2, violations.size, "$violations")
        assertEquals(setOf("a", "b"), violations.map { it.functionName }.toSet())
    }
}
