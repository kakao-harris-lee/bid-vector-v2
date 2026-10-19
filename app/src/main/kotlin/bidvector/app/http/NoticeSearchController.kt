package bidvector.app.http

import bidvector.app.wiring.NoticeSearchQuery
import bidvector.procurement.Notice
import bidvector.sharedkernel.export
import bidvector.workflow.notice.NoticeSearchPage
import bidvector.workflow.notice.NoticeSearchParameters
import bidvector.workflow.notice.NoticeSearchRequest
import bidvector.workflow.notice.parseNoticeSearchParameters
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 전체 건수 — 페이지 길이와 다른 값이다(legacy `GET /projects/` 가 남긴 채택 가능한 계약 형태). */
const val TOTAL_COUNT_HEADER = "X-Total-Count"

/** 빈 결과의 두 뜻 — 추적표 STR-16 「"조건에 맞는 것이 없다"와 "아직 수집되지 않았다"」. */
const val RESULT_STATE_HEADER = "X-Result-State"

/**
 * 계약이 든 질의 매개변수 이름 — 반복 거부의 판정 대상이다. 문서와의 등식은
 * `OpenApiNoticeSearchContractTest` 의 「문서가 든 질의 매개변수 전수」가 잰다.
 */
private val DECLARED_PARAMETERS =
    listOf("q", "agency", "amountMin", "amountMax", "deadlineFrom", "deadlineTo", "page", "size")

/**
 * 공고 검색 endpoint(M7/7C ①, STR-16 검색 절반) — **읽기 전용**이다. 컨트롤러는
 * `query.search(criteria)` 하나만 부르고 조건 판정을 복제하지 않는다.
 *
 * **질의 값을 전부 `String?` 으로 받는다.** `Long?`·`Int?`·`Instant?` 로 직접 바인딩하면
 * 형식 오류가 프레임워크의 타입 변환 실패(500 경로)로 새거나 조용히 강제 변환된다 — 원문을
 * 받아 순수 파싱([parseNoticeSearchParameters])이 **명시** 검증한다(D-6A3-19 와 같은 규율).
 * 거부는 [InvalidNoticeSearchException] 하나로 400 `INVALID_REQUEST` 가 된다(오류 형태를
 * 만드는 자리는 `ErrorBody.kt` 하나다 — 이 컨트롤러는 `ErrorBody` 를 짓지 않는다).
 *
 * **질의를 읽는 자리는 [readDeclaredParameters] 하나다**(verifier r3 R3-M-1). `@RequestParam`
 * 으로 받으면 그 읽기가 **핸들러 바깥**(프레임워크의 인자 해석)에서 일어나, 잘못된 퍼센트
 * 인코딩처럼 **읽기 자체가 깨지는** 입력이 전역 폴백(500)으로 샜다. 읽기를 이 안으로 들이면
 * 그 실패를 같은 거부 하나로 옮길 수 있고, 다른 경로의 거동을 건드리지 않는다.
 */
@RestController
class NoticeSearchController(
    private val notices: NoticeSearchQuery,
) {
    @GetMapping("/api/notices")
    fun search(request: HttpServletRequest): ResponseEntity<List<NoticeSummaryResponse>> {
        val values = readDeclaredParameters(request)
        rejectRepeated(values)
        val parameters =
            NoticeSearchParameters(
                text = values.first("q"),
                agencyText = values.first("agency"),
                amountMinWon = values.first("amountMin"),
                amountMaxWon = values.first("amountMax"),
                deadlineFrom = values.first("deadlineFrom"),
                deadlineTo = values.first("deadlineTo"),
                page = values.first("page"),
                size = values.first("size"),
            )
        return when (val parsed = parseNoticeSearchParameters(parameters)) {
            is NoticeSearchRequest.Rejected -> throw InvalidNoticeSearchException(parsed.reason.name)
            is NoticeSearchRequest.Accepted -> respond(notices.search(parsed.criteria))
        }
    }

    /**
     * 계약이 든 이름들의 **원문 값 목록** — 이 요청의 질의를 읽는 **유일한 자리**다.
     *
     * 서블릿 컨테이너는 질의 전체를 한 번에 해석한다. 그래서 `%` 뒤에 16진 두 자리가 없는 꼴이나
     * UTF-8 로 디코딩되지 않는 바이트열이 **어느 매개변수에 있든**(계약 밖 이름이어도) 이 읽기가
     * 깨진다. 계약은 그 입력에 400 을 선언하므로 여기서 거부로 옮긴다 — 컨테이너가 던지는 타입을
     * 이 층이 이름으로 들지 않는다(서블릿 구현의 좌표이고, HTTP 층의 바깥 참조 허용 밖이라
     * 전송 표면 게이트가 실측으로 막는다). **자리가 술어다**: 이 호출이 실패했다는 것이 곧 질의
     * 해석이 실패했다는 사실이다.
     *
     * 잡는 타입은 실측한 가장 좁은 JDK 상위 타입이다. 컨테이너가 다른 부류를 던지게 바뀌면 출하
     * 조립 E2E 가 그 꼴을 500 으로 잡아 붉어진다(그 회귀는 숨지 않는다).
     */
    private fun readDeclaredParameters(request: HttpServletRequest): Map<String, List<String>> =
        try {
            DECLARED_PARAMETERS.associateWith { name -> request.getParameterValues(name)?.toList().orEmpty() }
        } catch (failure: IllegalStateException) {
            throw InvalidNoticeSearchException("질의를 해석할 수 없다", failure)
        }

    /**
     * 같은 매개변수를 두 번 보내면 거부한다(verifier r1 L-3) — 값 둘을 하나로 합치면 **리터럴
     * 검색어에 쉼표가 들어간 질의**와 구별되지 않는다. 계약에 없는 조용한 결합이라 400 으로 끊는다.
     *
     * 판정 대상은 **계약이 든 이름들**이다 — 알 수 없는 매개변수는 지금처럼 무시한다(그 거동은
     * 바꾸지 않는다). 서블릿 요청의 값 목록이 합쳐지기 전의 사실을 들고 있다.
     */
    private fun rejectRepeated(values: Map<String, List<String>>) {
        val repeated = values.filterValues { it.size > 1 }.keys
        if (repeated.isNotEmpty()) {
            throw InvalidNoticeSearchException("같은 매개변수가 반복됐다: $repeated")
        }
    }

    /** 값이 없으면 `null` — `@RequestParam(required = false)` 이 주던 것과 같은 모양이다. */
    private fun Map<String, List<String>>.first(name: String): String? = this[name]?.firstOrNull()

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
