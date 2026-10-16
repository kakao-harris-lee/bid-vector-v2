package bidvector.workflow.access

import bidvector.workflow.strategy.Clock
import java.time.Instant

/** 상태를 바꾸는 요청인가 — 판정은 호출부(HTTP 층)가 메서드로 하고, 효과는 이 use case 가 진다. */
sealed interface CsrfDemand {
    /** 읽기 메서드 — CSRF 를 요구하지 않는다. */
    data object Exempt : CsrfDemand

    /** 상태를 바꾸는 메서드 — 헤더로 제시된 값이 **이 세션의 것**이어야 한다(없으면 거부). */
    class Required(
        val presented: CsrfStamp?,
    ) : CsrfDemand
}

/** 로그인 결과 — 거부의 **사유를 나누지 않는다**(D-6A1-6 의 규율: 401 하나). */
sealed interface SignInOutcome {
    class Granted(
        val session: IssuedSession,
    ) : SignInOutcome

    data object Refused : SignInOutcome
}

/** 요청 인증 결과. */
sealed interface AccessVerdict {
    class Granted(
        val account: AccountId,
        val stamp: CsrfStamp,
    ) : AccessVerdict

    data object Denied : AccessVerdict
}

/** 발급된 세션 — 쿠키에 실릴 값과 화면에 줄 CSRF 값, 그리고 절대 만료 시각. */
class IssuedSession(
    val key: BrowserSessionKey,
    val stamp: CsrfStamp,
    val absoluteExpiresAt: Instant,
)

/**
 * 살아 있는 세션의 보고 — 「지금 내 세션」 조회가 돌려주는 것. 쿠키를 **이미 쥔** 호출자만
 * 얻을 수 있고(인자가 세션 값이다) 새 권한을 만들지 않는다.
 */
class SessionDescription(
    val stamp: CsrfStamp,
    val absoluteExpiresAt: Instant,
)

/**
 * 브라우저 접근 use case(M7/7D) — 로그인·요청 인증·로그아웃. **세션을 만드는 유일한 자리**다
 * ([NewBrowserSession] 의 생성자가 `internal` 이라 이 모듈 밖에서는 만들 수 없고, 이 모듈
 * 안에서 만드는 함수는 [signIn] 하나다 — (2b) 값 획득 축의 핵).
 *
 * 거부는 전부 같은 모양이다 — 계정 부재·잠금·해시 불일치·세션 부재·폐기·만료·CSRF 불일치가
 * 호출부에 구분되어 나가지 않는다(사유를 나누면 무엇이 존재하는지가 새어 나간다).
 */
class BrowserAccessWorkflow(
    private val accounts: AccountStorePort,
    private val sessions: BrowserSessionStorePort,
    private val hasher: PassphraseHasher,
    private val keys: BrowserSessionKeySource,
    private val clock: Clock,
    private val policy: BrowserAccessPolicyData,
) {
    /**
     * 로그인 — 잠금 확인 → 해시 검증 → **재발급**. 실패는 계수를 올리고 상한에서 잠근다.
     *
     * 잠금 확인이 해시 검증보다 **앞**이다: 잠긴 동안에는 올바른 값에도 거부해야 하고,
     * 그 사이의 시도는 계수를 더 올리지 않는다(잠금 창이 겹겹이 길어지지 않는다).
     */
    fun signIn(
        login: LoginName,
        clear: ClearPassphrase,
    ): SignInOutcome {
        val now = clock.now()
        val account = accounts.byLogin(login) ?: return SignInOutcome.Refused
        if (isLocked(account, now)) return SignInOutcome.Refused
        if (!hasher.verify(clear, account.hash)) {
            registerFailure(account, now)
            return SignInOutcome.Refused
        }
        accounts.clearFailures(account.id)
        return SignInOutcome.Granted(issue(account.id, now))
    }

    /**
     * 요청 인증 — 세션이 살아 있고 CSRF 요구가 충족되면 통과시키고 **유휴 시한을 민다**.
     *
     * 유휴 시한은 절대 시한을 넘지 못한다([idleDeadline]) — 밀기만으로 세션이 영구해지는
     * 길이 없다.
     */
    fun authenticate(
        key: BrowserSessionKey,
        demand: CsrfDemand,
    ): AccessVerdict {
        val now = clock.now()
        val session = sessions.byDigest(key.digest()) ?: return AccessVerdict.Denied
        if (!isLive(session, now)) return AccessVerdict.Denied
        if (!csrfSatisfied(session, demand)) return AccessVerdict.Denied
        sessions.touch(session.digest, now, idleDeadline(now, session.absoluteExpiresAt))
        return AccessVerdict.Granted(session.account, key.deriveStamp())
    }

    /**
     * 「지금 내 세션」 — **밀지 않는다**(읽기다). 살아 있지 않으면 null 이고, CSRF 값은 쥔
     * 세션 값에서 다시 유도한다(새로고침 뒤에도 화면이 그 값을 받을 수 있는 이유).
     */
    fun describe(key: BrowserSessionKey): SessionDescription? {
        val now = clock.now()
        val session = sessions.byDigest(key.digest()) ?: return null
        if (!isLive(session, now)) return null
        return SessionDescription(key.deriveStamp(), session.absoluteExpiresAt)
    }

    /** 로그아웃 — **서버 쪽 폐기**다(쿠키를 지우는 것으로 끝내지 않는다). */
    fun signOut(key: BrowserSessionKey) {
        sessions.revoke(key.digest(), clock.now())
    }

    /**
     * 로그인은 늘 **새 값**을 발급하고 그 계정의 기존 세션을 전부 폐기한다 — 제시된 쿠키를
     * 승격시키는 경로가 아예 없으므로 세션 고정(우회 ⑤)이 구조로 성립하지 않는다.
     */
    private fun issue(
        account: AccountId,
        now: Instant,
    ): IssuedSession {
        sessions.revokeAllOf(account, now)
        val key = keys.newKey()
        val stamp = key.deriveStamp()
        val absoluteExpiresAt = now.plus(policy.absoluteWindow)
        sessions.open(
            NewBrowserSession(
                digest = key.digest(),
                account = account,
                csrfDigest = stamp.digest(),
                startedAt = now,
                idleExpiresAt = idleDeadline(now, absoluteExpiresAt),
                absoluteExpiresAt = absoluteExpiresAt,
            ),
        )
        return IssuedSession(key, stamp, absoluteExpiresAt)
    }

    private fun registerFailure(
        account: AccountSnapshot,
        now: Instant,
    ) {
        val attempts = account.failedAttempts + 1
        if (attempts >= policy.maxFailedAttempts) {
            // 잠그면서 계수를 비운다 — 그러지 않으면 잠금이 풀린 직후의 한 번이 다시 잠근다.
            accounts.recordFailure(account.id, 0, now.plus(policy.lockWindow))
        } else {
            accounts.recordFailure(account.id, attempts, null)
        }
    }

    private fun isLocked(
        account: AccountSnapshot,
        now: Instant,
    ): Boolean = account.lockedUntil?.isAfter(now) ?: false

    private fun isLive(
        session: BrowserSessionSnapshot,
        now: Instant,
    ): Boolean =
        session.revokedAt == null &&
            session.idleExpiresAt.isAfter(now) &&
            session.absoluteExpiresAt.isAfter(now)

    private fun csrfSatisfied(
        session: BrowserSessionSnapshot,
        demand: CsrfDemand,
    ): Boolean =
        when (demand) {
            CsrfDemand.Exempt -> true
            is CsrfDemand.Required -> {
                val presented = demand.presented
                presented != null && session.csrfDigest.matches(presented.digest())
            }
        }

    /**
     * `minOf` 를 쓰지 않는다 — 그 함수는 `kotlin.comparisons` 를 끌어오고, 이 모듈의 바깥 참조
     * 허용 집합은 **관측과 양방향 등식**이라 그 한 줄이 집합을 늘린다. 같은 판정을 `java.time`
     * 의 비교로 한다(이미 쓰는 패키지다).
     */
    private fun idleDeadline(
        now: Instant,
        absoluteExpiresAt: Instant,
    ): Instant {
        val candidate = now.plus(policy.idleWindow)
        return if (candidate.isBefore(absoluteExpiresAt)) candidate else absoluteExpiresAt
    }
}
