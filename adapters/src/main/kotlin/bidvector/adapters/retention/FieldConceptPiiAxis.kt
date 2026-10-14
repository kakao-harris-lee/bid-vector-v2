package bidvector.adapters.retention

import bidvector.procurement.FieldConcept

/**
 * **등재 개념의 PII 축 분류** — 「어느 계약 개념이 업체 식별을 나르는가」를 **한 자리**에 선언한다
 * (D-6B3-5 ①, privacy F-6·F-7 · vr R2-F1).
 *
 * 왜 분류가 **분할**이어야 하는가: 앞 판은 지울 개념을 운영자 결정이 이름으로 부른 **열거**로 두었고,
 * 그래서 셋째 운반자(`OPENING_COMPANY_INFO`)가 조용히 남았다 — 마스킹 test 전부가 같은 두 개념에서
 * 키를 끌어왔으므로 「선언한 것은 지운다」만 재고 **「나르는 것을 다 선언했는가」**를 재지 않았다.
 * 그 사각의 대가는 비싸다: 한 번 마스킹된 행은 표지가 찍혀 **다시 마스킹할 수 없다**(V18 관문 ④).
 *
 * 그래서 [COMPANY_IDENTITY_CONCEPTS] 와 [NON_PII_CONCEPTS] 는 `FieldConcept` 전수의 **분할**이고
 * (`FieldConceptPiiAxisTest` 가 합집합 등식과 서로소를 잰다), 새 개념은 **분류 없이는 빌드를 지나지
 * 못한다**. 분류하려면 사람이 그 개념의 값 모양을 보게 되고, 그것이 이 게이트의 전부다 —
 * `notice` 열 축에 세운 등식(`NOTICE_SNAPSHOT_CLASSIFIED_COLUMNS`)과 같은 꼴이다.
 */
internal val COMPANY_IDENTITY_CONCEPTS: Set<FieldConcept> =
    setOf(
        // 투찰업체명(`prcbdrNm`) — 개찰완료 축의 투찰자 상호.
        FieldConcept.BID_COMPANY_NAME,
        // 최종낙찰업체명(`bidwinnrNm`) — 낙찰 목록의 상호.
        FieldConcept.AWARD_COMPANY_NAME,
        // 개찰결과 목록의 합성 문자열(`opengCorpInfo`) — 수집 시점 마스킹이 사업자등록번호·대표자명을
        // 버리고 **상호·투찰금액·투찰율**을 남긴다. 성분 0 이 상호이므로 이 개념도 업체 식별을 나른다.
        // 성분만 떼지 않고 **키 전체를 지운다**: 이 slice 의 fail-closed 관례이고, 보존 경로에서
        // 위치 추측은 금지다(합성 형태가 바뀌면 조용히 상호가 남는다).
        FieldConcept.OPENING_COMPANY_INFO,
    )

/**
 * PII 를 나르지 않는 개념 전수 — 분할의 다른 쪽이다. **열거라야 한다**: 「나르지 않는다」가 개념마다
 * 적혀 있지 않으면 분류 누락과 「검토해 보니 아니다」가 구별되지 않는다.
 *
 * 자연인 축(담당자 이름·전화·전자우편 · 사업자등록번호 · 대표자명)은 **계약에 등재되지 않는다**
 * (D-3H-7) — 그래서 이 목록에도, 위 목록에도 없다. 그 부재는 `FieldContractTest` 와 마스킹의
 * allow-list 반전이 함께 진다.
 */
internal val NON_PII_CONCEPTS: Set<FieldConcept> =
    setOf(
        // 공고·입찰 식별자와 차수 축
        FieldConcept.NOTICE_NUMBER,
        FieldConcept.NOTICE_ROUND,
        FieldConcept.REBID_NUMBER,
        FieldConcept.BID_CLASSIFICATION_NUMBER,
        FieldConcept.RESERVE_PRICE_SEQUENCE,
        FieldConcept.LICENSE_LIMIT_GROUP_NUMBER,
        FieldConcept.LICENSE_LIMIT_SEQUENCE_NUMBER,
        FieldConcept.OPENING_RANK,
        FieldConcept.DRAW_NUMBER,
        FieldConcept.DRAW_FLAG,
        FieldConcept.PARTICIPANT_COUNT,
        // 공공기관 식별자 — **기관**의 이름·코드이고 자연인도 사업자 상호도 아니다. 그 값이 사는
        // `notice` 는 90일 파기 대상이고 `notice_audit` 스냅숏은 열 집합 등식이 따로 잠근다.
        FieldConcept.DEMAND_AGENCY_CODE,
        FieldConcept.DEMAND_AGENCY_NAME,
        FieldConcept.NOTICE_AGENCY_CODE,
        FieldConcept.NOTICE_AGENCY_NAME,
        // 분류·라벨·자유텍스트 — 상류 의미상 담당자 값이 아니다(privacy r2 의 전수 분류와 같은 판정).
        FieldConcept.BUSINESS_CATEGORY_CODE,
        FieldConcept.BUSINESS_CATEGORY_LABEL,
        FieldConcept.PUBLIC_PROCUREMENT_CLASS_CODE,
        FieldConcept.PUBLIC_PROCUREMENT_CLASS_NAME,
        FieldConcept.SERVICE_DIVISION,
        FieldConcept.MAIN_CONSTRUCTION_TYPE,
        FieldConcept.PROGRESS_DIVISION,
        FieldConcept.NOTICE_TITLE,
        FieldConcept.AWARD_METHOD_CODE,
        FieldConcept.AWARD_METHOD_NAME,
        FieldConcept.AWARD_METHOD_APPLICATION_STANDARD,
        FieldConcept.APPLICATION_BASIS_CONTENT,
        FieldConcept.PLANNED_PRICE_DECISION_METHOD,
        FieldConcept.CONSTRUCTION_CAPACITY_REQUIREMENT,
        // 금액·율 축
        FieldConcept.BASE_AMOUNT,
        FieldConcept.ESTIMATED_AMOUNT,
        FieldConcept.ALLOCATED_BUDGET,
        FieldConcept.AWARD_AMOUNT,
        FieldConcept.BID_AMOUNT,
        FieldConcept.BID_RATE,
        FieldConcept.WINNING_RATE,
        FieldConcept.FLOOR_RATE,
        FieldConcept.RESERVE_PRICE,
        FieldConcept.RESERVE_PRICE_PRELIMINARY,
        FieldConcept.RESERVE_PRICE_RANGE_BEGIN_RATE,
        FieldConcept.RESERVE_PRICE_RANGE_END_RATE,
        FieldConcept.PURE_CONSTRUCTION_COST,
        // A값 구성 축
        FieldConcept.A_NATIONAL_PENSION_PREMIUM,
        FieldConcept.A_HEALTH_INSURANCE_PREMIUM,
        FieldConcept.A_LONG_TERM_CARE_INSURANCE_PREMIUM,
        FieldConcept.A_RETIREMENT_MUTUAL_AID_CONTRIBUTION,
        FieldConcept.A_INDUSTRIAL_SAFETY_HEALTH_COST,
        FieldConcept.A_INDUSTRIAL_SAFETY_HEALTH_COST_GOODS,
        FieldConcept.A_SAFETY_MANAGEMENT_COST,
        FieldConcept.A_QUALITY_MANAGEMENT_COST,
        FieldConcept.A_QUALITY_MANAGEMENT_COST_APPLICABLE,
        FieldConcept.A_STANDARD_MARKET_UNIT_PRICE_AMOUNT,
        FieldConcept.A_STANDARD_MARKET_UNIT_PRICE_APPLICABLE,
        FieldConcept.BID_PRICE_FORMULA_A_APPLICABLE,
        FieldConcept.BID_PRICE_FORMULA_A_DISCLOSED_AT,
        FieldConcept.BASE_AMOUNT_DISCLOSED_AT,
        // 시각 축
        FieldConcept.NOTICE_POSTED_AT,
        FieldConcept.DEADLINE_AT,
        FieldConcept.BID_AT,
        FieldConcept.OPENING_SCHEDULED_AT,
        FieldConcept.ACTUAL_OPENING_AT,
        FieldConcept.FINAL_AWARD_DATE,
    )
