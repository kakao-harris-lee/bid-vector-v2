package bidvector.adapters.access

import bidvector.adapters.persistence.TransactionBoundary
import bidvector.workflow.access.AccountProvisioning
import bidvector.workflow.access.BrowserAccessPolicyData
import bidvector.workflow.access.BrowserAccessWorkflow
import bidvector.workflow.access.BrowserSessionKey
import bidvector.workflow.access.BrowserSessionKeySource
import bidvector.workflow.access.PassphraseHasher
import bidvector.workflow.strategy.Clock
import java.security.SecureRandom
import java.util.Base64

/**
 * 브라우저 접근 use case 를 **한 트랜잭션 안에서** 빌려주는 경계(`StrategyEditTransaction` 과
 * 같은 형태·같은 이유). 호출부는 use case 하나만 받고, 그 use case 가 쥔 포트(계정·세션
 * 저장소)는 이 경계 밖으로 나가지 않는다.
 *
 * 인터페이스가 `adapters` 에 사는 이유도 같다 — `app` 은 `adapters` 를 볼 수 있지만 그 반대는
 * 없다(v2-지침서 §3.1).
 */
interface BrowserAccessTransaction {
    fun <T> inTransaction(action: (BrowserAccessWorkflow) -> T): T
}

/** 운영자 명령 경로의 같은 경계 — **다른 타입**이라 요청 경로가 이것을 주입받을 수 없다. */
interface AccountProvisioningTransaction {
    fun <T> inTransaction(action: (AccountProvisioning) -> T): T
}

/**
 * 요청마다 트랜잭션을 열고 그 커넥션 위에 저장소를 세워 use case 를 조립한다
 * (`JdbcStrategyEditTransaction` 선례 — 조립이 `app` 이 아니라 여기인 이유는 `app` 쪽
 * 게이트를 넓히지 않고 어댑터 구체 타입을 이름 부를 수 있는 자리가 이 층이라는 것이다).
 */
class JdbcBrowserAccessTransaction(
    private val transactions: TransactionBoundary,
    private val hasher: PassphraseHasher,
    private val keys: BrowserSessionKeySource,
    private val clock: Clock,
    private val policy: BrowserAccessPolicyData,
) : BrowserAccessTransaction {
    override fun <T> inTransaction(action: (BrowserAccessWorkflow) -> T): T =
        transactions.inTransaction { action(assemble()) }

    private fun assemble(): BrowserAccessWorkflow =
        BrowserAccessWorkflow(
            accounts = JdbcAccountStore(transactions),
            sessions = JdbcBrowserSessionStore(transactions),
            hasher = hasher,
            keys = keys,
            clock = clock,
            policy = policy,
        )
}

/**
 * 운영자 명령의 경계 — **소유자 연결**로 만든 [TransactionBoundary] 를 받는다(계정 생성·해시
 * 교체는 앱 역할이 권한을 갖지 않는 일이다, V20 의 GRANT). 배선이 그 연결을 지역으로 만들어
 * 넘기고 빈으로 노출하지 않는다.
 */
class JdbcAccountProvisioningTransaction(
    private val transactions: TransactionBoundary,
    private val hasher: PassphraseHasher,
    private val clock: Clock,
) : AccountProvisioningTransaction {
    override fun <T> inTransaction(action: (AccountProvisioning) -> T): T =
        transactions.inTransaction { action(assemble()) }

    private fun assemble(): AccountProvisioning =
        AccountProvisioning(
            accounts = JdbcAccountProvisioningStore(transactions),
            sessions = JdbcBrowserSessionStore(transactions),
            hasher = hasher,
            clock = clock,
        )
}

/**
 * 세션 값의 난수 — **주입 자리는 `internal` 주 생성자**이고 public 보조 생성자가 실물
 * [SecureRandom] 을 꽂는다(계약 설계 검토 (2b) 「주입 자리(시계·난수)는 밖에서 못 꽂게」).
 * 이 모듈 밖에서는 난수원을 바꿀 수 없다 — test 사본은 이 모듈 안에서만 선다.
 *
 * 값은 **URL 안전 base64 로 인코딩한 [KEY_BYTES] 바이트**다. 쿠키 값에 쓸 수 없는 문자가
 * 섞이지 않고, 엔트로피가 128비트를 넘어 추측이 성립하지 않는다.
 */
class SecureBrowserSessionKeySource internal constructor(
    private val random: SecureRandom,
) : BrowserSessionKeySource {
    constructor() : this(SecureRandom())

    override fun newKey(): BrowserSessionKey {
        val bytes = ByteArray(KEY_BYTES)
        random.nextBytes(bytes)
        return BrowserSessionKey(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
    }

    private companion object {
        /** 32바이트 = 256비트. 쿠키 값 하나에 들어가고 추측 비용이 의미 없는 수준이다. */
        const val KEY_BYTES = 32
    }
}
