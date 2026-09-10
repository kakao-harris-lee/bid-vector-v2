package bidvector.procurement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * [RowDiscriminator.of] — 값 우선 규칙(D-3E-1a (a))의 유일한 조립 지점. 「부재를 `0`·`""`로
 * 채우지 않는다」(설계 검토 (3) 우회 #2)와 「값이 있으면 위치를 쓰지 않는다」(우회 #3)를
 * 여기서 고정한다 — 상위 층(adapters)이 이 factory 대신 직접 `Positional`을 골라 값이 있는
 * 행에까지 위치를 쓰면 응답 순서가 바뀔 때 같은 행이 다른 키를 얻는다.
 */
class RowDiscriminatorTest {
    @Test
    fun `값이 있으면 Identified 를 낸다`() {
        RowDiscriminator.of("003", ordinalIfAbsent = 5) shouldBe RowDiscriminator.Identified("003")
    }

    @Test
    fun `값이 null 이면 Positional 을 낸다 — 0 이나 빈 문자열로 지어내지 않는다`() {
        RowDiscriminator.of(null, ordinalIfAbsent = 5) shouldBe RowDiscriminator.Positional(5)
    }

    @Test
    fun `값이 공백뿐이면 부재로 취급해 Positional 을 낸다`() {
        RowDiscriminator.of("   ", ordinalIfAbsent = 2) shouldBe RowDiscriminator.Positional(2)
    }

    @Test
    fun `값이 있으면 위치 인자가 무엇이든 무시된다 — 순서 변화에 안전하다`() {
        val atOrdinalZero = RowDiscriminator.of("003", ordinalIfAbsent = 0)
        val atOrdinalFive = RowDiscriminator.of("003", ordinalIfAbsent = 5)
        atOrdinalZero shouldBe atOrdinalFive
    }

    @Test
    fun `Identified 는 빈 문자열을 거부한다`() {
        shouldThrow<IllegalArgumentException> { RowDiscriminator.Identified("") }
    }

    @Test
    fun `Positional 은 음수 ordinal 을 거부한다`() {
        shouldThrow<IllegalArgumentException> { RowDiscriminator.Positional(-1) }
    }
}
