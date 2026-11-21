package bidvector.workflow.access

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import java.time.Duration

/**
 * 열린 가입의 정책(M15/15B) — 아이디의 꼴·길이, 비밀번호의 하한과 상한, 그리고 남용 상한.
 * **매직 넘버를 코드에 두지 않는다**(v2-지침서 §5): 값은 이 데이터에만 있고 정본은 아래
 * [SIGN_UP_POLICY] 의 `source` 가 가리키는 문서다.
 *
 * [BrowserAccessPolicyData] 와 **다른 정책**이다. 그쪽은 「세션이 얼마나 사는가」이고 이쪽은
 * 「누가 계정을 만들 수 있는가」다 — 합치면 세션 창을 바꾸는 변경이 가입 규칙을 함께 건드린다.
 *
 * **상한의 뜻은 테스트 단계 한정이다**(계약 「승인 경계」). 상용 전 재결정은
 * `OPEN-M15-SIGNUP-HARDENING` 이 든다.
 */
data class SignUpPolicyData(
    /** 아이디의 길이 하한(글자 수). */
    val loginNameMinLength: Int,
    /** 아이디의 길이 상한(글자 수). */
    val loginNameMaxLength: Int,
    /**
     * 아이디에 허용되는 **글자 하나**의 정규식. 길이는 위 둘이 따로 보므로 여기서는 반복을
     * 적지 않는다 — 한 식에 길이와 글자를 함께 적으면 어느 쪽이 거부했는지 알 수 없다.
     */
    val loginNameCharacter: String,
    /** 비밀번호의 길이 하한(글자 수). */
    val passphraseMinLength: Int,
    /**
     * 비밀번호의 **바이트** 상한. bcrypt 는 72바이트를 넘는 입력을 조용히 자르므로, 넘는 값을
     * 받으면 「입력한 것과 다른 값으로 잠긴다」 — 글자 수가 아니라 UTF-8 바이트로 잰다.
     */
    val passphraseMaxBytes: Int,
    /** 한 원격 주소가 [signUpWindow] 안에 만들 수 있는 계정 수. */
    val signUpsPerAddress: Int,
    /** 위 계수를 세는 창. */
    val signUpWindow: Duration,
) {
    /** 글자 하나의 술어를 창마다 다시 컴파일하지 않는다 — 값 객체가 한 번 만든다. */
    private val character: Regex = Regex(loginNameCharacter)

    init {
        require(loginNameMinLength in 1..loginNameMaxLength) {
            "아이디 길이 범위가 성립하지 않는다: $loginNameMinLength..$loginNameMaxLength"
        }
        require(passphraseMinLength in 1..passphraseMaxBytes) {
            "비밀번호 길이 범위가 성립하지 않는다: $passphraseMinLength..$passphraseMaxBytes"
        }
        require(signUpsPerAddress >= 1) { "가입 상한은 1 이상이어야 한다: $signUpsPerAddress" }
        require(signUpWindow.isPositive()) { "가입 창은 양수여야 한다: $signUpWindow" }
    }

    /**
     * 아이디가 이 정책을 만족하는가 — 길이와 글자 둘 다. 대문자·공백·한글·`@` 는 전부 여기서
     * 떨어진다(글자 술어가 허용 집합이라, 새 글자를 받으려면 정책 값을 바꿔야 한다).
     */
    fun accepts(login: LoginName): Boolean =
        login.value.length in loginNameMinLength..loginNameMaxLength &&
            login.value.all { character.matches(it.toString()) }

    /**
     * 비밀번호가 이 정책을 만족하는가. **판정을 값 타입에 맡긴다** — 원문이 [ClearPassphrase]
     * 밖으로 나오지 않게 하는 규율이고, 그래서 이 함수에는 길이만 오간다.
     */
    fun accepts(clear: ClearPassphrase): Boolean = clear.fitsLength(passphraseMinLength, passphraseMaxBytes)
}

/**
 * **정본은 코드가 아니라 문서다**(`BROWSER_ACCESS_POLICY` 관례) — 값을 바꾸려면 `source` 의
 * 문서를 먼저 갱신한다.
 */
val SIGN_UP_POLICY: EffectiveDatedPolicy<SignUpPolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m15/15b/policy-values.md §1 — M15/15B 열린 가입의 입력 규칙과 남용 상한",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    SignUpPolicyData(
                        loginNameMinLength = 4,
                        loginNameMaxLength = 32,
                        loginNameCharacter = "[a-z0-9._-]",
                        passphraseMinLength = 10,
                        passphraseMaxBytes = 72,
                        signUpsPerAddress = 10,
                        signUpWindow = Duration.ofHours(1),
                    ),
            ),
    )
