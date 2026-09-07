package bidvector.procurement

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom

/** 실패 응답을 어떻게 다룰 것인가라는 도메인 판정(D-M3-4, `OPEN-COL-02`). */
enum class ResultCodeCategory {
    RETRYABLE,
    NOT_RETRYABLE,
    QUOTA_EXCEEDED,
    INPUT_ERROR,
}

/** KONEPS `resultCode` 하나 → 범주 하나(D-M3-4). 어댑터는 코드 문자열을 옮길 뿐 범주는 여기서 정한다. */
data class ResultCodeCategoryEntry(
    val code: String,
    val category: ResultCodeCategory,
)

/**
 * KONEPS 수집이 소비하는 정책 전체(D-M3-3·4, M3/3A 설계 검토 「구현 지침」) — 필드 계약
 * 레지스트리 + resultCode 범주표 + 금액 해석 순서 둘 + 일시 해석 규칙 + 조회 가치 gate.
 *
 * **추정가격 해석 순서에 기초금액 키가 없다**(§5.2)는 이 정책이 스스로 거부하는 불변식이다
 * (우회 후보 (11)) — 값은 curator 승인 표가 정하지만, 그 값이 이 규율을 어기면 정책
 * 구성 자체가 실패한다.
 */
data class KonepsCollectionPolicyData(
    val fieldContracts: KonepsFieldContractRegistry,
    val resultCodeCategories: List<ResultCodeCategoryEntry>,
    val baseAmountResolutionOrder: List<RawKey>,
    val estimatedPriceResolutionOrder: List<RawKey>,
    val dateInterpretation: SourceZoneRuleId,
    val detailFetchGates: DetailFetchGates,
) {
    init {
        val baseAmountKeys = fieldContracts.contractsFor(FieldConcept.BASE_AMOUNT).map { it.rawName }.toSet()
        val leakedIntoEstimated = estimatedPriceResolutionOrder.filter { it in baseAmountKeys }
        require(leakedIntoEstimated.isEmpty()) {
            "추정가격 해석 순서에 기초금액 키가 있으면 안 된다(§5.2): $leakedIntoEstimated"
        }
        val allOrderKeys = baseAmountResolutionOrder + estimatedPriceResolutionOrder
        require(allOrderKeys.all { fieldContracts.contractFor(it) != null }) {
            "해석 순서의 모든 키는 등재된 필드 계약이 있어야 한다: $allOrderKeys"
        }
        require(resultCodeCategories.map { it.code }.toSet().size == resultCodeCategories.size) {
            "resultCodeCategories에 중복 코드가 있다: $resultCodeCategories"
        }
    }
}

/**
 * 운영 정책 인스턴스 — **형태 + 최소 내용**만 둔다(M3/3A 설계 검토 「과잉 금지」). 실제
 * 필드 계약·resultCode 범주·해석 순서 값은 curator 승인 표(`policy-values.md`) 수령 뒤
 * 별도 커밋으로 채운다 — main이 값을 지어내지 않는다(매직넘버 금지 원칙의 정책판).
 * test는 이 인스턴스를 쓰지 않고 `TestKonepsCollectionPolicy`류 test 전용 정책을 쓴다.
 */
val KONEPS_COLLECTION_POLICY: EffectiveDatedPolicy<KonepsCollectionPolicyData> =
    EffectiveDatedPolicy(
        source = "curator 승인 대기 — reports/evidence/m3/3a/policy-values.md",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    KonepsCollectionPolicyData(
                        fieldContracts = KonepsFieldContractRegistry(emptyList()),
                        resultCodeCategories = emptyList(),
                        baseAmountResolutionOrder = emptyList(),
                        estimatedPriceResolutionOrder = emptyList(),
                        dateInterpretation = SourceZoneRuleId.ASSUME_KST,
                        detailFetchGates = DetailFetchGates(ageGateHours = 0, recheckGateHours = 0),
                    ),
            ),
    )
