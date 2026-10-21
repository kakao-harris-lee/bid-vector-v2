package bidvector.settlement

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * SET-01 acceptance 1 — *"법인 표기 차이를 흡수해 같은 회사로 판정한다"* 의 정규화 축.
 *
 * 상호는 전부 **가상**이다(PII — 실 상호·사업자번호를 test 데이터에 쓰지 않는다).
 */
class CompanyNameNormalizationTest {
    private val policy = shippedAwardMatchingPolicy()

    private fun normalizedValueOf(rawName: String): String {
        val normalization = normalizeCompanyName(rawName, policy)
        check(normalization is CompanyNameNormalization.Normalized) { "정규화 결과가 비었다" }
        return normalization.name.value
    }

    /**
     * 출하 정책의 토큰 **열하나 각각**에 사례를 둔다. 하나라도 목록에서 빠지면 그 줄이 붉다 —
     * 「토큰 목록 전체」가 아니라 토큰마다 재는 것이 계약(산출물 3 첫째 줄)이다.
     */
    @Test
    fun `법인 표기 토큰 열하나가 각각 제거된다`() {
        val bare = "가상해양기술"
        val variants =
            listOf(
                "주식회사$bare",
                "${bare}주식회사",
                "유한책임회사$bare",
                "유한회사$bare",
                "합자회사$bare",
                "합명회사$bare",
                "(주)$bare",
                "$bare(주)",
                "（주）$bare",
                "㈜$bare",
                "(유)$bare",
                "（유）$bare",
                "㈔$bare",
            )
        variants.forEach { variant -> normalizedValueOf(variant) shouldBe bare }
    }

    /**
     * 공백 제거가 **토큰 제거보다 먼저**여야 「주식 회사 가상해양기술」이 토큰 제거에 닿는다
     * (legacy 순서). 순서가 뒤집히면 이 줄만으로 붉다.
     */
    @Test
    fun `공백 제거가 토큰 제거보다 먼저라 공백이 낀 법인 표기도 흡수된다`() {
        normalizedValueOf("주식 회사 가상해양기술") shouldBe "가상해양기술"
        normalizedValueOf("( 주 ) 가상해양기술") shouldBe "가상해양기술"
    }

    /** legacy `\s` 는 유니코드 공백 전체다 — 전각 공백·NBSP 가 낀 표기도 같은 회사다. */
    @Test
    fun `전각 공백과 NBSP 도 공백으로 제거된다`() {
        normalizedValueOf("주식회사　가상해양기술") shouldBe "가상해양기술"
        normalizedValueOf("주식회사 가상해양기술") shouldBe "가상해양기술"
        normalizedValueOf("가상해양기술\t\n") shouldBe "가상해양기술"
    }

    @Test
    fun `소문자화가 대소문자 차이를 흡수한다`() {
        normalizedValueOf("(주)Garang Marine") shouldBe "garangmarine"
        normalizedValueOf("가상해양기술 CO") shouldBe normalizedValueOf("가상해양기술 co")
    }

    /** 법인 표기만 남는 상호는 값이 아니라 **사유 있는 결말**이다(`false` 로 접지 않는다). */
    @Test
    fun `정규화 뒤 남는 글자가 없으면 Empty 다`() {
        normalizeCompanyName("(주)", policy) shouldBe CompanyNameNormalization.Empty
        normalizeCompanyName("   ", policy) shouldBe CompanyNameNormalization.Empty
        normalizeCompanyName("", policy) shouldBe CompanyNameNormalization.Empty
    }

    /** 정규화된 상호의 동등성이 판정의 전부다 — 다른 회사는 다른 값이다. */
    @Test
    fun `서로 다른 상호는 정규화 뒤에도 다르다`() {
        normalizedValueOf("(주)가상해양기술") shouldBe "가상해양기술"
        normalizedValueOf("(주)가상토목건설") shouldBe "가상토목건설"
    }

    /**
     * 토큰 목록이 **주입된 정책**에서 온다 — 커널 안 상수였다면 이 줄이 통과할 수 없다
     * (같은 입력이 정책에 따라 다르게 정규화된다).
     */
    @Test
    fun `토큰 목록은 주입된 정책에서 온다`() {
        val custom = AwardMatchingPolicyData(legalEntityTokens = listOf("협동조합"))
        normalizeCompanyName("협동조합가상해양기술", custom) shouldBe
            CompanyNameNormalization.Normalized(normalizedNameOf("가상해양기술", custom))
        // 출하 정책에는 그 토큰이 없으므로 같은 입력이 다르게 정규화된다.
        normalizedValueOf("협동조합가상해양기술") shouldBe "협동조합가상해양기술"
    }

    /** 빈 문자열은 [NormalizedCompanyName] 의 값이 될 수 없다 — 구성 시점 불변식. */
    @Test
    fun `NormalizedCompanyName 은 빈 문자열로 만들어지지 않는다`() {
        assertThrows<IllegalArgumentException> { NormalizedCompanyName.of("") }
    }
}
