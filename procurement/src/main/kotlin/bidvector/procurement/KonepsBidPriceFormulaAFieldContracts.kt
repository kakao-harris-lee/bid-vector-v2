package bidvector.procurement

import bidvector.sharedkernel.VatTreatment

/**
 * M6/6G D-6G-12 — 입찰가격산식 A 정보(입찰공고정보서비스 op 24
 * `getBidPblancListBidPrceCalclAInfo`) 축 필드 계약 열. `CollectionPolicy.kt` 에서 분리한
 * 파일이다(sizeGate, `KonepsOpeningCompleteFieldContracts.kt` 와 같은 전례).
 *
 * 공사 적격심사의 낙찰하한가는 `(예정가격 − A) × 하한율 + A` 이고, A 를 0 으로 두면 실측
 * 표본에서 약 77 bp 낮게 나온다 — 하한가를 잘못 잡으면 적격 판정 자체가 틀리므로 이 축은
 * 실험의 **채점 입력**이다.
 *
 * **등재하지 않는 키**: `bidNtceNo`·`bidNtceOrd`(공고 식별자 — `KONEPS_OPERATIONAL_FIELD_ROWS`
 * 가 이미 나른다). 그 밖에 이 응답에는 사업자·개인 식별자가 없다.
 */
internal val KONEPS_BID_PRICE_FORMULA_A_ROWS: List<FieldContractRow> =
    listOf(
        aComponentRow("npnInsrprm", FieldConcept.A_NATIONAL_PENSION_PREMIUM),
        aComponentRow("mrfnHealthInsrprm", FieldConcept.A_HEALTH_INSURANCE_PREMIUM),
        aComponentRow("odsnLngtrmrcprInsrprm", FieldConcept.A_LONG_TERM_CARE_INSURANCE_PREMIUM),
        aComponentRow("rtrfundNon", FieldConcept.A_RETIREMENT_MUTUAL_AID_CONTRIBUTION),
        aComponentRow("sftyMngcst", FieldConcept.A_INDUSTRIAL_SAFETY_HEALTH_COST),
        aComponentRow("sftyChckMngcst", FieldConcept.A_SAFETY_MANAGEMENT_COST),
        aComponentRow("qltyMngcst", FieldConcept.A_QUALITY_MANAGEMENT_COST),
        aComponentRow("smkpAmt", FieldConcept.A_STANDARD_MARKET_UNIT_PRICE_AMOUNT),
        // 술어 둘 — 문서상 필수(1). 값이 `Y` 일 때만 해당 금액이 A 에 합산된다. `Y`/`N` 을
        // boolean 으로 접지 않는다: 문서가 두 값만 선언했다는 근거가 없어 세 번째 값이 오면
        // 조용히 `false` 가 되는 자리를 만들지 않는다(원문 그대로, 해석은 읽는 쪽이).
        aPredicateRow("qltyMngcstAObjYn", FieldConcept.A_QUALITY_MANAGEMENT_COST_APPLICABLE),
        aPredicateRow("smkpAmtYn", FieldConcept.A_STANDARD_MARKET_UNIT_PRICE_APPLICABLE),
        // 일시 둘 — 서로 다른 시각이다. 공고게시는 창 포함 판정(D-6G-14), A 공개는 누출
        // 판정(D-6G-13 ⑥)의 입력이라 한 축으로 접을 수 없다.
        aDateTimeRow("ntceNticeDt", FieldConcept.NOTICE_POSTED_AT),
        aDateTimeRow("bidPrceCalclAOpenDt", FieldConcept.BID_PRICE_FORMULA_A_DISCLOSED_AT),
        FieldContractRow(
            rawName = RawKey("prearngPrceDcsnMthdNm"),
            concept = FieldConcept.PLANNED_PRICE_DECISION_METHOD,
            basis = null,
            scale = FieldScale.OPAQUE_TEXT,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.BID_PRICE_FORMULA_A),
        ),
    )

/**
 * A 합산 항목 금액 한 행 — 과세는 미확정이다(`OPEN-REG-05` 와 같은 결). basis 를 두지 않는다:
 * A 는 기초금액·예정가격·낙찰금액 어느 축도 아니라 **산식의 항**이고, 그 셀에 맞는 basis 어휘가
 * 승인 표에 없다(미확정 칸은 인스턴스화하지 않는다).
 */
private fun aComponentRow(
    rawName: String,
    concept: FieldConcept,
): FieldContractRow =
    FieldContractRow(
        rawName = RawKey(rawName),
        concept = concept,
        basis = null,
        scale = FieldScale.WON_INTEGER,
        nullability = FieldNullability.OPTIONAL,
        vatTreatment = VatTreatment.UNKNOWN,
        provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
        presentIn = setOf(SourceEndpoint.BID_PRICE_FORMULA_A),
    )

private fun aPredicateRow(
    rawName: String,
    concept: FieldConcept,
): FieldContractRow =
    FieldContractRow(
        rawName = RawKey(rawName),
        concept = concept,
        basis = null,
        scale = FieldScale.OPAQUE_TEXT,
        nullability = FieldNullability.REQUIRED,
        vatTreatment = VatTreatment.UNKNOWN,
        provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
        presentIn = setOf(SourceEndpoint.BID_PRICE_FORMULA_A),
    )

private fun aDateTimeRow(
    rawName: String,
    concept: FieldConcept,
): FieldContractRow =
    FieldContractRow(
        rawName = RawKey(rawName),
        concept = concept,
        basis = null,
        scale = FieldScale.DATETIME_NO_ZONE,
        nullability = FieldNullability.REQUIRED,
        vatTreatment = VatTreatment.UNKNOWN,
        provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
        sourceZone = SourceZoneRuleId.ASSUME_KST,
        presentIn = setOf(SourceEndpoint.BID_PRICE_FORMULA_A),
    )
