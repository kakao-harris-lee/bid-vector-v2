package bidvector.settlement

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * SET-01 acceptance 네 줄을 커널에서 잰다. 상호는 전부 **가상**이다.
 *
 * - *"법인 표기 차이를 흡수해 같은 회사로 판정한다"*
 * - *"동일 금액·다른 상호는 패찰이다(금액은 판정에 쓰지 않는다)"*
 * - *"낙찰가만 있고 낙찰자 상호가 없으면 판정이 `false` 가 아니라 미기록(3-state)"*
 * - *"운영자 상호가 미설정이면 판정하지 않고 외부 조회도 하지 않는다"*
 */
class AwardMatchingKernelTest {
    private val policy = shippedAwardMatchingPolicy()

    private fun judge(
        operator: OperatorTradeName,
        winner: AwardWinnerTradeName,
    ): AwardOutcome = judgeAwardOutcome(decideAwardLookup(operator, policy), winner, policy)

    @Test
    fun `법인 표기가 달라도 같은 회사면 낙찰이다`() {
        judge(
            OperatorTradeName.Declared("주식회사 가상해양기술"),
            AwardWinnerTradeName.Published("(주)가상해양기술"),
        ) shouldBe SettledAwardOutcome.Won
    }

    /**
     * 금액은 **시그니처에 자리가 없다** — 같은 투찰가를 낸 다른 업체라는 상황 자체를 이 함수에
     * 표현할 수 없고, 상호가 다르면 패찰이다. 금액 인자를 더한 호출이 컴파일되지 않는다는 쪽은
     * [CompileFailureHarnessTest] fixture 1 이 잰다.
     */
    @Test
    fun `상호가 다르면 패찰이다 — 금액은 판정에 들어갈 자리가 없다`() {
        judge(
            OperatorTradeName.Declared("(주)가상해양기술"),
            AwardWinnerTradeName.Published("(주)가상토목건설"),
        ) shouldBe SettledAwardOutcome.Lost
    }

    @Test
    fun `낙찰자 상호가 공개되지 않으면 패찰이 아니라 미기록이다`() {
        judge(
            OperatorTradeName.Declared("(주)가상해양기술"),
            AwardWinnerTradeName.NotPublished,
        ) shouldBe AwardOutcome.NotRecorded(WinnerNameAbsentReason.NotPublished)
    }

    @Test
    fun `낙찰자 상호가 법인 표기뿐이면 미기록이고 사유가 다르다`() {
        judge(
            OperatorTradeName.Declared("(주)가상해양기술"),
            AwardWinnerTradeName.Published("(주)"),
        ) shouldBe AwardOutcome.NotRecorded(WinnerNameAbsentReason.NormalizesToEmpty)
    }

    @Test
    fun `운영자 상호가 미설정이면 조회 술어가 Skip 이다`() {
        decideAwardLookup(OperatorTradeName.NotDeclared, policy) shouldBe
            AwardLookupDecision.Skip(OperatorNameAbsentReason.NotDeclared)
    }

    @Test
    fun `운영자 상호가 법인 표기뿐이어도 조회 술어가 Skip 이다`() {
        decideAwardLookup(OperatorTradeName.Declared("（주）"), policy) shouldBe
            AwardLookupDecision.Skip(OperatorNameAbsentReason.NormalizesToEmpty)
    }

    /** 미설정은 패찰도 미기록도 아니다 — **판정 안 함**이다. 낙찰자가 공개돼 있어도 그렇다. */
    @Test
    fun `운영자 상호가 미설정이면 낙찰자가 공개돼 있어도 판정하지 않는다`() {
        judge(OperatorTradeName.NotDeclared, AwardWinnerTradeName.Published("(주)가상토목건설")) shouldBe
            AwardOutcome.NotJudged(OperatorNameAbsentReason.NotDeclared)
    }

    /** 운영자 축 검사가 결과 축보다 먼저다 — 둘 다 없으면 「판정 안 함」이지 「미기록」이 아니다. */
    @Test
    fun `둘 다 없으면 미기록이 아니라 판정 안 함이다`() {
        judge(OperatorTradeName.NotDeclared, AwardWinnerTradeName.NotPublished) shouldBe
            AwardOutcome.NotJudged(OperatorNameAbsentReason.NotDeclared)
    }

    @Test
    fun `설정된 운영자 상호는 정규화된 값으로 조회 관문을 통과한다`() {
        val decision = decideAwardLookup(OperatorTradeName.Declared("주식회사 가상해양기술"), policy)
        decision.shouldBeInstanceOf<AwardLookupDecision.Lookup>()
        decision.operator shouldBe normalizedNameOf("가상해양기술", policy)
    }

    /** PII — 상호 운반 타입의 `toString()` 이 실값을 내보내지 않는다(로그·예외·단언 출력). */
    @Test
    fun `상호 운반 타입은 toString 으로 실값을 내보내지 않는다`() {
        val rawName = "가상해양기술"
        OperatorTradeName.Declared(rawName).toString() shouldNotContain rawName
        AwardWinnerTradeName.Published(rawName).toString() shouldNotContain rawName
        normalizedNameOf(rawName, policy).toString() shouldNotContain rawName
    }
}
