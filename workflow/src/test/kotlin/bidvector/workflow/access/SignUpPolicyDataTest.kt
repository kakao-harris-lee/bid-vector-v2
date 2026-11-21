package bidvector.workflow.access

import bidvector.sharedkernel.Resolution
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration
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
    fun `출하 정책이 계약의 값으로 해소된다 — 4~32자 · 10자 이상 · 72바이트 · 시간당 10건`() {
        val resolved =
            SIGN_UP_POLICY
                .resolve(LocalDate.of(2026, 11, 21))
                .shouldBeInstanceOf<Resolution.Resolved<SignUpPolicyData>>()
                .value

        resolved.loginNameMinLength shouldBe 4
        resolved.loginNameMaxLength shouldBe 32
        resolved.loginNameCharacter shouldBe "[a-z0-9._-]"
        resolved.passphraseMinLength shouldBe 10
        resolved.passphraseMaxBytes shouldBe 72
        resolved.signUpsPerAddress shouldBe 10
        resolved.signUpWindow shouldBe Duration.ofHours(1)
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

    @Test
    fun `비밀번호 술어는 바이트로 상한을 잰다 — 글자 수로 재면 bcrypt 가 조용히 자른다`() {
        val policy = shippedSignUpPolicy()

        policy.accepts(ClearPassphrase.wrap("a".repeat(10))) shouldBe true
        policy.accepts(ClearPassphrase.wrap("a".repeat(9))) shouldBe false
        policy.accepts(ClearPassphrase.wrap("a".repeat(72))) shouldBe true
        policy.accepts(ClearPassphrase.wrap("a".repeat(73))) shouldBe false
        // 한글 24자 = 72바이트(통과) · 25자 = 75바이트(거부) — 글자 수 술어였다면 둘 다 통과한다.
        policy.accepts(ClearPassphrase.wrap("가".repeat(24))) shouldBe true
        policy.accepts(ClearPassphrase.wrap("가".repeat(25))) shouldBe false
    }

    @Test
    fun `성립하지 않는 조합은 값 객체가 거부한다 — 조립 시점에 선다`() {
        shouldThrow<IllegalArgumentException> { shippedSignUpPolicy().copy(loginNameMinLength = 33) }
        shouldThrow<IllegalArgumentException> { shippedSignUpPolicy().copy(passphraseMinLength = 73) }
        shouldThrow<IllegalArgumentException> { shippedSignUpPolicy().copy(signUpsPerAddress = 0) }
        shouldThrow<IllegalArgumentException> { shippedSignUpPolicy().copy(signUpWindow = Duration.ZERO) }
    }
}
