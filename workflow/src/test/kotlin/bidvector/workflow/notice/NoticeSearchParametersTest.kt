package bidvector.workflow.notice

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Basis
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.compareKnownVat
import bidvector.sharedkernel.export
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * [parseNoticeSearchParameters] — 계약이 선언한 경계 전부를 **HTTP 없이** 잰다(순수 함수).
 * 설계 검토 (2) 의 우회 ③(상한 초과 size)·④(역전된 범위)·⑥(아주 긴 `q`)이 여기서 닫히고,
 * 금액 축은 basis·과세·provenance 가 감시 경로와 같은 규약인지를 `compareKnownVat` 로
 * 직접 잰다(문면 대조가 아니라 실제 비교 성립 여부).
 */
class NoticeSearchParametersTest {
    private fun accepted(parameters: NoticeSearchParameters): NoticeSearchCriteria =
        parseNoticeSearchParameters(parameters).shouldBeInstanceOf<NoticeSearchRequest.Accepted>().criteria

    private fun rejection(parameters: NoticeSearchParameters): NoticeSearchRejection =
        parseNoticeSearchParameters(parameters).shouldBeInstanceOf<NoticeSearchRequest.Rejected>().reason

    @Test
    fun `질의가 비어 있으면 조건 없음 + 기본 페이지다`() {
        val criteria = accepted(NoticeSearchParameters())

        criteria.text shouldBe null
        criteria.agencyText shouldBe null
        criteria.amount shouldBe null
        criteria.deadlineFrom shouldBe null
        criteria.deadlineTo shouldBe null
        criteria.page shouldBe 0
        criteria.size shouldBe NoticeSearchLimits.DEFAULT_PAGE_SIZE
    }

    @Test
    fun `공백만인 텍스트는 조건이 아니다 — 아무것도 안 맞는 조건으로 바뀌지 않는다`() {
        val criteria = accepted(NoticeSearchParameters(text = "   ", agencyText = "\t"))

        criteria.text shouldBe null
        criteria.agencyText shouldBe null
    }

    @Test
    fun `텍스트는 trim 된 리터럴로 들어온다 — 와일드카드도 글자다`() {
        val criteria = accepted(NoticeSearchParameters(text = "  100%_순수  "))

        criteria.text shouldBe SearchText("100%_순수")
    }

    @Test
    fun `우회 ⑥ — 상한을 넘는 텍스트는 거부다(두 텍스트 칸 모두)`() {
        val overLimit = "가".repeat(NoticeSearchLimits.MAX_TEXT_LENGTH + 1)
        val atLimit = "가".repeat(NoticeSearchLimits.MAX_TEXT_LENGTH)

        rejection(NoticeSearchParameters(text = overLimit)) shouldBe NoticeSearchRejection.TEXT_TOO_LONG
        rejection(NoticeSearchParameters(agencyText = overLimit)) shouldBe NoticeSearchRejection.TEXT_TOO_LONG
        accepted(NoticeSearchParameters(text = atLimit)).text shouldBe SearchText(atLimit)
    }

    @Test
    fun `우회 ③ — size 는 상한 초과도 0 도 거부다(조용히 절삭하지 않는다)`() {
        val overCap = (NoticeSearchLimits.MAX_PAGE_SIZE + 1).toString()

        rejection(NoticeSearchParameters(size = overCap)) shouldBe NoticeSearchRejection.SIZE_OUT_OF_RANGE
        rejection(NoticeSearchParameters(size = "100000")) shouldBe NoticeSearchRejection.SIZE_OUT_OF_RANGE
        rejection(NoticeSearchParameters(size = "0")) shouldBe NoticeSearchRejection.SIZE_OUT_OF_RANGE
        rejection(NoticeSearchParameters(size = "-1")) shouldBe NoticeSearchRejection.SIZE_OUT_OF_RANGE
        accepted(NoticeSearchParameters(size = NoticeSearchLimits.MAX_PAGE_SIZE.toString())).size shouldBe
            NoticeSearchLimits.MAX_PAGE_SIZE
    }

    @Test
    fun `우회 ④ — 역전된 금액 범위와 역전된 마감 범위는 거부다`() {
        rejection(NoticeSearchParameters(amountMinWon = "200", amountMaxWon = "100")) shouldBe
            NoticeSearchRejection.AMOUNT_RANGE_INVERTED
        rejection(
            NoticeSearchParameters(deadlineFrom = "2026-10-20T00:00:00Z", deadlineTo = "2026-10-19T00:00:00Z"),
        ) shouldBe NoticeSearchRejection.DEADLINE_RANGE_INVERTED
    }

    @Test
    fun `같은 값의 범위는 거부가 아니다 — 한 점 구간`() {
        val criteria =
            accepted(
                NoticeSearchParameters(
                    amountMinWon = "100",
                    amountMaxWon = "100",
                    deadlineFrom = "2026-10-20T00:00:00Z",
                    deadlineTo = "2026-10-20T00:00:00Z",
                ),
            )

        criteria.amount shouldNotBe null
        criteria.deadlineFrom shouldBe Instant.parse("2026-10-20T00:00:00Z")
    }

    /**
     * verifier r1 F-2 — PostgreSQL `text` 가 담을 수 없는 `U+0000` 이 질의로 들어오면 계약이
     * 400 을 선언한 입력이 바인딩 예외(500)로 나갔다. 규칙표가 먼저 거부한다.
     */
    @Test
    fun `제어 문자가 든 텍스트는 거부다 — NUL 포함`() {
        rejection(NoticeSearchParameters(text = "\u0000")) shouldBe NoticeSearchRejection.TEXT_CONTROL_CHARACTER
        rejection(NoticeSearchParameters(text = "도로\u0000포장")) shouldBe
            NoticeSearchRejection.TEXT_CONTROL_CHARACTER
        rejection(NoticeSearchParameters(agencyText = "조달\u0000청")) shouldBe
            NoticeSearchRejection.TEXT_CONTROL_CHARACTER
        rejection(NoticeSearchParameters(text = "도로\u0001포장")) shouldBe
            NoticeSearchRejection.TEXT_CONTROL_CHARACTER
        rejection(NoticeSearchParameters(text = "도로\t포장")) shouldBe NoticeSearchRejection.TEXT_CONTROL_CHARACTER
    }

    /** 위 규칙이 「공백만인 질의는 조건 없음」을 깨지 않는다 — 양끝 공백류는 trim 이 먼저 걷는다. */
    @Test
    fun `공백류만 있는 텍스트는 제어 문자 규칙에 걸리지 않는다`() {
        val criteria = accepted(NoticeSearchParameters(text = "\t\n ", agencyText = "\u000B"))

        criteria.text shouldBe null
        criteria.agencyText shouldBe null
    }

    @Test
    fun `형식이 틀린 값은 조용히 변환되지 않고 거부다`() {
        rejection(NoticeSearchParameters(amountMinWon = "1.5")) shouldBe NoticeSearchRejection.AMOUNT_NOT_INTEGER
        rejection(NoticeSearchParameters(amountMaxWon = "일억")) shouldBe NoticeSearchRejection.AMOUNT_NOT_INTEGER
        rejection(NoticeSearchParameters(amountMinWon = "-1")) shouldBe NoticeSearchRejection.AMOUNT_NEGATIVE
        rejection(NoticeSearchParameters(deadlineFrom = "2026-10-20")) shouldBe
            NoticeSearchRejection.DEADLINE_NOT_INSTANT
        rejection(NoticeSearchParameters(page = "첫장")) shouldBe NoticeSearchRejection.PAGE_NOT_INTEGER
        rejection(NoticeSearchParameters(page = "-1")) shouldBe NoticeSearchRejection.PAGE_NEGATIVE
        rejection(NoticeSearchParameters(size = "스물")) shouldBe NoticeSearchRejection.SIZE_NOT_INTEGER
    }

    @Test
    fun `금액 범위의 basis 는 기초금액이고 과세·provenance 는 감시 경로와 같은 규약이다`() {
        val criteria = accepted(NoticeSearchParameters(amountMinWon = "1000", amountMaxWon = "2000"))
        val bound = requireNotNull(criteria.amount)
        val min = requireNotNull(bound.min).export()

        min.won shouldBe 1000L
        min.basis shouldBe Basis.BASE_AMOUNT
        min.currency shouldBe Currency.KRW
        min.vatTreatment shouldBe VatTreatment.INCLUSIVE
        min.provenance shouldBe Provenance.OperatorDeclared
        requireNotNull(bound.max).export().won shouldBe 2000L
    }

    /**
     * R-BASIS-01 — 한계값이 **실제로 비교에 성립하는지**를 잰다. 공고의 기초금액(게시값,
     * 과세 포함)과 `compareKnownVat` 가 `Known` 을 내야 감시 경로와 같은 답이 가능하다.
     * 과세가 다른 금액에는 성립하지 않는 것도 같은 자리에서 고정한다.
     */
    @Test
    fun `한계값은 공고 기초금액과 비교가 성립한다 — 과세가 다르면 성립하지 않는다`() {
        val bound = requireNotNull(accepted(NoticeSearchParameters(amountMinWon = "1000")).amount).min
        val published = Provenance.Published(NoticeRound.of("001"))
        val comparable = BaseAmount(1500L, Currency.KRW, VatTreatment.INCLUSIVE, published)
        val otherVat = BaseAmount(1500L, Currency.KRW, VatTreatment.EXCLUSIVE, published)
        val undeclared = BaseAmount(1500L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.Undeclared)

        compareKnownVat(requireNotNull(bound), comparable).shouldBeInstanceOf<Fact.Known<Int>>()
        compareKnownVat(requireNotNull(bound), otherVat).shouldBeInstanceOf<Fact.Absent>()
        compareKnownVat(requireNotNull(bound), undeclared).shouldBeInstanceOf<Fact.Absent>()
    }

    @Test
    fun `숫자 칸은 공백과 더하기 부호를 받지 않는다`() {
        rejection(NoticeSearchParameters(amountMinWon = " 5")) shouldBe NoticeSearchRejection.AMOUNT_NOT_INTEGER
        rejection(NoticeSearchParameters(amountMaxWon = "+5")) shouldBe NoticeSearchRejection.AMOUNT_NOT_INTEGER
        rejection(NoticeSearchParameters(page = "+1")) shouldBe NoticeSearchRejection.PAGE_NOT_INTEGER
        rejection(NoticeSearchParameters(size = " 10")) shouldBe NoticeSearchRejection.SIZE_NOT_INTEGER
        // 공백만인 값은 여전히 「없음」이다(거동 무변경).
        accepted(NoticeSearchParameters(amountMinWon = "  ")).amount shouldBe null
    }

    /**
     * code-reviewer r1 L-4 — 한계값의 과세·provenance 가 **감시 경로가 요구하는 그 값**인지를
     * 전략 커널의 검증으로 직접 잰다(상수끼리의 비교가 아니다). 검색이 다른 규약으로 바뀌면
     * 같은 값이 전략 한계값으로서 거부되므로 이 단언이 붉어진다.
     */
    @Test
    fun `한계값은 전략 커널이 예산 한계로 받아들이는 구성이다`() {
        val bound = requireNotNull(accepted(NoticeSearchParameters(amountMinWon = "1000")).amount).min
        val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>

        val draft = StrategyDraft(focusCategories = listOf("CAT-1"), minBudget = bound)

        val result = validate(draft, StrategyRevision(1), policy)

        result.shouldBeInstanceOf<StrategyValidation.Valid>()
    }

    @Test
    fun `offset 은 큰 page 에서도 넘치지 않는다`() {
        val criteria = accepted(NoticeSearchParameters(page = Int.MAX_VALUE.toString(), size = "100"))

        criteria.offset shouldBe Int.MAX_VALUE.toLong() * 100L
    }
}
