package bidvector.procurement

import bidvector.sharedkernel.VatTreatment

/**
 * 계약 행 가운데 **가장 흔한 형태** — 옵션이고, basis 축이 없고, 과세가 미확정이며, 금액 해석에 쓰이지
 * 않는 행. 축(`scale`)과 어느 응답에 오는가(`presentIn`)만 호출부가 정한다.
 *
 * 축별 계약 파일들이 각자 같은 형태의 `private` helper 를 갖고 있었고 CPD 가 그 복제를 잡았다 —
 * 형태가 같은 것이 우연이 아니라 **같은 규율**(「미확정 칸은 인스턴스화하지 않는다」)의 결과라서 한
 * 자리로 모은다. 이 형태를 벗어나는 행(금액 provenance·일시 zone·구분자 목록)은 호출부가 직접
 * [FieldContractRow] 를 쓴다 — helper 에 슬롯을 늘려 모든 축을 여기로 끌어오지 않는다.
 */
internal fun optionalFieldRow(
    rawName: String,
    concept: FieldConcept,
    scale: FieldScale,
    presentIn: Set<SourceEndpoint> = setOf(SourceEndpoint.NOTICE_LIST),
): FieldContractRow =
    FieldContractRow(
        rawName = RawKey(rawName),
        concept = concept,
        basis = null,
        scale = scale,
        nullability = FieldNullability.OPTIONAL,
        vatTreatment = VatTreatment.UNKNOWN,
        provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
        presentIn = presentIn,
    )
