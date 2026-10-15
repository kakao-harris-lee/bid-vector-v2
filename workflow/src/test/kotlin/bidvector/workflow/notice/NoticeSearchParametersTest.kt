package bidvector.workflow.notice

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Basis
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import bidvector.sharedkernel.compareKnownVat
import bidvector.sharedkernel.export
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

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
    fun `offset 은 큰 page 에서도 넘치지 않는다`() {
        val criteria = accepted(NoticeSearchParameters(page = Int.MAX_VALUE.toString(), size = "100"))

        criteria.offset shouldBe Int.MAX_VALUE.toLong() * 100L
    }
}
