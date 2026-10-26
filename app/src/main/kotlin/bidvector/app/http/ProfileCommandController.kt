package bidvector.app.http

import bidvector.workflow.access.AccountId
import bidvector.workflow.profile.ListConstructionCapacitiesUseCase
import bidvector.workflow.profile.ReadProfileUseCase
import bidvector.workflow.profile.RemoveConstructionCapacityUseCase
import bidvector.workflow.profile.ReplaceBusinessDetailsUseCase
import bidvector.workflow.profile.ReplaceQualificationFactsUseCase
import bidvector.workflow.profile.UpsertConstructionCapacityUseCase
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

/**
 * 프로필 **쓰기** 넷(M7/7C④ 2b) — 자격 사실 · 사업 정보 · 시평액 upsert · 시평액 삭제.
 *
 * 전부 POST 라 7D 의 기본 거부 필터가 **CSRF 를 요구한다**(`CSRF_EXEMPT_METHODS` 밖이라 자동이다).
 * 새 무인증 경로는 0 이다.
 *
 * **주체를 본문에서 받지 않는다.** 모든 핸들러가 본문을 읽기 **전에** 주체를 꺼내므로, 인증되지 않은
 * 요청은 본문 형식 오류(400)가 아니라 401 로 떨어진다 — 검증 결과가 인증 앞에서 새지 않는다.
 * 넘기는 계정은 `authenticatedAccount()` 의 값 하나이고, 이 클래스에 다른 출처가 없다(2b 표적 2).
 *
 * 결과 → 상태 코드 변환은 이 클래스에 없다. `ProfileHttp` 한 자리다.
 */
@RestController
@RequestMapping(AccountScopedRoutes.PROFILE)
class ProfileCommandController(
    private val readProfile: ReadProfileUseCase,
    private val listCapacities: ListConstructionCapacitiesUseCase,
    private val replaceQualificationFacts: ReplaceQualificationFactsUseCase,
    private val replaceBusinessDetails: ReplaceBusinessDetailsUseCase,
    private val upsertCapacity: UpsertConstructionCapacityUseCase,
    private val removeCapacity: RemoveConstructionCapacityUseCase,
) {
    /**
     * 자격 사실(V12) 절 교체 — **계정을 넘기지 않는다**: 그 표는 아직 싱글턴이고 use case 서명에 계정이
     * 없다(받는 척하면 「이미 계정 단위다」라는 거짓 신호가 된다). 그래도 **경로는 계정 범위**라 세션
     * 주체가 없으면 401 이다(R-2 남은 절반이 열릴 때 이 자리가 그대로 계정을 받는다).
     */
    @PostMapping(QUALIFICATION_FACTS_PATH)
    fun qualificationFacts(
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        replaceQualificationFacts.replace(ProfileRequests.qualificationFacts(body))
        return ResponseEntity.ok(ProfileResponse.from(readProfile.read(account)))
    }

    @PostMapping(BUSINESS_DETAILS_PATH)
    fun businessDetails(
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        val outcome = replaceBusinessDetails.replace(account, ProfileRequests.businessDetails(body))
        return ProfileHttp.toResponse(outcome, request) {
            ResponseEntity.ok(ProfileResponse.from(readProfile.read(account)))
        }
    }

    /** 면허명이 같은 행이 있으면 갱신, 없으면 추가. 응답은 **다시 읽은 목록**이다. */
    @PostMapping(CAPACITIES_PATH)
    fun upsertConstructionCapacity(
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        val outcome = upsertCapacity.upsert(account, ProfileRequests.constructionCapacity(body))
        return ProfileHttp.toResponse(outcome, request) { capacities(account) }
    }

    /**
     * 시평액 한 행 삭제 — **POST 다**(DELETE 가 아니다): 쓰기 메서드를 하나로 모아 CSRF 요구를
     * 메서드로 받는다(7B 와 같은 관례). 지울 행이 없으면 404 다.
     */
    @PostMapping(CAPACITIES_REMOVE_PATH)
    fun removeConstructionCapacity(
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        val removed = removeCapacity.remove(account, ProfileRequests.licenseName(body))
        return if (removed) capacities(account) else ProfileHttp.capacityNotFound(request)
    }

    private fun capacities(account: AccountId): ResponseEntity<Any> =
        ResponseEntity.ok(listCapacities.list(account).map(ConstructionCapacityResponse::from))
}

/** 쓰기 경로 셋 — OpenAPI 문서와 같은 문자열이다. 애너테이션 인자라 전부 `const` 여야 한다. */
internal const val QUALIFICATION_FACTS_PATH = "/qualification-facts"
internal const val BUSINESS_DETAILS_PATH = "/business-details"
internal const val CAPACITIES_REMOVE_PATH = "$CAPACITIES_PATH/remove"
