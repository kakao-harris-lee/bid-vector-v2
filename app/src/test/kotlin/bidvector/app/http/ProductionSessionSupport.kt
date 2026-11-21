package bidvector.app.http

import bidvector.adapters.access.BcryptPassphraseHasher
import bidvector.workflow.access.ClearPassphrase
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import javax.sql.DataSource

/**
 * **출하 조립 E2E 가 세션으로 닿는 한 자리**(M15/15A).
 *
 * 왜 생겼나: V28 이 전략·프로필·편집 세션을 계정 소유로 옮기면서 전략 · 제안 · 투찰가 ·
 * dry-run 이 계정 전용 뿌리가 됐다. 계정을 내는 인증 경로는 세션 하나뿐이므로(D-7B-2) 그
 * 경로들을 자격 헤더로 두드리던 E2E 는 이제 **로그인해야** 한다.
 *
 * 한 자리에 모은 이유는 중복이다 — 같은 열몇 줄(계정 심기 · 로그인 · 쿠키/CSRF 헤더)을 E2E
 * 여섯 곳이 각자 들면 `cpdCheck` 가 맞게 붉어지고, 그 사본들은 따로 낡는다.
 *
 * **계정은 소유자 자격으로 심는다** — 앱 역할에는 `account` INSERT 권한이 없다(V20 의 결정).
 */
internal class ProductionSessionSupport(
    private val restTemplate: TestRestTemplate,
    private val url: (String) -> String,
) {
    private var cookie: String = ""
    private var stamp: String = ""

    /** 심은 계정의 id — 격리 축 test 가 「누구의 행인가」를 직접 읽을 때 쓴다. */
    var accountId: Long = 0
        private set

    /**
     * 계정을 심고 곧바로 로그인한다. 실패하면 그 자리에서 터진다 — 뒤의 단언이 「로그인 실패」를
     * 다른 실패로 읽지 않게 한다(`SessionTestClient` 와 같은 규율).
     */
    fun seedAndSignIn(
        admin: DataSource,
        login: String,
        passphrase: String = E2E_SESSION_PASSPHRASE,
    ): ProductionSessionSupport {
        accountId = insertAccount(admin, login, passphrase)
        signIn(login, passphrase)
        return this
    }

    /** 읽기용 헤더 — 쿠키만. */
    fun readHeaders(): HttpHeaders =
        HttpHeaders().apply { set(HttpHeaders.COOKIE, "${SessionCookieWriter.HOST_PREFIXED_NAME}=$cookie") }

    /** 쓰기용 헤더 — 쿠키 + CSRF + JSON. 기본 거부 필터가 상태 변경 메서드에서 CSRF 를 요구한다. */
    fun writeHeaders(): HttpHeaders =
        readHeaders().apply {
            set(BrowserSessionAuthenticator.CSRF_HEADER, stamp)
            contentType = MediaType.APPLICATION_JSON
        }

    private fun insertAccount(
        admin: DataSource,
        login: String,
        passphrase: String,
    ): Long {
        val hash = BcryptPassphraseHasher(E2E_HASH_COST).hash(ClearPassphrase.wrap(passphrase))
        return admin.connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, failed_attempts) " +
                        "VALUES (?, ?, now(), now(), 0) RETURNING id",
                ).use { statement ->
                    statement.setString(1, login)
                    statement.setString(2, hash.encoded)
                    statement.executeQuery().use { rows ->
                        rows.next()
                        rows.getLong("id")
                    }
                }
        }
    }

    private fun signIn(
        login: String,
        passphrase: String,
    ) {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response =
            restTemplate.exchange(
                url("/api/session"),
                HttpMethod.POST,
                HttpEntity("""{"loginName":"$login","passphrase":"$passphrase"}""", headers),
                String::class.java,
            )
        check(response.statusCode.value() == E2E_CREATED) { "로그인 실패: ${response.statusCode}" }
        val setCookie = requireNotNull(response.headers.getFirst(HttpHeaders.SET_COOKIE)) { "Set-Cookie 가 없다" }
        cookie = setCookie.substringAfter("${SessionCookieWriter.HOST_PREFIXED_NAME}=").substringBefore(";")
        stamp = requireNotNull(E2E_CSRF.find(response.body.orEmpty())?.groupValues?.get(1)) { "csrf 가 없다" }
    }
}

/** 심는 계정의 비밀 문구 — 값 자체는 축이 아니고, 한 자리에만 둔다. */
internal const val E2E_SESSION_PASSPHRASE = "production-e2e-fixture-passphrase"

/** bcrypt 비용 — test 라 낮춘다(출하 값은 조립이 정한다). */
private const val E2E_HASH_COST = 10

private const val E2E_CREATED = 201

private val E2E_CSRF = Regex("\"csrf\"\\s*:\\s*\"([^\"]+)\"")
