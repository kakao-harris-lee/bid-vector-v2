package bidvector.procurement

/**
 * 이 필드가 나르는 도메인 개념 — [canonicalize]의 목적지(②).
 *
 * **P-9 ① 승인(3B-2, 2026-09-08)** — 개찰 축 토큰을 더한다(`policy-values.md` §1.7. 낡는
 * 수치를 KDoc 에 박지 않는다 — 실제 수는 이 enum 의 선언 목록 자체가 정본이다, verifier r1
 * L-1). `WINNING_RATE`는 이미 있던 토큰을 그대로 쓴다 — `sucsfbidRate`(최종낙찰률)가 그
 * 개념에 정확히 들어맞아 새 토큰을 만들지 않는다(2026-09-01 규칙 「어휘를 지어내지 않는다」).
 * `bidwinnrBizno`(사업자등록번호)·대표자명 축은 토큰을 두지 않는다 — P-10 (a) 결정으로
 * 어댑터 경계에서 치환·폐기되어 계약 레지스트리에 등재되지 않기 때문이다(어느 토큰도 그
 * 값을 가리키지 않는다).
 */
enum class FieldConcept {
    NOTICE_NUMBER,
    NOTICE_ROUND,
    BASE_AMOUNT,
    ESTIMATED_AMOUNT,
    ALLOCATED_BUDGET,

    /**
     * 최종낙찰률(`sucsfbidRate`, 분자는 **최종낙찰금액**) — verifier r1 L-5. 투찰률
     * (개찰완료의 `bidprcrt`, 분자는 투찰금액)과 **다른 축**이다(§1.7.2 「두 율을 한
     * 축으로 접지 않는다」, P-11). 개찰완료 오퍼레이션을 여는 후속 slice 는 `bidprcrt`
     * 에 이 토큰을 재사용하지 마라 — 새 토큰(예: `BID_RATE`)이 필요하다.
     */
    WINNING_RATE,
    FLOOR_RATE,
    BUSINESS_CATEGORY_CODE,
    BUSINESS_CATEGORY_LABEL,
    DEADLINE_AT,
    OPENING_SCHEDULED_AT,
    CONSTRUCTION_CAPACITY_REQUIREMENT,
    AWARD_AMOUNT,
    RESERVE_PRICE,
    RESERVE_PRICE_PRELIMINARY,
    PARTICIPANT_COUNT,
    RESERVE_PRICE_SEQUENCE,
    ACTUAL_OPENING_AT,
    FINAL_AWARD_DATE,
    DRAW_FLAG,
    PROGRESS_DIVISION,
    OPENING_COMPANY_INFO,
    AWARD_COMPANY_NAME,

    /**
     * 제한그룹번호(`lmtGrpNo`, license-limit §1.9.5) — verifier r2 G-4. license-limit 이 행
     * 식별자로 쓰는 두 축 중 하나이지만 계약이 없어 「행 식별자로 쓰는 키가 계약 미등재로
     * 돈다」는 지적을 받았다 — 계약 없이 도는 상태를 없앤다.
     */
    LICENSE_LIMIT_GROUP_NUMBER,

    /** 제한순번(`lmtSno`, license-limit §1.9.5) — `LICENSE_LIMIT_GROUP_NUMBER`와 같은 이유. */
    LICENSE_LIMIT_SEQUENCE_NUMBER,

    // M3/3F P-13 (a) 승인(§1.11) — 개찰완료 오퍼레이션(투찰 행) 축. 평가점수 넷은 scale
    // 미확정이라 토큰을 두지 않는다(P-13 「제외」 — 어휘를 지어내지 않는다).

    /** 입찰분류번호(`bidClsfcNo`, 동일 공고번호의 집행일련번호, §1.11). */
    BID_CLASSIFICATION_NUMBER,

    /** 재입찰번호(`rbidNo`, §1.11). */
    REBID_NUMBER,

    /**
     * 개찰순위(`opengRank`, §1.11) — 실측(§1.9.7)에서 전 행 채워지고 유일한 건 4/15 뿐이다
     * (결측·중복 흔함). 행 정체성으로 쓰지 않는다(D-3F-3 해소) — 관측값으로만 나른다.
     */
    OPENING_RANK,

    /**
     * 투찰업체명(`prcbdrNm`, §1.11) — `AWARD_COMPANY_NAME`(`bidwinnrNm`, 최종낙찰업체명)과
     * 다른 축이다(투찰자 대 낙찰자, 서로 다른 오퍼레이션의 서로 다른 개념).
     */
    BID_COMPANY_NAME,

    /**
     * 투찰금액(`bidprcAmt`, §1.11) — `AWARD_AMOUNT`(`sucsfbidAmt`, 최종낙찰금액)와 다른 축이다.
     * `bidvector.sharedkernel.BidAmount`(Basis.BID)는 파생 전용(`MoneyArithmetic.kt`)이라
     * 이 관측값에 그 타입을 쓰지 않는다(procurement `ObservedBidAmount`, NoticeFacts.kt).
     */
    BID_AMOUNT,

    /**
     * 투찰률(`bidprcrt`, §1.11, *"투찰금액/예정가격 *100"*) — `WINNING_RATE`(분자가
     * 최종낙찰금액)와 **다른 축**이다(P-11, `WINNING_RATE` KDoc이 이미 이 분리를 예고했다).
     */
    BID_RATE,

    /** 추첨번호(`drwtNo1`·`drwtNo2`, §1.11, 1-기반 인덱스) — 두 raw 키가 이 개념 하나를 공유한다. */
    DRAW_NUMBER,

    /** 투찰일시(`bidprcDt`, §1.11) — zone 미확정(§1.7.4 와 같은 결, `ASSUME_KST` 초기값). */
    BID_AT,

    /**
     * 수요기관코드(`dminsttCd`, M3/3H-1 D-3H-1) — 엔진 `agency_id` 정본(D-3H-2). 코드가
     * 있으면 「행자부코드, 없으면 조달청 부여 코드」(참고자료 문면). [NOTICE_AGENCY_CODE]
     * 값으로 접지 않는다(폴백 없음, scope.md 우회 (3)).
     */
    DEMAND_AGENCY_CODE,

    /** 수요기관명(`dminsttNm`, D-3H-1) — 표시·감사용, 동일성 판정에 쓰지 않는다(D-3H-2). */
    DEMAND_AGENCY_NAME,

    /** 공고기관코드(`ntceInsttCd`, D-3H-1) — [DEMAND_AGENCY_CODE]와 다른 축(자기 필드만). */
    NOTICE_AGENCY_CODE,

    /** 공고기관명(`ntceInsttNm`, D-3H-1, 문서 필수) — 표시·감사용. */
    NOTICE_AGENCY_NAME,

    /**
     * 공고명(`bidNtceNm`, M6/6F-8 D-6F8-2 — `OPEN-6F4-TITLE-INGEST` 닫음) — 감시 키워드 매칭 입력의
     * 조각이다(D-6F4-3). 원문 키는 이 개념의 계약 행이 나른다(D-6F4-6 — 어댑터·use case 에 키를
     * 박지 않는다).
     */
    NOTICE_TITLE,

    // M6/6F-9 D-6F9-2 — 업무구분 세부 분류 넷. 대분류는 필드가 아니라 수집 오퍼레이션이 정한다(D-6F9-1,
    // `RawNoticeObservation.sourceDivision`). 서로 다른 축이라 [BUSINESS_CATEGORY_CODE]·[BUSINESS_CATEGORY_LABEL]
    // (코드+라벨 축, `bsnsDivNm` 행)에 섞지 않는다(P-7 · `OPEN-COL-03`).

    /** 공공조달분류 세분류 번호(`pubPrcrmntClsfcNo`, 용역) — 제로패딩 보존 식별자, `BusinessCategory.code` 로 흐른다. */
    PUBLIC_PROCUREMENT_CLASS_CODE,

    /** 공공조달분류 세분류명(`pubPrcrmntClsfcNm`, 용역) — [PUBLIC_PROCUREMENT_CLASS_CODE]와 같은 쌍의 라벨. */
    PUBLIC_PROCUREMENT_CLASS_NAME,

    /** 용역구분명(`srvceDivNm`, 용역 — 일반용역·기술용역 …) — 자기 칸 `service_division`. */
    SERVICE_DIVISION,

    /** 주공종명(`mainCnsttyNm`, 공사 — 전기공사업·건축공사업 …) — 코드가 응답에 없어 이름만이다. */
    MAIN_CONSTRUCTION_TYPE,

    // M6/6G D-6G-12 — 낙찰방법 축. 제외 조건 ①⑦⑩(D-6G-13)의 1차 입력인데 계약에 자리가
    // 없었다(응답은 이미 받고 있고 읽지 않을 뿐이었다). 코드와 이름은 다른 축이다.

    /** 낙찰방법코드(`sucsfbidMthdCd`, 한글 1 + 숫자 6) — `int` 변환 금지(IDENTIFIER 축). */
    AWARD_METHOD_CODE,

    /** 낙찰방법명(`sucsfbidMthdNm`) — 「적격심사제…」·「협상에의한계약」 등 원문 라벨. */
    AWARD_METHOD_NAME,

    /** 예정가격결정방법명(`prearngPrceDcsnMthdNm`, 「복수예가」 등) — 제외 조건 ④(단일 예정가격)의 입력. */
    PLANNED_PRICE_DECISION_METHOD,

    /** 공고게시일시(`ntceNticeDt`) — D-6G-14 의 **공고일** 기준(예규 적용례 「시행일 이후 최초 입찰공고분」). */
    NOTICE_POSTED_AT,

    // M6/6G D-6G-12 — 입찰가격산식 A 의 합산 항목 일곱. **개념 하나로 접지 않는다**:
    // [KonepsFieldContractRegistry.valueIn] 이 개념의 첫 계약만 읽으므로 서로 다른 일곱 금액이
    // 한 개념을 공유하면 그중 하나가 조용히 나머지를 가린다(`drwtNo1`·`drwtNo2` 가 개념을
    // 공유하는 것은 둘이 **같은 축의 두 슬롯**이기 때문이고 여기는 다른 축 일곱이다).

    /** 국민연금보험료(`npnInsrprm`). */
    A_NATIONAL_PENSION_PREMIUM,

    /** 국민건강보험료(`mrfnHealthInsrprm`). */
    A_HEALTH_INSURANCE_PREMIUM,

    /** 노인장기요양보험료(`odsnLngtrmrcprInsrprm`). */
    A_LONG_TERM_CARE_INSURANCE_PREMIUM,

    /** 퇴직공제부금비(`rtrfundNon`). */
    A_RETIREMENT_MUTUAL_AID_CONTRIBUTION,

    /** 산업안전보건관리비(`sftyMngcst`). */
    A_INDUSTRIAL_SAFETY_HEALTH_COST,

    /** 안전관리비(`sftyChckMngcst`). */
    A_SAFETY_MANAGEMENT_COST,

    /** 품질관리비(`qltyMngcst`) — [A_QUALITY_MANAGEMENT_COST_APPLICABLE] 이 참일 때만 A 에 합산된다. */
    A_QUALITY_MANAGEMENT_COST,

    /**
     * 품질관리비A적용대상여부(`qltyMngcstAObjYn`) — **값이 아니라 술어**다. 이 술어를 무시하고
     * 합산하면 A 를 과대평가해 하한가를 높게 잡는다. 원문(`Y`/`N`)을 그대로 나른다 — 참·거짓
     * 해석은 읽는 쪽이 한다(어휘를 지어내지 않는다).
     */
    A_QUALITY_MANAGEMENT_COST_APPLICABLE,

    /**
     * 표준시장단가금액(`smkpAmt`) — 예규 원문의 A 일곱 항목 열거에 **없다**. 응답이 적용 여부
     * 술어를 함께 주므로 별도 규율로 합산되는 경로가 있다는 뜻이지만 그 근거 예규 문면은 아직
     * 확보되지 않았다 — 관측값으로만 나른다(합산 판단은 이 계약의 몫이 아니다).
     */
    A_STANDARD_MARKET_UNIT_PRICE_AMOUNT,

    /** 표준시장단가금액A적용대상여부(`smkpAmtYn`) — [A_QUALITY_MANAGEMENT_COST_APPLICABLE] 과 같은 술어 축. */
    A_STANDARD_MARKET_UNIT_PRICE_APPLICABLE,

    /**
     * 입찰가격산식A공개일시(`bidPrceCalclAOpenDt`) — 공고게시와 **다른 시각**이다(문서 샘플은
     * 27일 차이). 전략이 A 를 **입력**으로 쓰려면 이 시각이 투찰 마감 이전이어야 한다
     * (D-6G-13 ⑥ — 투찰 시점에 알 수 없는 값을 입력으로 쓰면 누출이다). 실제 하한가 **채점**은
     * 사후 값을 써도 된다(채점은 정의상 개찰 결과로 한다).
     */
    BID_PRICE_FORMULA_A_DISCLOSED_AT,

    // M6/6G D-6G-19 — 기초금액 조회(op 5·6·7) 축.

    /**
     * 예비가격범위시작률(`rsrvtnPrceRngBgnRate`) — **단위 %**이고 값이 **부호를 문자열로 달고 온다**
     * (`-3`). S0 의 반폭 `h` 가 이 값에서 나온다 — 상수 2%·3% 가 아니라 공고별 필드다(P-5 §3.2).
     */
    RESERVE_PRICE_RANGE_BEGIN_RATE,

    /** 예비가격범위종료율(`rsrvtnPrceRngEndRate`) — 선행 `+` 를 달고 온다(`+3`). 시작률의 반대수라는 보장이 없다. */
    RESERVE_PRICE_RANGE_END_RATE,

    /**
     * 기초금액공개일시(`bssamtOpenDt`) — 「그 기초금액이 **언제** 공개되었나」. 전략 입력으로 쓰려면 이
     * 시각이 입찰 마감 이전이어야 한다(D-6G-19 provenance 분리 — 투찰 시점에 모르는 값은 입력이 아니다).
     */
    BASE_AMOUNT_DISCLOSED_AT,

    /** 입찰가격산식A여부(`bidPrceCalclAYn`, **공사 전용·필수**) — 「이 공고가 A값 공고인가」를 한 필드로 답한다. */
    BID_PRICE_FORMULA_A_APPLICABLE,

    /** 기초금액순공사비(`bssAmtPurcnstcst`, 공사 전용) — 순공사원가 98% 배제 규칙(D-6G-13 ⑨)의 입력. */
    PURE_CONSTRUCTION_COST,

    /**
     * 산업안전보건관리비의 **물품 표기**(`industSftyHelthMngcst`) — 공사·용역의 `sftyMngcst` 와 같은 축을
     * 재지만 raw 키가 다르다. [A_INDUSTRIAL_SAFETY_HEALTH_COST] 와 개념을 **공유하지 않는다**: 개념을
     * 공유하면 개념의 첫 계약만 읽는 소비 경로가 물품 응답에서 조용히 `null` 을 낸다.
     */
    A_INDUSTRIAL_SAFETY_HEALTH_COST_GOODS,

    // M6/6G D-6G-22 — 새 호출 비용 0(공고 목록 응답에 이미 온다). 값 어휘·채움률은 실측해 판정문에 공시한다.

    /** 낙찰방법적용기준(`sucsfbidMthdAppStd`, v1.2 추가) — 자유텍스트. 문서 XML 예제는 전부 빈 값이다. */
    AWARD_METHOD_APPLICATION_STANDARD,

    /** 적용기준내용(`aplBssCntnts`, 공사 전용) — 자유텍스트. 문서 표본 둘이 모두 같은 값이라 어휘를 모른다. */
    APPLICATION_BASIS_CONTENT,
}
