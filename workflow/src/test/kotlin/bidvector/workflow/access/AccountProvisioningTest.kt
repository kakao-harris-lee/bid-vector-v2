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
    fun `같은 아이디가 있으면 거부한다 — 덮어쓰지 않는다`() {
        command().create(LOGIN, ClearPassphrase.wrap(FIRST)) shouldBe ProvisionOutcome.Created
        val first = provisioning.hashOf(LOGIN)

        command().create(LOGIN, ClearPassphrase.wrap(SECOND)) shouldBe ProvisionOutcome.RefusedAccountExists

        provisioning.hashOf(LOGIN)?.encoded shouldBe first?.encoded
    }

    /**
     * M15/15B — **완화된 조건의 반대쪽**. 앞 판은 이 자리에서 거부했고(계정이 하나라도 있으면),
     * 그래서 운영자가 둘째 계정을 손으로 만들 수 없었다. 가입이 열린 뒤 그 금지는 아무것도
     * 지키지 않는다.
     */
    @Test
    fun `다른 아이디는 계정이 이미 있어도 만든다 — 운영자가 둘째 계정을 손으로 만들 수 있다`() {
        command().create(LOGIN, ClearPassphrase.wrap(FIRST)) shouldBe ProvisionOutcome.Created

        command().create(LoginName("another"), ClearPassphrase.wrap(SECOND)) shouldBe ProvisionOutcome.Created

        hasher.verify(ClearPassphrase.wrap(SECOND), requireNotNull(provisioning.hashOf(LoginName("another")))) shouldBe
            true
        // 첫 계정의 자격은 그대로다 — 둘째 생성이 앞 행을 건드리지 않는다.
        hasher.verify(ClearPassphrase.wrap(FIRST), requireNotNull(provisioning.hashOf(LOGIN))) shouldBe true
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
        provisioning.isEmpty shouldBe true
    }

    /** 교체 전의 세션을 만들기 위한 로그인 — 저장된 해시를 그대로 쓰는 요청 경로 조립이다. */
    private fun signInWithStoredHash(): IssuedSession =
        workflow()
            .signIn(LOGIN, ClearPassphrase.wrap(FIRST))
            .shouldBeInstanceOf<SignInOutcome.Granted>()
            .session

    private fun workflow(): BrowserAccessWorkflow {
        val accounts =
            InMemoryAccountStore(
                knownLogin = LOGIN,
                snapshot =
                    AccountSnapshot(
                        AccountId(1),
                        requireNotNull(provisioning.hashOf(LOGIN)) { "계정이 없다" },
                        null,
                    ),
            )
        return BrowserAccessWorkflow(
            accounts = accounts,
            sessions = sessions,
            completion = InMemorySignInCompletion(accounts, sessions),
            signUps = InMemorySignUpStore(),
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
                    lockWait = Duration.ofMillis(500),
                ),
            signUpPolicy = shippedSignUpPolicy(),
        )
    }
}
