package bidvector.workflow.access

import bidvector.workflow.collection.sha256Hex
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 브라우저 접근(M7/7D)의 값 타입 — **원문이 타입 밖으로 나가는 자리를 세어 둔다.**
 *
 * 이름에 스캔 어휘를 쓰지 않는다(D-6A1-9·19 의 일반화) — 비밀 문구는 `Passphrase`,
 * 세션 쿠키 값은 `BrowserSessionKey`, CSRF 값은 `CsrfStamp` 다.
 *
 * **왜 `workflow` 인가.** 값·포트는 HTTP 층과 저장 어댑터가 **둘 다** 이름 불러야 한다.
 * `app.http` 는 `bidvector.adapters` 를 참조할 수 없고(의존 게이트), `adapters` 는 `app` 을
 * 볼 수 없다 — 두 층이 공유할 수 있는 자리는 `workflow` 뿐이다(`EditSessionRepository` 선례).
 *
 * **(2b) 값 획득 축 — 이 파일이 밖에 허락하는 것.**
 *  · [ClearPassphrase] — 접근자 하나([hashInput])뿐이고 그 하나의 소비자는 해시 어댑터다.
 *    보유자 집합은 `architecture-policy.properties` 의 `app.secret.passphrase-*` 가 등식으로 잠근다.
 *  · [BrowserSessionKey] — [cookieValue]·[digest]·[deriveStamp] 셋. 쿠키로 나가는 자리가
 *    [cookieValue] 하나이고, **저장 포트의 서명이 [BrowserSessionDigest]** 라 어댑터는 원문을
 *    받을 수 없다(「세션 식별자는 해시로만 저장」을 문장이 아니라 **타입**이 진다).
 *  · [BrowserSessionDigest]·[CsrfDigest] — 해시 hex 를 담는 저장형이고 CSRF 비교는 상수 시간
 *    경로([CsrfDigest.matches]) 하나다.
 */
class LoginName(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "로그인 식별자는 빈 값일 수 없다" }
    }
}

/** 계정 한 행의 식별자 — 저장 층이 발급한다(애플리케이션이 지어내지 않는다). */
class AccountId(
    val value: Long,
)

/**
 * 제시된 비밀 문구(로그인 요청 본문·부트스트랩 환경변수) — `toString()` 을 가려 로그·예외
 * 문면에 원문이 실리지 않게 한다([bidvector.adapters.koneps.ServiceKey] 와 같은 관례).
 *
 * `equals`/`hashCode` 를 재정의하지 않는다(`OperatorCredential` 과 같은 근거) — 내용 기반
 * 비교를 제공하면 그것이 곧 「빠른 비교」가 되어 해시 검증을 비껴가는 길이 된다.
 */
class ClearPassphrase private constructor(
    private val chars: CharArray,
) {
    /**
     * **유일한 접근자**이고 (2b) 값 획득 축의 등재 항목이다 — 소비자는 해시 어댑터 하나이며
     * 보유자 집합을 아키텍처 게이트가 등식으로 잠근다. 사본을 돌려준다(호출자가 지운 뒤에도
     * 이 인스턴스가 쓰일 수 있어야 한다).
     */
    fun hashInput(): CharArray = chars.copyOf()

    override fun toString(): String = "ClearPassphrase(***)"

    companion object {
        /** 신뢰하지 않는 제시값 — 검증하지 않는다(빈 값도 감싼다, `OperatorCredential.wrap` 과 같은 이유). */
        fun wrap(value: String): ClearPassphrase = ClearPassphrase(value.toCharArray())
    }
}

/**
 * 저장된 비밀 문구 해시(bcrypt 문면 — salt·cost 가 그 안에 있다). 이 타입은 **비교를 제공하지
 * 않는다** — 검증은 [PassphraseHasher] 가 하고 그 구현만 해시 문면을 읽는다.
 */
class PassphraseHash(
    val encoded: String,
) {
    init {
        require(encoded.isNotBlank()) { "비밀 문구 해시는 빈 값일 수 없다" }
    }

    override fun toString(): String = "PassphraseHash(***)"
}

/**
 * 브라우저 쿠키에 실리는 세션 값 — **평문은 이 타입 안에만 있다.**
 *
 * [digest] 는 저장·조회 키이고 [deriveStamp] 는 CSRF 값이다. 둘 다 단방향이라 밖으로 나간
 * 값에서 원문을 되돌릴 수 없다 — 쿠키를 읽지 못하는 자리(DB 행·audit·로그)에서는 세션을
 * 재현할 수 없다.
 */
class BrowserSessionKey(
    private val value: String,
) {
    init {
        require(value.isNotBlank()) { "세션 값은 빈 값일 수 없다" }
    }

    /** 쿠키로 내보내는 유일한 자리 — HTTP 층이 `Set-Cookie` 에 싣는다. */
    fun cookieValue(): String = value

    fun digest(): BrowserSessionDigest = BrowserSessionDigest(sha256Hex(value))

    /**
     * CSRF 값은 세션 값에서 **유도**한다(계약 산출물 3 「해시 또는 세션 비밀로부터 유도」) —
     * 별도 난수를 저장하지 않아도 쿠키를 쥔 요청에서 언제나 다시 만들 수 있다(새로고침 뒤
     * `GET /api/session` 이 그 값을 다시 준다). 단방향이라 CSRF 값이 새어도 세션이 되지 않는다.
     */
    fun deriveStamp(): CsrfStamp = CsrfStamp(sha256Hex("$value$CSRF_LABEL"))

    private companion object {
        /** 도메인 분리 라벨 — 같은 입력이 두 용도로 같은 값을 내지 않게 한다. */
        const val CSRF_LABEL = ":csrf"
    }
}

/** 세션 조회·저장 키 — 저장 포트가 받는 유일한 세션 식별 타입이다. */
class BrowserSessionDigest(
    val hex: String,
) {
    init {
        require(hex.isNotBlank()) { "세션 해시는 빈 값일 수 없다" }
    }
}

/** 요청 헤더로 제시되는 CSRF 값 — 저장형은 [CsrfDigest] 다(평문을 저장하지 않는다). */
class CsrfStamp(
    private val value: String,
) {
    init {
        require(value.isNotBlank()) { "CSRF 값은 빈 값일 수 없다" }
    }

    /** 응답으로 내보내는 유일한 자리 — 같은 출처의 화면만 읽을 수 있다. */
    fun headerValue(): String = value

    fun digest(): CsrfDigest = CsrfDigest(sha256Hex(value))

    companion object {
        /** 신뢰하지 않는 제시값(헤더) — 빈 값이면 감싸지 않는다(검증 자체가 거짓이 된다). */
        fun wrapOrNull(value: String?): CsrfStamp? = value?.takeIf { it.isNotBlank() }?.let { CsrfStamp(it) }
    }
}

/** 저장된 CSRF 해시 — **비교의 유일한 경로**가 상수 시간이다([OperatorCredential] 과 같은 규율). */
class CsrfDigest(
    val hex: String,
) {
    init {
        require(hex.isNotBlank()) { "CSRF 해시는 빈 값일 수 없다" }
    }

    /** JDK 가 상수 시간을 보장하는 [MessageDigest.isEqual] 만 쓴다. */
    fun matches(other: CsrfDigest): Boolean =
        MessageDigest.isEqual(hex.toByteArray(StandardCharsets.UTF_8), other.hex.toByteArray(StandardCharsets.UTF_8))
}
