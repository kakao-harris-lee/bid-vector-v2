package bidvector.adapters.access

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.workflow.access.AccessVerdict
import bidvector.workflow.access.AccountProvisioning
import bidvector.workflow.access.BrowserAccessPolicyData
import bidvector.workflow.access.BrowserAccessWorkflow
import bidvector.workflow.access.BrowserSessionKey
import bidvector.workflow.access.ClearPassphrase
import bidvector.workflow.access.CsrfDemand
import bidvector.workflow.access.IssuedSession
import bidvector.workflow.access.LoginName
import bidvector.workflow.access.ProvisionOutcome
import bidvector.workflow.access.SignInOutcome
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

private val LOGIN = LoginName("adapter-test-operator")
private const val FIRST = "adapter-test-fixture-passphrase"
private const val SECOND = "adapter-test-fixture-passphrase-two"

/** 실 KDF 를 쓰되 느리지 않게 — 정책 최소 비용(값의 축은 정책 test 가 잰다). */
private const val TEST_HASH_COST = 10

private val POLICY =
    BrowserAccessPolicyData(
        idleWindow = Duration.ofMinutes(30),
        absoluteWindow = Duration.ofHours(12),
        maxFailedAttempts = 5,
        lockWindow = Duration.ofMinutes(15),
        hashCost = TEST_HASH_COST,
    )

/** 밀 수 있는 시계 — 만료·폐기 시각을 실 DB 에서 재려면 시간을 통제해야 한다. */
private class MovableClock(
    private var instant: Instant,
) : Clock {
    override fun now(): Instant = instant

    fun advance(amount: Duration) {
        instant = instant.plus(amount)
    }
}

/**
 * M7/7D — V20 두 표와 그 어댑터의 실 DB 측정. **use case 는 실물**이고 바뀌는 것이 없다
 * (경계·저장소가 전부 production 경로다) — 그래서 이 test 가 재는 것은 「SQL·열·트랜잭션이
 * 도메인 판정을 그대로 실어 나르는가」와 **DB 에 무엇이 남는가**다.
 *
 * 운영자 명령 경로(계정 생성·해시 교체)도 같은 자리에서 잰다 — 그 경로는 소유자 연결로만
 * 서고, 이 하네스의 `dataSource()` 가 그 소유자다(권한 축은 `AccessPrivilegeTest`).
 */
class AccessStorePersistenceTest : PersistenceTestSupport() {
    private val clock = MovableClock(Instant.parse("2026-10-16T00:00:00Z"))
    private val hasher = BcryptPassphraseHasher(TEST_HASH_COST)

    @Test
    fun `실 KDF 해시는 원문을 담지 않고 같은 원문도 매번 다른 문면이다 — salt`() {
        val first = hasher.hash(ClearPassphrase.wrap(FIRST))
        val second = hasher.hash(ClearPassphrase.wrap(FIRST))

        first.encoded shouldNotBe second.encoded
        first.encoded.contains(FIRST) shouldBe false
        hasher.verify(ClearPassphrase.wrap(FIRST), first) shouldBe true
        hasher.verify(ClearPassphrase.wrap(FIRST), second) shouldBe true
        hasher.verify(ClearPassphrase.wrap(SECOND), first) shouldBe false
    }

    @Test
    fun `부트스트랩은 계정을 만들고 두 번째 실행은 거부한다 — 덮어쓰지 않는다`() {
        provisioning().inTransaction { it.create(LOGIN, ClearPassphrase.wrap(FIRST)) } shouldBe ProvisionOutcome.Created
        val stored = storedHash()

        provisioning().inTransaction {
            it.create(LoginName("another"), ClearPassphrase.wrap(SECOND))
        } shouldBe ProvisionOutcome.RefusedAccountExists

        accountCount() shouldBe 1
        storedHash() shouldBe stored
    }

    @Test
    fun `저장된 세션 식별자는 쿠키 값의 해시다 — 평문 열이 없다`() {
        createAccount()
        val issued = signIn()

        storedDigests() shouldBe setOf(issued.key.digest().hex)
        storedDigests().contains(issued.key.cookieValue()) shouldBe false
        columnDump().any { it.contains(issued.key.cookieValue()) } shouldBe false
    }

    @Test
    fun `인증은 유휴 시한을 밀고 절대 시한은 밀지 않는다`() {
        createAccount()
        val issued = signIn()
        val absoluteBefore = timestampOf("absolute_expires_at")

        clock.advance(Duration.ofMinutes(10))
        access()
            .inTransaction { it.authenticate(issued.key, CsrfDemand.Exempt) }
            .shouldBeInstanceOf<AccessVerdict.Granted>()

        timestampOf("idle_expires_at") shouldBe clock.now().plus(POLICY.idleWindow)
        timestampOf("absolute_expires_at") shouldBe absoluteBefore
    }

    @Test
    fun `폐기 시각은 처음 값이 남는다 — 두 번째 로그아웃이 덮지 않는다`() {
        createAccount()
        val issued = signIn()

        access().inTransaction { it.signOut(issued.key) }
        val firstRevokedAt = timestampOf("revoked_at")
        clock.advance(Duration.ofMinutes(1))
        access().inTransaction { it.signOut(issued.key) }

        timestampOf("revoked_at") shouldBe firstRevokedAt
        access().inTransaction { it.authenticate(issued.key, CsrfDemand.Exempt) } shouldBe AccessVerdict.Denied
    }

    @Test
    fun `해시 교체는 그 계정의 세션을 전부 폐기한다 — 한 트랜잭션`() {
        createAccount()
        val issued = signIn()

        provisioning().inTransaction { it.rotate(LOGIN, ClearPassphrase.wrap(SECOND)) } shouldBe
            ProvisionOutcome.Rotated

        revokedCount() shouldBe 1
        access().inTransaction { it.authenticate(issued.key, CsrfDemand.Exempt) } shouldBe AccessVerdict.Denied
        access().inTransaction { it.signIn(LOGIN, ClearPassphrase.wrap(FIRST)) } shouldBe SignInOutcome.Refused
        access()
            .inTransaction { it.signIn(LOGIN, ClearPassphrase.wrap(SECOND)) }
            .shouldBeInstanceOf<SignInOutcome.Granted>()
    }

    @Test
    fun `교체 대상이 없으면 거부하고 계정을 만들지 않는다`() {
        provisioning().inTransaction { it.rotate(LOGIN, ClearPassphrase.wrap(FIRST)) } shouldBe
            ProvisionOutcome.RefusedAccountAbsent

        accountCount() shouldBe 0
    }

    @Test
    fun `연속 실패는 계수를 올리고 상한에서 잠근다 — 두 열이 그 상태를 든다`() {
        createAccount()

        repeat(POLICY.maxFailedAttempts - 1) {
            access().inTransaction { it.signIn(LOGIN, ClearPassphrase.wrap(SECOND)) } shouldBe SignInOutcome.Refused
        }
        failedAttempts() shouldBe POLICY.maxFailedAttempts - 1
        lockedUntil() shouldBe null

        access().inTransaction { it.signIn(LOGIN, ClearPassphrase.wrap(SECOND)) } shouldBe SignInOutcome.Refused

        failedAttempts() shouldBe 0
        lockedUntil() shouldBe clock.now().plus(POLICY.lockWindow)
        access().inTransaction { it.signIn(LOGIN, ClearPassphrase.wrap(FIRST)) } shouldBe SignInOutcome.Refused
    }

    @Test
    fun `모르는 세션 해시는 조회되지 않는다 — 다른 계정의 세션도 자기 해시로만 산다`() {
        createAccount()
        signIn()

        access().inTransaction {
            it.authenticate(BrowserSessionKey("never-issued-cookie-value"), CsrfDemand.Exempt)
        } shouldBe AccessVerdict.Denied
    }

    // --- 조립·조회 헬퍼 -------------------------------------------------------

    private fun access(): JdbcBrowserAccessTransaction =
        JdbcBrowserAccessTransaction(
            transactions = TransactionBoundary(dataSource()),
            hasher = hasher,
            keys = SecureBrowserSessionKeySource(),
            clock = clock,
            policy = POLICY,
        )

    private fun provisioning(): JdbcAccountProvisioningTransaction =
        JdbcAccountProvisioningTransaction(
            transactions = TransactionBoundary(dataSource()),
            hasher = hasher,
            clock = clock,
        )

    private fun createAccount() {
        provisioning().inTransaction { it.create(LOGIN, ClearPassphrase.wrap(FIRST)) } shouldBe ProvisionOutcome.Created
    }

    private fun signIn(): IssuedSession =
        access()
            .inTransaction { it.signIn(LOGIN, ClearPassphrase.wrap(FIRST)) }
            .shouldBeInstanceOf<SignInOutcome.Granted>()
            .session

    private fun storedDigests(): Set<String> = queryStrings("SELECT session_digest FROM browser_session").toSet()

    private fun columnDump(): List<String> =
        queryStrings(
            "SELECT concat_ws('|', session_digest, account_id, csrf_digest, created_at, last_used_at, " +
                "idle_expires_at, absolute_expires_at, revoked_at) FROM browser_session",
        )

    private fun storedHash(): String = queryStrings("SELECT passphrase_hash FROM account").single()

    private fun accountCount(): Int = queryStrings("SELECT COUNT(*) FROM account").single().toInt()

    private fun revokedCount(): Int =
        queryStrings("SELECT COUNT(*) FROM browser_session WHERE revoked_at IS NOT NULL").single().toInt()

    private fun failedAttempts(): Int = queryStrings("SELECT failed_attempts FROM account").single().toInt()

    private fun lockedUntil(): Instant? = timestampOrNull("SELECT locked_until FROM account")

    private fun timestampOf(column: String): Instant? = timestampOrNull("SELECT $column FROM browser_session")

    private fun timestampOrNull(sql: String): Instant? =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    check(rs.next()) { "행이 없다: $sql" }
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
}
