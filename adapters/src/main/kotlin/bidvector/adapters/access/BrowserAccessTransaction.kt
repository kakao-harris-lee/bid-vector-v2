package bidvector.adapters.access

import bidvector.adapters.persistence.ConnectionSource
import bidvector.adapters.persistence.OwnTransactionConnectionSource
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
import javax.sql.DataSource

/**
 * 브라우저 접근 use case 를 빌려주는 경계(`StrategyEditTransaction` 과 같은 형태·같은 이유).
 * 호출부는 use case 하나만 받고, 그 use case 가 쥔 포트(계정·세션 저장소)는 이 경계 밖으로
 * 나가지 않는다.
 *
 * **빌려주는 자리가 둘**이고 다른 것은 커밋 경계뿐이다 — 짧은 경로는 블록 하나가 한 커밋이고,
 * 로그인은 문장마다 제 트랜잭션이다(아래 [inSignInScope] 의 사유).
 *
 * 인터페이스가 `adapters` 에 사는 이유도 같다 — `app` 은 `adapters` 를 볼 수 있지만 그 반대는
 * 없다(v2-지침서 §3.1).
 */
interface BrowserAccessTransaction {
    /** 짧은 경로(요청 인증·세션 조회·로그아웃) — 호출 하나가 한 커밋이다. */
    fun <T> inTransaction(action: (BrowserAccessWorkflow) -> T): T

    /**
     * 로그인 전용 — **느린 KDF 검증이 커넥션을 쥐지 않는다**(code-review r1 M-2).
     *
     * 앞 판은 로그인도 [inTransaction] 을 지났다. 그러면 계정 조회부터 갱신까지가 한
     * 트랜잭션이고, 그 한가운데의 bcrypt(~300ms) 동안 **풀 커넥션 하나가 묶인다** — 무인증
     * 으로 닿을 수 있는 유일한 endpoint 가 그 경로라, 동시 로그인 요청 열 건이면 풀(10)이
     * 비고 **인증된 요청까지 함께 멈춘다**. 요청 자체의 유량 제한은 이 slice 밖이다.
     *
     * 그래서 로그인만 **문장마다 제 트랜잭션**으로 돈다: 조회(커넥션 빌림→반납) → 검증
     * (커넥션 없음) → 원자 갱신(빌림→반납). 커넥션을 쥐는 시간이 KDF 가 아니라 문장 하나의
     * 길이로 줄어든다. 한 커밋이 필요한 묶음은 포트 **한 호출** 안에 있다
     * ([bidvector.workflow.access.SignInCompletionPort.complete] 의 세 문장 · 실패 계수와 잠금의
     * 한 문장 갱신) — 경계가 느슨해져도 쪼개질 수 있는 쌍이 남지 않는다.
     *
     * **이 자리를 부를 수 있는 호출자는 하나**다 — `app.adapter.member-call-pairs` 가
     * (호출자, 선언 타입, 메서드, 서술자) 넷으로 못박는다(fail-closed). 그래서 로그인 아닌
     * 긴 작업이 이 느슨한 경계로 들어오려면 게이트 설정을 먼저 바꿔야 한다.
     */
    fun <T> inSignInScope(action: (BrowserAccessWorkflow) -> T): T
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
    dataSource: DataSource,
    private val hasher: PassphraseHasher,
    private val keys: BrowserSessionKeySource,
    private val clock: Clock,
    private val policy: BrowserAccessPolicyData,
) : BrowserAccessTransaction {
    /**
     * 로그인 전용 공급원 — **호출 하나가 트랜잭션 하나**다(`JdbcStrategyRepository` 의
     * `DataSource` 생성자와 같은 선택). `internal` 이라 이 모듈 밖에서는 만들 수 없다.
     */
    private val perStatement: ConnectionSource = OwnTransactionConnectionSource(dataSource)

    override fun <T> inTransaction(action: (BrowserAccessWorkflow) -> T): T =
        transactions.inTransaction { action(assemble(transactions)) }

    override fun <T> inSignInScope(action: (BrowserAccessWorkflow) -> T): T = action(assemble(perStatement))

    /**
     * 계정 행을 잠그는 문장은 **전부** 대기 상한 아래 선다(code-review r4 M-1) — 확정뿐 아니라
     * 실패 계수 갱신도 그렇다. 세션 저장소는 그 상한을 받지 않는다: 그 문장들은 로그인 scope 가
     * 아니라 요청 인증·로그아웃 경로의 것이고, 거기서 시한 초과를 거부로 접는 어휘가 아직 없다
     * (그 축은 이 slice 의 범위 밖이다).
     */
    private fun assemble(connections: ConnectionSource): BrowserAccessWorkflow {
        val bounded = SignInConnections(connections, policy.lockWait)
        return BrowserAccessWorkflow(
            accounts = JdbcAccountStore(bounded),
            sessions = JdbcBrowserSessionStore(connections),
            completion = JdbcSignInCompletion(bounded),
            hasher = hasher,
            keys = keys,
            clock = clock,
            policy = policy,
        )
    }
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
