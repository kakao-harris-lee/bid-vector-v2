package bidvector.app.http

import bidvector.workflow.profile.ReadOnboardingChecklistUseCase
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 시작하기 체크리스트 조회(M7/7C④-3) — 읽기 하나뿐이다. 진행 상태를 쓰는 표면이 없다(저장하지
 * 않는다, D-7C4-1 P1).
 *
 * **주체는 세션 계정 하나**다: 질의 인자도 본문도 없고, 7B 의 바인딩 가드가 「요청 값으로
 * `AccountId` 를 짓는」 길을 앱 전역에서 막는다. 계정 없는 인증(자격 헤더 경로)은 401 이고 사유를
 * 나누지 않는다(D-6A1-6) — `ProfileQueryController` 와 같은 규율이다.
 *
 * 받는 것은 use case 하나다(D-6A2b-8): 이 컨트롤러는 자격 사실 · 사업 정보 · 전략 port 를 이름조차
 * 부르지 않으므로, HTTP 층에서 판정 입력을 바꿔 끼울 자리가 없다.
 */
@RestController
@RequestMapping(AccountScopedRoutes.ONBOARDING_CHECKLIST)
class OnboardingChecklistController(
    private val readChecklist: ReadOnboardingChecklistUseCase,
) {
    @GetMapping
    fun read(request: HttpServletRequest): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        return ResponseEntity.ok(OnboardingChecklistResponse.from(readChecklist.read(account)))
    }
}
