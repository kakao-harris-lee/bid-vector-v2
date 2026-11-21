package bidvector.workflow.access

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom

/**
 * 열린 가입의 **입력 규칙**(M15/15B) — 아이디의 꼴·길이와 비밀번호의 길이 범위.
 * **매직 넘버를 코드에 두지 않는다**(v2-지침서 §5): 값은 이 데이터에만 있고 정본은 아래
 * [SIGN_UP_POLICY] 의 `source` 가 가리키는 문서다.
 *
 * **남용 상한은 여기 없다**(code-review r1 H-1). 그 값은 운영자가 **환경에서 올릴 수 있어야**
 * 하는 값이라 versioned policy 가 아니라 설정 속성이다(`bidvector.signup.attempts-per-hour`) —
 * 프록시 뒤에서 버킷이 하나가 되는 동안 그 수가 유일한 손잡이다.
 *
 * [BrowserAccessPolicyData] 와 **다른 정책**이다. 그쪽은 「세션이 얼마나 사는가」이고 이쪽은
 * 「누가 계정을 만들 수 있는가」다 — 합치면 세션 창을 바꾸는 변경이 가입 규칙을 함께 건드린다.
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
     * 비밀번호의 길이 상한(글자 수) — **입력 위생 경계**다.
     *
     * **bcrypt 의 72바이트와 아무 관계가 없다**(code-review r1 M-1 정정). 이 저장소의 해시
     * 어댑터는 `LongPasswordStrategies.hashSha512` 를 해시와 검증 양쪽에 꽂으므로 긴 입력이
     * **잘리지도 거부되지도 않는다** — 전부 SHA-512 로 접혀 결과에 들어간다. 앞 판은 그 반대를
     * 적고 72바이트를 상한으로 썼고, 그래서 한글 25자가 아무 이유 없이 막혔다.
     *
     * 그러면 왜 상한이 남는가: **무인증으로 닿는 경로의 본문 크기**를 사람이 쓸 수 있는 범위로
     * 묶는다(그 위는 입력이 아니라 부하다). 로그인·운영자 명령에는 이 상한이 없다 — 그 둘은
     * 이미 존재하는 계정의 경로다.
     */
    val passphraseMaxLength: Int,
) {
    /** 글자 하나의 술어를 호출마다 다시 컴파일하지 않는다 — 값 객체가 한 번 만든다. */
    private val character: Regex = Regex(loginNameCharacter)

    init {
        require(loginNameMinLength in 1..loginNameMaxLength) {
            "아이디 길이 범위가 성립하지 않는다: $loginNameMinLength..$loginNameMaxLength"
        }
        require(passphraseMinLength in 1..passphraseMaxLength) {
            "비밀번호 길이 범위가 성립하지 않는다: $passphraseMinLength..$passphraseMaxLength"
        }
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
    fun accepts(clear: ClearPassphrase): Boolean = clear.fitsLength(passphraseMinLength, passphraseMaxLength)
}

/**
 * **정본은 코드가 아니라 문서다**(`BROWSER_ACCESS_POLICY` 관례) — 값을 바꾸려면 `source` 의
 * 문서를 먼저 갱신한다.
 */
val SIGN_UP_POLICY: EffectiveDatedPolicy<SignUpPolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m15/15b/policy-values.md §1 — M15/15B 열린 가입의 입력 규칙",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    SignUpPolicyData(
                        loginNameMinLength = 4,
                        loginNameMaxLength = 32,
                        loginNameCharacter = "[a-z0-9._-]",
                        passphraseMinLength = 10,
                        passphraseMaxLength = 1024,
                    ),
            ),
    )
