package bidvector.procurement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * D-3H-3 — [AgencyCode]가 [CategoryCode]와 같은 정규화 함수([normalizeCategoryKey])를
 * 재사용하는지(우회 (2)), 빈 값이 예외가 아니라 `null`로 거부되는지(우회 (8))를 고정한다.
 * 생성은 [AgencyCode.of]·[AgencyName.of] 하나뿐이다 — 직접 생성자 호출은 `private`라
 * 컴파일되지 않는다(이 gate 는 컴파일 거부라 test 로 상주하지 않는다, `BusinessCategoryTest`
 * 관례와 같음).
 */
class AgencyTest {
    @Test
    fun `AgencyCode of 는 CategoryCode of 와 같은 문자열을 낸다 — 여러 입력 교차`() {
        listOf(" A01 ", "0411", "　BB02　", "abcXYZ").forEach { raw ->
            AgencyCode.of(raw)?.value shouldBe CategoryCode.of(raw).value
        }
    }

    @Test
    fun `빈 문자열은 예외 없이 null 이다`() {
        AgencyCode.of("") shouldBe null
    }

    @Test
    fun `공백만 있는 문자열은 정규화 뒤 빈 키라 null 이다`() {
        AgencyCode.of("   ") shouldBe null
    }

    @Test
    fun `AgencyName of 는 원문을 trim 만 하고 정규화하지 않는다 — 우회 (7)`() {
        AgencyName.of("  조달청  ")?.value shouldBe "조달청"
        AgencyName.of("조 달 청")?.value shouldBe "조 달 청"
    }

    /** verifier r1 F-2 — 우회 (7) 문면 「대소문자·내부 공백 보존」의 대소문자 절반. */
    @Test
    fun `AgencyName of 는 라틴 대소문자를 그대로 보존한다 — 우회 (7)`() {
        AgencyName.of("  ABC Corp 지사  ")?.value shouldBe "ABC Corp 지사"
    }

    @Test
    fun `AgencyName 빈 문자열은 예외 없이 null 이다`() {
        AgencyName.of("") shouldBe null
    }

    @Test
    fun `AgencyName 공백만 있는 문자열은 trim 뒤 빈 값이라 null 이다`() {
        AgencyName.of("   ") shouldBe null
    }

    @Test
    fun `Agency 는 code 와 name 이 둘 다 null 이면 거부된다`() {
        shouldThrow<IllegalArgumentException> { Agency(code = null, name = null) }
    }

    @Test
    fun `Agency 는 code 만 있어도 성립한다`() {
        val agency = Agency(code = AgencyCode.of("1234567"), name = null)

        agency.code?.value shouldBe "1234567"
        agency.name shouldBe null
    }

    @Test
    fun `Agency 는 name 만 있어도 성립한다`() {
        val agency = Agency(code = null, name = AgencyName.of("조달청"))

        agency.code shouldBe null
        agency.name?.value shouldBe "조달청"
    }
}
