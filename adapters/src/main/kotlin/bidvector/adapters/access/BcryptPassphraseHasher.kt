package bidvector.adapters.access

import at.favre.lib.crypto.bcrypt.BCrypt
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies
import at.favre.lib.crypto.bcrypt.LongPasswordStrategy
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
            PassphraseHash(HASHER.hashToString(cost, input))
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
            VERIFYER.verify(input, hash.encoded).verified
        } finally {
            input.fill('\u0000')
        }
    }

    private companion object {
        /**
         * bcrypt 판 — `withDefaults()` 와 같은 `2a` 다. 명시하는 이유는 아래 전략을 **해시와 검증
         * 양쪽에 같은 값으로** 꽂아야 하기 때문이다(한쪽만 바뀌면 그 입력 길이대의 왕복이 깨진다).
         */
        val VERSION: BCrypt.Version = BCrypt.Version.VERSION_2A

        /**
         * **긴 입력의 처리**(code-review r1 M-1). bcrypt 의 블록 암호 입력은 72바이트에서 끊기고,
         * 라이브러리의 기본 전략(`strict`)은 그 길이를 넘는 제시값에 `IllegalArgumentException` 을
         * **던진다** — 그 예외가 로그인 경로를 지나 **401 이 아니라 500** 으로 나갔다. 긴 문구를
         * 넣어 본 사용자에게 「서버가 깨졌다」를 보여 주고, 그 코드 차이가 곧 「이 입력은 다르게
         * 처리된다」는 신호가 된다.
         *
         * 고른 전략은 `hashSha512` 다 — 길이가 한계를 넘는 입력만 SHA-512(64바이트)로 접어 넣는다.
         * 셋 중 이것인 이유:
         *  · `strict` 는 위의 500 그 자체다.
         *  · `truncate` 는 73바이트째부터를 **조용히 버린다** — 앞 72바이트가 같은 두 문구가 같은
         *    값이 되므로, 긴 문구를 쓴 사용자의 유효 강도가 말없이 줄어든다.
         *  · `hashSha512` 는 전체 입력이 결과에 들어가고 거부도 절단도 없다. 긴 문구는 **틀리면
         *    401**(다른 틀린 값과 같은 경로·같은 실패 계수)이고 맞으면 통과한다.
         *
         * **한계 아래 입력에는 아무 일도 하지 않는다** — 라이브러리의 전략 기반 클래스가 길이
         * 검사를 먼저 하고 그 아래면 입력을 그대로 넘긴다. 그래서 기존 해시 문면의 검증 결과가
         * 바뀌지 않고, **시간도 길이와 무관하게 bcrypt 한 번**이다(SHA-512 는 측정 불가 수준).
         * 「긴 입력만 빨리 거부」하는 분기를 두지 않은 이유가 그것이다 — 그런 분기는 제시값의
         * 길이를 응답 시간에 비추는 새 축을 연다.
         */
        val LONG_INPUT: LongPasswordStrategy = LongPasswordStrategies.hashSha512(VERSION)

        /** 둘 다 불변이고 스레드 안전하다 — 요청마다 새로 만들지 않는다. */
        val HASHER: BCrypt.Hasher = BCrypt.with(VERSION, LONG_INPUT)
        val VERIFYER: BCrypt.Verifyer = BCrypt.verifyer(VERSION, LONG_INPUT)
    }
}
