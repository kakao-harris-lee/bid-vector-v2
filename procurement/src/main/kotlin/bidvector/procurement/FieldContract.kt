package bidvector.procurement

import bidvector.sharedkernel.Basis
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.VatTreatment

/**
 * raw 값이 표현하는 수치·문자 축의 형태 — [canonicalize] 변환 규칙을 정한다(§5.3,
 * ADR 0002 D-4 「값 크기로 단위를 추측하지 않는다」의 반대 방향: 계약 선언만이 변환을 연다).
 */
enum class FieldScale {
    /** 원 단위 정수 문자열(콤마 포함 가능) — 금액 축. */
    WON_INTEGER,

    /** `0`~`1` fraction 문자열. */
    FRACTION,

    /** `0`~`100` percent 문자열(`"87.995"`). */
    PERCENT,

    /** 제로패딩 식별자 문자열 — `int` 변환 금지(R-QUAL-05). */
    IDENTIFIER,

    /** 타임존 없는 일시 문자열 — `sourceZone`이 해석 규칙을 정한다(D-3A-4). */
    DATETIME_NO_ZONE,

    /** 코드+라벨 등 추가 파싱 없이 원문 그대로 두는 축. */
    OPAQUE_TEXT,
}

/** 이 필드가 나르는 도메인 개념 — [canonicalize]의 목적지(②). */
enum class FieldConcept {
    NOTICE_NUMBER,
    NOTICE_ROUND,
    BASE_AMOUNT,
    ESTIMATED_AMOUNT,
    ALLOCATED_BUDGET,
    WINNING_RATE,
    FLOOR_RATE,
    BUSINESS_CATEGORY_CODE,
    BUSINESS_CATEGORY_LABEL,
    DEADLINE_AT,
    OPENING_SCHEDULED_AT,
    CONSTRUCTION_CAPACITY_REQUIREMENT,
}

/**
 * 이 필드가 금액 해석에 쓰일 때 낼 provenance 형태 — 실제 first-match 판정은 [resolveAmount]
 * 하나가 한다(⑤). 금액 개념이 아닌 필드는 [NOT_APPLICABLE]이다.
 */
enum class FieldProvenanceTemplate {
    PUBLISHED,
    FILLED_FROM_BUDGET_KEY,
    NOT_APPLICABLE,
}

enum class FieldNullability {
    REQUIRED,
    OPTIONAL,
}

/**
 * 기대 범위의 단일 출처 참조 — 계약이 밴드를 재선언하지 않는다(§5.3 규율 2). 실제 밴드
 * 값은 이 slice 밖의 정책 데이터가 소유한다 — 3A는 참조 자리만 둔다.
 */
data class ExpectedRangeKey(
    val id: String,
)

/** 타임존 없는 KONEPS 일시 문자열의 해석 규칙 id — 값은 정책 데이터(D-3A-4, `OPEN-3A-SOURCE-TZ`). */
enum class SourceZoneRuleId {
    ASSUME_KST,
}

/**
 * 원문 값이 **무엇을 재는가**(§5.3 `unit` 슬롯, verifier r1 F-2 — `scale`과 분리된 슬롯이
 * 승인 명세에 있는데 이전 판이 빠뜨렸다). `scale`이 파싱 **규칙**이라면 `unit`은 그 규칙이
 * 적용되는 **물리 단위**다 — `data-dictionary.md` §1.4.1 "원문 unit이 보존되는 자리는 값이
 * 아니라 필드 계약이다"의 그 자리.
 */
enum class FieldUnit {
    WON,
    PERCENT,
    NONE,
}

/** `scale`↔`unit` 결속 — 하나가 다른 하나를 결정한다(같은 축의 두 이름이 서로 어긋나지 않게). */
private val SCALE_UNIT_PAIRING: Map<FieldScale, FieldUnit> =
    mapOf(
        FieldScale.WON_INTEGER to FieldUnit.WON,
        FieldScale.PERCENT to FieldUnit.PERCENT,
        FieldScale.FRACTION to FieldUnit.NONE,
        FieldScale.IDENTIFIER to FieldUnit.NONE,
        FieldScale.DATETIME_NO_ZONE to FieldUnit.NONE,
        FieldScale.OPAQUE_TEXT to FieldUnit.NONE,
    )

/**
 * 개념이 요구하는 basis — 값이 있으면 그 개념의 계약은 반드시 이 basis 여야 한다(F-3,
 * legacy `KEY_BASIS`가 `presmptPrce`·`presmptAmt`·`asignBdgtAmt`·`bdgtAmt` 넷을
 * `BUDGET_ESTIMATE` 하나로 접은 것을 되돌린다 — 키마다 자기 basis 를 갖는다).
 */
private val CONCEPT_EXPECTED_BASIS: Map<FieldConcept, Basis> =
    mapOf(
        FieldConcept.BASE_AMOUNT to Basis.BASE_AMOUNT,
        FieldConcept.ESTIMATED_AMOUNT to Basis.ESTIMATED,
        FieldConcept.ALLOCATED_BUDGET to Basis.ALLOCATED_BUDGET,
    )

/** `contract`의 `concept`·`basis` 선언이 서로 어긋나는가 — 어긋나면 [resolveAmount]가 항목을 거부한다(F-3). */
internal fun basisMismatch(contract: KonepsFieldContract): Boolean {
    val expected = CONCEPT_EXPECTED_BASIS[contract.concept] ?: return false
    return contract.basis != expected
}

/**
 * KONEPS 원문 필드의 의미 계약(④, §5.3) — **소비되는 모든 키에 필수**. 등재되지 않은 키는
 * canonical 값으로 소비될 수 없다 — 소비 함수([RawNoticeObservation.valueOf])가 이 타입을
 * 인자로 요구하는 구조 자체가 그 닫힘이다.
 *
 * **생성자가 `internal`이다**(verifier r1 F-4) — 공개였다면 다른 모듈이 그 자리에서 계약을
 * 지어내 미등재 키를 읽을 수 있었다(우회 (1b)·(10) 실측). 운영 인스턴스는
 * [KONEPS_COLLECTION_POLICY] 하나이고, procurement 밖에서 이 타입을 조립하는 경로는 없다.
 */
@ConsistentCopyVisibility
data class KonepsFieldContract internal constructor(
    val rawName: RawKey,
    val concept: FieldConcept,
    val basis: Basis?,
    val scale: FieldScale,
    val unit: FieldUnit,
    val nullability: FieldNullability,
    val vatTreatment: VatTreatment,
    val authoritative: Boolean,
    val presentIn: Set<SourceEndpoint>,
    val provenanceTemplate: FieldProvenanceTemplate,
    val effectiveFrom: EffectiveFrom,
    val expectedRange: ExpectedRangeKey?,
    val sourceZone: SourceZoneRuleId?,
) {
    init {
        require(presentIn.isNotEmpty()) { "presentIn은 비어 있을 수 없다: $rawName" }
        require((scale == FieldScale.DATETIME_NO_ZONE) == (sourceZone != null)) {
            "sourceZone은 DATETIME_NO_ZONE 필드에만 있어야 한다: $rawName"
        }
        require(unit == SCALE_UNIT_PAIRING.getValue(scale)) {
            "unit은 scale이 정하는 값이어야 한다: scale=$scale 기대 unit=${SCALE_UNIT_PAIRING.getValue(scale)} 실제=$unit ($rawName)"
        }
    }

    companion object {
        fun of(
            rawName: RawKey,
            concept: FieldConcept,
            basis: Basis?,
            scale: FieldScale,
            nullability: FieldNullability,
            vatTreatment: VatTreatment,
            authoritative: Boolean,
            presentIn: Set<SourceEndpoint>,
            provenanceTemplate: FieldProvenanceTemplate,
            effectiveFrom: EffectiveFrom,
            expectedRange: ExpectedRangeKey? = null,
            sourceZone: SourceZoneRuleId? = null,
        ): KonepsFieldContract =
            KonepsFieldContract(
                rawName = rawName,
                concept = concept,
                basis = basis,
                scale = scale,
                unit = SCALE_UNIT_PAIRING.getValue(scale),
                nullability = nullability,
                vatTreatment = vatTreatment,
                authoritative = authoritative,
                presentIn = presentIn,
                provenanceTemplate = provenanceTemplate,
                effectiveFrom = effectiveFrom,
                expectedRange = expectedRange,
                sourceZone = sourceZone,
            )
    }
}

/**
 * 계약 레지스트리 — raw 키 하나에 계약 하나(중복 등재는 구성 오류). **생성자가 `internal`이다**
 * (F-4) — [KonepsCollectionPolicyData] 를 통해서만 만들어진다.
 */
@ConsistentCopyVisibility
data class KonepsFieldContractRegistry internal constructor(
    val contracts: List<KonepsFieldContract>,
) {
    private val byRawName: Map<RawKey, KonepsFieldContract> = contracts.associateBy { it.rawName }

    init {
        require(byRawName.size == contracts.size) {
            "KonepsFieldContractRegistry에 중복 rawName이 있다"
        }
    }

    fun contractFor(rawKey: RawKey): KonepsFieldContract? = byRawName[rawKey]

    fun contractsFor(concept: FieldConcept): List<KonepsFieldContract> = contracts.filter { it.concept == concept }

    /** 관측이 가진 키 중 이 레지스트리에 없는 키 — 미지 필드(COL-07 acceptance). */
    fun unknownKeysIn(observation: RawNoticeObservation): Set<RawKey> = observation.keys - byRawName.keys

    companion object {
        fun of(contracts: List<KonepsFieldContract>): KonepsFieldContractRegistry =
            KonepsFieldContractRegistry(contracts)
    }
}
