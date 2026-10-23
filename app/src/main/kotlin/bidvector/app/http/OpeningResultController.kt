package bidvector.app.http

import bidvector.app.wiring.OpeningResultQuery
import bidvector.procurement.NoticeId
import bidvector.sharedkernel.export
import bidvector.workflow.opening.OpeningProgress
import bidvector.workflow.opening.OpeningResultQueryRequest
import bidvector.workflow.opening.OpeningResultView
import bidvector.workflow.opening.ProvisionalRankOne
import bidvector.workflow.opening.parseOpeningResultQuery
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 계약이 든 질의 매개변수 이름 — 반복 거부의 판정 대상이다. 문서와의 등식은
 * `OpenApiOpeningResultContractTest` 가 잰다.
 */
private val DECLARED_PARAMETERS = listOf("noticeNumber", "noticeRound")

/**
 * 공고 단위 개찰 결과 조회 endpoint(M7/7C⑤-a — SET-02 표시 절반, COL-02 표면) — **읽기
 * 전용**이다. 컨트롤러는 조회기를 한 번 부르고 상태 판정을 복제하지 않는다.
 *
 * 질의 값을 전부 `String?` 으로 받아 순수 파싱([parseOpeningResultQuery])이 **명시** 검증한다
 * (7C① 과 같은 규율). 거부는 [InvalidOpeningResultQueryException] 하나로 400
 * `INVALID_REQUEST` 가 된다 — 오류 **형태**를 만드는 자리는 `ErrorBody.kt` 하나다.
 */
@RestController
class OpeningResultController(
    private val openingResults: OpeningResultQuery,
) {
    @GetMapping("/api/opening-results")
    fun read(request: HttpServletRequest): OpeningResultResponse {
        val values =
            try {
                request.readDeclaredValues(DECLARED_PARAMETERS)
            } catch (failure: IllegalStateException) {
                throw InvalidOpeningResultQueryException("질의를 해석할 수 없다").apply { initCause(failure) }
            }
        val repeated = values.repeatedNames()
        if (repeated.isNotEmpty()) {
            throw InvalidOpeningResultQueryException("같은 매개변수가 반복됐다: $repeated")
        }
        val parsed =
            parseOpeningResultQuery(
                noticeNumber = values.firstValue("noticeNumber"),
                noticeRound = values.firstValue("noticeRound"),
            )
        return when (parsed) {
            is OpeningResultQueryRequest.Rejected -> throw InvalidOpeningResultQueryException(parsed.reason.name)
            is OpeningResultQueryRequest.Accepted ->
                OpeningResultResponse.of(parsed.id, openingResults.read(parsed.id))
        }
    }
}

/**
 * 개찰 결과 응답 하나 — **평탄하다**(D-6A1-38 게이트: 모든 속성이 스칼라다). 그래서 잠정 축과
 * 확정 축의 분리는 중첩 object 가 아니라 **접두사와 상태**가 진다(SET-02 acceptance 「잠정이
 * 확정 필드를 바꾸지 않는다」를 응답 모양에서도 지킨다).
 *
 * **다른 업체의 상호 칸이 없다**(D-7C5-3) — 사업자등록번호·대표자명은 수집 경계에서 이미
 * 버려져 읽을 열조차 없고, 상호 두 열은 조회 질의가 읽지 않는다. 키 집합 등식 test 와 표지
 * 글자 단언이 그 부재를 잠근다.
 *
 * 금액은 값만 싣지 않는다 — 원·통화·basis·과세·provenance 가 함께 간다(R-BASIS-06).
 * 투찰율은 **문자열**이다: JSON 수치는 클라이언트(TS)에서 배정밀도로 읽히고, 사정률·투찰율은
 * 소수 다섯째 자리까지 의미가 있어 표기 그대로 나른다(값을 반올림해 보내지 않는다).
 */
data class OpeningResultResponse(
    val noticeNumber: String,
    val noticeRound: String,
    val state: String,
    val participantCount: Int?,
    val actualOpeningAt: String?,
    val observedAt: String?,
    val confirmedAwardAmountWon: Long?,
    val confirmedAwardAmountCurrency: String?,
    val confirmedAwardAmountBasis: String?,
    val confirmedAwardAmountVatTreatment: String?,
    val confirmedAwardAmountProvenance: String?,
    val provisionalRankOneState: String,
    val provisionalRankOneDuplicateCount: Int?,
    val provisionalRankOneBidAmountWon: Long?,
    val provisionalRankOneBidAmountCurrency: String?,
    val provisionalRankOneBidAmountVatTreatment: String?,
    val provisionalRankOneBidRateFraction: String?,
    val provisionalRankOneObservedAt: String?,
) {
    companion object {
        fun of(
            id: NoticeId,
            view: OpeningResultView,
        ): OpeningResultResponse {
            val figures = (view as? OpeningResultView.Observed)?.figures
            val award = figures?.confirmedAward?.export()
            val rankOne = figures?.provisionalRankOne ?: ProvisionalRankOne.NotObserved
            val determined = rankOne as? ProvisionalRankOne.Determined
            return OpeningResultResponse(
                noticeNumber = id.number.value,
                noticeRound = id.round.value,
                state = stateTokenOf(view),
                participantCount = figures?.participantCount,
                actualOpeningAt = figures?.actualOpeningAt?.toString(),
                observedAt = figures?.observedAt?.toString(),
                confirmedAwardAmountWon = award?.won,
                confirmedAwardAmountCurrency = award?.currency?.name,
                confirmedAwardAmountBasis = award?.basis?.name,
                confirmedAwardAmountVatTreatment = award?.vatTreatment?.name,
                confirmedAwardAmountProvenance = award?.provenance?.let(::provenanceLabel),
                provisionalRankOneState = rankOneTokenOf(rankOne),
                provisionalRankOneDuplicateCount = (rankOne as? ProvisionalRankOne.RankDuplicated)?.count,
                provisionalRankOneBidAmountWon = determined?.bidAmount?.won,
                provisionalRankOneBidAmountCurrency = determined?.bidAmount?.currency?.name,
                provisionalRankOneBidAmountVatTreatment = determined?.bidAmount?.vatTreatment?.name,
                provisionalRankOneBidRateFraction = determined?.bidRate?.fraction?.toPlainString(),
                provisionalRankOneObservedAt = rankOne.observedAt?.toString(),
            )
        }
    }
}

/**
 * 상태 토큰 아홉 — 소진 `when` 두 겹이라 갈래가 늘면 컴파일이 이 자리를 막는다. 공고 부재도
 * **상태**다(404 가 아니다): 조회의 질문은 「이 공고의 개찰에 대해 우리가 아는 것」이고 그 답이
 * 「그 공고를 모른다」인 것은 다른 「모른다」 여덟과 같은 축이다 — 7C① 이 빈 결과의 두 뜻을
 * 상태로 가른 것과 같은 판단(클라이언트의 분기 자리를 하나로 둔다).
 */
private fun stateTokenOf(view: OpeningResultView): String =
    when (view) {
        OpeningResultView.NoticeAbsent -> "NOTICE_ABSENT"
        OpeningResultView.NotOpenedYet -> "NOT_OPENED_YET"
        OpeningResultView.NotCollected -> "NOT_COLLECTED"
        OpeningResultView.ObservedNotPromoted -> "OBSERVED_NOT_PROMOTED"
        is OpeningResultView.Observed ->
            when (view.progress) {
                OpeningProgress.OPENED -> "OPENED"
                OpeningProgress.FAILED_BID -> "FAILED_BID"
                OpeningProgress.REBID -> "REBID"
                OpeningProgress.UNRECOGNIZED -> "PROGRESS_UNRECOGNIZED"
                OpeningProgress.ABSENT -> "PROGRESS_ABSENT"
            }
    }

private fun rankOneTokenOf(rankOne: ProvisionalRankOne): String =
    when (rankOne) {
        ProvisionalRankOne.NotObserved -> "NOT_OBSERVED"
        is ProvisionalRankOne.RankMissing -> "RANK_MISSING"
        is ProvisionalRankOne.RankDuplicated -> "RANK_DUPLICATED"
        is ProvisionalRankOne.Determined -> "DETERMINED"
    }
