package bidvector.app.http

import bidvector.adapters.access.BrowserAccessTransaction
import bidvector.sharedkernel.AccountId
import bidvector.workflow.access.AccountSignUpPort
import bidvector.workflow.access.AccountSnapshot
import bidvector.workflow.access.AccountStorePort
import bidvector.workflow.access.BrowserAccessPolicyData
import bidvector.workflow.access.BrowserAccessWorkflow
import bidvector.workflow.access.BrowserSessionDigest
import bidvector.workflow.access.BrowserSessionKey
import bidvector.workflow.access.BrowserSessionKeySource
import bidvector.workflow.access.BrowserSessionSnapshot
import bidvector.workflow.access.BrowserSessionStorePort
import bidvector.workflow.access.ClearPassphrase
import bidvector.workflow.access.LoginName
import bidvector.workflow.access.NewBrowserSession
import bidvector.workflow.access.PassphraseHash
import bidvector.workflow.access.PassphraseHasher
import bidvector.workflow.access.SignInCompletionPort
import bidvector.workflow.access.SignInWrite
import bidvector.workflow.access.SignUpPolicyData
import bidvector.workflow.strategy.Clock
import java.time.Duration
import java.time.Instant

/** HTTP 층 test 의 고정 계정 — 실 자격증명과 겹치지 않는 문구다(비밀값이 아니다). */
const val TEST_LOGIN = "http-layer-test-login"
const val TEST_PASSPHRASE = "http-layer-test-fixture-passphrase"
const val TEST_ACCOUNT_ID = 7L

/** 출하 가입 정책과 **같은 값** — 경계 단언이 이 값을 읽는다(M15/15B). */
val TEST_SIGN_UP_POLICY: SignUpPolicyData =
    SignUpPolicyData(
        loginNameMinLength = 4,
        loginNameMaxLength = 32,
        loginNameCharacter = "[a-z0-9._-]",
        passphraseMinLength = 10,
        passphraseMaxLength = 1024,
    )

/**
 * 상한의 test 값 — **출하 기본값(30)보다 작게** 둔다. 429 를 재는 test 가 그 수만큼 요청을
 * 보내야 하므로, 작을수록 그 test 가 싸다. 상한 자체의 값과 **출하 배선**은
 * `bidvector.app.SignUpThrottleWiringTest` 가 든다(앞 판은 없는 class 이름을 가리켰다 — cr r1 N-7).
 */
const val TEST_SIGN_UP_ATTEMPTS = 10

val TEST_SIGN_UP_WINDOW: Duration = Duration.ofHours(1)

/** 추적 주소 상한의 test 값 — 이 레인의 요청은 모두 한 주소라 그 축을 재는 것은 순수 test 다. */
const val TEST_TRACKED_ADDRESS_LIMIT = 100

/** 출하 정책과 **같은 모양**의 값 — 쿠키 Max-Age·만료 단언이 이 값을 읽는다. */
val TEST_ACCESS_POLICY: BrowserAccessPolicyData =
    BrowserAccessPolicyData(
        idleWindow = Duration.ofMinutes(30),
        absoluteWindow = Duration.ofHours(12),
        maxFailedAttempts = 5,
        lockWindow = Duration.ofMinutes(15),
        hashCost = 12,
        lockWait = Duration.ofMillis(500),
    )

/**
 * 실 DB 없이 use case 를 빌려주는 경계의 이중체 — **use case 는 실물**이다(세션 판정·CSRF
 * 비교·만료 fold 를 test 사본으로 다시 짓지 않는다). 바뀌는 것은 저장 경계뿐이라, 이 위에서
 * 재는 것은 「HTTP 층이 쿠키·헤더를 어떻게 그 입력으로 옮기고 결과를 어떤 응답으로 내는가」다.
 *
 * 실 KDF 는 여기서 쓰지 않는다(어댑터 test 가 그것을 잰다) — 해시 검증의 **거동**만 필요하다.
 */
class InMemoryBrowserAccessTransaction(
    private val clock: Clock,
) : BrowserAccessTransaction {
    val accounts: MutableAccountStore = MutableAccountStore(FakeHasher().hash(ClearPassphrase.wrap(TEST_PASSPHRASE)))
    val sessions: MutableSessionStore = MutableSessionStore()
    val keys: SequentialKeySource = SequentialKeySource()

    /** M15/15B — 가입이 만든 계정은 **같은 계정 표**에 선다(그래야 그 자격으로 로그인이 된다). */
    val signUps: MutableSignUpStore = MutableSignUpStore(accounts)

    /** 공개한다 — 확정이 경합으로 끝나는 갈래를 HTTP test 가 세울 수 있어야 한다(r3 열린 질문 ①). */
    val completion: MutableSignInCompletion = MutableSignInCompletion(accounts, sessions)

    override fun <T> inTransaction(action: (BrowserAccessWorkflow) -> T): T = action(workflow())

    /**
     * 이 이중체에는 커넥션이 없으므로 로그인도 같은 use case 를 지난다 — 경계의 **차이**(문장마다
     * 제 트랜잭션)는 실 DB 위에서만 보이고, 그 축은 `AccessConcurrencyTest` 가 잰다.
     */
    override fun <T> inSignInScope(action: (BrowserAccessWorkflow) -> T): T = action(workflow())

    private fun workflow(): BrowserAccessWorkflow =
        BrowserAccessWorkflow(
            accounts = accounts,
            sessions = sessions,
            completion = completion,
            signUps = signUps,
            hasher = FakeHasher(),
            keys = keys,
            clock = clock,
            policy = TEST_ACCESS_POLICY,
            signUpPolicy = TEST_SIGN_UP_POLICY,
        )

    /** Spring 컨텍스트가 test 사이에 재사용되므로 상태를 매 test 전에 비운다. */
    fun reset() {
        accounts.reset()
        sessions.clear()
        keys.reset()
        signUps.clear()
        completion.contended = false
    }
}

/** 접두 하나로 같은 입력 ↔ 같은 해시만 보장하는 fake. 원문을 되돌릴 수 없다. */
class FakeHasher : PassphraseHasher {
    override fun hash(clear: ClearPassphrase): PassphraseHash =
        PassphraseHash("fake:${clear.hashInput().concatToString().hashCode()}")

    override fun verify(
        clear: ClearPassphrase,
        hash: PassphraseHash,
    ): Boolean = hash.encoded == hash(clear).encoded
}

/**
 * 계정 표의 이중체 — **고정 계정 하나로 시작하고 가입이 행을 더한다**(M15/15B). 앞 판은 스냅숏
 * 하나였고, 그러면 가입이 만든 계정으로는 로그인도 세션 확정도 설 수 없다.
 */
class MutableAccountStore(
    private val seedHash: PassphraseHash,
) : AccountStorePort {
    private val rows: MutableMap<String, MutableRow> = mutableMapOf()

    init {
        reset()
    }

    override fun byLogin(login: LoginName): AccountSnapshot? = rows[login.value]?.snapshot()

    override fun registerFailure(
        account: AccountId,
        attemptLimit: Int,
        lockUntil: Instant,
    ) {
        val row = rows.values.firstOrNull { it.id == account.value } ?: return
        val next = row.attempts + 1
        if (next >= attemptLimit) {
            row.attempts = 0
            row.lockedUntil = lockUntil
        } else {
            row.attempts = next
        }
    }

    /** 확정 이중체가 쓰는 자리 — 실물에서는 조건부 UPDATE 한 문장이다(자격·잠금·행 잠금). */
    fun claim(
        account: AccountId,
        verified: PassphraseHash,
        now: Instant,
    ): Boolean {
        val row = rows.values.firstOrNull { it.id == account.value } ?: return false
        val claimable = row.hash.encoded == verified.encoded && row.lockedUntil?.isAfter(now) != true
        if (claimable) {
            row.attempts = 0
            row.lockedUntil = null
        }
        return claimable
    }

    /** 가입이 부르는 자리 — 이름이 비어 있을 때만 행이 선다(실물의 `ON CONFLICT DO NOTHING`). */
    fun addIfAbsent(
        login: LoginName,
        hash: PassphraseHash,
    ): AccountId? {
        if (rows.containsKey(login.value)) return null
        val id = nextId
        nextId += 1
        rows[login.value] = MutableRow(id, hash)
        return AccountId(id)
    }

    fun reset() {
        rows.clear()
        nextId = TEST_ACCOUNT_ID + 1
        rows[TEST_LOGIN] = MutableRow(TEST_ACCOUNT_ID, seedHash)
    }

    private var nextId: Long = TEST_ACCOUNT_ID + 1

    private class MutableRow(
        val id: Long,
        val hash: PassphraseHash,
    ) {
        var attempts: Int = 0
        var lockedUntil: Instant? = null

        fun snapshot(): AccountSnapshot = AccountSnapshot(AccountId(id), hash, lockedUntil)
    }
}

/** 가입 쓰기의 이중체 — 계정 표에 그대로 행을 더한다(출처가 둘이 되지 않게). */
class MutableSignUpStore(
    private val accounts: MutableAccountStore,
) : AccountSignUpPort {
    /** 이 이중체가 만든 아이디 전수 — HTTP test 가 「몇 개가 섰나」를 센다. */
    val created: MutableList<String> = mutableListOf()

    override fun insertIfAbsent(
        login: LoginName,
        hash: PassphraseHash,
    ): AccountId? = accounts.addIfAbsent(login, hash)?.also { created += login.value }

    fun clear() = created.clear()
}

class MutableSessionStore : BrowserSessionStorePort {
    private val rows: MutableMap<String, BrowserSessionSnapshot> = mutableMapOf()

    /** 저장된 **해시 키** 전부 — 「평문이 저장되지 않는다」를 HTTP 층에서도 볼 수 있게 낸다. */
    val storedKeys: Set<String> get() = rows.keys.toSet()

    /** 확정 이중체가 폐기 뒤에 부른다 — 포트 표면에는 없다. */
    fun put(session: NewBrowserSession) {
        rows[session.digest.hex] =
            BrowserSessionSnapshot(
                digest = session.digest,
                account = session.account,
                csrfDigest = session.csrfDigest,
                lastUsedAt = session.startedAt,
                idleExpiresAt = session.idleExpiresAt,
                absoluteExpiresAt = session.absoluteExpiresAt,
                revokedAt = null,
            )
    }

    override fun byDigest(digest: BrowserSessionDigest): BrowserSessionSnapshot? = rows[digest.hex]

    override fun touch(
        digest: BrowserSessionDigest,
        usedAt: Instant,
        idleExpiresAt: Instant,
    ) {
        replace(digest.hex) { row -> row.with(lastUsedAt = usedAt, idleExpiresAt = idleExpiresAt) }
    }

    override fun revoke(
        digest: BrowserSessionDigest,
        at: Instant,
    ) {
        replace(digest.hex) { row -> if (row.revokedAt == null) row.with(revokedAt = at) else row }
    }

    override fun revokeAllOf(
        account: AccountId,
        at: Instant,
    ) {
        rows.keys.toList().forEach { key ->
            replace(key) { row ->
                if (row.account.value == account.value && row.revokedAt == null) row.with(revokedAt = at) else row
            }
        }
    }

    fun clear() = rows.clear()

    private fun replace(
        key: String,
        change: (BrowserSessionSnapshot) -> BrowserSessionSnapshot,
    ) {
        rows[key]?.let { rows[key] = change(it) }
    }
}

private fun BrowserSessionSnapshot.with(
    lastUsedAt: Instant = this.lastUsedAt,
    idleExpiresAt: Instant = this.idleExpiresAt,
    revokedAt: Instant? = this.revokedAt,
): BrowserSessionSnapshot =
    BrowserSessionSnapshot(
        digest = digest,
        account = account,
        csrfDigest = csrfDigest,
        lastUsedAt = lastUsedAt,
        idleExpiresAt = idleExpiresAt,
        absoluteExpiresAt = absoluteExpiresAt,
        revokedAt = revokedAt,
    )

/** 로그인 확정의 이중체 — 실물의 한 트랜잭션을 단일 스레드 순서로 흉내 낸다. */
class MutableSignInCompletion(
    private val accounts: MutableAccountStore,
    private val sessions: MutableSessionStore,
) : SignInCompletionPort {
    /**
     * 확정이 **경합으로 끝나게** 한다(M15/15B r3 — `SessionUnavailable` 이 죽은 분기인지 재는
     * 자리). 실물에서 그 갈래는 조건부 UPDATE 가 0행을 고치는 순간이다 — 가입이 행을 세운 뒤
     * 세션을 적기 전에 운영자 `rotate` 가 자격을 바꾸거나 행이 사라지면 선다.
     */
    @Volatile
    var contended: Boolean = false

    override fun complete(
        session: NewBrowserSession,
        verified: PassphraseHash,
        at: Instant,
    ): SignInWrite {
        if (contended) return SignInWrite.Contended
        if (!accounts.claim(session.account, verified, at)) return SignInWrite.CredentialMoved
        sessions.revokeAllOf(session.account, at)
        sessions.put(session)
        return SignInWrite.Applied
    }
}

/** 발급 값을 셀 수 있는 난수원 — 「로그인마다 새 값」을 HTTP 응답에서 단언할 수 있게 한다. */
class SequentialKeySource : BrowserSessionKeySource {
    private var issued = 0

    override fun newKey(): BrowserSessionKey {
        issued += 1
        return BrowserSessionKey("http-layer-test-session-$issued")
    }

    fun reset() {
        issued = 0
    }
}
