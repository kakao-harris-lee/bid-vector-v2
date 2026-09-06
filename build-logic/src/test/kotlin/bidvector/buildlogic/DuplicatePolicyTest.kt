package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `duplicate-policy.properties`의 값 해석 — 순수 함수(Gradle·CPD 없이 테스트가 붙는다). */
class DuplicatePolicyTest {
    private fun policy(
        mode: String,
        failSourceSets: String = "main",
    ) = DuplicatePolicy(
        mapOf(
            "language" to "kotlin",
            "minimumTokenCount" to "50",
            "toolVersion" to "7.7.0",
            "mode" to mode,
            "fail.source-sets" to failSourceSets,
        ),
    )

    @Test
    fun `mode observe 는 ignoreFailures 를 참으로 낸다`() {
        assertTrue(policy("observe").ignoreFailures)
    }

    @Test
    fun `mode fail 은 ignoreFailures 를 거짓으로 낸다`() {
        assertFalse(policy("fail").ignoreFailures)
    }

    @Test
    fun `language minimumTokenCount toolVersion 을 그대로 낸다`() {
        val p = policy("observe")
        assertEquals("kotlin", p.language)
        assertEquals(50, p.minimumTokenCount)
        assertEquals("7.7.0", p.toolVersion)
    }

    @Test
    fun `fail source-sets 를 콤마 목록으로 낸다`() {
        assertEquals(listOf("main"), policy("fail", failSourceSets = "main").failSourceSets)
    }

    @Test
    fun `fail source-sets 는 여러 항목도 받는다`() {
        assertEquals(listOf("main", "test"), policy("fail", failSourceSets = "main, test").failSourceSets)
    }

    @Test
    fun `fail source-sets 가 빈 값이면 거부한다`() {
        // verifier r1 C-2 — 키는 있는데 값이 비면(`fail.source-sets=`) requireList 가
        // 빈 리스트를 그대로 내 cpdCheck 의 source 와 프레즌스 게이트의 expectedSource 가
        // 함께 비어 실패가 침묵으로 통과했다. 키 부재뿐 아니라 빈 값도 정책 오류다.
        val error = assertFailsWith<IllegalArgumentException> { policy("fail", failSourceSets = "").failSourceSets }
        assertTrue(error.message.orEmpty().contains("fail.source-sets"), "${error.message}")
    }
}
