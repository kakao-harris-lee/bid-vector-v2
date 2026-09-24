package bidvector.archfixture.violating.app.wiring

import bidvector.procurement.CanonicalizationOutcome
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.ObservationKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RawObservationStore
import bidvector.procurement.canonicalize

/**
 * D-6F8-1 우회 1·5 위반 표본 — app 의 배선 헬퍼가 use case 를 거치지 않고 원문 저장 포트를 직접 부르고
 * 정규화 함수(`canonicalize`)를 직접 부른다.
 */
class RogueCollectionPortCaller(
    private val store: RawObservationStore,
) {
    fun append(observation: RawNoticeObservation): ObservationKey = store.append(observation)

    fun normalize(
        observation: RawNoticeObservation,
        policy: KonepsCollectionPolicyData,
    ): CanonicalizationOutcome = canonicalize(observation, policy)
}
