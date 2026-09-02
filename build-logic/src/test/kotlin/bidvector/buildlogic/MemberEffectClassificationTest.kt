package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 분류 래칫의 판정. **양방향으로 어긋남을 낸다** — 미분류도, 낡은 분류도 거짓말이다. */
class MemberEffectClassificationTest {
    private val entries =
        mapOf(
            "policy.version" to "1",
            "java.lang.Math#random" to "forbidden",
            "java.util.ArrayList#add" to "reviewed:내부 복사일 뿐이다",
        )

    @Test
    fun `forbidden 만 금지로 읽는다`() {
        val classification = MemberEffectClassification.parse(entries)
        assertEquals(setOf("java.lang.Math#random"), classification.forbidden)
        assertEquals(setOf("java.util.ArrayList#add"), classification.reviewed)
    }

    @Test
    fun `분류되지 않은 후보를 낸다`() {
        val mismatches =
            MemberEffectClassification
                .parse(entries)
                .mismatches(setOf("java.lang.Math#random", "java.util.ArrayList#add", "java.lang.Object#wait"))
        assertEquals(1, mismatches.size, "실제: $mismatches")
        assertTrue(mismatches.single().contains("java.lang.Object#wait"))
    }

    @Test
    fun `후보에 없는 낡은 분류를 낸다`() {
        val mismatches =
            MemberEffectClassification.parse(entries).mismatches(setOf("java.lang.Math#random"))
        assertEquals(1, mismatches.size, "실제: $mismatches")
        assertTrue(mismatches.single().contains("java.util.ArrayList#add"))
    }

    @Test
    fun `사유 없는 reviewed 는 거부한다`() {
        val failure =
            assertFailsWith<IllegalStateException> {
                MemberEffectClassification.parse(mapOf("java.lang.Math#random" to "reviewed:"))
            }
        assertTrue(failure.message!!.contains("java.lang.Math#random"))
    }

    @Test
    fun `모르는 값은 거부한다`() {
        assertFailsWith<IllegalStateException> {
            MemberEffectClassification.parse(mapOf("java.lang.Math#random" to "allowed"))
        }
    }
}
