package bidvector.workflow.access

import bidvector.workflow.strategy.Clock
import java.time.Duration
import java.time.Instant

/**
 * 7D use case test 의 이중체 — **mock framework 를 쓰지 않는다**(v2-지침서 §5: 도메인·use case
 * test 는 값과 fake port 로).
 */
class MovableClock(
    private var instant: Instant,
) : Clock {
    override fun now(): Instant = instant

    fun advance(amount: Duration) {
        instant = instant.plus(amount)
    }
}

/** 해시를 흉내 내는 fake — 실 KDF 는 어댑터 test 가 잰다. 원문을 **되돌릴 수 있게** 두지 않는다. */
class FakePassphraseHasher : PassphraseHasher {
    override fun hash(clear: ClearPassphrase): PassphraseHash = PassphraseHash("fake:${fingerprint(clear)}")

    override fun verify(
        clear: ClearPassphrase,
        hash: PassphraseHash,
    ): Boolean = hash.encoded == hash(clear).encoded

    /** 같은 입력이 같은 값을 내고 다른 입력이 다른 값을 내는 것만 보장한다(test 용). */
    private fun fingerprint(clear: ClearPassphrase): Int = clear.hashInput().concatToString().hashCode()
}

/** 발급 값을 셀 수 있는 fake — 「로그인마다 새 값」을 단언할 수 있어야 한다. */
class CountingKeySource(
    private val prefix: String = "session-value",
) : BrowserSessionKeySource {
    var issued: Int = 0
        private set

    override fun newKey(): BrowserSessionKey {
        issued += 1
        return BrowserSessionKey("$prefix-$issued")
    }
}

/**
 * 계수는 **이 사본 안에서만** 산다(스냅숏에 없다, H-1) — 포트 서명이 「올린다」이므로 fake 도
 * 같은 모양으로 올린다. 동시성 자체는 여기서 재지 못한다(실 DB test 가 잰다).
 */
class InMemoryAccountStore(
    private val knownLogin: LoginName,
    private var snapshot: AccountSnapshot,
) : AccountStorePort {
    /** 올라간 계수의 이력 — 「상한까지 몇 번 셌나」를 use case test 가 단언한다. */
    val recordedFailures: MutableList<Int> = mutableListOf()
    private var attempts: Int = 0

    override fun byLogin(login: LoginName): AccountSnapshot? = snapshot.takeIf { login.value == knownLogin.value }

    override fun registerFailure(
        account: AccountId,
        attemptLimit: Int,
        lockUntil: Instant,
    ) {
        val next = attempts + 1
        recordedFailures += next
        if (next >= attemptLimit) {
            attempts = 0
            snapshot = AccountSnapshot(snapshot.id, snapshot.hash, lockUntil)
        } else {
            attempts = next
        }
    }

    override fun clearFailures(
        account: AccountId,
        now: Instant,
    ): Boolean {
        if (snapshot.lockedUntil?.isAfter(now) == true) return false
        attempts = 0
        snapshot = AccountSnapshot(snapshot.id, snapshot.hash, null)
        return true
    }
}

/** 세션 저장의 in-memory 판 — 해시 키로만 색인한다(포트 서명이 원문을 받지 않는다). */
class InMemorySessionStore : BrowserSessionStorePort {
    private val rows: MutableMap<String, BrowserSessionSnapshot> = mutableMapOf()

    val openCount: Int get() = rows.size

    override fun open(session: NewBrowserSession) {
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
        rows[digest.hex]?.let { rows[digest.hex] = it.copyWith(lastUsedAt = usedAt, idleExpiresAt = idleExpiresAt) }
    }

    override fun revoke(
        digest: BrowserSessionDigest,
        at: Instant,
    ) {
        rows[digest.hex]?.let { rows[digest.hex] = it.copyWith(revokedAt = at) }
    }

    override fun revokeAllOf(
        account: AccountId,
        at: Instant,
    ) {
        rows
            .filterValues { it.account.value == account.value && it.revokedAt == null }
            .forEach { (key, row) -> rows[key] = row.copyWith(revokedAt = at) }
    }
}

/** `BrowserSessionSnapshot` 은 data class 가 아니다(값 획득 축을 좁게 두려고) — test 쪽 사본 helper. */
private fun BrowserSessionSnapshot.copyWith(
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

class InMemoryProvisioningStore : AccountProvisioningPort {
    private val byLogin: MutableMap<String, Pair<AccountId, PassphraseHash>> = mutableMapOf()
    private var nextId: Long = 1

    override fun anyExists(): Boolean = byLogin.isNotEmpty()

    override fun insert(
        login: LoginName,
        hash: PassphraseHash,
        at: Instant,
    ): AccountId {
        val id = AccountId(nextId)
        nextId += 1
        byLogin[login.value] = id to hash
        return id
    }

    override fun replaceHash(
        login: LoginName,
        hash: PassphraseHash,
        at: Instant,
    ): AccountId? {
        val existing = byLogin[login.value] ?: return null
        byLogin[login.value] = existing.first to hash
        return existing.first
    }

    fun hashOf(login: LoginName): PassphraseHash? = byLogin[login.value]?.second
}
