package bidvector.procurement

/**
 * 공용 `presentIn` 집합 — `CollectionPolicy.kt`에서 분리한 파일이다(sizeGate 500줄,
 * v2-지침서 §5 — M3/3H-1 발주기관 넷 추가로 그 파일이 한도를 넘겨 이 상수를 옮겼다.
 * `KonepsOpeningCompleteFieldContracts.kt` 분리와 같은 전례). `internal`(`private`에서
 * 완화) — 같은 모듈의 `CollectionPolicy.kt`가 소비한다.
 *
 * `bidNtceNo`·`bidNtceOrd` 공용 presentIn — 공고 식별자라 개찰 축 세 엔드포인트(F-6)·
 * license-limit(G-4, §1.9.5)·개찰완료(P-13)에도 실린다(어느 응답이든 「어느 공고의
 * 행인가」 없이 오지 않는다). 좁히면 개찰 축 allow-list 강제가 이 필드부터 떨어뜨려 전
 * 항목이 「공고번호 없음」으로 오분류된다. 여러 행이 이 집합을 그대로 반복해 `cpdCheck`가
 * 중복으로 잡아 값 객체로 뽑았다(§5 중복 금지).
 */
internal val NOTICE_IDENTIFIER_PRESENT_IN: Set<SourceEndpoint> =
    setOf(
        SourceEndpoint.NOTICE_LIST,
        SourceEndpoint.OPENING_AWARD_LIST,
        SourceEndpoint.OPENING_RESULT_LIST,
        SourceEndpoint.RESERVE_PRICE_DETAIL,
        SourceEndpoint.LICENSE_LIMIT_DETAIL,
        SourceEndpoint.OPENING_COMPLETE,
        // M6/6G D-6G-12 — 입찰가격산식 A 정보도 「어느 공고의 행인가」를 필수 항목으로 싣는다.
        SourceEndpoint.BID_PRICE_FORMULA_A,
        // M6/6G D-6G-19 — 기초금액 조회도 공고번호·차수를 응답 필수 항목으로 싣는다.
        SourceEndpoint.BASE_AMOUNT_DETAIL,
    )

/**
 * A 합산 항목 가운데 **입찰가격산식 A 정보(op 24)와 기초금액 조회(op 5·6·7) 양쪽에 오는** 키의 presentIn
 * (M6/6G D-6G-19, P-5 §3.1 전수표). 같은 raw 키를 두 행으로 등재할 수 없으므로(레지스트리가 중복을
 * 거부한다) 한 행의 presentIn 을 넓힌다. 물품은 산업안전보건관리비의 **이름이 다르고**
 * (`industSftyHelthMngcst`) 노인장기요양보험료·안전관리비가 **없다** — presentIn 은 엔드포인트 축이라
 * 업무별 부재는 값의 부재로 나타난다.
 */
internal val A_ITEM_PRESENT_IN: Set<SourceEndpoint> =
    setOf(SourceEndpoint.BID_PRICE_FORMULA_A, SourceEndpoint.BASE_AMOUNT_DETAIL)
