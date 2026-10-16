package bidvector.workflow.access

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

private val LOGIN = LoginName("operator")
private const val FIRST = "first-passphrase-value"
private const val SECOND = "second-passphrase-value"

/**
 * 산출물 2(부트스트랩)와 산출물 3 마지막 절(비밀 문구가 바뀌면 그 계정 세션 전부 폐기)의
 * use case 층 측정.
 */
class AccountProvisioningTest {
    private val hasher = FakePassphraseHasher()
    private val clock = MovableClock(Instant.parse("2026-10-16T00:00:00Z"))
    private val provisioning = InMemoryProvisioningStore()
    private val sessions = InMemorySessionStore()

    private fun command(): AccountProvisioning = AccountProvisioning(provisioning, sessions, hasher, clock)

    @Test
    fun `첫 실행은 계정을 만든다 — 기본 값을 지어내지 않고 받은 값만 해시한다`() {
        command().create(LOGIN, ClearPassphrase.wrap(FIRST)) shouldBe ProvisionOutcome.Created

        val stored = requireNotNull(provisioning.hashOf(LOGIN)) { "해시가 저장되지 않았다" }
        hasher.verify(ClearPassphrase.wrap(FIRST), stored) shouldBe true
        stored.encoded shouldNotBe FIRST
    }

    @Test
    fun `이미 계정이 있으면 거부한다 — 덮어쓰지 않는다`() {
        command().create(LOGIN, ClearPassphrase.wrap(FIRST)) shouldBe ProvisionOutcome.Created
        val first = provisioning.hashOf(LOGIN)

        command().create(LoginName("another"), ClearPassphrase.wrap(SECOND)) shouldBe
            ProvisionOutcome.RefusedAccountExists
        command().create(LOGIN, ClearPassphrase.wrap(SECOND)) shouldBe ProvisionOutcome.RefusedAccountExists

        provisioning.hashOf(LOGIN)?.encoded shouldBe first?.encoded
    }

    @Test
    fun `교체는 그 계정의 세션을 전부 폐기한다`() {
        command().create(LOGIN, ClearPassphrase.wrap(FIRST)) shouldBe ProvisionOutcome.Created
        val issued = signInWithStoredHash()
        workflow().authenticate(issued.key, CsrfDemand.Exempt).shouldBeInstanceOf<AccessVerdict.Granted>()

        command().rotate(LOGIN, ClearPassphrase.wrap(SECOND)) shouldBe ProvisionOutcome.Rotated

        workflow().authenticate(issued.key, CsrfDemand.Exempt) shouldBe AccessVerdict.Denied
    }

    @Test
    fun `교체 대상이 없으면 거부한다 — 교체가 계정을 만들지 않는다`() {
        command().rotate(LOGIN, ClearPassphrase.wrap(FIRST)) shouldBe ProvisionOutcome.RefusedAccountAbsent
        provisioning.anyExists() shouldBe false
    }

    /** 교체 전의 세션을 만들기 위한 로그인 — 저장된 해시를 그대로 쓰는 요청 경로 조립이다. */
    private fun signInWithStoredHash(): IssuedSession =
        workflow()
            .signIn(LOGIN, ClearPassphrase.wrap(FIRST))
            .shouldBeInstanceOf<SignInOutcome.Granted>()
            .session

    private fun workflow(): BrowserAccessWorkflow =
        BrowserAccessWorkflow(
            accounts =
                InMemoryAccountStore(
                    knownLogin = LOGIN,
                    snapshot =
                        AccountSnapshot(
                            AccountId(1),
                            requireNotNull(provisioning.hashOf(LOGIN)) { "계정이 없다" },
                            0,
                            null,
                        ),
                ),
            sessions = sessions,
            hasher = hasher,
            keys = CountingKeySource(),
            clock = clock,
            policy =
                BrowserAccessPolicyData(
                    idleWindow = Duration.ofMinutes(30),
                    absoluteWindow = Duration.ofHours(12),
                    maxFailedAttempts = 5,
                    lockWindow = Duration.ofMinutes(15),
                    hashCost = 12,
                ),
        )
}
