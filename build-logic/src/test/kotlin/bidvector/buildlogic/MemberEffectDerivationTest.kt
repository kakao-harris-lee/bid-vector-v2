package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 도출이 **실제 JDK 바이트코드**에서 나온다는 것을 고정한다. 목록을 손으로 적고 그 목록을
 * 다시 단언하면 tautology 이므로, 여기서는 좁은 표면을 주고 그 표면이 내는 좌표를 확인한다.
 */
class MemberEffectDerivationTest {
    private val surface = EffectSurface(listOf("java.io"), setOf("java.lang.System", "java.util.Random"))

    @Test
    fun `표면에 닿는 멤버를 도출한다`() {
        val keys = deriveMemberEffects(listOf("java.lang.Throwable", "java.lang.Math"), surface).map { it.key }
        assertTrue("java.lang.Throwable#printStackTrace" in keys, "실제 도출: $keys")
        assertTrue("java.lang.Math#random" in keys, "실제 도출: $keys")
    }

    @Test
    fun `표면에 닿지 않는 멤버는 후보가 아니다`() {
        val keys = deriveMemberEffects(listOf("java.lang.Math"), surface).map { it.key }
        assertFalse("java.lang.Math#max" in keys, "순수 계산이 후보가 되면 분류가 무의미해진다")
    }

    @Test
    fun `상속으로 들어오는 선언 클래스도 본다`() {
        val keys = deriveMemberEffects(listOf("java.lang.IllegalStateException"), surface).map { it.key }
        assertTrue("java.lang.Throwable#printStackTrace" in keys, "선언 클래스로 나와야 한다: $keys")
    }

    @Test
    fun `도출은 결정적이다`() {
        val allowed = listOf("java.lang.String", "java.util.Collections")
        val first = deriveMemberEffects(allowed, surface).map { "${it.key}=${it.surface}" }
        val second = deriveMemberEffects(allowed, surface).map { "${it.key}=${it.surface}" }
        assertEquals(first, second, "재생성 대조가 성립하려면 값까지 같아야 한다")
    }

    @Test
    fun `표면 밖 좌표는 표면이 아니다`() {
        val narrow = EffectSurface(emptyList(), setOf("java.util.Random"))
        val keys = deriveMemberEffects(listOf("java.lang.Throwable"), narrow).map { it.key }
        assertFalse("java.lang.Throwable#printStackTrace" in keys, "표면을 좁히면 후보도 줄어야 한다")
    }
}
