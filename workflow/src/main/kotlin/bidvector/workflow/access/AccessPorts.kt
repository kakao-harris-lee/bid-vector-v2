package bidvector.workflow.access

import java.time.Instant

/**
 * 브라우저 접근의 포트 — **요청 경로와 운영자 명령 경로를 두 포트로 가른다**(M7/7D 설계 검토 (1)).
 *
 * 왜 둘인가: 요청 경로가 계정을 **만들거나 해시를 바꿀 수 있으면** 그것은 인증 결함 하나가
 * 계정 탈취로 번지는 경로다. 포트를 가르면 그 능력이 타입으로 없다 — 그리고 같은 경계를 DB
 * 권한이 한 겹 더 진다(V20: 앱 역할은 `account` 에 INSERT 가 없고 UPDATE 는 실패 계수·잠금
 * 두 열에만 있다).
 */
interface AccountStorePort {
    /** 로그인 식별자로 한 계정을 읽는다 — 없으면 null(지어내지 않는다). */
    fun byLogin(login: LoginName): AccountSnapshot?

    /**
     * 실패 계수를 올리고, 상한에 닿았으면 잠금 해제 시각을 싣는다.
     *
     * **갱신 시각을 받지 않는다** — V20 의 앱 역할은 `account` 의 `updated_at` 에 UPDATE 권한이
     * 없다(그 열은 운영자 명령의 사건을 적는다). 서명에 없으면 그 열을 쓰는 코드가 생길 수 없다.
     */
    fun recordFailure(
        account: AccountId,
        attempts: Int,
        lockedUntil: Instant?,
    )

    /** 성공했을 때 실패 계수·잠금을 비운다(같은 이유로 갱신 시각을 받지 않는다). */
    fun clearFailures(account: AccountId)
}

/**
 * 운영자 명령 경로 전용 — 계정 생성과 해시 교체. 요청 경로의 조립에는 이 포트의 구현이
 * **들어가지 않는다**(배선이 소유자 연결 위에서만 만든다, `JdbcRetentionStore` 선례).
 */
interface AccountProvisioningPort {
    /** 계정이 하나라도 있는가 — 「이미 있으면 거부」의 판정 입력이다. */
    fun anyExists(): Boolean

    fun insert(
        login: LoginName,
        hash: PassphraseHash,
        at: Instant,
    ): AccountId

    /** 해시를 바꾼다 — 그 계정이 없으면 null(만들지 않는다). */
    fun replaceHash(
        login: LoginName,
        hash: PassphraseHash,
        at: Instant,
    ): AccountId?
}

/**
 * 세션 저장 — **받는 세션 식별 타입이 [BrowserSessionDigest] 하나다.** 쿠키 원문을 받는
 * 서명이 없으므로 「세션 식별자는 해시로만 저장」이 문장이 아니라 타입으로 선다.
 */
interface BrowserSessionStorePort {
    fun open(session: NewBrowserSession)

    fun byDigest(digest: BrowserSessionDigest): BrowserSessionSnapshot?

    fun touch(
        digest: BrowserSessionDigest,
        usedAt: Instant,
        idleExpiresAt: Instant,
    )

    fun revoke(
        digest: BrowserSessionDigest,
        at: Instant,
    )

    /** 한 계정의 **모든** 세션을 폐기한다 — 로그인 재발급과 해시 교체가 함께 쓴다. */
    fun revokeAllOf(
        account: AccountId,
        at: Instant,
    )
}

/** 비밀 문구 해시·검증 — 느린 KDF 는 어댑터의 일이다(workflow 는 알고리즘을 모른다). */
interface PassphraseHasher {
    fun hash(clear: ClearPassphrase): PassphraseHash

    fun verify(
        clear: ClearPassphrase,
        hash: PassphraseHash,
    ): Boolean
}

/** 난수 주입 자리 — 구현이 `SecureRandom` 을 쥐고, 그 주입은 어댑터의 `internal` 주 생성자다. */
interface BrowserSessionKeySource {
    fun newKey(): BrowserSessionKey
}

/** 계정 한 행의 읽기 스냅숏 — 저장 층이 만든다(해시 문면은 [PassphraseHasher] 만 읽는다). */
class AccountSnapshot(
    val id: AccountId,
    val hash: PassphraseHash,
    val failedAttempts: Int,
    val lockedUntil: Instant?,
) {
    init {
        require(failedAttempts >= 0) { "실패 계수는 음수일 수 없다: $failedAttempts" }
    }
}

/**
 * 새 세션 한 벌 — **생성자가 `internal` 이다.** 이 모듈 밖에서는 세션을 만들 수 없고, 이
 * 모듈 안에서 만드는 자리는 [BrowserAccessWorkflow.signIn] 하나다((2b) 「로그인 use case 밖에서
 * 세션을 만들 수 있으면 없던 권한」의 폐쇄 — `EditSession` 의 `internal constructor` 선례).
 */
class NewBrowserSession internal constructor(
    val digest: BrowserSessionDigest,
    val account: AccountId,
    val csrfDigest: CsrfDigest,
    val startedAt: Instant,
    val idleExpiresAt: Instant,
    val absoluteExpiresAt: Instant,
)

/** 세션 한 행의 읽기 스냅숏 — 저장 층이 만든다. */
class BrowserSessionSnapshot(
    val digest: BrowserSessionDigest,
    val account: AccountId,
    val csrfDigest: CsrfDigest,
    val lastUsedAt: Instant,
    val idleExpiresAt: Instant,
    val absoluteExpiresAt: Instant,
    val revokedAt: Instant?,
)
