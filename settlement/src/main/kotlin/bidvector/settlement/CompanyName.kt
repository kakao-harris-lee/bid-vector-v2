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
 * ① 공백 제거 → ② 법인 표기 토큰 제거 → ③ 소문자화.
 *
 * **순서가 거동을 가른다.** capability map 문면(「법인 접두·접미 토큰 제거 + 공백 제거 +
 * casefold」)은 단계의 **열거**이고 순서를 주장하지 않는데, 공백 제거가 먼저여야
 * 「주식 회사 가상해양기술」이 토큰 제거에 닿는다. 그래서 문면이 아니라 legacy 순서를 구현한다.
 *
 * **① 의 공백 집합**: legacy 는 `re.sub(r"\s+", "", …)` 다. Python `str` 정규식의 `\s` 는
 * 유니코드 공백 전체(전각 공백 `U+3000`·NBSP `U+00A0` 등)를 매치하고, Kotlin
 * `Char.isWhitespace()` 가 정확히 그 집합이라는 실측은 이 저장소에 이미 있다
 * (`qualification/…/LicensePolicy.kt` 의 `_KEY_NOISE_RE` 주석 — `Character.isWhitespace` 단독은
 * NBSP 류를 **일부러** 빼므로 그것만으로는 부족하다는 실측까지 포함한다). 공백 넷 리터럴로
 * 옮기면 전각 공백이 낀 표기를 놓쳐 거짓 「패찰」을 낸다.
 *
 * **③ 의 알려진 제한**: legacy 는 `casefold()` 이고 Kotlin 에는 대응이 없다. 한글·ASCII 상호
 * 에서는 `lowercase()` 와 결과가 같고 `ß` → `ss` 류 특수 접힘만 갈린다. `java.util.Locale` 은
 * domain 의 금지 표면이라 인자 없는 `lowercase()`(locale 비의존)를 쓴다.
 *
 * 토큰 목록은 [policy] 가 나른다 — 이 함수 안에 상수로 두지 않는다.
 */
fun normalizeCompanyName(
    rawName: String,
    policy: AwardMatchingPolicyData,
): CompanyNameNormalization {
    val withoutWhitespace = rawName.filterNot { it.isWhitespace() }
    var text = withoutWhitespace
    for (token in policy.legalEntityTokens) {
        text = text.replace(token, "")
    }
    val normalized = text.lowercase()
    return if (normalized.isEmpty()) {
        CompanyNameNormalization.Empty
    } else {
        CompanyNameNormalization.Normalized(NormalizedCompanyName.of(normalized))
    }
}

/**
 * 운영자 상호의 **상태** — 「미설정」이 빈 문자열이나 `null` 이 아니라 값이다.
 *
 * 운영자 상호 입력 칸은 아직 없다(7C ④ 소관, 이 slice 는 마이그레이션을 만들지 않는다) —
 * 그래서 오늘 production 이 만들 수 있는 값은 [NotDeclared] 뿐이고, 그 사실이 추적표
 * SET-01 행의 빈 자리 하나다.
 */
sealed interface OperatorTradeName {
    data class Declared(
        val rawName: String,
    ) : OperatorTradeName {
        override fun toString(): String = REDACTED_TRADE_NAME
    }

    data object NotDeclared : OperatorTradeName
}

/**
 * 결과 쪽(낙찰자) 상호의 **상태**. [NotPublished] 는 「낙찰자가 아니다」가 아니라 「낙찰자
 * 상호가 공개되지 않았다」다 — 이 구별이 접히면 미기록이 패찰이 된다.
 */
sealed interface AwardWinnerTradeName {
    data class Published(
        val rawName: String,
    ) : AwardWinnerTradeName {
        override fun toString(): String = REDACTED_TRADE_NAME
    }

    data object NotPublished : AwardWinnerTradeName
}
