package bidvector.app.access

import bidvector.adapters.access.AccountProvisioningTransaction
import bidvector.app.wiring.AccountCommandProperties
import bidvector.workflow.access.AccountId
import bidvector.workflow.access.AccountProvisioning
import bidvector.workflow.access.AccountProvisioningPort
import bidvector.workflow.access.BrowserSessionDigest
import bidvector.workflow.access.BrowserSessionSnapshot
import bidvector.workflow.access.BrowserSessionStorePort
import bidvector.workflow.access.ClearPassphrase
import bidvector.workflow.access.LoginName
import bidvector.workflow.access.PassphraseHash
import bidvector.workflow.access.PassphraseHasher
import bidvector.workflow.strategy.Clock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import java.time.Instant

private val LOGIN = LoginName("runner-test-operator")
private const val INITIAL = "runner-test-fixture-initial-value"
private const val NEXT = "runner-test-fixture-next-value"
private val NOW: Instant = Instant.parse("2026-10-19T00:00:00Z")

/** 같은 입력 ↔ 같은 문면만 보장하는 fake — 실 KDF 는 어댑터 test 가 잰다. */
private class StubHasher : PassphraseHasher {
    override fun hash(clear: ClearPassphrase): PassphraseHash =
        PassphraseHash("stub:${clear.hashInput().concatToString().hashCode()}")

    override fun verify(
        clear: ClearPassphrase,
        hash: PassphraseHash,
    ): Boolean = hash.encoded == hash(clear).encoded
}

private class StubProvisioningStore : AccountProvisioningPort {
    private val rows: MutableMap<String, Pair<AccountId, PassphraseHash>> = mutableMapOf()

    override fun anyExists(): Boolean = rows.isNotEmpty()

    override fun insert(
        login: LoginName,
        hash: PassphraseHash,
        at: Instant,
    ): AccountId = AccountId(rows.size + 1L).also { rows[login.value] = it to hash }

    override fun replaceHash(
        login: LoginName,
        hash: PassphraseHash,
        at: Instant,
    ): AccountId? = rows[login.value]?.first?.also { rows[login.value] = it to hash }

    fun storedHash(login: LoginName): String? = rows[login.value]?.second?.encoded
}

/** 세션 폐기만 받는 자리 — 러너 축에서는 호출 여부가 아니라 「터지지 않는다」만 필요하다. */
private class StubSessionStore : BrowserSessionStorePort {
    override fun byDigest(digest: BrowserSessionDigest): BrowserSessionSnapshot? = null

    override fun touch(
        digest: BrowserSessionDigest,
        usedAt: Instant,
        idleExpiresAt: Instant,
    ) = Unit

    override fun revoke(
        digest: BrowserSessionDigest,
        at: Instant,
    ) = Unit

    override fun revokeAllOf(
        account: AccountId,
        at: Instant,
    ) = Unit
}

/**
 * M7/7D — **계정 명령 러너**(verifier r1 F3). 앞 판은 이 class 가 없었고, 그러면서 KDoc 은
 * 「`AccountCommandRunnerTest` 가 실측한다」고 적고 있었다 — 로그 줄·종료 코드·빈 값 거부 가운데
 * 무엇도 재지 않았다.
 *
 * 러너는 저장 경로를 모른다(그 축은 `adapters` 의 실 DB test) — 여기서 재는 것은 **명령으로서의
 * 거동**이다: 결과가 로그 한 줄로 나가는가 · 거부가 비-0 종료인가 · 그 줄에 값이 섞이지 않는가.
 */
class AccountCommandRunnerTest {
    private val store = StubProvisioningStore()
    private val hasher = StubHasher()
    private val lines = mutableListOf<String>()
    private val exitCodes = mutableListOf<Int>()

    private val transaction =
        object : AccountProvisioningTransaction {
            override fun <T> inTransaction(action: (AccountProvisioning) -> T): T =
                action(
                    AccountProvisioning(
                        accounts = store,
                        sessions = StubSessionStore(),
                        hasher = hasher,
                        clock = Clock { NOW },
                    ),
                )
        }

    private fun run(
        mode: AccountCommandMode,
        value: String = INITIAL,
    ) = AccountCommandRunner(
        transaction = transaction,
        mode = mode,
        login = LOGIN,
        initial = ClearPassphrase.wrap(value),
        log = AccountCommandLog { lines += it },
        termination = AccountCommandTermination { exitCodes += it },
    ).run(DefaultApplicationArguments())

    @Test
    fun `생성은 계정을 만들고 결과 한 줄과 종료 코드 0 을 낸다`() {
        run(AccountCommandMode.CREATE)

        lines.single() shouldBe "account command=create result=created login=${LOGIN.value}"
        exitCodes.single() shouldBe 0
        store.storedHash(LOGIN) shouldBe hasher.hash(ClearPassphrase.wrap(INITIAL)).encoded
    }

    @Test
    fun `이미 계정이 있으면 거부하고 종료 코드가 0 이 아니다 — 덮어쓰지 않는다`() {
        run(AccountCommandMode.CREATE)
        val stored = store.storedHash(LOGIN)
        run(AccountCommandMode.CREATE, NEXT)

        lines.last() shouldBe "account command=create result=refused cause=account-exists"
        exitCodes.last() shouldBe 1
        store.storedHash(LOGIN) shouldBe stored
    }

    @Test
    fun `교체는 해시를 바꾸고, 대상이 없으면 거부하며 종료 코드가 0 이 아니다`() {
        run(AccountCommandMode.ROTATE)

        lines.single() shouldBe "account command=rotate result=refused cause=account-absent"
        exitCodes.single() shouldBe 1

        run(AccountCommandMode.CREATE)
        run(AccountCommandMode.ROTATE, NEXT)

        lines.last() shouldBe "account command=rotate result=rotated login=${LOGIN.value}"
        exitCodes.last() shouldBe 0
        store.storedHash(LOGIN) shouldBe hasher.hash(ClearPassphrase.wrap(NEXT)).encoded
    }

    @Test
    fun `로그 줄에 비밀 문구 원문도 해시 문면도 없다`() {
        run(AccountCommandMode.CREATE)
        run(AccountCommandMode.ROTATE, NEXT)

        val written = lines.joinToString("\n")
        written shouldNotContain INITIAL
        written shouldNotContain NEXT
        written shouldNotContain "stub:"
        written shouldContain LOGIN.value
    }

    /**
     * verifier r1 F3 의 probe 를 그대로 잠근다 — 앞 판에서는 **빈 값이 그대로 바인딩돼** 빈 비밀
     * 문구로 계정이 만들어지고 `result=created`·exit 0 이 나왔다. 이제 설정 객체가 서지 않으므로
     * 그 실행은 기동에서 떨어진다(비-0 종료).
     */
    @Test
    fun `빈 초기값·빈 로그인 식별자는 설정 객체가 서지 않는다 — 기동 실패`() {
        shouldThrow<IllegalArgumentException> {
            AccountCommandProperties(AccountCommandMode.CREATE, LOGIN.value, "")
        }
        shouldThrow<IllegalArgumentException> {
            AccountCommandProperties(AccountCommandMode.CREATE, LOGIN.value, "   ")
        }
        shouldThrow<IllegalArgumentException> {
            AccountCommandProperties(AccountCommandMode.CREATE, "", INITIAL)
        }
    }

    @Test
    fun `설정 객체의 문자열화에 초기값이 실리지 않는다 — data class 가 아니다`() {
        val properties = AccountCommandProperties(AccountCommandMode.CREATE, LOGIN.value, INITIAL)

        properties.toString() shouldNotContain INITIAL
    }
}
