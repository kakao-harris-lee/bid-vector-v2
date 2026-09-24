package bidvector.archfixture.violating.workflow.collectionx

import bidvector.procurement.FieldConcept
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.RawNoticeObservation

/**
 * D-6F8-6 위반 표본 — 수집 use case 패키지(`workflow.collection`) **밖의 이웃 패키지**(`workflow.collectionx`)에 원시 키
 * 읽기를 옮긴 헬퍼(6F-8 verifier F-1 의 M1 변이와 같은 형태). 패키지 하나를 입력으로 보는 규칙은 이 헬퍼를 못 본다 —
 * 모듈 전체 규칙이 원문 키 접근 타입(`FieldConcept`·레지스트리)과 통과 전용 `RawNoticeObservation.valueOf` 를 잡아야 한다.
 * production classpath 에는 오르지 않는다(test 소스).
 */
fun RawNoticeObservation.peekTitle(policy: KonepsCollectionPolicyData): String? =
    policy.fieldContracts
        .contractsFor(FieldConcept.NOTICE_TITLE)
        .firstOrNull()
        ?.let { valueOf(it) }
