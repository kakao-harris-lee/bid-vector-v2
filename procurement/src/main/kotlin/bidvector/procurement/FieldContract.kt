package bidvector.procurement

import bidvector.sharedkernel.Basis
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.VatTreatment
import java.math.BigDecimal

/**
 * raw 값이 표현하는 수치·문자 축의 형태 — [canonicalize] 변환 규칙을 정한다(§5.3,
 * ADR 0002 D-4 「값 크기로 단위를 추측하지 않는다」의 반대 방향: 계약 선언만이 변환을 연다).
 */
enum class FieldScale {
    /** 원 단위 정수 문자열(콤마 포함 가능) — 금액 축. */
    WON_INTEGER,

    /**
     * 정수 건수·순번 문자열(예: 참가업체수·복수예가순번) — 금액도 비율도 아닌 셈 축(P-9 ②
     * 승인, 3B-2 `policy-values.md` §1.7.3 — 3A 어휘에 이 축이 없었다).
     */
    COUNT,

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

    /**
     * 구분자로 나뉜 복합 목록(`[a^b^c],[a^b^c]`류) — §5.5 D-3A-8, v2-defect 018 수정(3A
     * 잔여 일괄 verifier r3 전). 레코드·성분으로 쪼개는 **수집 형태만** 열고, 단위·과세
     * 정규화(Money 변환)는 하지 않는다(`OPEN-QUAL-10` 소유) — [UnnormalizedFigure]가 그
     * 미확정을 타입으로 나른다.
     */
    DELIMITED_LIST,
}

/**
 * 이 필드가 나르는 도메인 개념 — [canonicalize]의 목적지(②).
 *
 * **P-9 ① 승인(3B-2, 2026-09-08)** — 개찰 축 토큰을 더한다(`policy-values.md` §1.7. 낡는
 * 수치를 KDoc 에 박지 않는다 — 실제 수는 이 enum 의 선언 목록 자체가 정본이다, verifier r1
 * L-1). `WINNING_RATE`는 이미 있던 토큰을 그대로 쓴다 — `sucsfbidRate`(최종낙찰률)가 그
 * 개념에 정확히 들어맞아 새 토큰을 만들지 않는다(2026-09-01 규칙 「어휘를 지어내지 않는다」).
 * `bidwinnrBizno`(사업자등록번호)·대표자명 축은 토큰을 두지 않는다 — P-10 (a) 결정으로
 * 어댑터 경계에서 치환·폐기되어 계약 레지스트리에 등재되지 않기 때문이다(어느 토큰도 그
 * 값을 가리키지 않는다).
 */
enum class FieldConcept {
    NOTICE_NUMBER,
    NOTICE_ROUND,
    BASE_AMOUNT,
    ESTIMATED_AMOUNT,
    ALLOCATED_BUDGET,

    /**
     * 최종낙찰률(`sucsfbidRate`, 분자는 **최종낙찰금액**) — verifier r1 L-5. 투찰률
     * (개찰완료의 `bidprcrt`, 분자는 투찰금액)과 **다른 축**이다(§1.7.2 「두 율을 한
     * 축으로 접지 않는다」, P-11). 개찰완료 오퍼레이션을 여는 후속 slice 는 `bidprcrt`
     * 에 이 토큰을 재사용하지 마라 — 새 토큰(예: `BID_RATE`)이 필요하다.
     */
    WINNING_RATE,
    FLOOR_RATE,
    BUSINESS_CATEGORY_CODE,
    BUSINESS_CATEGORY_LABEL,
    DEADLINE_AT,
    OPENING_SCHEDULED_AT,
    CONSTRUCTION_CAPACITY_REQUIREMENT,
    AWARD_AMOUNT,
    RESERVE_PRICE,
    RESERVE_PRICE_PRELIMINARY,
    PARTICIPANT_COUNT,
    RESERVE_PRICE_SEQUENCE,
    ACTUAL_OPENING_AT,
    FINAL_AWARD_DATE,
    DRAW_FLAG,
    PROGRESS_DIVISION,
    OPENING_COMPANY_INFO,
    AWARD_COMPANY_NAME,

    /**
     * 제한그룹번호(`lmtGrpNo`, license-limit §1.9.5) — verifier r2 G-4. license-limit 이 행
     * 식별자로 쓰는 두 축 중 하나이지만 계약이 없어 「행 식별자로 쓰는 키가 계약 미등재로
     * 돈다」는 지적을 받았다 — 계약 없이 도는 상태를 없앤다.
     */
    LICENSE_LIMIT_GROUP_NUMBER,

    /** 제한순번(`lmtSno`, license-limit §1.9.5) — `LICENSE_LIMIT_GROUP_NUMBER`와 같은 이유. */
    LICENSE_LIMIT_SEQUENCE_NUMBER,

    // M3/3F P-13 (a) 승인(§1.11) — 개찰완료 오퍼레이션(투찰 행) 축. 평가점수 넷은 scale
    // 미확정이라 토큰을 두지 않는다(P-13 「제외」 — 어휘를 지어내지 않는다).

    /** 입찰분류번호(`bidClsfcNo`, 동일 공고번호의 집행일련번호, §1.11). */
    BID_CLASSIFICATION_NUMBER,

    /** 재입찰번호(`rbidNo`, §1.11). */
    REBID_NUMBER,

    /**
     * 개찰순위(`opengRank`, §1.11) — 실측(§1.9.7)에서 전 행 채워지고 유일한 건 4/15 뿐이다
     * (결측·중복 흔함). 행 정체성으로 쓰지 않는다(D-3F-3 해소) — 관측값으로만 나른다.
     */
    OPENING_RANK,

    /**
     * 투찰업체명(`prcbdrNm`, §1.11) — `AWARD_COMPANY_NAME`(`bidwinnrNm`, 최종낙찰업체명)과
     * 다른 축이다(투찰자 대 낙찰자, 서로 다른 오퍼레이션의 서로 다른 개념).
     */
    BID_COMPANY_NAME,

    /**
     * 투찰금액(`bidprcAmt`, §1.11) — `AWARD_AMOUNT`(`sucsfbidAmt`, 최종낙찰금액)와 다른 축이다.
     * `bidvector.sharedkernel.BidAmount`(Basis.BID)는 파생 전용(`MoneyArithmetic.kt`)이라
     * 이 관측값에 그 타입을 쓰지 않는다(procurement `ObservedBidAmount`, NoticeFacts.kt).
     */
    BID_AMOUNT,

    /**
     * 투찰률(`bidprcrt`, §1.11, *"투찰금액/예정가격 *100"*) — `WINNING_RATE`(분자가
     * 최종낙찰금액)와 **다른 축**이다(P-11, `WINNING_RATE` KDoc이 이미 이 분리를 예고했다).
     */
    BID_RATE,

    /** 추첨번호(`drwtNo1`·`drwtNo2`, §1.11, 1-기반 인덱스) — 두 raw 키가 이 개념 하나를 공유한다. */
    DRAW_NUMBER,

    /** 투찰일시(`bidprcDt`, §1.11) — zone 미확정(§1.7.4 와 같은 결, `ASSUME_KST` 초기값). */
    BID_AT,
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
 * 값은 [KonepsCollectionPolicyData.rangeBands]가 이 id 로 참조되는 단일 출처로 소유한다
 * (v2-defect 002 수정, 3A 잔여 일괄 verifier r3 전 — 이전 판은 이 슬롯이 어디서도
 * 강제되지 않았다).
 */
data class ExpectedRangeKey(
    val id: String,
)

/**
 * `expectedRange` 가 참조하는 실제 (최소, 최대) 경계 — [ExpectedRangeKey]는 이름표,
 * `RangeBand`는 그 이름표가 가리키는 값이다(§5.3 규율 2 「계약이 밴드를 재선언하지 않고
 * 단일 출처를 참조한다」의 그 출처). 경계는 포함이다(`min`·`max` 자체는 위반이 아니다).
 */
data class RangeBand(
    val min: BigDecimal,
    val max: BigDecimal,
) {
    init {
        require(min <= max) { "RangeBand의 min 은 max 이하여야 한다: min=$min max=$max" }
    }

    fun violates(value: BigDecimal): Boolean = value < min || value > max
}

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
        FieldScale.COUNT to FieldUnit.NONE,
        FieldScale.PERCENT to FieldUnit.PERCENT,
        FieldScale.FRACTION to FieldUnit.NONE,
        FieldScale.IDENTIFIER to FieldUnit.NONE,
        FieldScale.DATETIME_NO_ZONE to FieldUnit.NONE,
        FieldScale.OPAQUE_TEXT to FieldUnit.NONE,
        // 단위 미확정(OPEN-QUAL-10) — NONE 이 그 미확정의 표현이다(§5.5, 수집 형태만).
        FieldScale.DELIMITED_LIST to FieldUnit.NONE,
    )

/**
 * `[a^b^c],[a^b^c]`류 원문의 성분 하나 — §5.5 D-3A-8, v2-defect 018 수정. 단위·과세가
 * 미확정이라 [bidvector.sharedkernel.Money]로 정규화하지 않는다(`OPEN-QUAL-10` 소유) —
 * 원문 문자열 그대로 나른다.
 */
data class UnnormalizedFigure(
    val raw: String,
)

private val BRACKETED_RECORD = Regex("""\[([^\[\]]*)]""")

/**
 * 구분자 목록 원문을 레코드(`[...]`) → 성분(`componentSeparator`로 나뉨) 목록으로 쪼갠다 —
 * D-3A-8 「수집 형태만」. 값을 해석·정규화하지 않는다(§5.5) — [UnnormalizedFigure]로만 낸다.
 * 구분자(`componentSeparator`)는 계약이 나르는 정책 데이터이지 이 함수의 리터럴이 아니다.
 */
fun parseDelimitedFigureList(
    raw: String,
    componentSeparator: Char,
): List<List<UnnormalizedFigure>> =
    BRACKETED_RECORD
        .findAll(raw)
        .map { match -> match.groupValues[1].split(componentSeparator).map(::UnnormalizedFigure) }
        .toList()

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
 * **생성자와 [of] 팩토리가 모두 `internal`이다**(verifier r1 F-4, r2 N-1 정정) — 생성자만
 * 닫았던 r1 판은 `of()`가 public이라 다른 모듈이 그 자리에서 계약을 지어내 미등재 키를 읽는
 * 경로가 그대로 열려 있었다(r2 실측: 격리 worktree의 `adapters`에서 `KonepsFieldContract.of(
 * rawName = RawKey("ghostKey"), …)`가 컴파일·실행됨). `of()`까지 `internal`로 낮춰 같은 모듈
 * 밖에서는 이 타입을 조립하는 경로가 없다 — 운영 인스턴스는 [KONEPS_COLLECTION_POLICY]
 * 하나다. **경계는 모듈이지 파일이 아니다** — 같은 모듈 안(이 파일의 다른 저자, test 소스셋)의
 * 조립은 여전히 열려 있고, 그것은 위협 모델이 방어 대상으로 두지 않은 자리다(scope.md
 * 「방어하지 않는 것」).
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
    // v2-defect 018 수정(3A 잔여 일괄 verifier r3 전) — `DELIMITED_LIST` 축의 성분 구분자.
    // `sourceZone`과 같은 자리(축 전용 슬롯, 그 축에만 쌍을 이룬다).
    val listComponentSeparator: Char?,
) {
    init {
        require(presentIn.isNotEmpty()) { "presentIn은 비어 있을 수 없다: $rawName" }
        require((scale == FieldScale.DATETIME_NO_ZONE) == (sourceZone != null)) {
            "sourceZone은 DATETIME_NO_ZONE 필드에만 있어야 한다: $rawName"
        }
        require((scale == FieldScale.DELIMITED_LIST) == (listComponentSeparator != null)) {
            "listComponentSeparator는 DELIMITED_LIST 필드에만 있어야 한다: $rawName"
        }
        require(unit == SCALE_UNIT_PAIRING.getValue(scale)) {
            "unit은 scale이 정하는 값이어야 한다: scale=$scale 기대 unit=${SCALE_UNIT_PAIRING.getValue(scale)} 실제=$unit ($rawName)"
        }
    }

    companion object {
        internal fun of(
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
            listComponentSeparator: Char? = null,
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
                listComponentSeparator = listComponentSeparator,
            )
    }
}

/**
 * 계약 레지스트리 — raw 키 하나에 계약 하나(중복 등재는 구성 오류). **생성자와 [of] 팩토리가
 * 모두 `internal`이다**(F-4, r2 N-1 정정 — 생성자만으로는 팩토리가 같은 능력을 돌려줬다).
 * [KonepsCollectionPolicyData]를 통해서만(procurement 안에서) 만들어진다.
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
        internal fun of(contracts: List<KonepsFieldContract>): KonepsFieldContractRegistry =
            KonepsFieldContractRegistry(contracts)
    }
}
