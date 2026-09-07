package bidvector.procurement

import bidvector.sharedkernel.Basis
import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.VatTreatment

/**
 * 실패 응답을 어떻게 다룰 것인가라는 도메인 판정(D-M3-4, `OPEN-COL-02`). `NO_DATA`는
 * 운영자 승인 2026-09-07(P-4 ③)이 연 세 번째 상태다 — `03`은 성공도 실패도 아니다
 * (legacy `OK_RESULT_CODES`의 `03`=성공 처리는 채택하지 않는다).
 */
enum class ResultCodeCategory {
    RETRYABLE,
    NOT_RETRYABLE,
    QUOTA_EXCEEDED,
    INPUT_ERROR,
    NO_DATA,
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
 * 운영 필드 계약 행 하나 — `authoritative`/`effectiveFrom`은 이 정책 인스턴스 전체가
 * 공유하는 고정값이라 행마다 반복하지 않는다(`toContract`). 위치 인자 표 형태를 쓰는
 * 이유는 CPD 다.[KonepsFieldContract.of]를 행마다 이름 인자로 풀어 쓰면 10 행이 서로
 * `authoritative = true, presentIn = setOf(...), effectiveFrom = ...` 같은 50토큰 넘는
 * 공통 꼬리를 그대로 반복해 `cpdCheck`가 중복으로 잡는다(실측) — 표 자체가 중복 없는
 * 유일한 정본이 되도록 이 행 하나로 좁힌다.
 */
private data class FieldContractRow(
    val rawName: RawKey,
    val concept: FieldConcept,
    val basis: Basis?,
    val scale: FieldScale,
    val nullability: FieldNullability,
    val vatTreatment: VatTreatment,
    val provenanceTemplate: FieldProvenanceTemplate,
    val sourceZone: SourceZoneRuleId? = null,
) {
    fun toContract(): KonepsFieldContract =
        KonepsFieldContract.of(
            rawName = rawName,
            concept = concept,
            basis = basis,
            scale = scale,
            nullability = nullability,
            vatTreatment = vatTreatment,
            authoritative = true,
            presentIn = setOf(SourceEndpoint.NOTICE_LIST),
            provenanceTemplate = provenanceTemplate,
            effectiveFrom = EffectiveFrom.Initial,
            sourceZone = sourceZone,
        )
}

/**
 * 운영 필드 계약 열 — 운영자 승인 2026-09-07(P-1)의 `authoritative` 칸만 옮긴다
 * (`policy-values.md` §1.1~§1.5). 「미확정」 칸(`bssAmt`·`bssAmtPurcnstcst`·`presmptAmt`·
 * `usefulAmt`·율 밴드 둘·개찰·예비가격 17건 등)은 인스턴스화하지 않는다(§6 결정문 —
 * "그 자리는 소유 OPEN이 닫힌 뒤"). 소비되지 않는 코드·플래그·봉투 키(§1.5 잔여·§1.6)도
 * 두지 않는다 — 지금 procurement 가 실제로 읽는 개념 축만 등재한다. 모든 행의 `presentIn`
 * 은 `NOTICE_LIST` 하나다(공고목록 응답 — [FieldContractRow.toContract] 고정값).
 */
private val KONEPS_OPERATIONAL_FIELD_ROWS: List<FieldContractRow> =
    listOf(
        FieldContractRow(
            RawKey("bidNtceNo"),
            FieldConcept.NOTICE_NUMBER,
            null,
            FieldScale.IDENTIFIER,
            FieldNullability.REQUIRED,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
        ),
        FieldContractRow(
            RawKey("bidNtceOrd"),
            FieldConcept.NOTICE_ROUND,
            null,
            FieldScale.IDENTIFIER,
            FieldNullability.REQUIRED,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
        ),
        // P-3 — 문서로 서는 기초금액 키는 `bssamt` 하나다. legacy `BASE_RESOLUTION_ORDER`의
        // `bssAmt`(미등재 키)·`bssAmtPurcnstcst`(다른 개념)는 채택하지 않는다. vatTreatment
        // 는 UNKNOWN(과세 미확정, `OPEN-REG-05`).
        FieldContractRow(
            RawKey("bssamt"),
            FieldConcept.BASE_AMOUNT,
            Basis.BASE_AMOUNT,
            FieldScale.WON_INTEGER,
            FieldNullability.OPTIONAL,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.PUBLISHED,
        ),
        FieldContractRow(
            RawKey("asignBdgtAmt"),
            FieldConcept.ALLOCATED_BUDGET,
            Basis.ALLOCATED_BUDGET,
            FieldScale.WON_INTEGER,
            FieldNullability.OPTIONAL,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.FILLED_FROM_BUDGET_KEY,
        ),
        FieldContractRow(
            RawKey("bdgtAmt"),
            FieldConcept.ALLOCATED_BUDGET,
            Basis.ALLOCATED_BUDGET,
            FieldScale.WON_INTEGER,
            FieldNullability.OPTIONAL,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.FILLED_FROM_BUDGET_KEY,
        ),
        FieldContractRow(
            RawKey("presmptPrce"),
            FieldConcept.ESTIMATED_AMOUNT,
            Basis.ESTIMATED,
            FieldScale.WON_INTEGER,
            FieldNullability.OPTIONAL,
            VatTreatment.EXCLUSIVE,
            FieldProvenanceTemplate.PUBLISHED,
        ),
        FieldContractRow(
            RawKey("sucsfbidLwltRate"),
            FieldConcept.FLOOR_RATE,
            null,
            FieldScale.PERCENT,
            FieldNullability.OPTIONAL,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
        ),
        // P-2 승인 — 원문 보존 + `sourceZone` 규칙 id, `OPEN-3A-SOURCE-TZ` 는 열려 있다.
        FieldContractRow(
            RawKey("bidClseDt"),
            FieldConcept.DEADLINE_AT,
            null,
            FieldScale.DATETIME_NO_ZONE,
            FieldNullability.OPTIONAL,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
            SourceZoneRuleId.ASSUME_KST,
        ),
        FieldContractRow(
            RawKey("opengDt"),
            FieldConcept.OPENING_SCHEDULED_AT,
            null,
            FieldScale.DATETIME_NO_ZONE,
            FieldNullability.OPTIONAL,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
            SourceZoneRuleId.ASSUME_KST,
        ),
        FieldContractRow(
            RawKey("bsnsDivNm"),
            FieldConcept.BUSINESS_CATEGORY_LABEL,
            null,
            FieldScale.OPAQUE_TEXT,
            FieldNullability.REQUIRED,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
        ),
    )

private val KONEPS_OPERATIONAL_FIELD_CONTRACTS: List<KonepsFieldContract> =
    KONEPS_OPERATIONAL_FIELD_ROWS.map { it.toContract() }

/**
 * `resultCode` → 범주(D-M3-4, `OPEN-COL-02`) — 운영자 승인 2026-09-07(P-4 ②③)의 범주
 * 후보 열을 그대로 옮긴다. `00`(성공)과 미등재 코드(`Unclassified`)는 이 표에 없다 —
 * 그 판정은 이 표의 부재로 표현된다(3B가 조회 실패 시 채택).
 */
private val KONEPS_OPERATIONAL_RESULT_CODE_CATEGORIES: List<ResultCodeCategoryEntry> =
    listOf(
        ResultCodeCategoryEntry("01", ResultCodeCategory.RETRYABLE),
        ResultCodeCategoryEntry("02", ResultCodeCategory.RETRYABLE),
        ResultCodeCategoryEntry("03", ResultCodeCategory.NO_DATA),
        ResultCodeCategoryEntry("04", ResultCodeCategory.RETRYABLE),
        ResultCodeCategoryEntry("05", ResultCodeCategory.RETRYABLE),
        ResultCodeCategoryEntry("06", ResultCodeCategory.INPUT_ERROR),
        ResultCodeCategoryEntry("07", ResultCodeCategory.INPUT_ERROR),
        ResultCodeCategoryEntry("08", ResultCodeCategory.INPUT_ERROR),
        ResultCodeCategoryEntry("10", ResultCodeCategory.INPUT_ERROR),
        ResultCodeCategoryEntry("11", ResultCodeCategory.INPUT_ERROR),
        ResultCodeCategoryEntry("12", ResultCodeCategory.NOT_RETRYABLE),
        ResultCodeCategoryEntry("20", ResultCodeCategory.NOT_RETRYABLE),
        ResultCodeCategoryEntry("22", ResultCodeCategory.QUOTA_EXCEEDED),
        ResultCodeCategoryEntry("30", ResultCodeCategory.NOT_RETRYABLE),
        ResultCodeCategoryEntry("31", ResultCodeCategory.NOT_RETRYABLE),
        ResultCodeCategoryEntry("32", ResultCodeCategory.NOT_RETRYABLE),
    )

/**
 * 운영 정책 인스턴스 — 운영자 승인 2026-09-07(`policy-values.md` §6, P-1~P-6 전부)을 옮긴다.
 * age/recheck-gate 24h/48h는 **「측정 전 잠정」**(P-5)이고, 필드 계약 열은 「미확정」 칸을
 * 뺀 채택분만 담는다(P-1). 추정가격 해석 순서에 기초금액 키가 없다는 불변식은 `init`이
 * 실값으로도 검사한다.
 */
val KONEPS_COLLECTION_POLICY: EffectiveDatedPolicy<KonepsCollectionPolicyData> =
    EffectiveDatedPolicy(
        source =
            "reports/evidence/m3/3a/policy-values.md 「운영자 승인 2026-09-07」 절 — " +
                "「curator 승인」(Q-1 · Q-2 B-1~B-10 · Q-3 P-1~P-6)",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    KonepsCollectionPolicyData(
                        fieldContracts = KonepsFieldContractRegistry.of(KONEPS_OPERATIONAL_FIELD_CONTRACTS),
                        resultCodeCategories = KONEPS_OPERATIONAL_RESULT_CODE_CATEGORIES,
                        baseAmountResolutionOrder = listOf(RawKey("bssamt"), RawKey("asignBdgtAmt"), RawKey("bdgtAmt")),
                        estimatedPriceResolutionOrder = listOf(RawKey("presmptPrce")),
                        dateInterpretation = SourceZoneRuleId.ASSUME_KST,
                        detailFetchGates = DetailFetchGates(ageGateHours = 24, recheckGateHours = 48),
                    ),
            ),
    )
