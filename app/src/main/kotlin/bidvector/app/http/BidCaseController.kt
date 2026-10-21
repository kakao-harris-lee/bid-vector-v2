package bidvector.app.http

import bidvector.bidding.BidCommand
import bidvector.bidding.BidKind
import bidvector.bidding.RejectionReason
import bidvector.bidding.TransitionOutcome
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.BidActor
import bidvector.workflow.bidding.BidCaseView
import bidvector.workflow.bidding.BidCaseWorkflow
import bidvector.workflow.bidding.BidCommandResult
import bidvector.workflow.bidding.isForbiddenTransition
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

/**
 * 내 입찰함 endpoint 열둘(M7/7B, `BID-01`) — 화면 C · D 가 쓰는 최소 집합이다.
 *
 * **인증은 이 클래스가 판정하지 않는다** — 경로가 `/api` 하위라 기본 거부 필터(7D)가 그대로
 * 덮고, 무인증으로 열리는 자리를 **하나도 더하지 않는다**. 상태를 바꾸는 메서드는 그 필터가
 * CSRF 를 요구한다(`BrowserSessionAuthenticator.CSRF_EXEMPT_METHODS` 밖이라 자동이다).
 *
 * **입찰함은 계정 경로 전용이다.** 주체는 필터가 적은 값에서만 오고(`BidCaseRequests.accountOf`),
 * 계정이 없는 인증(기존 운영자 자격 헤더)은 401 이다 — 소유 경계를 세울 주체가 없기 때문이다.
 * 거부의 사유를 나누지 않는다(D-6A1-6).
 *
 * controller 는 **use case 만** 받는다(D-6A2b-8) — 저장소 · `ConnectionSource` 를 이 층에서
 * 볼 수 없다(ArchUnit 의존 게이트가 구조로 강제한다). 하는 일은 요청 → command 변환과
 * 결과 → 상태 코드 변환뿐이고, 두 변환표는 각각 `BidCaseRequests.kt` 와 이 파일의
 * [toResponse] 한 자리씩이며 전수 `when` 이라 새 사유가 생기면 컴파일이 누락을 잡는다.
 */
@RestController
@RequestMapping("/api/bid-cases")
class BidCaseController(
    private val workflow: BidCaseWorkflow,
) {
    // --- 조회 -----------------------------------------------------------------

    /** 내 입찰함 — `kind` 가 **필수**라 페이퍼와 실투찰이 한 응답에 섞일 수 없다. */
    @GetMapping
    fun list(
        @RequestParam(name = "kind") kind: String?,
        @RequestParam(name = "state", required = false) state: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = accountOrNull(request) ?: return unauthenticated(request)
        val bidKind = BidCaseRequests.bidKind(kind)
        val cases = workflow.list(account, bidKind, BidCaseRequests.stateTag(state))
        return ResponseEntity.ok(
            BidCaseListResponse(bidKind.name, cases.map(BidCaseResponse::from)),
        )
    }

    @GetMapping("/{caseId}")
    fun read(
        @PathVariable caseId: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = accountOrNull(request) ?: return unauthenticated(request)
        val view = workflow.read(account, BidCaseRequests.caseId(caseId)) ?: return caseNotFound(request)
        return ResponseEntity.ok(BidCaseResponse.from(view))
    }

    @GetMapping("/{caseId}/timeline")
    fun timeline(
        @PathVariable caseId: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = accountOrNull(request) ?: return unauthenticated(request)
        val id = BidCaseRequests.caseId(caseId)
        val entries = workflow.timeline(account, id) ?: return caseNotFound(request)
        return ResponseEntity.ok(
            BidTimelineResponse(id.value, entries.map(BidTimelineEntryResponse::from)),
        )
    }

    // --- 생성 -----------------------------------------------------------------

    /** 관심 담기 — 같은 (공고, 종류)에 건은 하나이므로 이미 있으면 그 건을 200 으로 낸다. */
    @PostMapping
    fun open(
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = accountOrNull(request) ?: return unauthenticated(request)
        val (notice, kind) = BidCaseRequests.openCase(body)
        val view = workflow.open(account, notice, kind)
        return ResponseEntity.status(HttpStatus.CREATED).body(BidCaseResponse.from(view))
    }

    // --- 전이 -----------------------------------------------------------------

    @PostMapping("/{caseId}/defer")
    fun defer(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        dispatch(caseId, request) { BidCommand.Defer(BidCaseRequests.remindAt(body)) }

    @PostMapping("/{caseId}/resume")
    fun resume(
        @PathVariable caseId: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> = dispatch(caseId, request) { BidCommand.Resume }

    @PostMapping("/{caseId}/skip")
    fun skip(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        dispatch(caseId, request) { BidCommand.Skip(BidCaseRequests.skipReason(body)) }

    /**
     * 초안 만들기 · 수정 — **스냅샷을 요청에서 받지 않는다**(위협 ③). 서버가 공고에서 뜬다.
     * 금액 출처도 요청에 자리가 없고 `OPERATOR_ENTERED` 가 실린다(계약 산출물 6).
     */
    @PostMapping("/{caseId}/draft")
    fun draft(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = accountOrNull(request) ?: return unauthenticated(request)
        val input = BidCaseRequests.draft(body)
        return workflow
            .draft(
                account,
                BidCaseRequests.caseId(caseId),
                input.amount,
                input.presentedAt,
                BidActor.User(account),
            ).toResponse(account, BidCaseRequests.caseId(caseId), request)
    }

    /** 「제출함」 · 기록 갱신 · 소급 확인 — 셋이 한 operation 이고 출발 상태가 가른다. */
    @PostMapping("/{caseId}/record")
    fun record(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        dispatch(caseId, request) {
            BidCommand.RecordBid(BidCaseRequests.submission(body), evidenceRetainUntil = null)
        }

    @PostMapping("/{caseId}/await-opening")
    fun awaitOpening(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        dispatch(caseId, request) { BidCommand.AwaitOpening(BidCaseRequests.openingAt(body)) }

    @PostMapping("/{caseId}/opening")
    fun opening(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        dispatch(caseId, request) {
            if (BidCaseRequests.openingFirst(body)) {
                BidCommand.RecordOpeningFirst
            } else {
                BidCommand.RecordOpeningOutOfRank
            }
        }

    @PostMapping("/{caseId}/qualification-review")
    fun qualificationReview(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        dispatch(caseId, request) {
            BidCommand.EnterQualificationReview(BidCaseRequests.documentDueAt(body))
        }

    @PostMapping("/{caseId}/outcome")
    fun outcome(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        dispatch(caseId, request) { BidCommand.RecordOutcome(BidCaseRequests.outcome(body)) }

    /** 결과 정정 — **사유가 필수**다. 원본은 덮이지 않고 타임라인에 남는다. */
    @PostMapping("/{caseId}/outcome-correction")
    fun correctOutcome(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        dispatch(caseId, request) {
            val (outcome, reason) = BidCaseRequests.correction(body)
            BidCommand.CorrectOutcome(outcome, reason)
        }

    // --- 공통 -----------------------------------------------------------------

    private fun dispatch(
        caseId: String,
        request: HttpServletRequest,
        command: () -> BidCommand,
    ): ResponseEntity<Any> {
        val account = accountOrNull(request) ?: return unauthenticated(request)
        val id = BidCaseRequests.caseId(caseId)
        return workflow
            .command(account, id, command(), BidActor.User(account))
            .toResponse(account, id, request)
    }

    private fun accountOrNull(request: HttpServletRequest): AccountId? = BidCaseRequests.accountOf(request)

    /**
     * 결과 → 상태 코드의 **유일한** 변환표. 전수 `when` 이라 새 갈래가 생기면 컴파일이 잡는다.
     *
     *  · 전이 거부는 **409** 다(요청 자체는 올바르고 건의 상태가 받지 않는 것이다).
     *  · 주체 종류 거부도 409 다 — 인증 실패가 아니다(사용자는 맞고 그 명령이 시스템 전용이다).
     *  · 건 부재와 **남의 건**은 둘 다 404 다(존재 여부를 흘리지 않는다).
     */
    private fun BidCommandResult.toResponse(
        account: AccountId,
        caseId: bidvector.bidding.BidCaseId,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        when (this) {
            BidCommandResult.CaseNotFound -> caseNotFound(request)
            BidCommandResult.VersionConflict ->
                conflict(request, ErrorCode.EDIT_SESSION_CONFLICT)

            BidCommandResult.NoticeSnapshotUnavailable ->
                conflict(request, ErrorCode.NOTICE_SNAPSHOT_UNAVAILABLE)

            is BidCommandResult.Processed ->
                when (val result = outcome) {
                    is TransitionOutcome.Rejected -> rejected(request, result.reason)
                    is TransitionOutcome.Accepted -> accepted(account, caseId, request)
                }
        }

    private fun accepted(
        account: AccountId,
        caseId: bidvector.bidding.BidCaseId,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val view: BidCaseView = workflow.read(account, caseId) ?: return caseNotFound(request)
        return ResponseEntity.ok(BidCaseResponse.from(view))
    }

    private fun rejected(
        request: HttpServletRequest,
        reason: RejectionReason,
    ): ResponseEntity<Any> =
        if (reason.isForbiddenTransition()) {
            conflict(request, ErrorCode.INVALID_TRANSITION)
        } else {
            conflict(request, ErrorCode.SYSTEM_ACTOR_NOT_PERMITTED)
        }

    private fun conflict(
        request: HttpServletRequest,
        code: String,
    ): ResponseEntity<Any> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorBody(code, "요청을 적용할 수 없다", request.correlationIdOrUnknown()))

    private fun caseNotFound(request: HttpServletRequest): ResponseEntity<Any> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorBody(ErrorCode.NOT_FOUND, "대상을 찾을 수 없다", request.correlationIdOrUnknown()))

    private fun unauthenticated(request: HttpServletRequest): ResponseEntity<Any> =
        ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorMapping.unauthenticated(request.correlationIdOrUnknown()))
}
