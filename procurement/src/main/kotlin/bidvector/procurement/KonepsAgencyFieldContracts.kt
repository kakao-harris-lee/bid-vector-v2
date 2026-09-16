package bidvector.procurement

import bidvector.sharedkernel.VatTreatment

/**
 * M3/3H-1 D-3H-1 — 발주기관 넷 필드 계약 열(참고자료 응답 항목 표, P-14). `CollectionPolicy.kt`
 * 에서 분리한 파일이다(sizeGate 500줄, v2-지침서 §5 — `KonepsOpeningCompleteFieldContracts.kt`
 * 분리와 같은 전례). 담당자 키(`ntceInsttOfclNm`·`…TelNo`·`…EmailAdrs`·`dminsttOfcl…`)는
 * 등재하지 않는다(개인정보, D-3H-7) — `FieldConcept`에 토큰 자체가 없어 소비 함수에 들어올
 * 경로가 없다. 코드 둘은 IDENTIFIER(제로패딩 보존, R-QUAL-05 — int 변환 금지) — 정규화는
 * 조립부(`Agency.kt` `AgencyCode.of`)가 [CategoryCode.of]와 같은 함수로 한다(§6.3.1).
 * presentIn 은 기본값(NOTICE_LIST) — 참고자료 표가 공고 목록 응답 항목이다.
 */
internal val KONEPS_AGENCY_FIELD_ROWS: List<FieldContractRow> =
    listOf(
        FieldContractRow(
            RawKey("dminsttCd"),
            FieldConcept.DEMAND_AGENCY_CODE,
            null,
            FieldScale.IDENTIFIER,
            FieldNullability.OPTIONAL,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
        ),
        FieldContractRow(
            RawKey("dminsttNm"),
            FieldConcept.DEMAND_AGENCY_NAME,
            null,
            FieldScale.OPAQUE_TEXT,
            FieldNullability.OPTIONAL,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
        ),
        FieldContractRow(
            RawKey("ntceInsttCd"),
            FieldConcept.NOTICE_AGENCY_CODE,
            null,
            FieldScale.IDENTIFIER,
            FieldNullability.OPTIONAL,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
        ),
        // 참고자료 문면 — ntceInsttNm 만 필수다(D-3H-1).
        FieldContractRow(
            RawKey("ntceInsttNm"),
            FieldConcept.NOTICE_AGENCY_NAME,
            null,
            FieldScale.OPAQUE_TEXT,
            FieldNullability.REQUIRED,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
        ),
    )
