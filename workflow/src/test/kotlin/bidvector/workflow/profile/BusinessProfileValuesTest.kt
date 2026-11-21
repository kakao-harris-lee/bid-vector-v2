package bidvector.workflow.profile

import bidvector.qualification.LicenseName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 사업 정보 값 타입의 **입력 경계**(scope test 「빈 값 · 상한」)와 **가림**(P4 — 로그 · 예외 문면 노출 0).
 * 경계값은 [ProfileInputPolicy] 에서 읽는다 — 같은 값을 V22 CHECK 가 쓰고, 그 일치는
 * `JdbcBusinessProfileStoreTest` 가 실 DB 위에서 잰다.
 */
class BusinessProfileValuesTest {
    private val maxName = ProfileInputPolicy.TRADE_NAME_MAX_CODE_POINTS

    @Test
    fun `상호 — 공백뿐이면 거부한다(없음은 null 이다)`() {
        shouldThrow<IllegalArgumentException> { TradeName("") }
        shouldThrow<IllegalArgumentException> { TradeName("  　 ") }
    }

    @Test
    fun `상호 — 원문 그대로다(앞뒤 공백을 자르지 않는다, 이중 정규화 금지)`() {
        TradeName(" (주)합성 ").raw shouldBe " (주)합성 "
    }

    /** 코드포인트로 센다 — 서로게이트 쌍 글자 하나가 두 글자로 세이면 DB 와 갈린다. */
    @Test
    fun `상호 — 상한은 코드포인트 수다(경계 포함, 하나 넘으면 거부)`() {
        val astral = "𠮷" // U+20BB7 — UTF-16 두 단위, 코드포인트 하나
        TradeName(astral.repeat(maxName)).raw.codePointLength() shouldBe maxName
        shouldThrow<IllegalArgumentException> { TradeName("가".repeat(maxName + 1)) }
    }

    @Test
    fun `시평액 — 금액은 0 초과 · 상한 이하, 연도는 범위 안(양 끝 포함)`() {
        fun capacity(
            amount: Long,
            year: Int,
        ) = ConstructionCapacity(LicenseName("합성면허"), amount, year)

        capacity(1L, ProfileInputPolicy.ASSESSMENT_YEAR_MIN).amountWon shouldBe 1L
        capacity(ProfileInputPolicy.CAPACITY_AMOUNT_MAX_WON, ProfileInputPolicy.ASSESSMENT_YEAR_MAX).amountWon shouldBe
            ProfileInputPolicy.CAPACITY_AMOUNT_MAX_WON
        shouldThrow<IllegalArgumentException> { capacity(0L, 2026) }
        shouldThrow<IllegalArgumentException> { capacity(ProfileInputPolicy.CAPACITY_AMOUNT_MAX_WON + 1, 2026) }
        shouldThrow<IllegalArgumentException> { capacity(1L, ProfileInputPolicy.ASSESSMENT_YEAR_MIN - 1) }
        shouldThrow<IllegalArgumentException> { capacity(1L, ProfileInputPolicy.ASSESSMENT_YEAR_MAX + 1) }
    }

    @Test
    fun `시평액 — 면허명은 공백뿐일 수 없고 상한이 있다`() {
        shouldThrow<IllegalArgumentException> { ConstructionCapacity(LicenseName(" "), 1L, 2026) }
        shouldThrow<IllegalArgumentException> {
            ConstructionCapacity(LicenseName("가".repeat(ProfileInputPolicy.LICENSE_NAME_MAX_CODE_POINTS + 1)), 1L, 2026)
        }
    }

    /**
     * 받는 꼴은 **둘**이고 저장되는 꼴은 **하나**다(M15/15C). 하이픈 표기가 숫자 열 자리와 같은
     * 값이 되는 것이 「정규화가 한 번」의 뜻이다.
     */
    @Test
    fun `사업자번호 — 숫자 10자리와 3-2-5 표기가 같은 값이다`() {
        BusinessRegistrationNumber.of("1234567890").digits shouldBe "1234567890"
        BusinessRegistrationNumber.of("123-45-67890") shouldBe BusinessRegistrationNumber("1234567890")
    }

    /**
     * 그 둘 **밖**은 값이 아니다. 「하이픈을 지우고 남은 숫자가 10 이면 받는다」로 열면 아래 셋이
     * 전부 같은 값이 되고, 그때부터 적은 것과 저장된 것이 다르다.
     */
    @Test
    fun `사업자번호 — 하이픈 자리가 다르거나 자릿수가 어긋나면 값이 아니다`() {
        listOf(
            "1-2-3-4-5-6-7-8-9-0",
            "-1234567890",
            "1234567890-",
            "12345-67890",
            "123456789",
            "12345678901",
            "123-45-6789O", // 영문 대문자 O
            " 1234567890",
            "１２３４５６７８９０", // 전각 숫자
        ).forEach { shouldThrow<IllegalArgumentException> { BusinessRegistrationNumber.of(it) } }
    }

    /** 저장에서 읽어 오는 생성자도 열 자리만 받는다 — 표 CHECK 가 갈리면 여기서 크게 실패한다. */
    @Test
    fun `사업자번호 — 값 자체는 숫자 10자리만이다(하이픈을 담지 않는다)`() {
        shouldThrow<IllegalArgumentException> { BusinessRegistrationNumber("123-45-67890") }
        shouldThrow<IllegalArgumentException> { BusinessRegistrationNumber("") }
    }

    @Test
    fun `가림 — 상호 · 사업자번호 · 시평액 · 인증서 만료일이 toString 에 실리지 않는다`() {
        val details =
            BusinessDetails(
                tradeName = TradeName("합성상호표지"),
                businessRegistrationNumber = BusinessRegistrationNumber("1098765432"),
                headquartersRegion = HeadquartersRegion.JEJU,
                naraReadiness =
                    NaraReadiness.Registered(setOf(NaraRegistrationField.SERVICE), LocalDate.of(2031, 7, 9)),
            )
        val capacity = ConstructionCapacity(LicenseName("합성면허표지"), 987_654_321L, 2026)

        details.toString() shouldNotContain "합성상호표지"
        details.toString() shouldNotContain "1098765432"
        details.toString() shouldNotContain "2031"
        capacity.toString() shouldNotContain "987654321"
        capacity.toString() shouldNotContain "합성면허표지"
    }

    /** 행이 없는 계정은 사업자번호도 **미설정**이다 — 빈 문자열이나 0 을 지어내지 않는다. */
    @Test
    fun `빈 사업 정보의 사업자번호는 미설정이다`() {
        BusinessDetails.EMPTY.businessRegistrationNumber shouldBe null
    }

    /** 실패 문면도 출력 경로다 — 거부 사유가 입력값을 되울리면 로그로 샌다. */
    @Test
    fun `가림 — 거부 문면이 입력값을 싣지 않는다`() {
        val tooLong = "표지".repeat(maxName)
        val failure = shouldThrow<IllegalArgumentException> { TradeName(tooLong) }
        failure.message.orEmpty() shouldNotContain "표지"
        val badAmount = shouldThrow<IllegalArgumentException> { ConstructionCapacity(LicenseName("x"), -424242L, 2026) }
        badAmount.message.orEmpty() shouldNotContain "424242"
        val badNumber = shouldThrow<IllegalArgumentException> { BusinessRegistrationNumber.of("535353535353") }
        badNumber.message.orEmpty() shouldNotContain "535353"
    }
}
