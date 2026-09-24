package bidvector.archfixture.violating.workflow.collection

import bidvector.procurement.ObservationKey
import bidvector.procurement.RawNoticeObservation

/** D-6F8 (2b) 위반 표본 — 수집 결과 타입이 원문과 관측 키를 필드로 나른다. */
data class RogueCarrierResult(
    val observed: RawNoticeObservation,
    val key: ObservationKey,
)
