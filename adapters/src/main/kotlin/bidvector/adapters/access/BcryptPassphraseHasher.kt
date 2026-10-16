package bidvector.adapters.access

import at.favre.lib.crypto.bcrypt.BCrypt
import bidvector.workflow.access.ClearPassphrase
import bidvector.workflow.access.PassphraseHash
import bidvector.workflow.access.PassphraseHasher

/**
 * 느린 KDF 는 **검증된 라이브러리**가 진다(v2-지침서 §5 「바퀴 재발명 금지」) — salt 생성·
 * 비용 인코딩·상수 시간 비교를 손으로 짜는 것이 이 축에서 가장 틀리기 쉬운 자리다.
 *
 * **채택 근거(`at.favre.lib:bcrypt`)**
 *  · JDK 에는 비밀 문구용 KDF 로 PBKDF2(`javax.crypto.SecretKeyFactory`)밖에 없고, 그것을 쓰면
 *    salt 형식·반복 수·문면 인코딩을 **직접 정의**해야 한다 — 바로 그 자리를 피하려고 고르는
 *    라이브러리다. bcrypt 는 salt·cost 를 문면 하나에 담아 저장 열이 한 열로 끝난다.
 *  · `spring-security-crypto` 도 같은 일을 하지만 그 좌표는 `org.springframework.*` 이고,
 *    이 모듈(`adapters`)의 바깥 참조 허용 집합에는 Spring 패키지가 **하나도 없다**
 *    (`architecture-policy.properties` 의 `collection.external.allowed-packages.adapters`) —
 *    저장 어댑터 층에 프레임워크를 들이지 않는다는 그 경계를 이 slice 가 깨지 않는다.
 *  · 전이 의존은 `at.favre.lib:bytes` 하나이고 이 클래스가 그 패키지를 이름으로 쓰지 않는다.
 *
 * cost 는 **정책 데이터**에서 온다(매직 넘버 금지) — 조립이 `BROWSER_ACCESS_POLICY` 의 값을 꽂는다.
 */
class BcryptPassphraseHasher(
    private val cost: Int,
) : PassphraseHasher {
    override fun hash(clear: ClearPassphrase): PassphraseHash {
        val input = clear.hashInput()
        return try {
            PassphraseHash(BCrypt.withDefaults().hashToString(cost, input))
        } finally {
            input.fill('\u0000')
        }
    }

    /**
     * 검증은 라이브러리의 `verifyer` 하나다 — 해시 문면에서 salt·cost 를 읽어 같은 함수를 돌리고
     * 상수 시간으로 견준다. **문면을 우리가 파싱하지 않는다**(파싱하는 코드가 생기면 그것이
     * 「빠른 비교」로 되돌아갈 자리가 된다).
     */
    override fun verify(
        clear: ClearPassphrase,
        hash: PassphraseHash,
    ): Boolean {
        val input = clear.hashInput()
        return try {
            BCrypt.verifyer().verify(input, hash.encoded).verified
        } finally {
            input.fill('\u0000')
        }
    }
}
