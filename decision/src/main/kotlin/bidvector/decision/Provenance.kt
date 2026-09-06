package bidvector.decision

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.Money
import bidvector.sharedkernel.PolicyVersion

/**
 * 기초금액 provenance first-match 규칙 넷의 이름(M1/1D ①, D-12) — legacy 술어 어휘
 * (조사 §5.2 ②, kebab-case: `suspect-ratio`·`clean-integer`·`derived-yega`·`derived-vat`)를
 * variant 로 옮긴다. 순서·부분집합은 이 sealed 가 아니라 정책 데이터
 * (`ProvenancePolicyData.ruleOrder`)가 정한다 — 코드 상수 순서를 두지 않는다.
 */
sealed interface ProvenanceRuleId {
    data object SuspectRatio : ProvenanceRuleId

    data object CleanInteger : ProvenanceRuleId

    data object DerivedYega : ProvenanceRuleId

    data object DerivedVat : ProvenanceRuleId
}

/**
 * first-match 가 낸 근거(D-13) — 어느 규칙이 걸렸는지(`firstMatchedRule`, 매치 없으면
 * `null` — `Unknown`), 정책이 선언한 순서, 판정에 쓴 정책 version.
 */
data class ProvenanceEvidence(
    val firstMatchedRule: ProvenanceRuleId?,
    val ruleOrder: List<ProvenanceRuleId>,
    val policyVersion: PolicyVersion,
)

/**
 * provenance 판정 봉투(D-13) — 원본·판정·근거·복구 추정치를 함께 나른다(③). `original`
 * 은 `Money`(불변)라 조용히 교정될 수 없고, 복구 추정치는 별도 필드(`recoveryEstimate`)
 * 에만 있다(D-6) — 저장 자리를 이 타입이 정하지 않는다.
 */
data class ProvenanceJudgement(
    val original: BaseAmount,
    val classification: BaseAmountProvenance,
    val evidence: ProvenanceEvidence,
    val recoveryEstimate: Fact<Money>,
)
