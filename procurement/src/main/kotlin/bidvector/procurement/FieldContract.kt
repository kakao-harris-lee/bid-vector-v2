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
 * KONEPS 원문 필드의 의미 계약(④, §5.3) — **소비되는 모든 키에 필수**. 등재되지 않은 키는
 * canonical 값으로 소비될 수 없다 — 소비 함수([RawNoticeObservation.valueOf])가 이 타입을
 * 인자로 요구하는 구조 자체가 그 닫힘이다.
 */
data class KonepsFieldContract(
    val rawName: RawKey,
    val concept: FieldConcept,
    val basis: Basis?,
    val scale: FieldScale,
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
    }
}

/** 계약 레지스트리 — raw 키 하나에 계약 하나(중복 등재는 구성 오류). */
data class KonepsFieldContractRegistry(
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
}
