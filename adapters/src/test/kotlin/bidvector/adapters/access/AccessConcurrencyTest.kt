package bidvector.adapters.access

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.workflow.access.AccountProvisioning
import bidvector.workflow.access.BrowserAccessPolicyData
import bidvector.workflow.access.BrowserSessionKey
import bidvector.workflow.access.BrowserSessionKeySource
import bidvector.workflow.access.ClearPassphrase
import bidvector.workflow.access.LoginName
import bidvector.workflow.access.PassphraseHash
import bidvector.workflow.access.PassphraseHasher
import bidvector.workflow.access.ProvisionOutcome
import bidvector.workflow.access.SignInOutcome
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

private val ACCOUNT = LoginName("concurrent-access-test-operator")
private const val RIGHT = "concurrent-access-test-fixture-value"
private const val WRONG = "concurrent-access-test-fixture-value-other"
private const val NEXT = "concurrent-access-test-fixture-value-rotated"

/** 실 KDF 를 쓰되 느리지 않게 — 이 test 가 재는 축은 비용이 아니라 **동시성**이다. */
private const val CONCURRENCY_HASH_COST = 10

/** 상한보다 작다 — 「계수가 K 올랐다」를 잠금과 섞이지 않게 재려면 상한 아래여야 한다. */
private const val SIMULTANEOUS_FAILURES = 4

/** 둘이면 충분하다 — 「직렬화가 없으면 둘이 산다」를 재는 데 더 많은 스레드가 보태는 것이 없다. */
private const val SIMULTANEOUS_LOGINS = 2

/** 결과 불변식의 보조 반복 — 결정성은 위 잠금 test 가 지고 이쪽은 되풀이로 거든다. */
private const val CONCURRENT_LOGIN_ROUNDS = 5

/** 대기 상한 위의 여유 — KDF 한 번과 왕복 몇 번이 그 안에 든다. */
private val LOCK_WAIT_SLACK: Duration = Duration.ofSeconds(5)

/**
 * 두 스레드를 **난수 발급 자리에서** 만나게 하는 장벽. 그 자리를 고른 이유는 확정 호출
 * **직전**이라 사이에 느린 일(KDF)이 없다는 것이다 — 둘이 같은 순간 확정으로 들어간다.
 */
private class BarrierKeySource(
    parties: Int,
    private val label: String,
) : BrowserSessionKeySource {
    private val barrier = CyclicBarrier(parties)
    private val issued = AtomicInteger()

    override fun newKey(): BrowserSessionKey {
        val ordinal = issued.incrementAndGet()
        barrier.await(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return BrowserSessionKey("barrier-$label-$ordinal")
    }
}

private const val JOIN_TIMEOUT_SECONDS = 60L

private val CONCURRENCY_POLICY =
    BrowserAccessPolicyData(
        idleWindow = Duration.ofMinutes(30),
        absoluteWindow = Duration.ofHours(12),
        maxFailedAttempts = 5,
        lockWindow = Duration.ofMinutes(15),
        hashCost = CONCURRENCY_HASH_COST,
        lockWait = Duration.ofMillis(500),
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

    @Test
    fun `로그인의 느린 검증은 커넥션을 쥐지 않는다 — 풀이 하나여도 다른 질의가 지난다`() {
        createAccount()

        newPool(SINGLE_CONNECTION_POOL, POOL_TIMEOUT_MS).use { pool ->
            val gated = GatedPassphraseHasher(hasher)
            val slot = SignInSlot()
            val late = startSignIn(gated, RIGHT, slot, pool)
            gated.awaitEntry()

            // 검증 한가운데다. 앞 판은 여기서 트랜잭션이 열린 채였고 풀의 **유일한** 커넥션이
            // 묶여 있었다 — 이 질의가 `connectionTimeout` 을 넘겨 실패했다. 무인증으로 닿는
            // 로그인이 인증된 요청까지 굶기는 형태다.
            queryOne(pool, "SELECT 1") shouldBe "1"

            gated.release()
            late.join(JOIN_TIMEOUT_SECONDS * MILLIS_PER_SECOND)
            slot.value.shouldBeInstanceOf<SignInOutcome.Granted>()
        }
    }

    @Test
    fun `비밀 문구 교체 중이던 옛 값 로그인은 교체 뒤에 세션을 받지 못한다`() {
        createAccount()
        val gated = GatedPassphraseHasher(hasher)
        val outcome = SignInSlot()
        val late = startSignIn(gated, RIGHT, outcome)
        gated.awaitEntry()

        // 옛 값의 검증이 도는 사이에 운영자가 교체한다 — 교체는 그 계정의 세션도 전부 폐기한다.
        rotateTo(NEXT) shouldBe ProvisionOutcome.Rotated
        gated.release()
        late.join(JOIN_TIMEOUT_SECONDS * MILLIS_PER_SECOND)

        // 앞 판은 여기서 Granted 였고, 그 세션이 교체의 「전부 폐기」를 지나 **유일하게 살아
        // 있는** 세션이 됐다 — 옛 비밀 문구를 쥔 쪽이 교체 뒤에도 들어오는 길이다.
        outcome.value shouldBe SignInOutcome.Refused
        liveSessionCount() shouldBe 0
        signIn(hasher, NEXT).shouldBeInstanceOf<SignInOutcome.Granted>()
    }

    /**
     * **결정적**이다(verifier r2 R2-1 · 계약 D-7D-6). 기다리는 자리가 분명하다: test 가 다른
     * 커넥션으로 계정 행을 **먼저 잠그고**(커밋하지 않는다), 그 상태에서 로그인을 시작한다.
     * 확정의 첫 문장이 그 잠금을 얻어야만 진행하므로, 잠금이 풀리지 않는 한 로그인은 대기
     * 상한에 걸려 **반드시 거부**된다 — 스레드 경합의 운에 기대지 않는다.
     *
     * 잠금을 걷어낸 확정(변이 R-M23a)에서는 이 로그인이 **그대로 통과**해 세션을 만든다.
     * 그래서 이 test 는 그 변이에 대해 검출률 100% 다(앞 판의 단발 경합은 14~15/20 이었다).
     *
     * 같은 측정이 code-review r3 L-2(대기 상한)의 회귀 test 이기도 하다 — 상한이 없으면 이
     * test 는 거부가 아니라 **영원히 멈춘다**.
     */
    @Test
    fun `계정 행이 잠겨 있으면 로그인은 대기 상한 안에 거부된다 — 확정은 그 잠금을 얻어야 한다`() {
        createAccount()

        holdingAccountRowLock {
            val started = System.nanoTime()
            val outcome = signIn(hasher, RIGHT)
            val waited = Duration.ofNanos(System.nanoTime() - started)

            outcome shouldBe SignInOutcome.Refused
            sessionCount() shouldBe 0
            // 상한 안에서 끝났다 — 무한 대기가 아니다(여유는 KDF 와 왕복 비용).
            (waited < CONCURRENCY_POLICY.lockWait.plus(LOCK_WAIT_SLACK)) shouldBe true
        }

        // 잠금이 풀리면 같은 자격이 그대로 통한다 — 거부가 자격 탓이 아니었다.
        signIn(hasher, RIGHT).shouldBeInstanceOf<SignInOutcome.Granted>()
    }

    /**
     * **틀린 비밀 문구 경로도 상한 아래 선다**(code-review r4 M-1). 그 경로는 자격 없이 누구나
     * 칠 수 있는데, 앞 판에서는 상한이 확정 문장에만 있어 실패 계수 갱신이 **무한정** 기다렸다 —
     * 상한이 필요했던 이유(기다리는 쪽이 커넥션을 쥔 채 서 있어 풀이 마른다)가 더 싼 입구로
     * 남아 있었다.
     *
     * 계정 행을 쥔 채 틀린 문구로 로그인하면 지금은 **끝난다**: 계수를 올리지 못한 채 거부다
     * (계수를 못 올리는 것은 안전한 방향 — 잠금이 늦어질 뿐 권한이 새지 않는다).
     */
    @Test
    fun `계정 행이 잠겨 있으면 틀린 비밀 문구도 대기 상한 안에 끝난다 — 무한 대기가 아니다`() {
        createAccount()

        holdingAccountRowLock {
            val started = System.nanoTime()
            val outcome = signIn(hasher, WRONG)
            val waited = Duration.ofNanos(System.nanoTime() - started)

            outcome shouldBe SignInOutcome.Refused
            (waited < CONCURRENCY_POLICY.lockWait.plus(LOCK_WAIT_SLACK)) shouldBe true
        }

        // 잠금 아래에서는 계수를 올리지 못했다. 잠금이 풀린 뒤의 같은 실패는 정상대로 오른다.
        failedAttempts() shouldBe 0
        signIn(hasher, WRONG) shouldBe SignInOutcome.Refused
        failedAttempts() shouldBe 1
    }

    /**
     * 동시 로그인의 **결과**를 잰다. 출발은 장벽으로 맞춘다 — 두 스레드가 난수 발급 자리
     * ([BarrierKeySource])에서 서로를 기다렸다가 **같은 순간** 확정으로 들어간다. 그 자리를 고른
     * 이유는 확정 호출 **직전**이라 사이에 느린 일(KDF)이 없기 때문이다.
     *
     * 그래도 두 확정의 문장 순서까지 고정하지는 못하므로, 잠금 자체의 회귀는 위의 결정적 test 가
     * 진다. 이 test 가 보는 것은 **결과의 불변식**이다(둘 다 성공하되 살아 있는 것은 하나).
     * 반복은 보조다.
     */
    @Test
    fun `동시 로그인 둘이어도 살아 있는 세션은 하나다 — 확정이 계정 행에서 줄 선다`() {
        createAccount()

        repeat(CONCURRENT_LOGIN_ROUNDS) { round ->
            val barrier = BarrierKeySource(SIMULTANEOUS_LOGINS, "round-$round")
            val outcomes = inParallel(SIMULTANEOUS_LOGINS) { signIn(hasher, RIGHT, keys = barrier) }

            outcomes.size shouldBe SIMULTANEOUS_LOGINS
            outcomes.all { it is SignInOutcome.Granted } shouldBe true
            // 앞 판은 둘이었다 — 두 트랜잭션의 폐기 문장이 서로의 등록을 스냅숏 밖에 두고 지나갔다.
            liveSessionCount() shouldBe 1
        }

        sessionCount() shouldBe SIMULTANEOUS_LOGINS * CONCURRENT_LOGIN_ROUNDS
    }

    // --- 조립·동시 실행 헬퍼 ---------------------------------------------------

    private fun access(
        hasher: PassphraseHasher,
        source: DataSource = dataSource(),
        keys: BrowserSessionKeySource = SecureBrowserSessionKeySource(),
    ): JdbcBrowserAccessTransaction =
        JdbcBrowserAccessTransaction(
            transactions = TransactionBoundary(source),
            dataSource = source,
            hasher = hasher,
            keys = keys,
            clock = clock,
            policy = CONCURRENCY_POLICY,
        )

    private fun signIn(
        hasher: PassphraseHasher,
        value: String,
        source: DataSource = dataSource(),
        keys: BrowserSessionKeySource = SecureBrowserSessionKeySource(),
    ): SignInOutcome = access(hasher, source, keys).inSignInScope { it.signIn(ACCOUNT, ClearPassphrase.wrap(value)) }

    /**
     * 계정 행을 **잡은 채** [action] 을 돌린다 — 커밋하지 않으므로 그 잠금은 블록이 끝날 때까지
     * 산다. 잡는 문장은 production 과 같은 열만 쓴다(값을 바꾸지 않는 자기 대입이라 행의 내용은
     * 그대로이고, 잠금만 선다).
     */
    private fun holdingAccountRowLock(action: () -> Unit) {
        dataSource().connection.use { connection ->
            connection.autoCommit = false
            connection.createStatement().use {
                it.executeUpdate("UPDATE account SET failed_attempts = failed_attempts")
            }
            try {
                action()
            } finally {
                connection.rollback()
            }
        }
    }

    /** 결과를 스레드 밖으로 내는 자리 — 단언은 `join` 뒤에 본체 스레드가 한다. */
    private class SignInSlot {
        @Volatile
        var value: SignInOutcome? = null
    }

    private fun startSignIn(
        hasher: PassphraseHasher,
        value: String,
        slot: SignInSlot = SignInSlot(),
        source: DataSource = dataSource(),
    ): Thread = Thread { slot.value = signIn(hasher, value, source) }.also { it.start() }

    private fun sessionCount(): Int = queryStrings("SELECT COUNT(*) FROM browser_session").single().toInt()

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

    private fun provisioning(): JdbcAccountProvisioningTransaction =
        JdbcAccountProvisioningTransaction(
            transactions = TransactionBoundary(dataSource()),
            hasher = hasher,
            clock = clock,
        )

    private fun createAccount() {
        provisioning().inTransaction { it.create(ACCOUNT, ClearPassphrase.wrap(RIGHT)) } shouldBe
            ProvisionOutcome.Created
    }

    private fun rotateTo(value: String): ProvisionOutcome =
        provisioning().inTransaction { it.rotate(ACCOUNT, ClearPassphrase.wrap(value)) }

    private fun liveSessionCount(): Int =
        queryStrings("SELECT COUNT(*) FROM browser_session WHERE revoked_at IS NULL").single().toInt()

    private fun failedAttempts(): Int = queryStrings("SELECT failed_attempts FROM account").single().toInt()

    private fun lockedUntil(): Instant? =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT locked_until FROM account").use { rs ->
                    check(rs.next()) { "계정 행이 없다" }
                    rs.getTimestamp(1)?.toInstant()
                }
            }
        }

    /** 풀에서 커넥션을 빌려 한 값을 읽는다 — **빌릴 수 있는가** 자체가 이 test 의 측정이다. */
    private fun queryOne(
        source: DataSource,
        sql: String,
    ): String =
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    check(rs.next()) { "행이 없다: $sql" }
                    rs.getString(1)
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

        /** 하나다 — 「쥐고 있으면 아무도 못 쓴다」를 모호함 없이 재는 유일한 크기다. */
        const val SINGLE_CONNECTION_POOL = 1

        /** 쥐고 있으면 이 시간 뒤 실패한다. KDF 한 번(수십 ms)보다 충분히 길다 — 이 시간이 짧아
         *  실패하는 것이 아니라 **커넥션이 묶여 있어** 실패하는 것임을 분명히 한다. */
        const val POOL_TIMEOUT_MS = 3_000L
    }
}
