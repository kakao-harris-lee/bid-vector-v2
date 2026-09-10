package bidvector.procurement

import bidvector.sharedkernel.VatTreatment

/**
 * M3/3F P-13 (a) 승인(`policy-values.md` §1.11) — 개찰완료(투찰 행) 축 필드 계약 열.
 * `CollectionPolicy.kt`에서 분리한 파일이다(sizeGate 500줄, v2-지침서 §5 — 3E
 * `CleanMigrationCheckTest.kt` 분리와 같은 전례). 평가점수 넷(`bidPrceEvlVal`·`techEvlVal`·
 * `techEvlNaturVal`·`totalEvlAmtVal`)·사업자등록번호(`prcbdrBizno`)·대표자명(`prcbdrCeoNm`)·
 * 비고(`rmrk`)·URL(`cnsttyAccotBidAmtUrl`)은 P-13 결정으로 등재하지 않는다 — 평가점수는
 * scale 미확정(어휘를 지어내지 않는다), 나머지 넷은 P-10 (a)(사업자·개인 식별자 미등재)와
 * 소비자 부재다. 미등재 키는 §5.3 규율 1 대로 allow-list 반전에서 자동 제외된다.
 */
internal val KONEPS_OPENING_COMPLETE_ROWS: List<FieldContractRow> =
    listOf(
        // 개찰결과구분명 — 기존 PROGRESS_DIVISION 개념(진행구분, progrsDivCdNm)을 그대로
        // 쓴다(같은 축의 다른 오퍼레이션·다른 raw 키, finalAwardDateRow 와 같은 관례).
        FieldContractRow(
            rawName = RawKey("opengRsltDivNm"),
            concept = FieldConcept.PROGRESS_DIVISION,
            basis = null,
            scale = FieldScale.OPAQUE_TEXT,
            nullability = FieldNullability.REQUIRED,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.OPENING_COMPLETE),
        ),
        FieldContractRow(
            rawName = RawKey("bidClsfcNo"),
            concept = FieldConcept.BID_CLASSIFICATION_NUMBER,
            basis = null,
            scale = FieldScale.IDENTIFIER,
            nullability = FieldNullability.REQUIRED,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.OPENING_COMPLETE),
        ),
        FieldContractRow(
            rawName = RawKey("rbidNo"),
            concept = FieldConcept.REBID_NUMBER,
            basis = null,
            scale = FieldScale.IDENTIFIER,
            nullability = FieldNullability.REQUIRED,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.OPENING_COMPLETE),
        ),
        // opengRank — 실측(§1.9.7)에서 4/15 만 전 행 유일(결측·중복 흔함). 행 정체성으로 쓰지
        // 않는다(D-3F-3 해소) — 관측값으로만 나른다.
        FieldContractRow(
            rawName = RawKey("opengRank"),
            concept = FieldConcept.OPENING_RANK,
            basis = null,
            scale = FieldScale.COUNT,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.OPENING_COMPLETE),
        ),
        FieldContractRow(
            rawName = RawKey("prcbdrNm"),
            concept = FieldConcept.BID_COMPANY_NAME,
            basis = null,
            scale = FieldScale.OPAQUE_TEXT,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.OPENING_COMPLETE),
        ),
        FieldContractRow(
            rawName = RawKey("bidprcAmt"),
            concept = FieldConcept.BID_AMOUNT,
            basis = null,
            scale = FieldScale.WON_INTEGER,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.OPENING_COMPLETE),
        ),
        // bidprcrt — 분자가 투찰금액이다(WINNING_RATE 는 분자가 최종낙찰금액, P-11).
        FieldContractRow(
            rawName = RawKey("bidprcrt"),
            concept = FieldConcept.BID_RATE,
            basis = null,
            scale = FieldScale.PERCENT,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.OPENING_COMPLETE),
        ),
        // drwtNo1·drwtNo2 — 두 raw 키가 DRAW_NUMBER 개념 하나를 공유한다. rawName 만 다른
        // 두 행이라 helper 로 뽑는다(finalAwardDateRow 와 같은 이유, CPD 중복 회피 실측).
        drawNumberRow("drwtNo1"),
        drawNumberRow("drwtNo2"),
        FieldContractRow(
            rawName = RawKey("bidprcDt"),
            concept = FieldConcept.BID_AT,
            basis = null,
            scale = FieldScale.DATETIME_NO_ZONE,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            sourceZone = SourceZoneRuleId.ASSUME_KST,
            presentIn = setOf(SourceEndpoint.OPENING_COMPLETE),
        ),
    )

private fun drawNumberRow(rawName: String): FieldContractRow =
    FieldContractRow(
        rawName = RawKey(rawName),
        concept = FieldConcept.DRAW_NUMBER,
        basis = null,
        scale = FieldScale.COUNT,
        nullability = FieldNullability.OPTIONAL,
        vatTreatment = VatTreatment.UNKNOWN,
        provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
        presentIn = setOf(SourceEndpoint.OPENING_COMPLETE),
    )
