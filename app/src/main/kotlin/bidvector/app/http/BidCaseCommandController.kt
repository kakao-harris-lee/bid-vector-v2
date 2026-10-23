package bidvector.app.http

import bidvector.bidding.BidCommand
import bidvector.workflow.bidding.BidActor
import bidvector.workflow.bidding.BidCaseWorkflow
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

/**
 * 내 입찰함 **쓰기** 열하나(M7/7B) — 건 열기 + 전이 열.
 *
 * 전부 POST 라 7D 의 기본 거부 필터가 **CSRF 를 요구한다**(`CSRF_EXEMPT_METHODS` 밖이라
 * 자동이다 — 이 클래스가 따로 판정하지 않는다). 새 무인증 경로는 0 이다.
 *
 * **주체를 본문에서 받지 않는다.** 모든 핸들러가 본문을 읽기 **전에** 주체를 꺼내므로,
 * 인증되지 않은 요청은 본문 형식 오류(400)가 아니라 401 로 떨어진다 — 검증 결과가 인증
 * 앞에서 새지 않는다.
 *
 * 결과 → 상태 코드 변환은 이 클래스에 없다. `BidCaseHttp` 한 자리다(조회 controller 와 공유).
 */
@RestController
@RequestMapping(AccountScopedRoutes.BID_CASES)
class BidCaseCommandController(
    private val workflow: BidCaseWorkflow,
) {
    /** 관심 담기 — 같은 (공고, 종류)에 건은 하나라 이미 있으면 그 건을 돌려준다. */
    @PostMapping
    fun open(
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return BidCaseHttp.unauthenticated(request)
        val (notice, kind) = BidCaseRequests.openCase(body)
        val view = workflow.open(account, notice, kind)
        return ResponseEntity.status(HttpStatus.CREATED).body(BidCaseResponse.from(view))
    }

    @PostMapping("/{caseId}/defer")
    fun defer(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        BidCaseHttp.dispatch(workflow, caseId, request) { BidCommand.Defer(BidCaseRequests.remindAt(body)) }

    /**
     * 나중에 해제 — 인자가 없지만 **본문을 받는다**(빈 객체 `{}`).
     *
     * 인자가 없다고 `@RequestBody` 를 빼면 그 매핑만 미지원 미디어 타입·깨진 JSON 에서 다른
     * 답을 낸다(Spring 이 본문을 보지 않으므로 415·400 이 나지 않는다). 출하 표면 전수 test 가
     * 「본문을 받는 POST 매핑은 전부 같은 모양으로 거부한다」를 재므로, 모양을 맞춘다.
     */
    @PostMapping("/{caseId}/resume")
    fun resume(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        BidCaseHttp.dispatch(workflow, caseId, request) {
            body.requireObject().requireKnownKeys(emptySet())
            BidCommand.Resume
        }

    @PostMapping("/{caseId}/skip")
    fun skip(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        BidCaseHttp.dispatch(workflow, caseId, request) { BidCommand.Skip(BidCaseRequests.skipReason(body)) }

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
        val account = request.authenticatedAccount() ?: return BidCaseHttp.unauthenticated(request)
        val id = BidCaseRouting.caseId(caseId)
        val input = BidCaseRequests.draft(body)
        val result = workflow.draft(account, id, input.amount, input.presentedAt, BidActor.User(account))
        return BidCaseHttp.toResponse(result, workflow, account, id, request)
    }

    /** 「제출함」 · 기록 갱신 · 소급 확인 — 셋이 한 operation 이고 **출발 상태가 가른다**. */
    @PostMapping("/{caseId}/record")
    fun record(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        BidCaseHttp.dispatch(workflow, caseId, request) {
            BidCommand.RecordBid(BidCaseRequests.submission(body), evidenceRetainUntil = null)
        }

    @PostMapping("/{caseId}/await-opening")
    fun awaitOpening(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        BidCaseHttp.dispatch(workflow, caseId, request) {
            BidCommand.AwaitOpening(BidCaseRequests.openingAt(body))
        }

    /** 개찰 결과 — **순위 숫자를 받지 않는다**(「몇 위보다 낙찰 여부」, §6.1). */
    @PostMapping("/{caseId}/opening")
    fun opening(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        BidCaseHttp.dispatch(workflow, caseId, request) {
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
        BidCaseHttp.dispatch(workflow, caseId, request) {
            BidCommand.EnterQualificationReview(BidCaseRequests.documentDueAt(body))
        }

    @PostMapping("/{caseId}/outcome")
    fun outcome(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        BidCaseHttp.dispatch(workflow, caseId, request) {
            BidCommand.RecordOutcome(BidCaseRequests.outcome(body))
        }

    /** 결과 정정 — **사유 필수**. 원본은 덮이지 않고 타임라인에 남는다. */
    @PostMapping("/{caseId}/outcome-correction")
    fun correctOutcome(
        @PathVariable caseId: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        BidCaseHttp.dispatch(workflow, caseId, request) {
            val (outcome, reason) = BidCaseRequests.correction(body)
            BidCommand.CorrectOutcome(outcome, reason)
        }
}
