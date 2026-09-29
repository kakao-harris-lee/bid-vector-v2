package bidvector.procurement

import bidvector.sharedkernel.Basis
import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.VatTreatment

/**
 * 실패 응답을 어떻게 다룰 것인가라는 도메인 판정(D-M3-4, `OPEN-COL-02`). `NO_DATA`는
 * 운영자 승인(P-4 ③)이 연 세 번째 상태다 — `03`은 성공도 실패도 아니다
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
 * KONEPS 수집이 소비하는 정책 전체(D-M3-3·4, 설계 검토 「구현 지침」) — 필드 계약
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
    // KONEPS 실제 wire 형식(공식 문서, "YYYY-MM-DD HH:MM:SS")은 이 목록으로만 온다 — 매직 패턴 리터럴 금지.
    val dateTimePatterns: List<DateTimePatternId>,
    // `ExpectedRangeKey.id` → `RangeBand` 단일 출처(§5.3 규율 2). 운영 정책은 아직 **빈 표**다 —
    // `sucsfbidLwltRate`의 B6 밴드는
    // `legacy-behavior`이고 값 확정은 활성 `OPEN-DEC-10` 소유라, 승인 전 실값을 main 에
    // 지어내지 않는다(「미확정 칸은 인스턴스화하지 않는다」와 같은 원칙). 메커니즘은
    // `resolveAmount`의 amount 축에 실제로 배선돼 있다(`AmountResolutionOutcome.kt`).
    val rangeBands: Map<String, RangeBand> = emptyMap(),
    // `bsnsDivNm`(업무구분명) 문서 열거 어휘. `policy-values.md` §1.5 표에 이미 `authoritative` 로
    // 있으나 P-1~P-6 같은 별도
    // 「운영자 승인」 항목이 아니다 — checklist.md 「판단이 갈린 지점」에 policy-values.md
    // 후속 등재 요청을 남긴다(curator 레인 소관, 이 필드 자체는 지어낸 값이 아니다).
    val businessCategoryDocumentedLabels: DocumentedVocabulary = DocumentedVocabulary(emptyList()),
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
        require(dateTimePatterns.isNotEmpty()) {
            "dateTimePatterns는 비어 있을 수 없다 — 타임존 없는 일시를 해석할 형식이 최소 하나 필요하다"
        }
    }
}

/**
 * 운영 필드 계약 행 하나 — `authoritative`/`effectiveFrom`은 이 정책 인스턴스 전체가
 * 공유하는 고정값이라 행마다 반복하지 않는다(`toContract`). 위치 인자 표 형태를 쓰는
 * 이유는 CPD 다.[KonepsFieldContract.of]를 행마다 이름 인자로 풀어 쓰면 10 행이 서로
 * `authoritative = true, presentIn = setOf(...), effectiveFrom = ...` 같은 50토큰 넘는
 * 공통 꼬리를 그대로 반복해 `cpdCheck`가 중복으로 잡는다(실측) — 표 자체가 중복 없는
 * 유일한 정본이 되도록 이 행 하나로 좁힌다. `internal` — KonepsOpeningCompleteFieldContracts.kt 가 재사용한다.
 */
internal data class FieldContractRow(
    val rawName: RawKey,
    val concept: FieldConcept,
    val basis: Basis?,
    val scale: FieldScale,
    val nullability: FieldNullability,
    val vatTreatment: VatTreatment,
    val provenanceTemplate: FieldProvenanceTemplate,
    val sourceZone: SourceZoneRuleId? = null,
    val listComponentSeparator: Char? = null,
    // P-9 ④ 승인(3B-2) — 계약 행 helper 의 presentIn 고정 해제. 기본값(NOTICE_LIST)은 기존
    // 행 전부(공고 목록)를 그대로 재현해 이 필드 추가가 기존 행에 영향을 주지 않는다.
    val presentIn: Set<SourceEndpoint> = setOf(SourceEndpoint.NOTICE_LIST),
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
            presentIn = presentIn,
            provenanceTemplate = provenanceTemplate,
            effectiveFrom = EffectiveFrom.Initial,
            sourceZone = sourceZone,
            listComponentSeparator = listComponentSeparator,
        )
}

/**
 * 운영 필드 계약 열 — 운영자 승인(P-1)의 `authoritative` 칸만 옮긴다
 * (`policy-values.md` §1.1~§1.5). 「미확정」 칸(`bssAmt`·`bssAmtPurcnstcst`·`presmptAmt`·
 * `usefulAmt`·율 밴드 둘·개찰·예비가격 17건 등)은 인스턴스화하지 않는다(§6 결정문 —
 * "그 자리는 소유 OPEN이 닫힌 뒤"). 소비되지 않는 코드·플래그·봉투 키(§1.5 잔여·§1.6)도
 * 두지 않는다 — 지금 procurement 가 실제로 읽는 개념 축만 등재한다. 대다수 행의 `presentIn`
 * 은 `NOTICE_LIST` 하나다([FieldContractRow.presentIn] 기본값) — `bidNtceNo`·`bidNtceOrd`
 * ([NOTICE_IDENTIFIER_PRESENT_IN] 참고)와 `bssamt`(예비가격 상세에도 실려 온다,
 * `policy-values.md` §1.7.1 각주)만 명시적으로 넓혔다. P-9 승인이 더한 개찰 축 행은
 * [KONEPS_OPENING_FIELD_ROWS] 가 따로 갖고 각자 다른 `presentIn`을 명시한다.
 */
private val KONEPS_OPERATIONAL_FIELD_ROWS: List<FieldContractRow> =
    listOf(
        FieldContractRow(
            NOTICE_NUMBER_RAW_KEY,
            FieldConcept.NOTICE_NUMBER,
            null,
            FieldScale.IDENTIFIER,
            FieldNullability.REQUIRED,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = NOTICE_IDENTIFIER_PRESENT_IN,
        ),
        FieldContractRow(
            NOTICE_ROUND_RAW_KEY,
            FieldConcept.NOTICE_ROUND,
            null,
            FieldScale.IDENTIFIER,
            FieldNullability.REQUIRED,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = NOTICE_IDENTIFIER_PRESENT_IN,
        ),
        // P-3 — 문서로 서는 기초금액 키는 `bssamt` 하나다. legacy `BASE_RESOLUTION_ORDER`의
        // `bssAmt`(미등재 키)·`bssAmtPurcnstcst`(다른 개념)는 채택하지 않는다. vatTreatment
        // 는 UNKNOWN(과세 미확정, `OPEN-REG-05`). **presentIn 이 예비가격 상세까지 넓어진다**
        // (`policy-values.md` §1.7.1 각주 — "§1.1 의 bssamt 행은
        // 그대로 서고 presentIn 만 예비가격 상세 4종으로 넓어진다"의 승인 문면을 그대로
        // 따른다).
        FieldContractRow(
            RawKey("bssamt"),
            FieldConcept.BASE_AMOUNT,
            Basis.BASE_AMOUNT,
            FieldScale.WON_INTEGER,
            FieldNullability.OPTIONAL,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.PUBLISHED,
            presentIn = BASE_AMOUNT_PRESENT_IN,
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
        // M6/6G D-6G-12 — 낙찰방법 둘. `sucsfbidLwltRate` 와 **같은 응답**이 이미 싣고 있으나
        // 계약에 자리가 없어 allow-list 반전이 떨어뜨리고 있었다(새 호출 0). 제외 조건
        // ①(적격심사가 아닌 낙찰방법)·⑦(소액수의견적)·⑩(중소기업자간 경쟁물품 계약이행능력
        // 심사)은 이 둘을 읽지 못하면 판정 자체가 불가능하다. 코드는 한글 1 + 숫자 6 형태라
        // IDENTIFIER 축이다(수치 변환 금지).
        FieldContractRow(
            RawKey("sucsfbidMthdCd"),
            FieldConcept.AWARD_METHOD_CODE,
            null,
            FieldScale.IDENTIFIER,
            FieldNullability.OPTIONAL,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
        ),
        FieldContractRow(
            RawKey("sucsfbidMthdNm"),
            FieldConcept.AWARD_METHOD_NAME,
            null,
            FieldScale.OPAQUE_TEXT,
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
        // D-6F8-2 — 공고명. 공고 목록 응답 항목(입찰공고명, 옵션으로 다룬다).
        FieldContractRow(
            RawKey("bidNtceNm"),
            FieldConcept.NOTICE_TITLE,
            null,
            FieldScale.OPAQUE_TEXT,
            FieldNullability.OPTIONAL,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
        ),
        // D-3A-8·§5.5 — 시공능력평가금액목록. 형식(`^` 구분, `[...]` 레코드)만 authoritative
        // (policy-values.md §1.5) — 단위·과세는 미확정(OPEN-QUAL-10)이라 수집 형태만 연다.
        FieldContractRow(
            rawName = RawKey("cnstrtnAbltyEvlAmtList"),
            concept = FieldConcept.CONSTRUCTION_CAPACITY_REQUIREMENT,
            basis = null,
            scale = FieldScale.DELIMITED_LIST,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            listComponentSeparator = '^',
        ),
    )

private val KONEPS_OPERATIONAL_FIELD_CONTRACTS: List<KonepsFieldContract> =
    (
        KONEPS_OPERATIONAL_FIELD_ROWS + KONEPS_OPENING_FIELD_ROWS + KONEPS_OPENING_COMPLETE_ROWS +
            KONEPS_BID_PRICE_FORMULA_A_ROWS + KONEPS_BASE_AMOUNT_ROWS + KONEPS_AWARD_METHOD_TEXT_ROWS
    ).map { it.toContract() }
private val KONEPS_ALL_FIELD_CONTRACTS: List<KonepsFieldContract> =
    KONEPS_OPERATIONAL_FIELD_CONTRACTS +
        (KONEPS_AGENCY_FIELD_ROWS + KONEPS_CLASSIFICATION_FIELD_ROWS).map { it.toContract() }

/**
 * `resultCode` → 범주(D-M3-4, `OPEN-COL-02`) — 운영자 승인(P-4 ②③)의 범주
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
 * 운영 정책 인스턴스 — 운영자 승인(`policy-values.md` §6, P-1~P-6 전부)을 옮긴다.
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
                        fieldContracts = KonepsFieldContractRegistry.of(KONEPS_ALL_FIELD_CONTRACTS),
                        resultCodeCategories = KONEPS_OPERATIONAL_RESULT_CODE_CATEGORIES,
                        baseAmountResolutionOrder = listOf(RawKey("bssamt"), RawKey("asignBdgtAmt"), RawKey("bdgtAmt")),
                        // P-3 는 기초금액 순서의 legacy 불채택만 결정하고 추정가격 순서는
                        // "기초금액 키 없음" 성질만 요구한다 — legacy 의 4키 폴백 사슬
                        // (presmptPrce → presmptAmt → asignBdgtAmt → bdgtAmt)에서 첫 키
                        // 하나만 남긴 이 좁힘은 의도적 선택이다:
                        // presmptAmt 는 문서 미등재 키이고, 배정예산 둘을 추정가격 폴백으로
                        // 쓰면 §1.1 표가 비판하는 legacy KEY_BASIS 접힘(넷을 한 basis 로
                        // 접음)이 되살아난다.
                        estimatedPriceResolutionOrder = listOf(RawKey("presmptPrce")),
                        dateInterpretation = SourceZoneRuleId.ASSUME_KST,
                        detailFetchGates = DetailFetchGates(ageGateHours = 24, recheckGateHours = 48),
                        // policy-values.md §1.4 authoritative — "YYYY-MM-DD HH:MM:SS"(항목크기
                        // 19, offset 없음). koneps-collection-026 이 이 배선을 고정한다.
                        dateTimePatterns = listOf(DateTimePatternId.KONEPS_SPACE_DELIMITED_19),
                        // policy-values.md §1.5 authoritative(조달청 OpenAPI 참고자료,
                        // koneps-collection-016 이 이 배선을 고정한다) — 문서 표기 순서 그대로, [BusinessDivision] 파생.
                        businessCategoryDocumentedLabels =
                            DocumentedVocabulary(BusinessDivision.entries.map { it.label }),
                    ),
            ),
    )
