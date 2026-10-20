package bidvector.workflow.notice

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import java.time.Instant

/**
 * 검색 텍스트 — **리터럴 부분 일치의 입력이고 패턴이 아니다.** `%`·`_`·`\` 는 와일드카드가
 * 아니라 그대로 찾을 글자다(그 이스케이프는 질의를 조립하는 어댑터가 진다 —
 * `NoticeSearchStatement`). 생성 경로가 [NoticeSearchParameters] 의 파싱 하나뿐이라
 * (`internal constructor` + `@ConsistentCopyVisibility`) 패턴 문자열이 이 타입으로 들어와
 * 질의 문면에 닿는 통로가 없다.
 */
@ConsistentCopyVisibility
data class SearchText internal constructor(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "SearchText는 공백일 수 없다" }
        require(value.length <= NoticeSearchLimits.MAX_TEXT_LENGTH) { "SearchText가 상한을 넘었다" }
    }
}

/**
 * 금액 한계(기초금액) — `bidvector.strategy.BudgetBound` 와 같은 모양이다. 한계가 하나도
 * 없으면 이 타입을 만들지 않는다(「금액 조건 없음」은 센티넬이 아니라 `null`).
 *
 * 값이 `BaseAmount` 라는 것이 **basis 선언 자체**다(`Basis.BASE_AMOUNT`, 타입이 낸다) —
 * 추정가격과 비교하는 legacy 검색 형태(capability map STR-16 「`폐기`」)는 이 타입으로
 * 표현할 수 없다. 과세·provenance 가 감시 경로의 한계값과 같은 규약인 것도 여기서 선다
 * (R-BASIS-01 「같은 쌍이 감시 경로와 검색 경로에서 같은 답을 낸다」).
 */
@ConsistentCopyVisibility
data class NoticeAmountBound internal constructor(
    val min: BaseAmount?,
    val max: BaseAmount?,
) {
    init {
        require(min != null || max != null) { "NoticeAmountBound는 한계가 하나도 없을 수 없다" }
    }
}

/**
 * 검증을 지난 검색 조건 — 생성 경로는 [parseNoticeSearchParameters] 하나다. 상한·역전·형식은
 * 그 함수가 이미 거부했으므로 이 타입을 쥔 코드는 다시 검사하지 않는다.
 */
@ConsistentCopyVisibility
data class NoticeSearchCriteria internal constructor(
    val text: SearchText?,
    val agencyText: SearchText?,
    val amount: NoticeAmountBound?,
    val deadlineFrom: Instant?,
    val deadlineTo: Instant?,
    val page: Int,
    val size: Int,
) {
    /** 건너뛸 행 수 — `page * size` 를 `Int` 로 곱하면 큰 `page` 에서 음수로 넘친다. */
    val offset: Long get() = page.toLong() * size.toLong()
}

/**
 * 계약이 선언한 한계와 금액 규약. **수치의 정본은 `openapi/bidvector-operator-api.yaml` 이고**
 * (`maxLength`·`maximum`·`default`), `OpenApiNoticeSearchContractTest` 가 문서와 이 상수를
 * 양방향 등식으로 잰다 — 두 자리가 조용히 갈라지지 않는다.
 *
 * 금액 규약 셋은 감시 경로의 한계값 구성과 **같은 값**이다(`bidvector.strategy.validate` 가
 * 한계값에 요구하는 `OperatorDeclared`·`INCLUSIVE`, 그리고 `StrategyFieldValue` 가 실제로
 * 그렇게 짓는다) — 검색이 다른 규약을 쓰면 같은 쌍에 다른 답이 난다.
 */
object NoticeSearchLimits {
    const val MAX_TEXT_LENGTH: Int = 200
    const val MAX_PAGE_SIZE: Int = 100
    const val DEFAULT_PAGE_SIZE: Int = 20
    val AMOUNT_CURRENCY: Currency = Currency.KRW
    val AMOUNT_VAT_TREATMENT: VatTreatment = VatTreatment.INCLUSIVE
    val AMOUNT_PROVENANCE: Provenance = Provenance.OperatorDeclared
}

/**
 * 운영자가 보낸 **원문 그대로의** 질의 값 여덟. 전부 문자열이라 「형식이 틀렸다」가 조용한
 * 강제 변환이 아니라 [NoticeSearchRequest.Rejected] 로 관측된다(D-6A3-19 와 같은 규율 —
 * 프레임워크의 기본 변환에 기대지 않는다).
 */
data class NoticeSearchParameters(
    val text: String? = null,
    val agencyText: String? = null,
    val amountMinWon: String? = null,
    val amountMaxWon: String? = null,
    val deadlineFrom: String? = null,
    val deadlineTo: String? = null,
    val page: String? = null,
    val size: String? = null,
)

/** 파싱 결과 — 거부를 조용히 삼키지 않도록 소진 `when` 으로 소비한다(예외가 아니다). */
sealed interface NoticeSearchRequest {
    data class Accepted(
        val criteria: NoticeSearchCriteria,
    ) : NoticeSearchRequest

    data class Rejected(
        val reason: NoticeSearchRejection,
    ) : NoticeSearchRequest
}

/**
 * 거부 사유 — **응답 본문에는 실리지 않는다**(400 `INVALID_REQUEST` 하나로 합친다, 편집
 * 경로의 `STRATEGY_VALUE_INVALID` 와 같은 관례 `OPEN-6A2B-VIOLATION-DETAIL`). 진단과 test
 * 단언의 어휘다.
 */
enum class NoticeSearchRejection {
    TEXT_TOO_LONG,
    TEXT_CONTROL_CHARACTER,
    AMOUNT_NOT_INTEGER,
    AMOUNT_NEGATIVE,
    AMOUNT_RANGE_INVERTED,
    DEADLINE_NOT_INSTANT,
    DEADLINE_RANGE_INVERTED,
    PAGE_NOT_INTEGER,
    PAGE_NEGATIVE,
    SIZE_NOT_INTEGER,
    SIZE_OUT_OF_RANGE,
}

/**
 * 원문 질의 → 조건. 거부 규칙은 **표**다(`NOTICE_SEARCH_REJECTIONS`) — 분기를 함수 본문에
 * 쌓지 않고, 첫 위반 하나만 낸다(어느 입력이 틀렸는지는 응답에 싣지 않으므로 전부 모을
 * 이유가 없다).
 *
 * 공백만인 텍스트는 **조건 없음**이다(빈 `q` 경계값) — 「아무것도 안 맞는다」가 조용히
 * 성립하지 않게 한다(D-6F4-8 과 같은 뿌리).
 *
 * **제어 문자는 거부한다**(verifier r1 F-2) — PostgreSQL 의 `text` 는 `U+0000` 을 담을 수
 * 없어 그 질의가 바인딩 단계에서 예외가 되고, 계약이 400 을 선언한 입력이 500 으로 나갔다.
 * 「DB 가 거부하는 값을 거부 규칙표가 먼저 거부한다」로 닫는다 — `U+0000` 하나를 특별히
 * 적는 대신 **제어 문자 전부**를 거부한다(열거를 늘리지 않는 쪽). 양끝 공백류는 trim 이
 * 먼저 걷어내므로 「공백만인 질의」는 여전히 조건 없음이다.
 */
fun parseNoticeSearchParameters(parameters: NoticeSearchParameters): NoticeSearchRequest {
    val rejection = NOTICE_SEARCH_REJECTIONS.firstNotNullOfOrNull { rule -> rule(parameters) }
    if (rejection != null) return NoticeSearchRequest.Rejected(rejection)
    val min = parameters.amountMinWon.parsedWon()
    val max = parameters.amountMaxWon.parsedWon()
    return NoticeSearchRequest.Accepted(
        NoticeSearchCriteria(
            text = parameters.text.searchTextOrNull(),
            agencyText = parameters.agencyText.searchTextOrNull(),
            amount = amountBoundOrNull(min, max),
            deadlineFrom = parameters.deadlineFrom.parsedInstant(),
            deadlineTo = parameters.deadlineTo.parsedInstant(),
            page = parameters.page.parsedInt() ?: 0,
            size = parameters.size.parsedInt() ?: NoticeSearchLimits.DEFAULT_PAGE_SIZE,
        ),
    )
}

/**
 * 거부 규칙표 — 순서가 곧 우선순위다. 형식(정수·시각) 검사가 값 범위 검사보다 앞에 온다:
 * 뒤 규칙들이 이미 파싱된 값을 전제로 하기 때문이다.
 */
private val NOTICE_SEARCH_REJECTIONS: List<(NoticeSearchParameters) -> NoticeSearchRejection?> =
    listOf(
        { p -> NoticeSearchRejection.TEXT_TOO_LONG.takeIf { p.text.tooLong() || p.agencyText.tooLong() } },
        { p -> NoticeSearchRejection.TEXT_CONTROL_CHARACTER.takeIf { p.hasControlCharacterText() } },
        { p -> NoticeSearchRejection.AMOUNT_NOT_INTEGER.takeIf { p.amountMinWon.notInteger() } },
        { p -> NoticeSearchRejection.AMOUNT_NOT_INTEGER.takeIf { p.amountMaxWon.notInteger() } },
        { p -> NoticeSearchRejection.AMOUNT_NEGATIVE.takeIf { p.amountMinWon.negative() } },
        { p -> NoticeSearchRejection.AMOUNT_NEGATIVE.takeIf { p.amountMaxWon.negative() } },
        { p -> NoticeSearchRejection.AMOUNT_RANGE_INVERTED.takeIf { amountInverted(p) } },
        { p -> NoticeSearchRejection.DEADLINE_NOT_INSTANT.takeIf { p.deadlineFrom.notInstant() } },
        { p -> NoticeSearchRejection.DEADLINE_NOT_INSTANT.takeIf { p.deadlineTo.notInstant() } },
        { p -> NoticeSearchRejection.DEADLINE_RANGE_INVERTED.takeIf { deadlineInverted(p) } },
        { p -> NoticeSearchRejection.PAGE_NOT_INTEGER.takeIf { p.page.notInteger32() } },
        { p -> NoticeSearchRejection.PAGE_NEGATIVE.takeIf { (p.page.parsedInt() ?: 0) < 0 } },
        { p -> NoticeSearchRejection.SIZE_NOT_INTEGER.takeIf { p.size.notInteger32() } },
        { p -> NoticeSearchRejection.SIZE_OUT_OF_RANGE.takeIf { sizeOutOfRange(p) } },
    )

private fun NoticeSearchParameters.hasControlCharacterText(): Boolean =
    text.hasControlCharacter() || agencyText.hasControlCharacter()

private fun amountInverted(parameters: NoticeSearchParameters): Boolean {
    val min = parameters.amountMinWon.parsedWon()
    val max = parameters.amountMaxWon.parsedWon()
    return min != null && max != null && min > max
}

private fun deadlineInverted(parameters: NoticeSearchParameters): Boolean {
    val from = parameters.deadlineFrom.parsedInstant()
    val to = parameters.deadlineTo.parsedInstant()
    return from != null && to != null && from.isAfter(to)
}

private fun sizeOutOfRange(parameters: NoticeSearchParameters): Boolean {
    val size = parameters.size.parsedInt()
    return size != null && (size < 1 || size > NoticeSearchLimits.MAX_PAGE_SIZE)
}

private fun amountBoundOrNull(
    min: Long?,
    max: Long?,
): NoticeAmountBound? =
    if (min == null && max == null) {
        null
    } else {
        NoticeAmountBound(min?.let(::declaredBaseAmount), max?.let(::declaredBaseAmount))
    }

/** 한계값의 구성 — 감시 경로와 같은 통화·과세·provenance(`NoticeSearchLimits`). */
private fun declaredBaseAmount(won: Long): BaseAmount =
    BaseAmount(
        won,
        NoticeSearchLimits.AMOUNT_CURRENCY,
        NoticeSearchLimits.AMOUNT_VAT_TREATMENT,
        NoticeSearchLimits.AMOUNT_PROVENANCE,
    )
