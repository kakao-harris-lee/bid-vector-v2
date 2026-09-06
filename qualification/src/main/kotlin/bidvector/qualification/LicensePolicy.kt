package bidvector.qualification

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom

/** 별칭 하나(canonical ↔ 별칭 집합). 내용은 `OPEN-QUAL-07` 미결 — 이 slice 는 형태만 낸다. */
data class LicenseAliasEntry(
    val canonical: LicenseName,
    val aliases: Set<LicenseName>,
)

data class LicenseAliasTable(
    val entries: List<LicenseAliasEntry>,
)

/**
 * 면허 자격 판정이 소비하는 정책 전체 — 별칭·포괄 코드(`v2-지침서.md` §4.2) + 지역 조건 자리
 * (D-5, 형태만). `regionalConditions` 는 이 slice 가 소비하지 않는다 — 지역 자격 **판정**은
 * QUAL-08(별도 slice)이 채운다.
 */
data class LicenseQualificationPolicyData(
    val aliasTable: LicenseAliasTable,
    val regionalConditions: List<String>,
)

/**
 * `OPEN-QUAL-07` — 내용은 비어 있으나 형태·version 배관은 선다. 판정 결과는 이 정책의
 * `PolicyVersion` 을 싣는다(decision 23, D-1 ②) — 별칭 테이블이 바뀌면 같은 입력의 판정이
 * 바뀔 수 있다는 사실 자체가 versioned policy 의 실질이다.
 */
val LICENSE_QUALIFICATION_POLICY: EffectiveDatedPolicy<LicenseQualificationPolicyData> =
    EffectiveDatedPolicy(
        source = "OPEN-QUAL-07 — 별칭·포괄 코드 내용 미확정(2026-09-06), 형태만",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    LicenseQualificationPolicyData(
                        aliasTable = LicenseAliasTable(emptyList()),
                        regionalConditions = emptyList(),
                    ),
            ),
    )

/**
 * verifier r1 F-4 — legacy `_KEY_NOISE_RE = re.compile(r"[\s·・‧⋅,.\-_/()\[\]（）]+")`
 * (`bid-vector/app/services/license_eligibility.py:142`) 가 정본이다. 이전 판은 이 집합보다
 * 좁아(공백 하나·나카구로 `・`·`‧`·`⋅`·대괄호·전각 괄호 `（）` 없음) 그 문자를 품은 면허명이
 * 거짓 `Ineligible` 을 냈다(PROBE F2·F3 실측 — 방향은 보수적/과차단이라 high 는 아니다).
 * 이 집합은 R-QUAL-03(별칭·포괄 코드 정책 데이터)의 대상이 아니다 — 별칭은 「어떤 면허를
 * 같은 것으로 볼까」라는 업무 판단이고, 이 집합은 legacy 가 정규식 상수로 고정해 둔 순수한
 * 표기 잡음(공백·구두점) 제거 규칙이다. R-QUAL-03 이 막는 것은 포괄 substring 별칭이
 * 서로 다른 전문분야를 collapse 시키는 것이지, 이 축의 문자 제거는 같은 면허명의 다른
 * 표기(전각/반각, 가운뎃점 종류)를 하나로 모을 뿐 서로 다른 면허를 모으지 않는다 — 그래서
 * `LicenseAliasTable`(정책 데이터)로 옮기지 않고 legacy 와 같은 상수로 유지한다.
 */
private val KEY_STRIP_CHARS =
    charArrayOf(
        ' ', '\t', '\n', '\r',
        '·', '・', '‧', '⋅',
        ',', '.', '-', '_', '/',
        '(', ')', '[', ']', '（', '）',
    )
private const val ASCII_CASE_OFFSET = 'a' - 'A'

/**
 * 구분자 제거 + ASCII `A`~`Z` 소문자화를 한 loop 로 낸다 — 면허명은 한글·라틴 알파벳
 * (`ENG001` 류 코드)만 쓰고 한글은 대소문자가 없다. Kotlin stdlib 의 `filterNot`·`map`·
 * `lowercase()`·`joinToString` 은 전부 내부적으로 `java.lang.Appendable`(JVM `lowercase()`
 * 는 그 위에 `Locale.ROOT` 까지)을 거치는데, 그 인터페이스가 domain 허용 목록 밖이라
 * `ArchitectureGateTest` 가 잡는다(실측). `CharArray` 직접 조립 + `String(CharArray, Int,
 * Int)` 생성자만 쓰면 그 표면에 닿지 않는다.
 */
private fun stripAndLowercase(value: String): String {
    val chars = CharArray(value.length)
    var count = 0
    for (i in value.indices) {
        val c = value[i]
        if (c !in KEY_STRIP_CHARS) {
            chars[count] = if (c in 'A'..'Z') c + ASCII_CASE_OFFSET else c
            count++
        }
    }
    return String(chars, 0, count)
}

/**
 * 코드 접미(`/1253`) 제거 → 구분자 제거 → 소문자화. legacy fallback(`normalize_license_key`)과
 * 같은 규칙 — 별칭 미등재 면허를 버리지 않고 원문 키로 보존한다(R-QUAL-03 반대 방향 회귀
 * 방지: 정규화가 서로 다른 면허를 같은 키로 collapse 시키지 않는다).
 */
private fun rawNormalizedKey(name: LicenseName): String = stripAndLowercase(name.value.substringBefore('/'))

/**
 * 면허명 → 비교 키. 커널이 갖는 유일한 파싱이다(D-3) — 코드 상수 별칭 금지(R-QUAL-03),
 * 정책 데이터(`aliasTable`) 하나만 소비한다. 별칭 미등재 면허는 원문 정규화 키로 남는다
 * (legacy `test_unregistered_license_is_kept_as_raw_key_not_dropped`).
 */
internal fun licenseComparisonKey(
    name: LicenseName,
    aliasTable: LicenseAliasTable,
): LicenseName {
    val rawKey = rawNormalizedKey(name)
    val entry =
        aliasTable.entries.firstOrNull { entry ->
            rawNormalizedKey(entry.canonical) == rawKey || entry.aliases.any { rawNormalizedKey(it) == rawKey }
        }
    val canonicalKey = entry?.let { rawNormalizedKey(it.canonical) }
    return LicenseName(canonicalKey ?: rawKey)
}
