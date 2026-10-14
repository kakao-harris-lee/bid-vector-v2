package bidvector.adapters.retention

import bidvector.procurement.FieldConcept
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsFieldContractRegistry

/**
 * 마스킹의 **키 집합은 계약 레지스트리에서 나온다**(D-6B3-5 ② — D-6B3-1 ④ 문면 복귀).
 *
 * 앞 판은 남길 키를 **그 행 자신의 투영 열**에서 읽었다. 정상 쓰기 경로에서는 두 집합이 같지만,
 * 적재자가 투영에 담당자 키를 실으면 그 키가 남김 집합에 들어와 **두 열 모두에 남는다**(vr R2-F2(a) —
 * 앱 역할이 그런 행을 INSERT 할 수 있었다). 집합의 출처를 행 데이터에서 **계약**으로 옮기면 적재자가
 * 마스킹 결과를 정할 수 없다.
 *
 * **전 개정의 합집합**이다(cr M-2). 마스킹 대상은 정의상 90일 이상 묵은 행이고 그 투영은 **적재 당시**
 * 계약으로 만들어졌다. run 날짜의 계약 하나로 풀면, 업체명 rawName 이 바뀌는 개정이 생기는 날 옛 키가
 * 지울 집합 밖이 되어 **영구히 남는다**(그 행은 표지가 찍혀 다시 마스킹할 수 없다). 「어느 개정에서든
 * 업체 식별이었던 키는 지운다」가 묵은 행에 옳은 술어다 — 그래서 기준일이 집합을 좌우하지 않는다.
 */
internal fun collectionContractRegistries(): List<KonepsFieldContractRegistry> =
    KONEPS_COLLECTION_POLICY.entries.map { (_, data) -> data.fieldContracts }

/** 전 개정의 계약 rawName 합집합 — 남길 키의 모집단. */
internal fun allContractRawNames(): Set<String> =
    collectionContractRegistries()
        .flatMap { registry -> registry.contracts.map { it.rawName.name } }
        .toSet()

/** 개념 전수의 rawName(전 개정 합집합). */
internal fun rawNamesOfConcepts(concepts: Set<FieldConcept>): Set<String> =
    concepts
        .flatMap { concept ->
            collectionContractRegistries().flatMap { registry ->
                registry.contractsFor(concept).map { it.rawName.name }
            }
        }.toSet()

/**
 * **지울 키** — 마스킹 규칙이 선언한 개념의 rawName 전수.
 *
 * 계약이 없는 개념은 **구성 오류로 던진다**: 조용히 빈 집합이 되면 「업체 식별을 지운다」가 아무 일도
 * 하지 않으면서 초록이다(공허의 가장 비싼 꼴 — 되돌릴 수 없는 경로에서).
 */
internal fun RetentionRule.MaskPii.removedRawNames(): Set<String> {
    val byConcept = maskedConcepts.associateWith { rawNamesOfConcepts(setOf(it)) }
    val empty = byConcept.filterValues { it.isEmpty() }.keys
    require(empty.isEmpty()) { "마스킹 개념에 필드 계약이 없다: $empty" }
    return byConcept.values.flatten().toSet()
}

/**
 * **남길 키** — 계약 rawName 전수에서 지울 키를 뺀 집합. 행이 무엇을 담았는지와 무관하다
 * (그래서 적재자가 남김 집합을 늘릴 수 없다).
 */
internal fun RetentionRule.MaskPii.keptRawNames(): Set<String> = allContractRawNames() - removedRawNames()
