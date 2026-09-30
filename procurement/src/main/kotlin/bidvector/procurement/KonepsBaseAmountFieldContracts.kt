package bidvector.procurement

import bidvector.sharedkernel.VatTreatment

/**
 * M6/6G D-6G-19 — 기초금액 조회(입찰공고정보서비스 op 5·6·7) 축 필드 계약 열. 선행 조사 P-5 의 전수표가
 * 정본이고, **응답명세 표의 「샘플데이터」 열이 아니라 같은 절의 XML 예제**를 기준으로 삼았다(문서 안에서
 * 둘이 어긋나는 자리가 다섯이다).
 *
 * 이 오퍼레이션이 `OPEN-6G-BASE-AMOUNT-OPERATION` 을 닫는다 — 여기서만 오는 값 셋(**예가 범위율**,
 * **순공사원가**, **A값 공고 여부**)이 각각 S0 의 반폭 `h` · 제외 ⑨ · 공사 A값 판정을 막고 있었다.
 *
 * **등재하지 않는 키**: 원가 기준율 넷(`etcGnrlexpnsBssRate` 등) · `evlBssAmt` · `dfcltydgrCfcnt` ·
 * `envCnsrvcst` · `scontrctPayprcePayGrntyFee` · `usefulAmt` · `inptDt` · `rmrk1`/`rmrk2` — 이 실험이
 * 소비하지 않는다(「지금 읽는 개념 축만 등재한다」). 미등재 키는 allow-list 반전이 관측 생성 전에
 * 떨어뜨리고 `unknownFields` 가 그 수를 센다.
 */
private val BASE_AMOUNT_ONLY = setOf(SourceEndpoint.BASE_AMOUNT_DETAIL)

internal val KONEPS_BASE_AMOUNT_ROWS: List<FieldContractRow> =
    listOf(
        // 예가 범위율 둘 — 원문 단위 %, 부호가 문자열 안에 있다(`-3`/`+3`). 선행 `+` 파싱은 canonical
        // 변환의 몫이고 계약은 축(PERCENT)만 선언한다.
        optionalFieldRow(
            "rsrvtnPrceRngBgnRate",
            FieldConcept.RESERVE_PRICE_RANGE_BEGIN_RATE,
            FieldScale.PERCENT,
            BASE_AMOUNT_ONLY,
        ),
        optionalFieldRow(
            "rsrvtnPrceRngEndRate",
            FieldConcept.RESERVE_PRICE_RANGE_END_RATE,
            FieldScale.PERCENT,
            BASE_AMOUNT_ONLY,
        ),
        // 기초금액 공개일시 — 이 값이 입찰 마감보다 늦으면 그 기초금액은 전략 입력이 아니다(D-6G-19).
        FieldContractRow(
            rawName = RawKey("bssamtOpenDt"),
            concept = FieldConcept.BASE_AMOUNT_DISCLOSED_AT,
            basis = null,
            scale = FieldScale.DATETIME_NO_ZONE,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            sourceZone = SourceZoneRuleId.ASSUME_KST,
            presentIn = setOf(SourceEndpoint.BASE_AMOUNT_DETAIL),
        ),
        // 공사 전용 둘. `bidPrceCalclAYn` 은 술어라 원문(`Y`/`N`)을 그대로 나른다.
        optionalFieldRow(
            "bidPrceCalclAYn",
            FieldConcept.BID_PRICE_FORMULA_A_APPLICABLE,
            FieldScale.OPAQUE_TEXT,
            BASE_AMOUNT_ONLY,
        ),
        optionalFieldRow(
            "bssAmtPurcnstcst",
            FieldConcept.PURE_CONSTRUCTION_COST,
            FieldScale.WON_INTEGER,
            BASE_AMOUNT_ONLY,
        ),
        // 물품 표기의 산업안전보건관리비 — 공사·용역의 `sftyMngcst` 와 다른 raw 키다(P-5 §3.1).
        optionalFieldRow(
            "industSftyHelthMngcst",
            FieldConcept.A_INDUSTRIAL_SAFETY_HEALTH_COST_GOODS,
            FieldScale.WON_INTEGER,
            BASE_AMOUNT_ONLY,
        ),
    )

/**
 * M6/6G D-6G-22 — 새 호출 비용 **0** 인 칸 둘. 공고 목록 응답에 이미 오고 있고 계약이 없어 떨어지고
 * 있었다. 값 어휘도 채움률도 모른다(문서 XML 예제가 전부 빈 값이거나 표본 둘이 같은 값이다) —
 * 그래서 어휘를 지어내지 않고 자유텍스트로 나르고, 실측은 판정문이 공시한다.
 */
internal val KONEPS_AWARD_METHOD_TEXT_ROWS: List<FieldContractRow> =
    listOf(
        optionalFieldRow(
            "sucsfbidMthdAppStd",
            FieldConcept.AWARD_METHOD_APPLICATION_STANDARD,
            FieldScale.OPAQUE_TEXT,
        ),
        optionalFieldRow("aplBssCntnts", FieldConcept.APPLICATION_BASIS_CONTENT, FieldScale.OPAQUE_TEXT),
        // M6/6G D-6G-28 — **공고일을 목록 축에서도 읽는다.** A값 오퍼레이션(op 24)은 공사 전용이라
        // 그 축에서만 읽으면 용역·물품의 공고일이 빈다. `bidNtceDt`(입찰공고일시)는 공고 목록 응답의
        // 항목이고 예규 적용례(「시행일 이후 최초 **입찰공고분**」)가 가리키는 바로 그 축이다.
        FieldContractRow(
            rawName = RawKey("bidNtceDt"),
            concept = FieldConcept.NOTICE_POSTED_AT,
            basis = null,
            scale = FieldScale.DATETIME_NO_ZONE,
            nullability = FieldNullability.OPTIONAL,
            vatTreatment = VatTreatment.UNKNOWN,
            provenanceTemplate = FieldProvenanceTemplate.NOT_APPLICABLE,
            sourceZone = SourceZoneRuleId.ASSUME_KST,
        ),
    )

/** `bssamt` 가 세 축(공고 목록·예비가격 상세·기초금액 조회)에 실려 오는 자리 — 행은 하나고 presentIn 만 넓다. */
internal val BASE_AMOUNT_PRESENT_IN: Set<SourceEndpoint> =
    setOf(SourceEndpoint.NOTICE_LIST, SourceEndpoint.RESERVE_PRICE_DETAIL, SourceEndpoint.BASE_AMOUNT_DETAIL)
