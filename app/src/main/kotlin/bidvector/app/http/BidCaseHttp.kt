package bidvector.app.http

import bidvector.bidding.BidCaseId
import bidvector.bidding.BidCommand
import bidvector.bidding.RejectionReason
import bidvector.bidding.TransitionOutcome
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.BidActor
import bidvector.workflow.bidding.BidCaseWorkflow
import bidvector.workflow.bidding.BidCommandResult
import bidvector.workflow.bidding.isForbiddenTransition
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/**
 * 입찰 건 응답 변환 — **두 controller 가 공유하는 유일한 매핑표**다.
 *
 * controller 에서 갈라 나왔다(detekt `TooManyFunctions`: 클래스당 11). 가른 경계는 의미가
 * 있다 — endpoint 는 「어떤 명령인가」만 적고, **결과 → 상태 코드**는 전부 이 파일 한 자리다.
 * 두 controller 가 각자 매핑을 들면 같은 사유가 다른 코드로 나가는 길이 열린다.
 *
 * 규율:
 *  · 전이 거부는 **409** 다 — 요청 자체는 올바르고 **건의 상태가 받지 않는** 것이다.
 *  · 주체 종류 거부도 409 다(인증 실패가 아니다 — 사용자는 맞고 그 명령이 시스템 전용이다).
 *  · 건 부재와 **남의 건**은 둘 다 404 다(존재 여부를 흘리지 않는다).
 *  · 본문은 `ErrorBody` 고정 형태뿐이고 예외 문면을 싣지 않는다(D-6A1-7 우회 (3)).
 */
internal object BidCaseHttp {
    /** 결과 → 응답. 전수 `when` 이라 새 갈래가 생기면 컴파일이 누락을 잡는다. */
    fun toResponse(
        result: BidCommandResult,
        workflow: BidCaseWorkflow,
        account: AccountId,
        caseId: BidCaseId,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        when (result) {
            BidCommandResult.CaseNotFound -> {
                caseNotFound(request)
            }

            BidCommandResult.VersionConflict -> {
                conflict(request, ErrorCode.EDIT_SESSION_CONFLICT)
            }

            BidCommandResult.NoticeSnapshotUnavailable -> {
                conflict(request, ErrorCode.NOTICE_SNAPSHOT_UNAVAILABLE)
            }

            is BidCommandResult.Processed -> {
                when (val outcome = result.outcome) {
                    is TransitionOutcome.Rejected -> rejected(request, outcome.reason)
                    is TransitionOutcome.Accepted -> view(workflow, account, caseId, request)
                }
            }
        }

    /**
     * 전이가 받아들여진 뒤의 응답은 **다시 읽은 건**이다 — 화면이 서버 판정(다음 확인 시점 ·
     * 지난 일정 경고)을 그 자리에서 받는다. 전이 결과로 응답을 조립하면 그 둘을 controller 가
     * 다시 계산하게 되고, 그러면 계산이 두 자리가 된다.
     */
    fun view(
        workflow: BidCaseWorkflow,
        account: AccountId,
        caseId: BidCaseId,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        workflow.read(account, caseId)?.let { ResponseEntity.ok(BidCaseResponse.from(it)) }
            ?: caseNotFound(request)

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

    fun caseNotFound(request: HttpServletRequest): ResponseEntity<Any> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorBody(ErrorCode.NOT_FOUND, "대상을 찾을 수 없다", request.correlationIdOrUnknown()))

    /**
     * 전이 공통 경로 — **주체를 먼저 꺼내고** 그 뒤에 본문을 읽는다([command] 가 지연 평가다).
     * 순서가 뒤집히면 인증되지 않은 요청이 400 을 받아 「어떤 본문이 유효한가」가 샌다.
     *
     * controller 가 아니라 여기 있는 이유: 쓰기 controller 가 endpoint 열하나로 클래스당 함수
     * 상한에 닿아 있고, 이 함수는 어차피 **결과 → 응답** 매핑과 한 몸이다.
     */
    fun dispatch(
        workflow: BidCaseWorkflow,
        caseId: String,
        request: HttpServletRequest,
        command: () -> BidCommand,
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        val id = BidCaseRouting.caseId(caseId)
        val result = workflow.command(account, id, command(), BidActor.User(account))
        return toResponse(result, workflow, account, id, request)
    }
}
