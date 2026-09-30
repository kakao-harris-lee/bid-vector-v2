package bidvector.procurement

import bidvector.sharedkernel.Basis
import bidvector.sharedkernel.VatTreatment

/**
 * 개찰 축 필드 계약 열 — 운영자 승인(P-9, `policy-values.md` §1.7·§6b)의
 * `authoritative` 칸 13 행 가운데 **12 행**(§1.7)에 더해 **§1.9.5(license-limit) 2 행**
 * (`lmtGrpNo`·`lmtSno`)을 옮긴다. `bidwinnrBizno`(사업자등록번호)는
 * 문서로 서지만 이 열에 없다 — P-10 (a) 결정(「사업자등록번호는 저장하지 않는다」)으로
 * 어댑터 경계에서 치환·폐기되어 계약으로 등재하지 않는다(allow-list 반전 — 계약 없는
 * 키는 자동으로 제외된다, 설계 검토 Phase 2.5 게이트 ①). `bsisPlnprc`(기초예정가격)의
 * `basis`는 미확정이라 `null`(P-9 결정문 「미확정 칸은 인스턴스화하지 않는다」— 이 값은
 * basis **셀**만 미확정이고 행 자체는 등재 대상이다). `sucsfbidRate`의 밴드(expectedRange)도
 * 같은 이유로 두지 않는다.
 *
 * `CollectionPolicy.kt` 에서 분리한 파일이다(sizeGate 500줄, v2-지침서 §5 —
 * `KonepsOpeningCompleteFieldContracts.kt`·`KonepsAgencyFieldContracts.kt` 분리와 같은 전례,
 * 축 하나가 파일 하나). M6/6G 가 낙찰방법 둘과 입찰가격산식 A 축을 더하며 그 파일이 한도를
 * 넘겨 **이미 한 축을 이룬 이 열**을 옮겼다 — 크기만 맞춘 기계적 분할이 아니라 형제 파일들과
 * 같은 경계다.
 */
private fun finalAwardDateRow(rawName: String): FieldContractRow =
    FieldContractRow(
        rawName = RawKey(rawName),
        concept = FieldConcept.FINAL_AWARD_DATE,
        basis = null,
        scale = FieldScale.OPAQUE_TEXT,
        nullability = FieldNullability.OPTIONAL,
        vatTreatment = VatTreatment.UNKNOWN,
        provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
        presentIn = setOf(SourceEndpoint.OPENING_AWARD_LIST),
    )

internal val KONEPS_OPENING_FIELD_ROWS: List<FieldContractRow> =
    listOf(
        // 낙찰 목록(1~4) + 낙찰 목록 검색(16~19) — presentIn 은 legacy 가 부르는 목록군
        // 하나(OPENING_AWARD_LIST)로 좁힌다. 검색군은 legacy 미소비(§1.9.1)이고 별도
        // SourceEndpoint 를 이 slice 가 새로 열지 않는다(과잉 금지, 설계 검토 (3)).
        FieldContractRow(
            rawName = RawKey("sucsfbidAmt"),
            concept = FieldConcept.AWARD_AMOUNT,
            basis = Basis.AWARD,
            scale = FieldScale.WON_INTEGER,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.OPENING_AWARD_LIST),
        ),
        FieldContractRow(
            rawName = RawKey("sucsfbidRate"),
            // 이미 있는 토큰을 그대로 쓴다 — "최종낙찰률"이 WINNING_RATE 개념에 들어맞아
            // 새 토큰을 짓지 않는다(2026-09-01 규칙).
            concept = FieldConcept.WINNING_RATE,
            basis = null,
            scale = FieldScale.PERCENT,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.OPENING_AWARD_LIST),
        ),
        FieldContractRow(
            rawName = RawKey("bidwinnrNm"),
            concept = FieldConcept.AWARD_COMPANY_NAME,
            basis = null,
            scale = FieldScale.OPAQUE_TEXT,
            nullability = FieldNullability.REQUIRED,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.OPENING_AWARD_LIST),
        ),
        // rlOpengDt·prtcptCnum 은 낙찰 목록·개찰결과 목록 양쪽에 있다(§1.7.4·§1.7.3).
        FieldContractRow(
            rawName = RawKey("rlOpengDt"),
            concept = FieldConcept.ACTUAL_OPENING_AT,
            basis = null,
            scale = FieldScale.DATETIME_NO_ZONE,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            sourceZone = SourceZoneRuleId.ASSUME_KST,
            presentIn = setOf(SourceEndpoint.OPENING_AWARD_LIST, SourceEndpoint.RESERVE_PRICE_DETAIL),
        ),
        FieldContractRow(
            rawName = RawKey("prtcptCnum"),
            concept = FieldConcept.PARTICIPANT_COUNT,
            basis = null,
            scale = FieldScale.COUNT,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.OPENING_AWARD_LIST, SourceEndpoint.OPENING_RESULT_LIST),
        ),
        // fnlSucsfDate/`FnlSucsfDate` — "일자, 시각 없음"(§1.7.4), OPAQUE_TEXT 로 원문만
        // 보존한다(canonicalize 는 이 slice 밖). 대문자 변형(외자 전용 표기)은 §1.7.4 가
        // "두 표기를 각각 등재"로 지시 — 대소문자 무시 조회는 §5.3 규율 1 을 무력화한다.
        // 같은 `FINAL_AWARD_DATE` 개념의 별도 raw 키 — rawName 외 전부 같아 helper 로 뽑았다.
        finalAwardDateRow("fnlSucsfDate"),
        finalAwardDateRow("FnlSucsfDate"),
        // 예비가격 상세(9~12) — plnprc·bsisPlnprc·compnoRsrvtnPrceSno·drwtYn.
        FieldContractRow(
            rawName = RawKey("plnprc"),
            concept = FieldConcept.RESERVE_PRICE,
            basis = Basis.YEGA,
            scale = FieldScale.WON_INTEGER,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.RESERVE_PRICE_DETAIL),
        ),
        // bsisPlnprc — basis 자체가 미확정(P-9 결정문). null 로 두고 basisMismatch 대상 밖에
        // 둔다(basis 가 없는 개념은 대상이 아니다, FieldContractTest 기존 관례와 같다).
        FieldContractRow(
            rawName = RawKey("bsisPlnprc"),
            concept = FieldConcept.RESERVE_PRICE_PRELIMINARY,
            basis = null,
            scale = FieldScale.WON_INTEGER,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.RESERVE_PRICE_DETAIL),
        ),
        FieldContractRow(
            rawName = RawKey("compnoRsrvtnPrceSno"),
            concept = FieldConcept.RESERVE_PRICE_SEQUENCE,
            basis = null,
            scale = FieldScale.COUNT,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.RESERVE_PRICE_DETAIL),
        ),
        FieldContractRow(
            rawName = RawKey("drwtYn"),
            concept = FieldConcept.DRAW_FLAG,
            basis = null,
            scale = FieldScale.OPAQUE_TEXT,
            nullability = FieldNullability.REQUIRED,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.RESERVE_PRICE_DETAIL),
        ),
        // 개찰결과 목록(5~8) — progrsDivCdNm·opengCorpInfo. opengCorpInfo 는 어댑터 경계에서
        // masking 을 거친 값만 이 계약으로 소비한다(원문 사업자번호·대표자명 폐기, P-10 (a)).
        FieldContractRow(
            rawName = RawKey("progrsDivCdNm"),
            concept = FieldConcept.PROGRESS_DIVISION,
            basis = null,
            scale = FieldScale.OPAQUE_TEXT,
            nullability = FieldNullability.REQUIRED,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.OPENING_RESULT_LIST),
        ),
        FieldContractRow(
            rawName = RawKey("opengCorpInfo"),
            concept = FieldConcept.OPENING_COMPANY_INFO,
            basis = null,
            scale = FieldScale.OPAQUE_TEXT,
            nullability = FieldNullability.REQUIRED,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.OPENING_RESULT_LIST),
        ),
        // license-limit(§1.9.5) — lmtGrpNo·lmtSno, 행 식별자 키가 계약 없이 도는 상태를 없앤다.
        // 필수 여부 미명시(`bidNtceNo`만 필수) → OPTIONAL.
        FieldContractRow(
            rawName = RawKey("lmtGrpNo"),
            concept = FieldConcept.LICENSE_LIMIT_GROUP_NUMBER,
            basis = null,
            scale = FieldScale.IDENTIFIER,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.LICENSE_LIMIT_DETAIL),
        ),
        FieldContractRow(
            rawName = RawKey("lmtSno"),
            concept = FieldConcept.LICENSE_LIMIT_SEQUENCE_NUMBER,
            basis = null,
            scale = FieldScale.IDENTIFIER,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn = setOf(SourceEndpoint.LICENSE_LIMIT_DETAIL),
        ),
    )
