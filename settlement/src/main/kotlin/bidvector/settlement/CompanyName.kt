package bidvector.settlement

/**
 * 상호 실값을 담은 타입이 로그·예외·단언 실패 출력으로 그 값을 내보내지 않게 하는 치환 문자열.
 *
 * 상호는 PII 축 `COMPANY_IDENTITY_CONCEPTS` 다(P-10 (a) — `bidwinnrNm` 은 canonical 에
 * 저장되지만 그 저장은 어댑터·영속화 경계의 허가이지 **출력 경로의 허가가 아니다**).
 * `data class` 가 자동으로 만드는 `toString()` 은 값이 사람이 읽는 출력으로 새는 **기본
 * 경로**라 이 모듈의 상호 운반 타입 셋은 그것을 덮는다. `equals`/`hashCode` 는 그대로 둔다 —
 * 낙찰/패찰 판정이 바로 그 동등성 위에 선다.
 */
private const val REDACTED_TRADE_NAME = "<redacted 상호>"

/**
 * 정규화된 상호 — 동등성 비교의 유일한 단위다.
 *
 * **생성자가 private 이고 유일한 생성 경로는 [normalizeCompanyName] 이다**(1E
 * `KeywordScopeText` 관례, `@ConsistentCopyVisibility` 가 `copy()` 도 같은 가시성으로 닫는다).
 * 모듈 밖에서 임의 문자열을 이 타입으로 승격할 수 있으면 「정규화를 거치지 않은 상호」가
 * 정규화된 것처럼 판정에 들어간다 — 그 경로가 **컴파일되지 않는다**는 것을
 * `CompileFailureHarnessTest` fixture 2 가 잰다.
 *
 * 빈 문자열은 값이 될 수 없다. 「정규화 뒤 남는 글자가 없다」는 상태는 값의 변종이 아니라
 * [CompanyNameNormalization.Empty] 라는 **사유 있는 결말**이다.
 */
@ConsistentCopyVisibility
data class NormalizedCompanyName private constructor(
    val value: String,
) {
    init {
        require(value.isNotEmpty()) {
            "NormalizedCompanyName 은 빈 문자열일 수 없다 — 빈 결과는 CompanyNameNormalization.Empty 다"
        }
    }

    override fun toString(): String = REDACTED_TRADE_NAME

    internal companion object {
        internal fun of(value: String): NormalizedCompanyName = NormalizedCompanyName(value)
    }
}

/**
 * 정규화 결과 — `null` 로 접지 않는다. 호출부는 소진 `when` 으로 두 결말을 각자 다룬다
 * (한쪽은 운영자 축의 「판정 안 함」, 다른 쪽은 결과 축의 「미기록」으로 간다).
 */
sealed interface CompanyNameNormalization {
    data class Normalized(
        val name: NormalizedCompanyName,
    ) : CompanyNameNormalization

    /** 정규화 뒤 남는 글자가 없다(예: 법인 표기 토큰만으로 이뤄진 상호). */
    data object Empty : CompanyNameNormalization
}

/**
 * 상호 정규화 — legacy `normalize_company_name`
 * (`app/services/award_verification.py:96-107`)의 **세 단계를 그 순서대로** 옮긴다.
 *
 * ① 공백 제거 → ② 법인 표기 토큰 제거(**접두·접미만**) → ③ 소문자화.
 *
 * **순서가 거동을 가른다.** capability map 문면(「법인 접두·접미 토큰 제거 + 공백 제거 +
 * casefold」)은 단계의 **열거**이고 순서를 주장하지 않는데, 공백 제거가 먼저여야
 * 「주식 회사 가상해양기술」이 토큰 제거에 닿는다. 그래서 순서는 legacy 를 따른다.
 *
 * **② 의 위치 — legacy 와 의도적으로 다르다.** legacy 는 `str.replace` 라 토큰이 상호 **중간**에
 * 있어도 지워 「가상주식회사건설」과 「가상건설」을 같은 회사로 판정한다(거짓 낙찰 방향).
 * 여기서는 계약 문면 「접두·접미」대로 양 끝에서만 지우고, 더 지울 것이 없을 때까지 되풀이한다
 * (「(주)주식회사가상」처럼 끝에 토큰이 겹친 표기는 legacy 와 같은 값으로 접힌다). 중간 토큰이
 * 남는 쪽의 오차는 거짓 「패찰」이고, 그 방향이 이 커널이 고르는 안전한 쪽이다.
 *
 * **① 의 공백 집합**: legacy 는 `re.sub(r"\s+", "", …)` 다. Kotlin `Char.isWhitespace()` 는 그
 * Python `\s` 에서 **U+0085(NEL) 하나를 뺀 부분집합**이다(BMP 전수 대조 29 대 28, Kotlin 에만
 * 있는 글자 0) — 전각 공백 `U+3000`·NBSP `U+00A0` 은 양쪽 다 든다. 차이는 그 글자가 남아
 * 거짓 「패찰」 쪽으로만 난다. 공백 넷 리터럴로 옮기면 전각 공백이 낀 표기를 놓쳐 같은 방향의
 * 오차가 훨씬 커진다. zero-width 글자(U+200B 등)·전각 라틴·NFD 한글·괄호 혼용 표기(`(주）`)도
 * 정규화하지 않는다 — 모두 거짓 「패찰」 쪽이고 실수집 상호 확인(`OPEN-SET-03`)과 같은 축이다.
 *
 * **③ 의 알려진 제한**: legacy 는 `casefold()` 이고 Kotlin 에는 대응이 없다. 여기서는
 * **ASCII `A`~`Z` 만** 접는다 — 상호는 한글(대소문자 없음)과 라틴 코드가 섞이는 축이고,
 * `qualification` 의 면허명 키가 같은 사유로 같은 선택을 했다(`LicensePolicy.stripAndLowercase`).
 * 비-ASCII 라틴 대문자(`À`·`Ç` 등)와 `ß` → `ss` 류 특수 접힘은 접지 않는다. 실데이터에서 이
 * 차이가 실제로 나타나는지는 `OPEN-SET-03` 과 같은 축(실수집 상호 확인)이다.
 *
 * **왜 stdlib 한 줄이 아니라 loop 인가**(전건 `check` 실측): `filterNot`·`lowercase()` 는
 * 각각 `java.lang.Appendable` 과 `java.util.Locale.ROOT` 에 닿고 **둘 다 domain 허용 목록
 * 밖**이라 `ArchitectureGateTest` 가 잡는다. 인자 없는 `lowercase()` 도 바이트코드에서는
 * `Locale.ROOT` 를 읽는다 — 「locale 비의존이니 안전」은 **틀린 추론**이었고 이 slice 의 첫
 * 전건 `check` 가 그것을 두 위반으로 실측했다. `CharArray` 직접 조립 + `String(CharArray,
 * Int, Int)` 생성자만 쓰면 그 표면에 닿지 않는다(같은 파일의 `qualification` 선례가 그 실측을
 * 이미 적고 있다). `Char.isWhitespace()` 는 이 표면을 건드리지 않는다.
 *
 * 세 단계를 **한 loop 로 합치지 않는다** — 단계마다 따로 변이해 RED 를 확인할 수 있어야 한다.
 *
 * 토큰 목록은 [policy] 가 나른다 — 이 함수 안에 상수로 두지 않는다.
 */
fun normalizeCompanyName(
    rawName: String,
    policy: AwardMatchingPolicyData,
): CompanyNameNormalization {
    val withoutWhitespace = stripWhitespace(rawName)
    val withoutTokens = stripEdgeTokens(withoutWhitespace, policy.legalEntityTokens)
    val normalized = lowercaseAscii(withoutTokens)
    return if (normalized.isEmpty()) {
        CompanyNameNormalization.Empty
    } else {
        CompanyNameNormalization.Normalized(NormalizedCompanyName.of(normalized))
    }
}

/**
 * 법인 표기 토큰 제거(②) — 양 끝에서만, 더 지울 것이 없을 때까지. 토큰은 비어 있을 수 없으므로
 * (`AwardMatchingPolicyData` 불변식) 매 회차 길이가 줄거나 loop 가 끝난다.
 */
private fun stripEdgeTokens(
    value: String,
    tokens: List<String>,
): String {
    var text = value
    var changed = true
    while (changed) {
        changed = false
        for (token in tokens) {
            if (text.startsWith(token)) {
                text = text.substring(token.length)
                changed = true
            }
            if (text.endsWith(token)) {
                text = text.substring(0, text.length - token.length)
                changed = true
            }
        }
    }
    return text
}

/** 공백 제거(①) — `Char.isWhitespace()`, legacy `\s` 에서 U+0085 하나를 뺀 부분집합(위 KDoc). */
private fun stripWhitespace(value: String): String {
    val chars = CharArray(value.length)
    var count = 0
    for (index in value.indices) {
        val char = value[index]
        if (!char.isWhitespace()) {
            chars[count] = char
            count++
        }
    }
    return String(chars, 0, count)
}

/** ASCII 소문자화(③) — 길이가 변하지 않는다(특수 접힘 없음, 위 KDoc 의 알려진 제한). */
private fun lowercaseAscii(value: String): String {
    val chars = CharArray(value.length)
    for (index in value.indices) {
        val char = value[index]
        chars[index] = if (char in 'A'..'Z') char + ASCII_CASE_OFFSET else char
    }
    return String(chars, 0, value.length)
}

/** `'A'`(65) → `'a'`(97). `qualification` 의 같은 상수와 값이 같지만 모듈 경계를 넘지 않는다. */
private const val ASCII_CASE_OFFSET = 32

/**
 * 운영자 상호의 **상태** — 「미설정」이 빈 문자열이나 `null` 이 아니라 값이다.
 *
 * 운영자 상호 입력 칸은 아직 없다(7C ④ 소관, 이 slice 는 마이그레이션을 만들지 않는다) —
 * 그래서 오늘 production 이 만들 수 있는 값은 [NotDeclared] 뿐이고, 그 사실이 추적표
 * SET-01 행의 빈 자리 하나다.
 *
 * 「상호 없음」을 나타내는 길은 [NotDeclared] 하나다 — 빈·공백만 문자열로는 [Declared] 를
 * 만들 수 없다(그 입력이 「설정은 됐으나 정규화 뒤 빈」 사유로 새지 않는다).
 */
sealed interface OperatorTradeName {
    data class Declared(
        val rawName: String,
    ) : OperatorTradeName {
        init {
            require(rawName.isNotBlank()) { "Declared 상호는 비어 있을 수 없다 — 미설정은 NotDeclared 다" }
        }

        override fun toString(): String = REDACTED_TRADE_NAME
    }

    data object NotDeclared : OperatorTradeName
}

/**
 * 결과 쪽(낙찰자) 상호의 **상태**. [NotPublished] 는 「낙찰자가 아니다」가 아니라 「낙찰자
 * 상호가 공개되지 않았다」다 — 이 구별이 접히면 미기록이 패찰이 된다. 빈·공백만 문자열로는
 * [Published] 를 만들 수 없다(「공개 안 됨」의 길은 [NotPublished] 하나다).
 *
 * [Published] 의 필드는 상호 하나뿐이다 — 금액·수치 필드가 더해지면 판정이 그것을 읽을 길이
 * 생기므로, 필드 집합을 test(`AwardMatchingKernelTest`)가 등식으로 고정한다.
 */
sealed interface AwardWinnerTradeName {
    data class Published(
        val rawName: String,
    ) : AwardWinnerTradeName {
        init {
            require(rawName.isNotBlank()) { "Published 상호는 비어 있을 수 없다 — 미공개는 NotPublished 다" }
        }

        override fun toString(): String = REDACTED_TRADE_NAME
    }

    data object NotPublished : AwardWinnerTradeName
}
