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
     * 실패 하나를 적는다 — 계수를 **하나 올리고**, 그 올린 값이 [attemptLimit] 에 닿으면 같은
     * 갱신에서 [lockUntil] 까지 싣는다.
     *
     * **「올린다」이지 「이 값으로 둔다」가 아니다**(code-review r1 H-1). 읽은 값에 더해 절대값을
     * 쓰면 ⓐ 동시 실패 K 건이 전부 같은 값을 읽어 계수가 1 만 오르고 ⓑ 느린 검증 뒤에 늦게
     * 끝난 요청이 그 사이 걸린 잠금을 덮어 지운다. 구현은 두 효과를 **한 문장**에 담아 그
     * 창을 없앤다 — 상한 아래면 잠금 열은 자기 값을 유지해야 한다.
     *
     * **갱신 시각을 받지 않는다** — V20 의 앱 역할은 `account` 의 `updated_at` 에 UPDATE 권한이
     * 없다(그 열은 운영자 명령의 사건을 적는다). 서명에 없으면 그 열을 쓰는 코드가 생길 수 없다.
     */
    fun registerFailure(
        account: AccountId,
        attemptLimit: Int,
        lockUntil: Instant,
    )
}

/**
 * 로그인 성공의 **확정** — 계정과 세션 **두 표에 걸친 한 단위**라 어느 한쪽 저장 포트에도 두지
 * 않는다. 그 경계가 곧 결함의 자리였다(verifier r1 F1): 자격 확인과 세션 등록이 서로 다른
 * 트랜잭션이면 그 사이에 비밀 문구 교체가 끼어들어, **옛 값으로 시작한 로그인이 교체 뒤에
 * 세션을 받는다**(그리고 그 세션이 교체의 「전부 폐기」를 지나 유일하게 살아남는다).
 */
interface SignInCompletionPort {
    /**
     * 한 트랜잭션에서 ⓐ 계정 행을 **잠그며** 실패 계수·잠금을 비우고 ⓑ 그 계정의 기존 세션을
     * 폐기하고 ⓒ [session] 을 등록한다.
     *
     * ⓐ 는 **[verified] 가 아직 그 계정의 자격이고, [at] 기준으로 잠겨 있지 않을 때만** 성립한다.
     * 성립하지 않으면 **아무것도 쓰지 않고** `false` 다 — 호출부는 그 로그인을 거부한다.
     *
     * 자격 비교는 코드가 아니라 **행 조건**이다. 제시값이 섞이지 않고(양쪽 다 이미 저장된 자격)
     * 그래서 이것은 인증이 아니라 **판 확인**이다 — 상수 시간이 필요한 자리가 아니고,
     * [PassphraseHash] 에 비교를 더하지 않아도 되는 이유이기도 하다.
     *
     * ⓐ 가 거는 행 잠금이 **동시 로그인 둘과 비밀 문구 교체를 이 단위와 직렬화한다**
     * (code-review r2 M-1): 뒤에 선 쪽은 잠금이 풀린 뒤 **새 스냅숏**으로 ⓑ 를 돌아 앞사람이
     * 방금 연 세션을 보고 폐기한다. 한 계정에 살아 있는 세션은 늘 하나다.
     */
    fun complete(
        session: NewBrowserSession,
        verified: PassphraseHash,
        at: Instant,
    ): Boolean
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
 *
 * **여는 서명이 여기 없다**(verifier r1 F1 뒤). 세션을 만드는 쓰기는 자격 확인·기존 세션 폐기와
 * 한 트랜잭션이어야 하므로 [SignInCompletionPort] 하나가 그 셋을 진다 — 이 포트에 `open` 이
 * 남아 있으면 그 단위를 비껴가는 길이 표면에 다시 생긴다.
 */
interface BrowserSessionStorePort {
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

/**
 * 계정 한 행의 읽기 스냅숏 — 저장 층이 만든다(해시 문면은 [PassphraseHasher] 만 읽는다).
 *
 * **실패 계수를 싣지 않는다**(code-review r1 H-1). 계수를 스냅숏으로 내보내면 「읽어서 더한
 * 값을 되쓰는」 형태가 다시 설 자리가 생긴다 — 그 계산은 [AccountStorePort.registerFailure]
 * 의 한 갱신 안에만 있다. 여기 남는 [lockedUntil] 은 **검증 전 조기 거부**에만 쓰이고,
 * 판정의 정본은 [SignInCompletionPort.complete] 의 조건이다(심층 방어의 바깥 겹).
 */
class AccountSnapshot(
    val id: AccountId,
    val hash: PassphraseHash,
    val lockedUntil: Instant?,
)

/**
 * 새 세션 한 벌 — **생성자가 `internal` 이다.** 이 모듈 밖에서는 세션을 만들 수 없고, 이
 * 모듈 안에서 만드는 자리는 [BrowserAccessWorkflow.signIn] 하나다((2b) 「로그인 use case 밖에서
 * 세션을 만들 수 있으면 없던 권한」의 폐쇄 — `EditSession` 의 `internal constructor` 선례).
 * 만들어도 [SignInCompletionPort] 의 자격 조건을 지나야 행이 된다.
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
