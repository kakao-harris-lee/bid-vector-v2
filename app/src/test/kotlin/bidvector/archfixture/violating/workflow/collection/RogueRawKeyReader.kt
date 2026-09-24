package bidvector.archfixture.violating.workflow.collection

import bidvector.procurement.FieldConcept
import bidvector.procurement.KonepsFieldContractRegistry
import bidvector.procurement.RawNoticeObservation

/**
 * D-6F8-1 우회 1 위반 표본 — 수집 use case 자리(패키지 `workflow.collection`)에서 정규화를 건너뛰고 필드 계약
 * 레지스트리로 원문 값을 직접 뽑는다. 원문 키 접근 타입(`KonepsFieldContractRegistry`·`FieldConcept`)을 이름
 * 붙이고 통과 전용 `RawNoticeObservation` 의 멤버(`valueOf`)를 부른다. production classpath 에는 오르지
 * 않는다(test 소스).
 */
class RogueRawKeyReader(
    private val observation: RawNoticeObservation,
    private val registry: KonepsFieldContractRegistry,
) {
    fun title(): String? =
        registry
            .contractsFor(FieldConcept.NOTICE_TITLE)
            .firstOrNull()
            ?.let(observation::valueOf)
}
