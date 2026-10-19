package bidvector.app.wiring

import bidvector.adapters.access.BrowserAccessTransaction
import bidvector.workflow.access.AccessVerdict
import bidvector.workflow.access.BrowserSessionKey
import bidvector.workflow.access.ClearPassphrase
import bidvector.workflow.access.CsrfDemand
import bidvector.workflow.access.LoginName
import bidvector.workflow.access.SessionDescription
import bidvector.workflow.access.SignInOutcome

/**
 * HTTP 가 부르는 유일한 접근 실행기(M7/7D, `StrategyEditExecutor` 와 같은 자리·같은 이유) —
 * 필터와 컨트롤러는 이 클래스만 받는다.
 *
 * **(2b) 「경계로 처리」 — 포트를 밖에 내지 않는다.** 이 클래스가 쥔 것은
 * [BrowserAccessTransaction] 하나이고, 그 계약의 유일한 메서드는 트랜잭션 **안에서만** use
 * case 를 빌려준다. 저장소·`ConnectionSource` 를 돌려주는 공개 경로가 없다.
 *
 * **표면 넷이 밖에 허락하는 것**(값 획득 축): [signIn] 은 올바른 비밀 문구 없이는 아무것도
 * 내지 않고, [authenticate]·[describe]·[signOut] 은 **이미 세션 값을 쥔** 호출자만 쓸 수 있다
 * (인자가 그 값이다). 세션을 만드는 길은 [signIn] 하나다 — use case 쪽 `NewBrowserSession` 의
 * `internal` 생성자가 그 사실을 타입으로 든다.
 *
 * **로그인만 경계가 다르다**(code-review r1 M-2) — 느린 KDF 검증이 풀 커넥션을 쥐지 않도록
 * 문장마다 제 트랜잭션으로 돈다([BrowserAccessTransaction.signIn]). 한 커밋이 필요한 묶음은
 * 포트 한 호출 안에 있어(기존 세션 폐기 + 새 세션 등록 · 실패 계수와 잠금) 반쯤 발급된
 * 세션이 남지 않는다.
 */
class BrowserAccessExecutor(
    private val transaction: BrowserAccessTransaction,
) {
    fun signIn(
        login: LoginName,
        clear: ClearPassphrase,
    ): SignInOutcome = transaction.inSignInScope { it.signIn(login, clear) }

    fun authenticate(
        key: BrowserSessionKey,
        demand: CsrfDemand,
    ): AccessVerdict = transaction.inTransaction { it.authenticate(key, demand) }

    fun describe(key: BrowserSessionKey): SessionDescription? = transaction.inTransaction { it.describe(key) }

    fun signOut(key: BrowserSessionKey) {
        transaction.inTransaction { it.signOut(key) }
    }
}
