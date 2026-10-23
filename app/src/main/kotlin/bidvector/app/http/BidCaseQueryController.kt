package bidvector.app.http

import bidvector.workflow.bidding.BidCaseWorkflow
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 내 입찰함 **조회** 셋(M7/7B) — 목록 · 건 하나 · 타임라인.
 *
 * 쓰기(`BidCaseCommandController`)와 가른다. detekt 의 클래스당 함수 상한(11)이 계기였지만
 * 경계 자체가 의미 있다 — 이 클래스에는 상태를 바꾸는 경로가 **하나도 없고**, 그래서
 * CSRF 를 요구하지 않는 메서드(GET)만 모인다.
 *
 * **인증은 이 클래스가 판정하지 않는다** — 경로가 `/api` 하위라 7D 기본 거부 필터가 덮고,
 * 무인증으로 열리는 자리를 하나도 더하지 않는다. 다만 **주체**는 여기서 꺼낸다:
 * 입찰함은 계정 경로 전용이라 계정 없는 인증은 401 이다(사유는 나누지 않는다, D-6A1-6).
 */
@RestController
@RequestMapping(AccountScopedRoutes.BID_CASES)
class BidCaseQueryController(
    private val workflow: BidCaseWorkflow,
) {
    /** `kind` 가 **필수**다 — 페이퍼와 실투찰을 함께 내는 응답이 계약에 없다(§6.1). */
    @GetMapping
    fun list(
        @RequestParam(name = "kind") kind: String?,
        @RequestParam(name = "state", required = false) state: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = BidCaseRouting.accountOf(request) ?: return BidCaseHttp.unauthenticated(request)
        val bidKind = BidCaseRouting.bidKind(kind)
        val cases = workflow.list(account, bidKind, BidCaseRouting.stateTag(state))
        // **맨 위가 배열**이다 — 배열을 감싼 object 는 평탄 규칙(D-6A1-20 ⓑ)에 걸린다.
        // 요청이 `kind` 를 지정했으므로 응답이 그것을 되풀이하지 않는다.
        return ResponseEntity.ok(cases.map(BidCaseResponse::from))
    }

    @GetMapping("/{caseId}")
    fun read(
        @PathVariable caseId: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = BidCaseRouting.accountOf(request) ?: return BidCaseHttp.unauthenticated(request)
        return BidCaseHttp.view(workflow, account, BidCaseRouting.caseId(caseId), request)
    }

    /**
     * 타임라인 — **본문이 없다.** 메모 · 증빙 참조는 이 응답에 자리가 없다(계약 산출물 8:
     * audit 는 본문을 남기지 않는다).
     */
    @GetMapping("/{caseId}/timeline")
    fun timeline(
        @PathVariable caseId: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = BidCaseRouting.accountOf(request) ?: return BidCaseHttp.unauthenticated(request)
        val id = BidCaseRouting.caseId(caseId)
        val entries = workflow.timeline(account, id)
        return entries
            ?.let { ResponseEntity.ok<Any>(it.map(BidTimelineEntryResponse::from)) }
            ?: BidCaseHttp.caseNotFound(request)
    }
}
