package bidvector.workflow.access

import bidvector.sharedkernel.Resolution
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 가입 정책(M15/15B)의 **값과 불변식** — 정책이 해소되는가, 그 값이 계약이 적은 것인가,
 * 그리고 성립하지 않는 조합이 조립 시점에 서지 못하는가.
 *
 * **해소 축을 따로 재는 이유**: 정책이 해소되지 않으면 조립이 기동에서 멈추는데, 그 사실은
 * 기동 test 하나에만 걸린다 — 값의 자리에서 먼저 붉어야 어느 값이 문제인지 보인다.
 */
class SignUpPolicyDataTest {
    @Test
    fun `출하 정책이 계약의 값으로 해소된다 — 아이디 4~32자 · 비밀번호 10~1024자`() {
        val resolved =
            SIGN_UP_POLICY
                .resolve(LocalDate.of(2026, 11, 21))
                .shouldBeInstanceOf<Resolution.Resolved<SignUpPolicyData>>()
                .value

        resolved.loginNameMinLength shouldBe 4
        resolved.loginNameMaxLength shouldBe 32
        resolved.loginNameCharacter shouldBe "[a-z0-9._-]"
        resolved.passphraseMinLength shouldBe 10
        resolved.passphraseMaxLength shouldBe 1024
    }

    @Test
    fun `아이디 술어는 길이와 글자를 함께 본다 — 허용 집합 밖은 전부 거부`() {
        val policy = shippedSignUpPolicy()

        policy.accepts(LoginName("abcd")) shouldBe true
        policy.accepts(LoginName("a.b-c_1")) shouldBe true
        policy.accepts(LoginName("abc")) shouldBe false
        policy.accepts(LoginName("a".repeat(33))) shouldBe false
        policy.accepts(LoginName("ABCD")) shouldBe false
        policy.accepts(LoginName("a b c")) shouldBe false
        policy.accepts(LoginName("한글이름")) shouldBe false
        policy.accepts(LoginName("a+b@c")) shouldBe false
    }

    /**
     * **글자 수로 잰다**(code-review r1 M-1 뒤). 앞 판은 UTF-8 바이트로 재며 「bcrypt 가 72바이트
     * 위를 조용히 자른다」를 근거로 들었는데, 이 저장소의 해시 어댑터는 긴 입력을 SHA-512 로
     * **접는다** — 자르지도 거부하지도 않는다. 그래서 한글 비밀번호가 영문과 **같은 길이 규칙**
     * 아래 선다(그 비대칭이 사라졌다는 것이 이 test 의 축이다).
     */
    @Test
    fun `비밀번호 술어는 글자 수로 잰다 — 한글이 영문과 같은 규칙 아래 선다`() {
        val policy = shippedSignUpPolicy()

        policy.accepts(ClearPassphrase.wrap("a".repeat(10))) shouldBe true
        policy.accepts(ClearPassphrase.wrap("a".repeat(9))) shouldBe false
        policy.accepts(ClearPassphrase.wrap("a".repeat(1024))) shouldBe true
        policy.accepts(ClearPassphrase.wrap("a".repeat(1025))) shouldBe false
        // 한글 25자(옛 판에서 75바이트라 막히던 값)가 이제 통과한다.
        policy.accepts(ClearPassphrase.wrap("가".repeat(25))) shouldBe true
        policy.accepts(ClearPassphrase.wrap("가".repeat(1024))) shouldBe true
        policy.accepts(ClearPassphrase.wrap("가".repeat(1025))) shouldBe false
        // cr r1 N-8 — BMP 밖 문자는 **한 글자**다(UTF-16 코드 단위로는 둘이다).
        policy.accepts(ClearPassphrase.wrap("\uD83D\uDE00".repeat(9))) shouldBe false
        policy.accepts(ClearPassphrase.wrap("\uD83D\uDE00".repeat(10))) shouldBe true
    }

    @Test
    fun `성립하지 않는 조합은 값 객체가 거부한다 — 조립 시점에 선다`() {
        shouldThrow<IllegalArgumentException> { shippedSignUpPolicy().copy(loginNameMinLength = 33) }
        shouldThrow<IllegalArgumentException> { shippedSignUpPolicy().copy(passphraseMinLength = 1025) }
        shouldThrow<IllegalArgumentException> { shippedSignUpPolicy().copy(loginNameMinLength = 0) }
        shouldThrow<IllegalArgumentException> { shippedSignUpPolicy().copy(passphraseMaxLength = 0) }
    }
}
