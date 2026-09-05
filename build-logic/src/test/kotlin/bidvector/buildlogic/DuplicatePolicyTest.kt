package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `duplicate-policy.properties`의 값 해석 — 순수 함수(Gradle·CPD 없이 테스트가 붙는다). */
class DuplicatePolicyTest {
    private fun policy(mode: String) =
        DuplicatePolicy(
            mapOf(
                "language" to "kotlin",
                "minimumTokenCount" to "50",
                "toolVersion" to "7.7.0",
                "mode" to mode,
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
}
