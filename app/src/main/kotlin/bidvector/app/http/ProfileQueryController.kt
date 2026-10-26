package bidvector.app.http

import bidvector.workflow.profile.ListConstructionCapacitiesUseCase
import bidvector.workflow.profile.ReadProfileUseCase
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 프로필 **조회** 둘(M7/7C④ 2b) — 프로필 한 벌 · 시평액 목록.
 *
 * 쓰기(`ProfileCommandController`)와 가른다 — CSRF 를 요구하지 않는 메서드(GET)만 모인다.
 *
 * **주체는 세션 계정 하나**다: 질의 인자에도 본문에도 계정을 받을 자리가 없고, 7B 의 바인딩 가드가
 * 「요청 값으로 `AccountId` 를 짓는」 길을 앱 전역에서 막는다. 계정 없는 인증(자격 헤더 경로)은
 * 401 이다 — 사유는 나누지 않는다(D-6A1-6).
 */
@RestController
@RequestMapping(AccountScopedRoutes.PROFILE)
class ProfileQueryController(
    private val readProfile: ReadProfileUseCase,
    private val listCapacities: ListConstructionCapacitiesUseCase,
) {
    /** 자격 사실 · 사업 정보 · 판정 대기 축 한 벌. 시평액은 아래 경로의 **최상위 배열**이다. */
    @GetMapping
    fun read(request: HttpServletRequest): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        return ResponseEntity.ok(ProfileResponse.from(readProfile.read(account)))
    }

    @GetMapping(CAPACITIES_PATH)
    fun capacities(request: HttpServletRequest): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        return ResponseEntity.ok(listCapacities.list(account).map(ConstructionCapacityResponse::from))
    }
}

/** 시평액 경로 — 조회 · 쓰기 두 controller 가 같은 상수를 쓴다(경로를 두 곳에 적으면 갈린다). */
internal const val CAPACITIES_PATH = "/construction-capacities"
