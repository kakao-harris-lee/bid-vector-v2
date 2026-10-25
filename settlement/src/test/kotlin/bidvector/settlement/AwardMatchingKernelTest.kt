package bidvector.settlement

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Modifier

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
    ): AwardOutcome = judgeAwardOutcome(decideAwardLookup(operator, policy), winner)

    @Test
    fun `법인 표기가 달라도 같은 회사면 낙찰이다`() {
        judge(
            OperatorTradeName.Declared("주식회사 가상해양기술"),
            AwardWinnerTradeName.Published("(주)가상해양기술"),
        ) shouldBe SettledAwardOutcome.Won()
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
        ) shouldBe SettledAwardOutcome.Lost()
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

    /**
     * 「정확매치만」 — 한쪽이 다른 쪽의 **접두·접미·부분 문자열**인 쌍은 전부 패찰이다. 비교를
     * 접두·포함 비교로 느슨하게 하면 운영자 「가상」이 결과 「가상건설」을 낙찰로 판정한다(가장
     * 해로운 방향). 양방향 모두 잰다.
     */
    @Test
    fun `한쪽이 다른 쪽의 접두 접미 부분 문자열이어도 패찰이다`() {
        val pairs =
            listOf(
                "가상" to "가상건설",
                "건설" to "가상건설",
                "가상건설" to "가상건설기술",
                "상건" to "가상건설",
            )
        pairs.forEach { (left, right) ->
            judge(OperatorTradeName.Declared(left), AwardWinnerTradeName.Published(right)) shouldBe
                SettledAwardOutcome.Lost()
            judge(OperatorTradeName.Declared(right), AwardWinnerTradeName.Published(left)) shouldBe
                SettledAwardOutcome.Lost()
        }
    }

    /**
     * 판정은 **관문을 만든 정책 하나**로 두 상호를 정규화한다 — 시그니처에 정책 인자가 없다.
     * 「협동조합 가상해양」과 「가상해양」은 출하 정책(그 토큰 없음)으로는 다른 회사이고, 그
     * 토큰을 가진 정책으로는 같은 회사다. 운영자 쪽만 다른 정책으로 접고 결과 쪽을 출하 정책으로
     * 접는 조합은 호출할 수 없다.
     */
    @Test
    fun `판정은 관문을 만든 정책 하나로 양쪽을 정규화한다`() {
        val cooperative = AwardMatchingPolicyData(legalEntityTokens = listOf("협동조합"))
        val operator = OperatorTradeName.Declared("협동조합 가상해양")
        val winner = AwardWinnerTradeName.Published("가상해양")

        val shippedLookup = decideAwardLookup(operator, policy)
        shippedLookup.shouldBeInstanceOf<AwardLookupDecision.Lookup>()
        shippedLookup.policy shouldBe policy
        judgeAwardOutcome(shippedLookup, winner) shouldBe SettledAwardOutcome.Lost()

        val cooperativeLookup = decideAwardLookup(operator, cooperative)
        cooperativeLookup.shouldBeInstanceOf<AwardLookupDecision.Lookup>()
        cooperativeLookup.policy shouldBe cooperative
        judgeAwardOutcome(cooperativeLookup, winner) shouldBe SettledAwardOutcome.Won()
    }

    /**
     * 결과 쪽 정책을 따로 잰다 — 토큰이 **낙찰자 상호에만** 있는 사례. 결과 쪽을 관문의 정책이 아닌
     * 다른 정책(예: 출하 정책)으로 접으면 「협동조합가상해양」이 남아 패찰이 된다.
     */
    @Test
    fun `낙찰자 상호도 관문을 만든 정책으로 정규화된다`() {
        val cooperative = AwardMatchingPolicyData(legalEntityTokens = listOf("협동조합"))
        val lookup = decideAwardLookup(OperatorTradeName.Declared("가상해양"), cooperative)
        judgeAwardOutcome(lookup, AwardWinnerTradeName.Published("협동조합 가상해양")) shouldBe
            SettledAwardOutcome.Won()
    }

    /**
     * 낙찰과 패찰은 **서로 다른 값**이다 — 두 값의 동등성은 손으로 쓴 멤버라 그 구별을 따로 잠근다.
     * 다른 모든 단언(정확매치·정책 동반)이 이 구별 위에 선다: 두 값이 같아지면 그 단언들이 비교
     * 방향과 무관하게 초록이 된다.
     */
    @Test
    fun `낙찰과 패찰은 동등성 해시 문자열 모두 구별된다`() {
        val won = SettledAwardOutcome.Won()
        val lost = SettledAwardOutcome.Lost()
        won shouldNotBe lost
        lost shouldNotBe won
        won.hashCode() shouldNotBe lost.hashCode()
        won.toString() shouldNotBe lost.toString()
        AwardOutcomeRecording.AlreadySettled(won) shouldNotBe AwardOutcomeRecording.AlreadySettled(lost)
        AwardOutcomeRecording.Recorded(lost) shouldNotBe AwardOutcomeRecording.Recorded(won)
        won shouldBe SettledAwardOutcome.Won()
        lost shouldBe SettledAwardOutcome.Lost()
    }

    /** 「상호 없음」의 길은 `NotDeclared`·`NotPublished` 하나씩이다 — 빈·공백만 상호는 만들어지지 않는다. */
    @Test
    fun `빈 상호와 공백만 상호는 입력 값으로 만들어지지 않는다`() {
        listOf("", "   ", "\t\n", "　").forEach { blank ->
            assertThrows<IllegalArgumentException> { OperatorTradeName.Declared(blank) }
            assertThrows<IllegalArgumentException> { AwardWinnerTradeName.Published(blank) }
        }
    }

    /**
     * 금액이 판정에 들어올 길을 **입력 타입의 모양**으로 닫는다 — 판정에 닿는 값 셋의 인스턴스
     * 필드 집합이 정확히 아래와 같다. 결과 쪽 입력에 기본값 있는 금액 필드를 더하는 변경은 판정
     * 함수의 인자 수를 바꾸지 않아 컴파일 하네스를 지나므로, 이 등식이 그 자리를 진다.
     */
    @Test
    fun `판정에 닿는 입력 타입의 필드 집합은 상호와 정책뿐이다`() {
        instanceFieldsOf(AwardWinnerTradeName.Published::class.java) shouldBe mapOf("rawName" to String::class.java)
        instanceFieldsOf(OperatorTradeName.Declared::class.java) shouldBe mapOf("rawName" to String::class.java)
        instanceFieldsOf(AwardLookupDecision.Lookup::class.java) shouldBe
            mapOf(
                "operator" to NormalizedCompanyName::class.java,
                "policy" to AwardMatchingPolicyData::class.java,
            )
        instanceFieldsOf(NormalizedCompanyName::class.java) shouldBe mapOf("value" to String::class.java)
        instanceFieldsOf(SettledAwardOutcome.Won::class.java) shouldBe emptyMap()
        instanceFieldsOf(SettledAwardOutcome.Lost::class.java) shouldBe emptyMap()
    }

    private fun instanceFieldsOf(type: Class<*>): Map<String, Class<*>> =
        type.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .associate { it.name to it.type }
}
