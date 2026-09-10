package bidvector.workflow.evaluation

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom

/**
 * 4B-6a 정책 데이터(scope.md ⑥) — 합성 규약 version·정책 키워드·텍스트 상한.
 * `textMaxChars`는 2E `contract-policy.properties`의 `embedding.text.max-chars`와
 * **같아야 한다**(D-4B6A-4, 두 자리가 어긋나면 4D-2 gateway가 거부한다) —
 * `OpportunityPolicyDataTest`가 그 파일 값과 직접 대조한다.
 */
data class OpportunityPolicyData(
    val synthesisVersion: SynthesisVersion,
    val keywords: List<String>,
    val textMaxChars: Int,
) {
    init {
        require(keywords.isNotEmpty()) { "keywords는 비어 있을 수 없다" }
        require(keywords.all { it.isNotBlank() }) { "keywords는 공백 항목을 가질 수 없다: $keywords" }
        require(keywords.size == keywords.distinct().size) { "keywords는 중복을 가질 수 없다: $keywords" }
        require(keywords.all { it == it.lowercase() }) { "keywords는 소문자여야 한다: $keywords" }
        require(textMaxChars > 0) { "textMaxChars는 양수여야 한다: $textMaxChars" }
    }
}

/**
 * **승인 대기 — `OPEN-4B6A-POLICY-VALUES`.** 키워드 14는 legacy-behavior
 * (`opportunity_analysis/base.py` `EXECUTION_COMPLEXITY_KEYWORDS`, 4B-5 D-4B5-5 인계)이고
 * `textMaxChars`는 2E `contract-policy.properties`의 `embedding.text.max-chars=4000`과
 * 같은 값이다(D-4B6A-4). 정본은 `reports/evidence/m4/4b6a/policy-values.md` — 값을
 * 바꾸려면 그 문서를 먼저 갱신한다.
 */
val OPPORTUNITY_POLICY: EffectiveDatedPolicy<OpportunityPolicyData> =
    EffectiveDatedPolicy(
        source = "reports/evidence/m4/4b6a/policy-values.md §1 — OPEN-4B6A-POLICY-VALUES 승인 대기",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    OpportunityPolicyData(
                        synthesisVersion = SynthesisVersion("v1"),
                        keywords =
                            listOf(
                                "통합",
                                "고도화",
                                "운영",
                                "유지관리",
                                "24시간",
                                "대규모",
                                "다기관",
                                "클라우드",
                                "센터",
                                "실시간",
                                "연계",
                                "보안",
                                "이관",
                                "플랫폼",
                            ),
                        textMaxChars = 4000,
                    ),
            ),
    )
