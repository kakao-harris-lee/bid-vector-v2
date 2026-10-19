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

/**
 * bcrypt 블록 입력의 한계(72바이트)를 **넘는** 길이. 값을 직접 적는 대신 한계보다 길다는
 * 사실만 쓰면 되므로 넉넉히 잡는다 — 이 수가 재는 것은 「한계 위에서 무슨 일이 일어나는가」다.
 */
private const val LONG_PASSPHRASE_BYTES = 100

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
    fun `한계를 넘는 긴 비밀 문구도 해시·검증이 왕복한다 — 거부도 절단도 아니다`() {
        val long = "x".repeat(LONG_PASSPHRASE_BYTES)
        val longWithDifferentTail = long.dropLast(1) + "y"

        val stored = hasher.hash(ClearPassphrase.wrap(long))

        hasher.verify(ClearPassphrase.wrap(long), stored) shouldBe true
        // 절단 전략이었다면 앞 72바이트가 같은 이 값이 통과한다 — 통과하면 유효 강도가 잘린 것이다.
        hasher.verify(ClearPassphrase.wrap(longWithDifferentTail), stored) shouldBe false
        hasher.verify(ClearPassphrase.wrap(FIRST), stored) shouldBe false
    }

    @Test
    fun `긴 비밀 문구의 로그인은 예외가 아니라 보통의 거부다`() {
        createAccount()

        val long = ClearPassphrase.wrap("y".repeat(LONG_PASSPHRASE_BYTES))

        access().inSignInScope { it.signIn(LOGIN, long) } shouldBe SignInOutcome.Refused
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
    fun `로그인 재발급은 앞 세션을 폐기한다 — 폐기와 등록이 포트 한 호출이다`() {
        createAccount()
        val first = signIn()

        val second = signIn()

        revokedCount() shouldBe 1
        access().inTransaction { it.authenticate(first.key, CsrfDemand.Exempt) } shouldBe AccessVerdict.Denied
        access()
            .inTransaction { it.authenticate(second.key, CsrfDemand.Exempt) }
            .shouldBeInstanceOf<AccessVerdict.Granted>()
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
        access().inSignInScope { it.signIn(LOGIN, ClearPassphrase.wrap(FIRST)) } shouldBe SignInOutcome.Refused
        access()
            .inSignInScope { it.signIn(LOGIN, ClearPassphrase.wrap(SECOND)) }
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
            access().inSignInScope { it.signIn(LOGIN, ClearPassphrase.wrap(SECOND)) } shouldBe SignInOutcome.Refused
        }
        failedAttempts() shouldBe POLICY.maxFailedAttempts - 1
        lockedUntil() shouldBe null

        access().inSignInScope { it.signIn(LOGIN, ClearPassphrase.wrap(SECOND)) } shouldBe SignInOutcome.Refused

        failedAttempts() shouldBe 0
        lockedUntil() shouldBe clock.now().plus(POLICY.lockWindow)
        access().inSignInScope { it.signIn(LOGIN, ClearPassphrase.wrap(FIRST)) } shouldBe SignInOutcome.Refused
    }

    /**
     * verifier r1 F5 — **계정 간 격리**. 실패 계수·잠금 문장의 `WHERE id = ?` 를 무력화해(전 계정에
     * 적용) 돌려도 access 전 test 가 초록이었다. 지금 코드는 맞지만 그 사실을 잠그는 test 가
     * 없었다 — R-2 가 다수 계정을 전제하므로 이 축은 행이 둘이 되는 날 조용히 깨질 자리다.
     */
    @Test
    fun `한 계정의 실패·잠금이 다른 계정에 닿지 않는다 — 계정 간 격리`() {
        createAccount()
        val other = LoginName("adapter-test-operator-other")
        insertSecondAccount(other)

        repeat(POLICY.maxFailedAttempts) {
            access().inSignInScope { it.signIn(LOGIN, ClearPassphrase.wrap(SECOND)) } shouldBe SignInOutcome.Refused
        }

        lockedUntilOf(LOGIN) shouldNotBe null
        failedAttemptsOf(other) shouldBe 0
        lockedUntilOf(other) shouldBe null
        // 잠긴 쪽은 올바른 값에도 거부되고, 다른 계정은 그대로 들어온다.
        access().inSignInScope { it.signIn(LOGIN, ClearPassphrase.wrap(FIRST)) } shouldBe SignInOutcome.Refused
        access()
            .inSignInScope { it.signIn(other, ClearPassphrase.wrap(SECOND)) }
            .shouldBeInstanceOf<SignInOutcome.Granted>()
    }

    /**
     * 같은 축의 반대 방향 — **성공의 초기화**도 자기 계정에만 닿는다(확정 문장의 `WHERE id = ?`).
     */
    @Test
    fun `한 계정의 성공이 다른 계정의 실패 계수를 비우지 않는다`() {
        createAccount()
        val other = LoginName("adapter-test-operator-other")
        insertSecondAccount(other)
        repeat(2) {
            access().inSignInScope { it.signIn(other, ClearPassphrase.wrap(FIRST)) } shouldBe SignInOutcome.Refused
        }

        access().inSignInScope { it.signIn(LOGIN, ClearPassphrase.wrap(FIRST)) }
            .shouldBeInstanceOf<SignInOutcome.Granted>()

        failedAttemptsOf(other) shouldBe 2
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
            dataSource = dataSource(),
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
            .inSignInScope { it.signIn(LOGIN, ClearPassphrase.wrap(FIRST)) }
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

    /**
     * 둘째 계정은 **저장소 직접**으로 심는다 — 부트스트랩 use case 는 「이미 있으면 거부」라
     * 둘째를 만들지 않는다(산출물 2). 쓰는 경로는 production 의 소유자 연결 저장소 그대로다.
     */
    private fun insertSecondAccount(login: LoginName) {
        val boundary = TransactionBoundary(dataSource())
        boundary.inTransaction {
            JdbcAccountProvisioningStore(boundary)
                .insert(login, hasher.hash(ClearPassphrase.wrap(SECOND)), clock.now())
        }
    }

    private fun failedAttemptsOf(login: LoginName): Int =
        queryStrings("SELECT failed_attempts FROM account WHERE login_name = '${login.value}'").single().toInt()

    private fun lockedUntilOf(login: LoginName): Instant? =
        timestampOrNull("SELECT locked_until FROM account WHERE login_name = '${login.value}'")

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
