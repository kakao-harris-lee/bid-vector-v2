package bidvector.workflow.access

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

private val LOGIN = LoginName("operator")
private const val RIGHT = "right-passphrase-value"
private const val WRONG = "wrong-passphrase-value"
private val START: Instant = Instant.parse("2026-10-16T00:00:00Z")

private val POLICY =
    BrowserAccessPolicyData(
        idleWindow = Duration.ofMinutes(30),
        absoluteWindow = Duration.ofHours(12),
        maxFailedAttempts = 5,
        lockWindow = Duration.ofMinutes(15),
        hashCost = 12,
    )

/**
 * 7D 우회 ③④⑤⑥ 의 **use case 층** 측정 — HTTP 없이 거동만 본다(쿠키·CSRF 헤더의 HTTP 형태는
 * `app` 의 test 가 잰다). 이중체는 값과 fake port 뿐이다(mock framework 0).
 */
class BrowserAccessWorkflowTest {
    private val hasher = FakePassphraseHasher()
    private val clock = MovableClock(START)
    private val keys = CountingKeySource()
    private val sessions = InMemorySessionStore()
    private val accounts =
        InMemoryAccountStore(
            knownLogin = LOGIN,
            snapshot = AccountSnapshot(AccountId(1), hasher.hash(ClearPassphrase.wrap(RIGHT)), 0, null),
        )

    private fun workflow(): BrowserAccessWorkflow =
        BrowserAccessWorkflow(accounts, sessions, hasher, keys, clock, POLICY)

    private fun signIn(value: String = RIGHT): SignInOutcome = workflow().signIn(LOGIN, ClearPassphrase.wrap(value))

    private fun granted(): IssuedSession = signIn().shouldBeInstanceOf<SignInOutcome.Granted>().session

    @Test
    fun `올바른 값이면 세션을 발급하고 절대 만료 시각을 싣는다`() {
        val issued = granted()

        issued.absoluteExpiresAt shouldBe START.plus(POLICY.absoluteWindow)
        workflow()
            .authenticate(issued.key, CsrfDemand.Exempt)
            .shouldBeInstanceOf<AccessVerdict.Granted>()
            .account.value shouldBe 1L
    }

    @Test
    fun `틀린 값은 거부하고 세션을 만들지 않는다`() {
        signIn(WRONG) shouldBe SignInOutcome.Refused
        sessions.openCount shouldBe 0
    }

    @Test
    fun `우회 ③ — 로그아웃한 세션 값은 다시 통하지 않는다`() {
        val issued = granted()
        workflow().authenticate(issued.key, CsrfDemand.Exempt).shouldBeInstanceOf<AccessVerdict.Granted>()

        workflow().signOut(issued.key)

        workflow().authenticate(issued.key, CsrfDemand.Exempt) shouldBe AccessVerdict.Denied
    }

    @Test
    fun `우회 ④ — 유휴 창을 넘기면 거부한다`() {
        val issued = granted()

        clock.advance(POLICY.idleWindow.plusSeconds(1))

        workflow().authenticate(issued.key, CsrfDemand.Exempt) shouldBe AccessVerdict.Denied
    }

    /**
     * **쉼 없이 쓴 세션도 절대 창에서 끝난다.** 마지막 한 걸음이 **유휴 창보다 짧다** — 그래서
     * 거부의 사유가 유휴가 아니라 절대 창이다. 앞 판은 마지막에 절대 창 길이를 한 번에 밀어
     * 유휴도 함께 만료시켰고, 그래서 절대 축의 변이가 초록이었다(실측 M4b).
     *
     * 절대 창을 지키는 **기제가 둘**이다: 유휴 시한을 절대 시한으로 **클램프**하는 것과 절대
     * 시한을 직접 보는 것. 하나만 변이하면 다른 하나가 받아내므로 둘을 **함께** 변이해야 RED 다
     * (M4b·M4c 초록 · M4d RED) — 그 셋이 이 자리의 심층 방어가 실재한다는 증거다.
     */
    @Test
    fun `우회 ④ — 쉼 없이 써도 절대 창을 넘기면 거부한다`() {
        val issued = granted()
        val step = POLICY.idleWindow.dividedBy(2)

        while (clock.now().plus(step).isBefore(issued.absoluteExpiresAt)) {
            clock.advance(step)
            workflow().authenticate(issued.key, CsrfDemand.Exempt).shouldBeInstanceOf<AccessVerdict.Granted>()
        }
        clock.advance(step)

        workflow().authenticate(issued.key, CsrfDemand.Exempt) shouldBe AccessVerdict.Denied
    }

    @Test
    fun `우회 ⑤ — 로그인은 늘 새 값을 발급하고 앞 세션을 폐기한다(세션 고정)`() {
        val first = granted()
        val second = granted()

        first.key.digest().hex shouldNotBe second.key.digest().hex
        keys.issued shouldBe 2
        workflow().authenticate(first.key, CsrfDemand.Exempt) shouldBe AccessVerdict.Denied
        workflow().authenticate(second.key, CsrfDemand.Exempt).shouldBeInstanceOf<AccessVerdict.Granted>()
    }

    @Test
    fun `우회 ⑥ — 연속 실패가 상한에 닿으면 올바른 값도 잠금 창 동안 거부한다`() {
        repeat(POLICY.maxFailedAttempts) { signIn(WRONG) shouldBe SignInOutcome.Refused }

        signIn() shouldBe SignInOutcome.Refused
        accounts.recordedFailures.size shouldBe POLICY.maxFailedAttempts

        clock.advance(POLICY.lockWindow.plusSeconds(1))
        signIn().shouldBeInstanceOf<SignInOutcome.Granted>()
    }

    @Test
    fun `우회 ⑥ — 상한 직전까지는 잠기지 않는다(상한이 실제로 그 수다)`() {
        repeat(POLICY.maxFailedAttempts - 1) { signIn(WRONG) shouldBe SignInOutcome.Refused }

        signIn().shouldBeInstanceOf<SignInOutcome.Granted>()
    }

    @Test
    fun `우회 ② — 상태 변경 요구에서 CSRF 값이 없거나 틀리면 거부한다`() {
        val issued = granted()

        workflow().authenticate(issued.key, CsrfDemand.Required(null)) shouldBe AccessVerdict.Denied
        workflow().authenticate(issued.key, CsrfDemand.Required(CsrfStamp("other-stamp"))) shouldBe AccessVerdict.Denied
        workflow()
            .authenticate(issued.key, CsrfDemand.Required(issued.stamp))
            .shouldBeInstanceOf<AccessVerdict.Granted>()
    }

    @Test
    fun `모르는 세션 값은 거부한다 — 저장소에 없는 해시`() {
        workflow().authenticate(BrowserSessionKey("not-issued-value"), CsrfDemand.Exempt) shouldBe AccessVerdict.Denied
    }

    @Test
    fun `모르는 로그인 식별자는 거부한다 — 계정을 지어내지 않는다`() {
        workflow().signIn(LoginName("someone-else"), ClearPassphrase.wrap(RIGHT)) shouldBe SignInOutcome.Refused
    }

    @Test
    fun `성공하면 실패 계수를 비운다`() {
        repeat(POLICY.maxFailedAttempts - 1) { signIn(WRONG) }
        granted()

        repeat(POLICY.maxFailedAttempts - 1) { signIn(WRONG) shouldBe SignInOutcome.Refused }
        signIn().shouldBeInstanceOf<SignInOutcome.Granted>()
    }
}
