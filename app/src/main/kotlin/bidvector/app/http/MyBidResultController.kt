package bidvector.app.http

import bidvector.workflow.result.ReadMyBidResults
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 내 입찰 결과 조회(M7/7C⑤-b) — **읽기 하나뿐**이다. 대사 판정을 저장하는 표면이 없다
 * (D-7C5B-1: 매 요청 원천에서 다시 판정한다).
 *
 * **주체는 세션 계정 하나**다: 질의 인자도 본문도 없고, 7B 의 바인딩 가드가 「요청 값으로
 * `AccountId` 를 짓는」 길을 앱 전역에서 막는다. 계정 없는 인증(자격 헤더 경로)은 401 이고
 * 사유를 나누지 않는다(D-6A1-6).
 *
 * 받는 것은 use case 하나다(D-6A2b-8) — 이 컨트롤러는 포트 셋을 이름조차 부르지 않으므로
 * HTTP 층에서 판정 입력을 바꿔 끼울 자리가 없다. 집계도 여기서 세지 않는다: 목록과 집계는
 * **같은 판정 패스**에서 use case 가 함께 낸다(화면이 목록을 세어 집계를 지어내지 않는다는
 * 원칙이 전송 층에서도 깨지지 않게).
 *
 * `produces` 를 선언한다(D-6A2b-21 축) — 받아들일 수 없는 `Accept` 가 핸들러에 닿기 전에
 * 406 이 되게 한다.
 */
@RestController
@RequestMapping(AccountScopedRoutes.BID_RESULTS)
class MyBidResultController(
    private val readMyBidResults: ReadMyBidResults,
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun read(request: HttpServletRequest): ResponseEntity<Any> {
        val account = request.authenticatedAccount() ?: return unauthenticated(request)
        return ResponseEntity.ok(MyBidResultsResponse.from(readMyBidResults.read(account)))
    }
}
