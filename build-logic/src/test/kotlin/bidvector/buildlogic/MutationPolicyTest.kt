package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `mutation-policy.properties` 의 값 해석 — 순수 함수(Gradle·pitest 없이 테스트가 붙는다). */
class MutationPolicyTest {
    private fun policy(
        modules: String = "qualification,settlement",
        extra: Map<String, String> = emptyMap(),
    ) = MutationPolicy(
        mapOf(
            "policy.version" to "1",
            "mutation.modules" to modules,
            "threads" to "1",
            "output.formats" to "XML,HTML",
            "ratchet.qualification.score.min-percent" to "77",
            "ratchet.qualification.mutations.min" to "109",
            "ratchet.qualification.classes.min" to "18",
            "ratchet.settlement.score.min-percent" to "80",
            "ratchet.settlement.mutations.min" to "40",
            "ratchet.settlement.classes.min" to "5",
        ) + extra,
    )

    @Test
    fun `모듈 목록을 콤마 목록으로 낸다`() {
        assertEquals(listOf("qualification", "settlement"), policy().modules)
    }

    @Test
    fun `모듈 목록이 비면 거부한다`() {
        // `mutation.modules=` 는 「대상 없음」이 아니라 정책 오류다 — 비면 등식 게이트의 양변이
        // 함께 비어 모든 모듈이 빠진 상태가 침묵으로 통과한다(DuplicatePolicy.failSourceSets 와 같은 틈).
        val error = assertFailsWith<IllegalArgumentException> { policy(modules = "").modules }
        assertTrue(error.message.orEmpty().contains("mutation.modules"), "${error.message}")
    }

    @Test
    fun `threads 와 output formats 를 그대로 낸다`() {
        assertEquals(1, policy().threads)
        assertEquals(listOf("XML", "HTML"), policy().outputFormats)
    }

    @Test
    fun `출력 형식에 XML 이 없으면 거부한다`() {
        // 판정은 `mutations.xml` 을 읽어서만 선다 — XML 을 끄는 것은 게이트를 끄는 것과 같다.
        val error =
            assertFailsWith<IllegalArgumentException> {
                policy(extra = mapOf("output.formats" to "HTML")).outputFormats
            }
        assertTrue(error.message.orEmpty().contains("XML"), "${error.message}")
    }

    @Test
    fun `모듈별 래칫 세 축을 낸다`() {
        assertEquals(
            MutationRatchet(minScorePercent = 77, minMutations = 109, minMutatedClasses = 18),
            policy().ratchet("qualification"),
        )
        assertEquals(
            MutationRatchet(minScorePercent = 80, minMutations = 40, minMutatedClasses = 5),
            policy().ratchet("settlement"),
        )
    }

    @Test
    fun `등재된 모듈의 래칫 키가 없으면 거부한다`() {
        // 모듈을 목록에 더하고 래칫을 빼면 그 모듈은 「대상이지만 임계 없음」이 된다 — 조용한 면제다.
        val error = assertFailsWith<IllegalStateException> { policy(modules = "strategy").ratchet("strategy") }
        assertTrue(error.message.orEmpty().contains("strategy"), "${error.message}")
    }

    @Test
    fun `점수 임계가 백분율 범위를 벗어나면 거부한다`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                policy(extra = mapOf("ratchet.qualification.score.min-percent" to "101")).ratchet("qualification")
            }
        assertTrue(error.message.orEmpty().contains("score.min-percent"), "${error.message}")
    }

    @Test
    fun `정책 판을 낸다`() {
        // 읽는 쪽이 없으면 형식이 바뀌는 날 조용히 지나간다 — 게이트 리포트가 이 값을 함께 적는다.
        assertEquals("1", policy().version)
    }

    @Test
    fun `정책 판 키가 없으면 거부한다`() {
        val error = assertFailsWith<IllegalStateException> { MutationPolicy(mapOf("threads" to "1")).version }
        assertTrue(error.message.orEmpty().contains("policy.version"), "${error.message}")
    }

    @Test
    fun `래칫 세 축은 0 을 거부한다 — 키가 있으면서 축을 무력화하는 자리다`() {
        // 0 은 키가 **있으므로** 「래칫 키가 없다」 검사를 통과하고 그 축만 조용히 꺼진다.
        for (axis in listOf("score.min-percent", "mutations.min", "classes.min")) {
            val error =
                assertFailsWith<IllegalArgumentException> {
                    policy(extra = mapOf("ratchet.qualification.$axis" to "0")).ratchet("qualification")
                }
            assertTrue(error.message.orEmpty().contains(axis), "$axis: ${error.message}")
        }
    }
}
