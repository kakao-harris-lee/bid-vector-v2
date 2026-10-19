package bidvector.adapters.access

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.workflow.access.AccountProvisioning
import bidvector.workflow.access.BrowserAccessPolicyData
import bidvector.workflow.access.ClearPassphrase
import bidvector.workflow.access.LoginName
import bidvector.workflow.access.PassphraseHash
import bidvector.workflow.access.PassphraseHasher
import bidvector.workflow.access.ProvisionOutcome
import bidvector.workflow.access.SignInOutcome
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val ACCOUNT = LoginName("concurrent-access-test-operator")
private const val RIGHT = "concurrent-access-test-fixture-value"
private const val WRONG = "concurrent-access-test-fixture-value-other"

/** 실 KDF 를 쓰되 느리지 않게 — 이 test 가 재는 축은 비용이 아니라 **동시성**이다. */
private const val CONCURRENCY_HASH_COST = 10

/** 상한보다 작다 — 「계수가 K 올랐다」를 잠금과 섞이지 않게 재려면 상한 아래여야 한다. */
private const val SIMULTANEOUS_FAILURES = 4

private const val JOIN_TIMEOUT_SECONDS = 60L

private val CONCURRENCY_POLICY =
    BrowserAccessPolicyData(
        idleWindow = Duration.ofMinutes(30),
        absoluteWindow = Duration.ofHours(12),
        maxFailedAttempts = 5,
        lockWindow = Duration.ofMinutes(15),
        hashCost = CONCURRENCY_HASH_COST,
    )

private class FixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}

/**
 * 검증 한가운데에서 멈출 수 있는 해시기 — **늦게 끝난 요청**을 결정적으로 만든다. 실 KDF 를
 * 감싸므로 검증 결과 자체는 실물과 같고, 바뀌는 것은 「언제 끝나는가」뿐이다.
 */
private class GatedPassphraseHasher(
    private val delegate: PassphraseHasher,
) : PassphraseHasher {
    private val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)

    override fun hash(clear: ClearPassphrase): PassphraseHash = delegate.hash(clear)

    override fun verify(
        clear: ClearPassphrase,
        hash: PassphraseHash,
    ): Boolean {
        entered.countDown()
        release.await(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return delegate.verify(clear, hash)
    }

    /** 계정을 이미 읽었고 검증 안에 들어왔다 — 이 시점 뒤의 상태 변화가 「늦은 요청」의 전제다. */
    fun awaitEntry() {
        entered.await(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
    }

    fun release() = release.countDown()
}

/**
 * M7/7D — **로그인 실패 계수와 잠금의 동시성**을 실 DB 위에서 잰다(code-review r1 H-1).
 *
 * 앞 판은 읽고-고쳐-쓰기였다: `SELECT` 에 잠금이 없고, 그 사이에 느린 KDF 검증이 들어가며,
 * `UPDATE` 가 **읽은 값에서 계산한 절대값**을 썼다. 그래서 (ⓐ) 동시 실패 K 건이 계수를 1 만
 * 올리고 (ⓑ) 늦게 끝난 요청이 막 걸린 잠금을 지웠다 — 둘 다 온라인 추측 방어를 사실상 무효로
 * 만드는 형태다. 여기 세 test 가 그 둘을 각각 잠근다.
 *
 * 이 축은 단위 test 로 잴 수 없다 — 계수 증가의 원자성은 **SQL 문장의 성질**이고, 포트의
 * in-memory 사본은 그 성질을 흉내 낼 뿐 증명하지 못한다. 그래서 실 Postgres 위에서 실제 스레드로
 * 잰다(`PersistenceTestSupport` 의 공유 컨테이너).
 */
class AccessConcurrencyTest : PersistenceTestSupport() {
    private val clock = FixedClock(Instant.parse("2026-10-16T00:00:00Z"))
    private val hasher = BcryptPassphraseHasher(CONCURRENCY_HASH_COST)

    @Test
    fun `동시 실패 K 건은 계수를 K 올린다 — 읽은 값이 아니라 행의 값에서 오른다`() {
        createAccount()

        val outcomes = inParallel(SIMULTANEOUS_FAILURES) { signIn(hasher, WRONG) }

        outcomes.size shouldBe SIMULTANEOUS_FAILURES
        outcomes.all { it == SignInOutcome.Refused } shouldBe true
        failedAttempts() shouldBe SIMULTANEOUS_FAILURES
        lockedUntil() shouldBe null
    }

    @Test
    fun `늦게 끝난 실패는 막 걸린 잠금을 지우지 않는다`() {
        createAccount()
        val gated = GatedPassphraseHasher(hasher)
        val late = startSignIn(gated, WRONG)
        gated.awaitEntry()

        repeat(CONCURRENCY_POLICY.maxFailedAttempts) { signIn(hasher, WRONG) shouldBe SignInOutcome.Refused }
        val lockedAt = lockedUntil()
        gated.release()
        late.join(JOIN_TIMEOUT_SECONDS * MILLIS_PER_SECOND)

        lockedAt shouldNotBe null
        lockedUntil() shouldBe lockedAt
    }

    @Test
    fun `늦게 끝난 성공은 막 걸린 잠금을 지우지 않고 세션도 받지 못한다`() {
        createAccount()
        val gated = GatedPassphraseHasher(hasher)
        val outcome = SignInSlot()
        val late = startSignIn(gated, RIGHT, outcome)
        gated.awaitEntry()

        repeat(CONCURRENCY_POLICY.maxFailedAttempts) { signIn(hasher, WRONG) shouldBe SignInOutcome.Refused }
        val lockedAt = lockedUntil()
        gated.release()
        late.join(JOIN_TIMEOUT_SECONDS * MILLIS_PER_SECOND)

        lockedAt shouldNotBe null
        lockedUntil() shouldBe lockedAt
        outcome.value shouldBe SignInOutcome.Refused
        sessionCount() shouldBe 0
    }

    // --- 조립·동시 실행 헬퍼 ---------------------------------------------------

    private fun access(hasher: PassphraseHasher): JdbcBrowserAccessTransaction =
        JdbcBrowserAccessTransaction(
            transactions = TransactionBoundary(dataSource()),
            hasher = hasher,
            keys = SecureBrowserSessionKeySource(),
            clock = clock,
            policy = CONCURRENCY_POLICY,
        )

    private fun signIn(
        hasher: PassphraseHasher,
        value: String,
    ): SignInOutcome = access(hasher).inTransaction { it.signIn(ACCOUNT, ClearPassphrase.wrap(value)) }

    /** 결과를 스레드 밖으로 내는 자리 — 단언은 `join` 뒤에 본체 스레드가 한다. */
    private class SignInSlot {
        @Volatile
        var value: SignInOutcome? = null
    }

    private fun startSignIn(
        hasher: PassphraseHasher,
        value: String,
        slot: SignInSlot = SignInSlot(),
    ): Thread = Thread { slot.value = signIn(hasher, value) }.also { it.start() }

    /**
     * 같은 순간에 출발시킨다 — 한 문 뒤에 다른 문이 서면 「동시」가 아니라 순차이고, 그 위에서는
     * 읽고-고쳐-쓰기도 초록이다(이 test 가 재려는 결함이 보이지 않는다).
     */
    private fun <T> inParallel(
        count: Int,
        action: () -> T,
    ): List<T> {
        val ready = CountDownLatch(count)
        val gate = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<T>())
        val threads =
            (1..count).map {
                Thread {
                    ready.countDown()
                    gate.await(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    results += action()
                }.also { it.start() }
            }
        ready.await(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
        gate.countDown()
        threads.forEach { it.join(JOIN_TIMEOUT_SECONDS * MILLIS_PER_SECOND) }
        return results.toList()
    }

    private fun createAccount() {
        JdbcAccountProvisioningTransaction(
            transactions = TransactionBoundary(dataSource()),
            hasher = hasher,
            clock = clock,
        ).inTransaction { it.create(ACCOUNT, ClearPassphrase.wrap(RIGHT)) } shouldBe ProvisionOutcome.Created
    }

    private fun failedAttempts(): Int = queryStrings("SELECT failed_attempts FROM account").single().toInt()

    private fun sessionCount(): Int = queryStrings("SELECT COUNT(*) FROM browser_session").single().toInt()

    private fun lockedUntil(): Instant? =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT locked_until FROM account").use { rs ->
                    check(rs.next()) { "계정 행이 없다" }
                    rs.getTimestamp(1)?.toInstant()
                }
            }
        }

    private fun queryStrings(sql: String): List<String> {
        val values = mutableListOf<String>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    while (rs.next()) values += rs.getString(1)
                }
            }
        }
        return values
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
