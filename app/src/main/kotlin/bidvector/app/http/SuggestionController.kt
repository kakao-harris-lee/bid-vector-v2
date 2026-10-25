package bidvector.app.http

import bidvector.app.wiring.SuggestionQuery
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.UncertainReason
import bidvector.workflow.suggestion.SUGGESTION_UNJUDGED_AXES
import bidvector.workflow.suggestion.Suggestion
import bidvector.workflow.suggestion.SuggestionList
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 이 결과를 계산한 전략 개정 — 화면이 저장 뒤 `readStrategy` 개정과 비교한다(STR-07). */
const val STRATEGY_REVISION_HEADER = "X-Strategy-Revision"

/** 계산 시각(주입 시계) — 원값이다. 「몇 분 전」 같은 표시는 화면이 한다. */
const val COMPUTED_AT_HEADER = "X-Computed-At"

/**
 * 제안 목록 endpoint(M7/7C①b, STR-06·STR-07 · QUAL-02 면허 축) — **읽기 전용**이다. 컨트롤러는
 * `query.list()` 하나만 부르고 게이트·비교·상한을 복제하지 않는다.
 *
 * 질의 매개변수가 **없다**. 그래서 질의를 읽지 않고, 7C ① 이 질의 읽기 자리에서 닫은 실패 부류
 * (잘못된 퍼센트 인코딩)도 이 경로에서는 생기지 않는다 — 읽지 않는 질의는 해석되지 않는다.
 *
 * 본문은 **최상위 배열**이고 신선도·총수·빈 결과의 뜻은 헤더다. 봉투 객체를 두면 `items` 가 object
 * 배열이 되어 평탄 계약(D-6A1-38)에 걸린다 — 7C ① 과 같은 관례다.
 */
@RestController
class SuggestionController(
    private val suggestions: SuggestionQuery,
) {
    @GetMapping("/api/suggestions")
    fun list(): ResponseEntity<List<SuggestedNoticeResponse>> {
        val result = suggestions.list()
        return ResponseEntity
            .ok()
            .header(STRATEGY_REVISION_HEADER, result.strategyRevision.value.toString())
            .header(COMPUTED_AT_HEADER, result.computedAt.toString())
            .header(TOTAL_COUNT_HEADER, result.totalCount.toString())
            .header(RESULT_STATE_HEADER, result.state.name)
            .body(result.toResponses())
    }
}

private fun SuggestionList.toResponses(): List<SuggestedNoticeResponse> = suggestions.map(SuggestedNoticeResponse::from)

/**
 * 공고 카드의 속성 — 검색 결과([NoticeSummaryResponse])와 제안([SuggestedNoticeResponse])이 **같은
 * 카드 형태**임을 컴파일러가 잰다. 제안 쪽은 이 인터페이스를 요약 값에 **위임**하므로 카드 매핑은
 * [NoticeSummaryResponse.from] 하나뿐이다(복제 없음).
 *
 * `ComplexInterface` 를 끈다 — 이 인터페이스는 행위가 없는 **카드 속성 목록**이고 크기는 계약의
 * `NoticeSummary` 속성 수(12)가 정한다. 나누면 같은 카드가 두 조각이 된다.
 */
@Suppress("ComplexInterface")
interface NoticeCard {
    val noticeNumber: String
    val noticeRound: String
    val status: String
    val title: String?
    val demandAgencyName: String?
    val noticeAgencyName: String?
    val baseAmountWon: Long?
    val baseAmountCurrency: String?
    val baseAmountBasis: String?
    val baseAmountVatTreatment: String?
    val baseAmountProvenance: String?
    val deadlineAt: String?
}

/**
 * 제안 하나 — 카드 속성(위임) + **면허 축 판정의 실제 값**(QUAL-02 의 한 조각).
 *
 * `Ineligible` 은 제안 목록에 오지 않으므로 [licenseVerdict] 는 `ELIGIBLE`·`UNCERTAIN` 둘뿐이다.
 * [licenseUncertainReason] 은 `UNCERTAIN` 일 때만 값이 있다. 지역·실적은 **판정 자체가 없다** —
 * [unjudgedAxes] 가 그 사실을 늘 싣는다(화면이 면허 통과를 자격 전체 통과로 보이지 않게).
 *
 * data class 가 아니다 — 응답 값을 비교할 일이 없고, 위임 인터페이스를 data class 로 두면 자동
 * `equals` 가 생긴다(비교 구조 게이트의 허용 목록을 늘리지 않는다).
 */
class SuggestedNoticeResponse private constructor(
    summary: NoticeSummaryResponse,
    val licenseVerdict: String,
    val licenseUncertainReason: String?,
    val unjudgedAxes: List<String>,
) : NoticeCard by summary {
    companion object {
        fun from(suggestion: Suggestion): SuggestedNoticeResponse =
            SuggestedNoticeResponse(
                summary = NoticeSummaryResponse.from(suggestion.notice),
                licenseVerdict = suggestion.license.verdictLabel(),
                licenseUncertainReason = (suggestion.license as? LicenseVerdict.Uncertain)?.reason?.label(),
                unjudgedAxes = SUGGESTION_UNJUDGED_AXES.map { it.name },
            )
    }
}

/** 제안에 오는 면허 판정의 어휘. `Ineligible` 은 use case 가 거르므로 여기 닿으면 조립 결함이다. */
private fun LicenseVerdict.verdictLabel(): String =
    when (this) {
        is LicenseVerdict.Eligible -> "ELIGIBLE"
        is LicenseVerdict.Uncertain -> "UNCERTAIN"
        is LicenseVerdict.Ineligible -> error("면허 미달 공고는 제안 목록에 오지 않는다")
    }

/**
 * 미결 사유 어휘 — `UncertainReason` 의 다섯을 그대로 옮긴다(지어내지 않는다). `is` 로 가른다 — 객체 `==` 는
 * `Intrinsics.areEqual` 로 컴파일되어 비교 구조 게이트의 허용 목록을 늘린다.
 */
private fun UncertainReason.label(): String =
    when (this) {
        is UncertainReason.RequirementDataAbsent -> "REQUIREMENT_DATA_ABSENT"
        is UncertainReason.RequirementUnparsable -> "REQUIREMENT_UNPARSABLE"
        is UncertainReason.OperatorLicensesNotDeclared -> "OPERATOR_LICENSES_NOT_DECLARED"
        is UncertainReason.CollectionFailed -> "COLLECTION_FAILED"
        is UncertainReason.PermittedIndustryCombinationRuleUndecided -> "PERMITTED_INDUSTRY_COMBINATION_RULE_UNDECIDED"
    }
