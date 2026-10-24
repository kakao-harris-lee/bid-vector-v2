package bidvector.settlement

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.Resolution
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

/**
 * 토큰 목록이 **코드 상수가 아니라 versioned 정책 데이터**라는 것과, 출하 값이 legacy 목록과
 * 같다는 것을 잰다(계약 산출물 1 첫째 줄).
 *
 * 기대 목록을 이 test 가 **자기 리터럴로** 다시 적는 이유: 출하 값을 그대로 읽어 비교하면
 * 항진명제다(게이트가 자기 입력을 재계산하는 꼴).
 */
class AwardMatchingPolicyDataTest {
    private val legacyTokens =
        listOf(
            "주식회사",
            "유한책임회사",
            "유한회사",
            "합자회사",
            "합명회사",
            "(주)",
            "（주）",
            "㈜",
            "(유)",
            "（유）",
            "㈔",
        )

    @Test
    fun `출하 정책의 토큰 목록은 legacy 목록과 값도 순서도 같다`() {
        shippedAwardMatchingPolicy().legalEntityTokens shouldBe legacyTokens
    }

    /** 정책 version 이 출처를 나른다 — 목록이 바뀌면 어느 version 부터인지가 남아야 한다. */
    @Test
    fun `출하 정책은 legacy 출처를 PolicyVersion 으로 나른다`() {
        val resolution = AWARD_MATCHING_POLICY.resolve(LocalDate.of(2026, 10, 21))
        check(resolution is Resolution.Resolved)
        resolution.version.effectiveFrom shouldBe EffectiveFrom.Initial
        resolution.version.source shouldContain "award_verification.py"
        resolution.version.source shouldContain "OPEN-SET-03"
    }

    @Test
    fun `빈 토큰 목록은 정책이 될 수 없다`() {
        assertThrows<IllegalArgumentException> { AwardMatchingPolicyData(emptyList()) }
    }

    @Test
    fun `공백 토큰은 정책이 될 수 없다`() {
        assertThrows<IllegalArgumentException> { AwardMatchingPolicyData(listOf("주식회사", " ")) }
    }

    @Test
    fun `중복 토큰은 정책이 될 수 없다`() {
        assertThrows<IllegalArgumentException> { AwardMatchingPolicyData(listOf("주식회사", "주식회사")) }
    }
}
