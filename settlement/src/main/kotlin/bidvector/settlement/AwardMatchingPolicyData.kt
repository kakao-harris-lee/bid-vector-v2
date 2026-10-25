package bidvector.settlement

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom

/**
 * 상호 매칭이 소비하는 정책(SET-01) — **법인 표기 토큰 목록 하나**다. 목록을 코드 상수로 두면
 * 「어떤 법인 형태를 같은 회사로 볼까」라는 업무 판단이 커널 본문에 박혀 version 을 갖지 못한다
 * (`LicenseQualificationPolicyData` 의 별칭 표와 같은 축). 커널은 이 값을 **주입받아 해석만**
 * 한다 — [normalizeCompanyName] 의 시그니처가 그 규율의 실물이다.
 *
 * 세 불변식은 구성 시점에 건다(`FloorShortfallPolicyData` 관례).
 * - **비어 있을 수 없다**: 빈 목록은 「법인 표기 토큰 제거 단계가 없는 정규화」와 같고, 그것은
 *   정책이 아니라 정규화 단계 하나를 지운 상태다. 데이터로 그 상태를 만들 수 있으면 커널의
 *   단계 제거를 정책 편집으로 우회할 수 있다.
 * - **공백 토큰 금지**: 빈 문자열 토큰을 제거해도 아무 일이 일어나지 않아 조용한 무효 항목이
 *   된다.
 * - **중복 금지**: 같은 토큰을 두 번 선언하면 목록의 크기가 규칙 수를 뜻하지 않게 된다
 *   (`ProvenancePolicyData.ruleOrder` 와 같은 사유).
 */
data class AwardMatchingPolicyData(
    val legalEntityTokens: List<String>,
) {
    init {
        require(legalEntityTokens.isNotEmpty()) { "legalEntityTokens 는 비어 있을 수 없다" }
        require(legalEntityTokens.none { it.isBlank() }) { "legalEntityTokens 에 공백 토큰이 있다" }
        require(legalEntityTokens.toSet().size == legalEntityTokens.size) {
            "legalEntityTokens 에 중복 토큰이 있다"
        }
    }
}

/**
 * 출하 정책 — 목록의 **값과 순서**는 legacy `_COMPANY_LEGAL_TOKENS` 를 그대로 옮긴 것이고,
 * 출처는 아래 [AWARD_MATCHING_POLICY_SOURCE] 가 `PolicyVersion` 에 실어 나른다.
 *
 * **목록이 충분한가는 이 slice 가 판정하지 않는다**(`OPEN-SET-03` — 일부 법인 형태가 목록에
 * 없고 실데이터 확인이 선행한다). 그 미결이 열린 채라는 사실이 바로 이 목록을 versioned 정책
 * 데이터로 두는 이유다 — 목록이 바뀌면 같은 입력의 판정이 바뀔 수 있고, 그 변화가 어느 version
 * 부터인지 남아야 한다.
 */
private val LEGACY_LEGAL_ENTITY_TOKENS: List<String> =
    listOf(
        "주식회사",
        "유한책임회사",
        "유한회사",
        "합자회사",
        "합명회사",
        "(주)",
        "（주）",
        "㈜",
        "(유)",
        "（유）",
        "㈔",
    )

/** 정책 version 의 출처 문자열 — test 가 이 값으로 출하 정책의 혈통을 대조한다. */
const val AWARD_MATCHING_POLICY_SOURCE: String =
    "legacy app/services/award_verification.py:77-89 `_COMPANY_LEGAL_TOKENS` (3447952d, 2026-08-07) " +
        "— 목록 충분성은 OPEN-SET-03 미결"

/** 출하 상호 매칭 정책. 기준일 축은 [EffectiveDatedPolicy] 가 지고 초기 version 하나다. */
val AWARD_MATCHING_POLICY: EffectiveDatedPolicy<AwardMatchingPolicyData> =
    EffectiveDatedPolicy(
        source = AWARD_MATCHING_POLICY_SOURCE,
        entries =
            listOf(
                EffectiveFrom.Initial to AwardMatchingPolicyData(LEGACY_LEGAL_ENTITY_TOKENS),
            ),
    )
