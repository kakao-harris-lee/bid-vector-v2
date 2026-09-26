package bidvector.procurement

import bidvector.sharedkernel.VatTreatment

/**
 * M6/6F-9 D-6F9-2 — 업무구분 세부 분류 넷 필드 계약 열(6F-8 실수집이 응답에서 실측한 키). `CollectionPolicy.kt` 에서
 * 분리한 파일이다(sizeGate 500줄 — `KonepsAgencyFieldContracts.kt` 와 같은 전례). **키 리터럴은 계약 데이터인
 * 이 파일에만 있다**(6F-8 키 리터럴 게이트가 이 파일 클래스를 허용 클래스로 둔다) — 어댑터·use case 는 키를 모른다.
 *
 * 전부 OPTIONAL·`presentIn` 기본값(공고 목록)이다: 용역 응답은 앞 셋, 공사 응답은 마지막 하나만 싣고(실측 — 공사 주공종은
 * 채움 약 33%) 어느 응답이든 빈 문자열로 올 수 있다(D-6F8-11 — 빈 값은 부재). 분류 번호는 제로패딩을 보존하는
 * `IDENTIFIER` 라 숫자로 변환하지 않는다. 공사에는 코드 키가 없어 [FieldConcept.MAIN_CONSTRUCTION_TYPE] 은 이름만
 * 나른다 — 코드를 지어내지 않는다.
 */
internal val KONEPS_CLASSIFICATION_FIELD_ROWS: List<FieldContractRow> =
    listOf(
        classificationRow("pubPrcrmntClsfcNo", FieldConcept.PUBLIC_PROCUREMENT_CLASS_CODE, FieldScale.IDENTIFIER),
        classificationRow("pubPrcrmntClsfcNm", FieldConcept.PUBLIC_PROCUREMENT_CLASS_NAME, FieldScale.OPAQUE_TEXT),
        classificationRow("srvceDivNm", FieldConcept.SERVICE_DIVISION, FieldScale.OPAQUE_TEXT),
        classificationRow("mainCnsttyNm", FieldConcept.MAIN_CONSTRUCTION_TYPE, FieldScale.OPAQUE_TEXT),
    )

private fun classificationRow(
    rawName: String,
    concept: FieldConcept,
    scale: FieldScale,
): FieldContractRow =
    FieldContractRow(
        rawName = RawKey(rawName),
        concept = concept,
        basis = null,
        scale = scale,
        nullability = FieldNullability.OPTIONAL,
        vatTreatment = VatTreatment.UNKNOWN,
        provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
    )
