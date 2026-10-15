package bidvector.app.http

import bidvector.app.wiring.NoticeSearchQuery
import bidvector.procurement.Notice
import bidvector.sharedkernel.export
import bidvector.workflow.notice.NoticeSearchPage
import bidvector.workflow.notice.NoticeSearchParameters
import bidvector.workflow.notice.NoticeSearchRequest
import bidvector.workflow.notice.parseNoticeSearchParameters
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 전체 건수 — 페이지 길이와 다른 값이다(legacy `GET /projects/` 가 남긴 채택 가능한 계약 형태). */
const val TOTAL_COUNT_HEADER = "X-Total-Count"

/** 빈 결과의 두 뜻 — 추적표 STR-16 「"조건에 맞는 것이 없다"와 "아직 수집되지 않았다"」. */
const val RESULT_STATE_HEADER = "X-Result-State"

/**
 * 공고 검색 endpoint(M7/7C ①, STR-16 검색 절반) — **읽기 전용**이다. 컨트롤러는
 * `query.search(criteria)` 하나만 부르고 조건 판정을 복제하지 않는다.
 *
 * **질의 값을 전부 `String?` 으로 받는다.** `Long?`·`Int?`·`Instant?` 로 직접 바인딩하면
 * 형식 오류가 프레임워크의 타입 변환 실패(500 경로)로 새거나 조용히 강제 변환된다 — 원문을
 * 받아 순수 파싱([parseNoticeSearchParameters])이 **명시** 검증한다(D-6A3-19 와 같은 규율).
 * 거부는 [InvalidNoticeSearchException] 하나로 400 `INVALID_REQUEST` 가 된다(오류 형태를
 * 만드는 자리는 `ErrorBody.kt` 하나다 — 이 컨트롤러는 `ErrorBody` 를 짓지 않는다).
 */
@RestController
class NoticeSearchController(
    private val notices: NoticeSearchQuery,
) {
    @Suppress("LongParameterList")
    @GetMapping("/api/notices")
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) agency: String?,
        @RequestParam(required = false) amountMin: String?,
        @RequestParam(required = false) amountMax: String?,
        @RequestParam(required = false) deadlineFrom: String?,
        @RequestParam(required = false) deadlineTo: String?,
        @RequestParam(required = false) page: String?,
        @RequestParam(required = false) size: String?,
    ): ResponseEntity<List<NoticeSummaryResponse>> {
        val parameters =
            NoticeSearchParameters(
                text = q,
                agencyText = agency,
                amountMinWon = amountMin,
                amountMaxWon = amountMax,
                deadlineFrom = deadlineFrom,
                deadlineTo = deadlineTo,
                page = page,
                size = size,
            )
        return when (val parsed = parseNoticeSearchParameters(parameters)) {
            is NoticeSearchRequest.Rejected -> throw InvalidNoticeSearchException(parsed.reason.name)
            is NoticeSearchRequest.Accepted -> respond(notices.search(parsed.criteria))
        }
    }

    private fun respond(page: NoticeSearchPage): ResponseEntity<List<NoticeSummaryResponse>> =
        ResponseEntity
            .ok()
            .header(TOTAL_COUNT_HEADER, page.totalCount.toString())
            .header(RESULT_STATE_HEADER, page.state.name)
            .body(page.notices.map(NoticeSummaryResponse::from))
}

/**
 * 공고 요약 하나 — **평탄하다**(D-6A1-20 ⓑ). 금액은 값만 싣지 않고 basis·통화·과세·
 * provenance 와 함께 싣는다(R-BASIS-06 — 값만 있고 출처가 없는 응답을 만들 수 없다).
 *
 * 원시 관측·감사 행·개찰 축·투찰자 이름은 **이 타입에 자리가 없다** — `Notice` 가 그 값을
 * 갖고 있지 않고(검색 질의도 `notice` 표만 읽는다), 키 집합 등식 test 가 그 사실을 잠근다.
 */
data class NoticeSummaryResponse(
    val noticeNumber: String,
    val noticeRound: String,
    val status: String,
    val title: String?,
    val demandAgencyName: String?,
    val noticeAgencyName: String?,
    val baseAmountWon: Long?,
    val baseAmountCurrency: String?,
    val baseAmountBasis: String?,
    val baseAmountVatTreatment: String?,
    val baseAmountProvenance: String?,
    val deadlineAt: String?,
) {
    companion object {
        fun from(notice: Notice): NoticeSummaryResponse {
            val base =
                notice.baseAmount
                    ?.amount
                    ?.export()
            return NoticeSummaryResponse(
                noticeNumber = notice.id.number.value,
                noticeRound = notice.id.round.value,
                status = notice.status.name,
                title = notice.title?.value,
                demandAgencyName = notice.demandAgency?.name?.value,
                noticeAgencyName = notice.noticeAgency?.name?.value,
                baseAmountWon = base?.won,
                baseAmountCurrency = base?.currency?.name,
                baseAmountBasis = base?.basis?.name,
                baseAmountVatTreatment = base?.vatTreatment?.name,
                baseAmountProvenance = base?.provenance?.let(::provenanceLabel),
                deadlineAt = notice.deadlineAt?.toString(),
            )
        }
    }
}
