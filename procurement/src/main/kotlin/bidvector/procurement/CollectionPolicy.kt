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
    // v2-defect(3A 잔여 일괄 verifier r3 전 수정) — `dateTimePatterns`가 정책 밖(main 함수
    // 리터럴)에 있어 `parseSourceZonedInstant`가 ISO `T` 구분자만 받았다. KONEPS 실제 wire
    // 형식(공식 문서, "YYYY-MM-DD HH:MM:SS")은 이 목록으로만 온다 — 매직 패턴 리터럴 금지.
    val dateTimePatterns: List<DateTimePatternId>,
    // v2-defect 002 수정(3A 잔여 일괄 verifier r3 전) — `ExpectedRangeKey.id` → `RangeBand`
    // 단일 출처(§5.3 규율 2). 운영 정책은 아직 **빈 표**다 — `sucsfbidLwltRate`의 B6 밴드는
    // `legacy-behavior`이고 값 확정은 활성 `OPEN-DEC-10` 소유라, 승인 전 실값을 main 에
    // 지어내지 않는다(「미확정 칸은 인스턴스화하지 않는다」와 같은 원칙). 메커니즘은
    // `resolveAmount`의 amount 축에 실제로 배선돼 있다(`AmountResolutionOutcome.kt`).
    val rangeBands: Map<String, RangeBand> = emptyMap(),
    // v2-defect 016 수정(3A 잔여 일괄 verifier r3 전) — `bsnsDivNm`(업무구분명) 문서 열거
    // 어휘. `policy-values.md` §1.5 표에 이미 `authoritative` 로 있으나 P-1~P-6 같은 별도
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
 * 운영 필드 계약 열 — 운영자 승인 2026-09-07(P-1)의 `authoritative` 칸만 옮긴다
 * (`policy-values.md` §1.1~§1.5). 「미확정」 칸(`bssAmt`·`bssAmtPurcnstcst`·`presmptAmt`·
 * `usefulAmt`·율 밴드 둘·개찰·예비가격 17건 등)은 인스턴스화하지 않는다(§6 결정문 —
 * "그 자리는 소유 OPEN이 닫힌 뒤"). 소비되지 않는 코드·플래그·봉투 키(§1.5 잔여·§1.6)도
 * 두지 않는다 — 지금 procurement 가 실제로 읽는 개념 축만 등재한다. 대다수 행의 `presentIn`
 * 은 `NOTICE_LIST` 하나다([FieldContractRow.presentIn] 기본값) — `bidNtceNo`·`bidNtceOrd`
 * (공고 식별자, 개찰 축 세 엔드포인트에도 실려 온다)와 `bssamt`(예비가격 상세에도 실려 온다,
 * `policy-values.md` §1.7.1 각주, verifier r1 F-6)만 명시적으로 넓혔다. P-9 승인(3B-2)이
 * 더한 개찰 축 행은 [KONEPS_OPENING_FIELD_ROWS] 가 따로 갖고 각자 다른 `presentIn`을
 * 명시한다 — 이 목록과 함께 레지스트리로 합쳐진다.
 */
private val KONEPS_OPERATIONAL_FIELD_ROWS: List<FieldContractRow> =
    listOf(
        // bidNtceNo·bidNtceOrd — verifier r1 F-6 수정(운영자 결정 2026-09-08, 재검토) — 이
        // 둘은 공고 식별자라 3B-2 가 여는 세 개찰 축 엔드포인트에도 실제로 실려 온다(어느
        // 응답이든 「어느 공고의 행인가」를 나르지 않는 오퍼레이션은 없다 — mapMaskedOpeningItem
        // 자신이 이 두 필드로 식별자를 뽑는다는 사실이 그 증거다). presentIn 을 NOTICE_LIST
        // 하나로 두면 개찰 축 allow-list 강제(F-6 아래)가 이 필드부터 떨어뜨려 모든 개찰 축
        // 항목이 「공고번호 없음」으로 오분류된다 — 문서가 실제로 싣는 범위를 그대로 반영한다.
        FieldContractRow(
            RawKey("bidNtceNo"),
            FieldConcept.NOTICE_NUMBER,
            null,
            FieldScale.IDENTIFIER,
            FieldNullability.REQUIRED,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn =
                setOf(
                    SourceEndpoint.NOTICE_LIST,
                    SourceEndpoint.OPENING_AWARD_LIST,
                    SourceEndpoint.OPENING_RESULT_LIST,
                    SourceEndpoint.RESERVE_PRICE_DETAIL,
                ),
        ),
        FieldContractRow(
            RawKey("bidNtceOrd"),
            FieldConcept.NOTICE_ROUND,
            null,
            FieldScale.IDENTIFIER,
            FieldNullability.REQUIRED,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.NOT_APPLICABLE,
            presentIn =
                setOf(
                    SourceEndpoint.NOTICE_LIST,
                    SourceEndpoint.OPENING_AWARD_LIST,
                    SourceEndpoint.OPENING_RESULT_LIST,
                    SourceEndpoint.RESERVE_PRICE_DETAIL,
                ),
        ),
        // P-3 — 문서로 서는 기초금액 키는 `bssamt` 하나다. legacy `BASE_RESOLUTION_ORDER`의
        // `bssAmt`(미등재 키)·`bssAmtPurcnstcst`(다른 개념)는 채택하지 않는다. vatTreatment
        // 는 UNKNOWN(과세 미확정, `OPEN-REG-05`). **presentIn 이 예비가격 상세까지 넓어진다**
        // (verifier r1 F-6 재검토, `policy-values.md` §1.7.1 각주 — "§1.1 의 bssamt 행은
        // 그대로 서고 presentIn 만 예비가격 상세 4종으로 넓어진다"의 승인 문면을 그대로
        // 따른다. 직전 라운드의 「기존 계약 행 불변 우선」은 이 각주를 놓친 판단이었다).
        FieldContractRow(
            RawKey("bssamt"),
            FieldConcept.BASE_AMOUNT,
            Basis.BASE_AMOUNT,
            FieldScale.WON_INTEGER,
            FieldNullability.OPTIONAL,
            VatTreatment.UNKNOWN,
            FieldProvenanceTemplate.PUBLISHED,
            presentIn = setOf(SourceEndpoint.NOTICE_LIST, SourceEndpoint.RESERVE_PRICE_DETAIL),
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

/**
 * 개찰 축 필드 계약 열 — 운영자 승인 2026-09-08(P-9, `policy-values.md` §1.7·§6b)의
 * `authoritative` 칸 13 행 가운데 **12 행**을 옮긴다. `bidwinnrBizno`(사업자등록번호)는
 * 문서로 서지만 이 열에 없다 — P-10 (a) 결정(「사업자등록번호는 저장하지 않는다」)으로
 * 어댑터 경계에서 치환·폐기되어 계약으로 등재하지 않는다(allow-list 반전 — 계약 없는
 * 키는 자동으로 제외된다, 설계 검토 Phase 2.5 게이트 ①). `bsisPlnprc`(기초예정가격)의
 * `basis`는 미확정이라 `null`(P-9 결정문 「미확정 칸은 인스턴스화하지 않는다」— 이 값은
 * basis **셀**만 미확정이고 행 자체는 등재 대상이다). `sucsfbidRate`의 밴드(expectedRange)도
 * 같은 이유로 두지 않는다.
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

private val KONEPS_OPENING_FIELD_ROWS: List<FieldContractRow> =
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
        // rlOpengDt·prtcptCnum 은 낙찰 목록·개찰결과 목록 양쪽에 있다(§1.7.4·§1.7.3) — 두
        // presentIn 을 함께 싣는다.
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
        // fnlSucsfDate/`FnlSucsfDate` — "일자, 시각 없음"(§1.7.4). DATETIME_NO_ZONE 은 시각 축
        // 계약(sourceZone 필수)이라 이 필드에 강제하지 않는다 — OPAQUE_TEXT 로 원문만 보존한다
        // (canonicalize 는 D-3B2-8 후속, 이번 slice 밖). 대문자 변형(외자 2종 전용 표기) 은
        // verifier r1 F-5 수정 — §1.7.4 가 "두 표기를 각각 등재하고 관측으로 좁힌다"로 **둘 다
        // 등재**를 지시한다(대소문자 무시 조회는 §5.3 규율 1 의 미지 필드 리포트를 무력화한다).
        // 같은 `FINAL_AWARD_DATE` 개념을 공유하는 별도 raw 키다 — `ALLOCATED_BUDGET` 이 이미
        // `asignBdgtAmt`·`bdgtAmt` 두 키를 갖는 것과 같은 패턴(개념 하나, 키 여럿). 두 행이
        // rawName 외 전부 같아 CPD 중복으로 잡혀(procurement:cpdCheck) helper 로 뽑았다.
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
        // 개찰결과 목록(5~8) — progrsDivCdNm·opengCorpInfo. opengCorpInfo 는 어댑터가 masking
        // 을 거친 값만 이 계약으로 소비한다(원문 사업자번호·대표자명은 어댑터 경계에서
        // 폐기, P-10 (a)) — 계약 자체는 masking 을 모르고 "이 키가 존재한다"만 안다.
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
    )

private val KONEPS_OPERATIONAL_FIELD_CONTRACTS: List<KonepsFieldContract> =
    (KONEPS_OPERATIONAL_FIELD_ROWS + KONEPS_OPENING_FIELD_ROWS).map { it.toContract() }

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
                        // P-3 는 기초금액 순서의 legacy 불채택만 결정하고 추정가격 순서는
                        // "기초금액 키 없음" 성질만 요구한다 — legacy 의 4키 폴백 사슬
                        // (presmptPrce → presmptAmt → asignBdgtAmt → bdgtAmt)에서 첫 키
                        // 하나만 남긴 이 좁힘은 3A 자신의 판단이다(verifier r3 N3-6):
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
                        // koneps-collection-016 이 이 배선을 고정한다) — 문서 표기 순서 그대로.
                        businessCategoryDocumentedLabels = DocumentedVocabulary(listOf("물품", "용역", "공사", "외자")),
                    ),
            ),
    )
