package bidvector.workflow.access

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

private val START: Instant = Instant.parse("2026-11-21T00:00:00Z")
private val LOGIN = LoginName("new-operator")
private const val PASSPHRASE = "a-long-enough-passphrase"

private val ACCESS_POLICY =
    BrowserAccessPolicyData(
        idleWindow = Duration.ofMinutes(30),
        absoluteWindow = Duration.ofHours(12),
        maxFailedAttempts = 5,
        lockWindow = Duration.ofMinutes(15),
        hashCost = 12,
        lockWait = Duration.ofMillis(500),
    )

/**
 * 열린 가입(M15/15B)의 **use case 층** 측정 — HTTP 없이 거동만 본다. 남용 상한은 여기 없다(키가
 * 원격 주소라 HTTP 층의 일이다 — `SignUpThrottleTest` 가 잰다).
 *
 * 이중체는 값과 fake port 뿐이다(mock framework 0, v2-지침서 §5).
 */
class AccountSignUpTest {
    private val hasher = FakePassphraseHasher()
    private val clock = MovableClock(START)
    private val keys = CountingKeySource()
    private val sessions = InMemorySessionStore()
    private val signUps = InMemorySignUpStore()
    private val accounts = SignUpBackedAccountStore(signUps)

    private fun workflow(completion: SignInCompletionPort = SignUpBackedCompletion(sessions)): BrowserAccessWorkflow =
        BrowserAccessWorkflow(
            accounts = accounts,
            sessions = sessions,
            completion = completion,
            signUps = signUps,
            hasher = hasher,
            keys = keys,
            clock = clock,
            policy = ACCESS_POLICY,
            signUpPolicy = policy(),
        )

    /** 출하 정책을 그대로 읽는다 — 공유 helper 한 자리(`shippedSignUpPolicy`). */
    private fun policy(): SignUpPolicyData = shippedSignUpPolicy()

    private fun signUp(
        login: LoginName = LOGIN,
        value: String = PASSPHRASE,
    ): SignUpOutcome = workflow().signUp(login, ClearPassphrase.wrap(value))

    @Test
    fun `가입은 계정을 만들고 곧바로 세션을 낸다 — 초대도 승인도 지나지 않는다`() {
        val granted = signUp().shouldBeInstanceOf<SignUpOutcome.Granted>()

        granted.session.absoluteExpiresAt shouldBe START.plus(ACCESS_POLICY.absoluteWindow)
        workflow()
            .authenticate(granted.session.key, CsrfDemand.Exempt)
            .shouldBeInstanceOf<AccessVerdict.Granted>()
            .account.value shouldBe requireNotNull(signUps.idOf(LOGIN)).value
    }

    @Test
    fun `저장되는 것은 해시뿐이다 — 원문을 되돌릴 수 없다`() {
        signUp().shouldBeInstanceOf<SignUpOutcome.Granted>()

        val stored = requireNotNull(signUps.hashOf(LOGIN)) { "해시가 저장되지 않았다" }
        stored.encoded shouldNotBe PASSPHRASE
        hasher.verify(ClearPassphrase.wrap(PASSPHRASE), stored) shouldBe true
    }

    @Test
    fun `가입한 자격으로 곧바로 로그인할 수 있다 — 같은 표를 로그인 경로가 읽는다`() {
        signUp().shouldBeInstanceOf<SignUpOutcome.Granted>()

        workflow().signIn(LOGIN, ClearPassphrase.wrap(PASSPHRASE)).shouldBeInstanceOf<SignInOutcome.Granted>()
    }

    @Test
    fun `같은 아이디는 거부한다 — 둘째 요청이 덮어쓰지 않는다`() {
        signUp().shouldBeInstanceOf<SignUpOutcome.Granted>()
        val first = requireNotNull(signUps.hashOf(LOGIN))

        signUp(value = "a-different-long-passphrase") shouldBe SignUpOutcome.RefusedDuplicate

        signUps.count shouldBe 1
        requireNotNull(signUps.hashOf(LOGIN)).encoded shouldBe first.encoded
    }

    @Test
    fun `아이디의 길이와 글자를 정책이 가른다 — 거부는 쓰지 않는다`() {
        val refused =
            listOf(
                LoginName("abc"),
                LoginName("a".repeat(policy().loginNameMaxLength + 1)),
                LoginName("Upper-Case"),
                LoginName("has space"),
                LoginName("사람이름"),
                LoginName("name@example"),
            )

        refused.map { signUp(login = it) } shouldBe refused.map { SignUpOutcome.RefusedLoginNameShape }
        signUps.count shouldBe 0
    }

    @Test
    fun `정책 경계의 아이디는 받는다 — 하한과 상한 양끝`() {
        signUp(login = LoginName("a".repeat(policy().loginNameMinLength)))
            .shouldBeInstanceOf<SignUpOutcome.Granted>()
        signUp(login = LoginName("b".repeat(policy().loginNameMaxLength)))
            .shouldBeInstanceOf<SignUpOutcome.Granted>()
        signUp(login = LoginName("dot.dash-under_1")).shouldBeInstanceOf<SignUpOutcome.Granted>()
    }

    @Test
    fun `비밀번호는 하한 미만과 바이트 상한 초과를 거부한다 — bcrypt 가 조용히 자르는 자리`() {
        val tooShort = "a".repeat(policy().passphraseMinLength - 1)
        // 한글 한 글자가 UTF-8 로 3바이트 — **글자 수가 아니라 바이트**로 재는 것을 이 값이 드러낸다.
        val tooManyBytes = "가".repeat(policy().passphraseMaxBytes / 3 + 1)

        signUp(value = tooShort) shouldBe SignUpOutcome.RefusedPassphraseShape
        signUp(value = tooManyBytes) shouldBe SignUpOutcome.RefusedPassphraseShape
        signUps.count shouldBe 0
    }

    @Test
    fun `바이트 상한 바로 아래는 받는다 — 경계가 거부 쪽으로 밀리지 않았다`() {
        signUp(value = "a".repeat(policy().passphraseMaxBytes)).shouldBeInstanceOf<SignUpOutcome.Granted>()
    }

    @Test
    fun `세션이 서지 않으면 그 사실을 이름으로 낸다 — 계정은 남는다`() {
        workflow(ContendedCompletion()).signUp(LOGIN, ClearPassphrase.wrap(PASSPHRASE)) shouldBe
            SignUpOutcome.SessionUnavailable

        signUps.count shouldBe 1
        sessions.openCount shouldBe 0
    }
}
