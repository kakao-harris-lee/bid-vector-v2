package bidvector.app.http

/**
 * **계정 주체가 있어야 닿는 경로**(M7/7B · 7C③ · 7C④ 2b) — 입찰함 · 알림함 · 프로필 셋이다.
 *
 * 왜 선언이 필요한가: 7D 의 기본 거부 필터는 「인증됐는가」까지만 보고 **어떤 주체인가**는
 * 보지 않는다. 인증 경로는 둘인데(운영자 자격 헤더 · 브라우저 세션) **계정을 내는 것은
 * 세션뿐이다** — 자격 헤더 경로의 주체는 `operator` 이고 계정 행이 없다. 소유 경계(R-2)를
 * 세우려면 `account.id` 가 있어야 하므로, 이 뿌리들은 세션 경로로만 닿는다.
 *
 * 그 사실이 **컨트롤러 안에만** 있으면(각자 `request.authenticatedAccount() ?: 401`) 표면 전수 test 가
 * 「이 매핑은 왜 401 인가」를 알 수 없다. 그래서 판정이 읽는 값을 한 자리에 두고,
 * `@RequestMapping` 도 이 상수에서 경로를 받는다 — 경로를 두 곳에 적으면 갈린다.
 *
 * **알려진 제한**: 자격 헤더만 쓰는 소비자(운영 스크립트 등)는 이 뿌리들에 닿지 못한다.
 * 그 경로에 계정을 묶는 일은 **R-2 남은 절반**(식별자 모델)이 풀 때까지 열리지 않는다
 * (운영자 결정 2026-10-21 · 계약 r2 D-7B-1).
 */
object AccountScopedRoutes {
    /** 입찰함의 뿌리 — 두 controller 의 `@RequestMapping` 이 이 값을 쓴다. */
    const val BID_CASES: String = "/api/bid-cases"

    /** 알림함의 뿌리(M7/7C③) — `NotificationInboxController` 의 `@RequestMapping` 이 쓴다. */
    const val NOTIFICATIONS: String = "/api/notifications"

    /**
     * 프로필의 뿌리(M7/7C④ 2b) — 조회 · 쓰기 두 controller 가 이 값을 쓴다.
     *
     * **V12 자격 사실(싱글턴)의 편집도 이 아래에 선다.** 그 표는 아직 계정 단위가 아니지만, 편집을
     * 계정 밖 경로에 두면 「누가 고쳤는가」가 자격 헤더 주체로도 열린다 — 더 좁은 쪽을 고른다.
     */
    const val PROFILE: String = "/api/profile"

    /**
     * 프로필의 하위 경로 — **두 controller 와 OpenAPI 가 같은 문자열**을 쓴다. 뿌리와 같은 자리에 두는
     * 이유도 같다: 경로를 두 파일에 적으면 갈린다(r2 CR L-6 — 앞 판은 넷이 두 controller 파일에 흩어져
     * 있었고 KDoc 은 한쪽에만 붙어 있었다).
     */
    const val PROFILE_QUALIFICATION_FACTS: String = "/qualification-facts"
    const val PROFILE_BUSINESS_DETAILS: String = "/business-details"
    const val PROFILE_CAPACITIES: String = "/construction-capacities"
    const val PROFILE_CAPACITIES_REMOVE: String = "$PROFILE_CAPACITIES/remove"

    /**
     * 시작하기 체크리스트의 뿌리(M7/7C④-3, D-7C4C-7) — **프로필 아래가 아니다.**
     *
     * 판정의 입력 넷 가운데 둘(감시 규칙 · 시계)이 프로필 밖이라, `/api/profile/…` 아래 두면 뒤에 이
     * 경로를 고치는 사람이 「프로필 저장만의 함수」로 읽는다 — 전략을 보지 않고 응답을 고치는 변경이
     * 그 오독에서 나온다.
     */
    const val ONBOARDING_CHECKLIST: String = "/api/onboarding-checklist"

    /**
     * 내 입찰 결과의 뿌리(M7/7C⑤-b) — **입찰함 아래가 아니다.**
     *
     * [BID_CASES] 아래 두면 그 뿌리의 GET 셋과 한 묶음으로 읽히는데, 그 셋은 마감 정산이
     * 타임라인에 **쓴다**(D-7B-4 결정 ⑦). 이 조회는 쓰기가 0 이고(D-7C5B-8) 그 차이가 경로에서
     * 보여야 한다 — 뒤에 이 자리를 고치는 사람이 「입찰함 조회니까 정산을 지나겠지」로 읽지
     * 않게([ONBOARDING_CHECKLIST] 를 프로필 아래 두지 않은 것과 같은 판단).
     */
    const val BID_RESULTS: String = "/api/bid-results"

    /** 계정 전용 뿌리 전수. 늘어나면 여기만 늘린다. */
    val ROOTS: Set<String> = setOf(BID_CASES, BID_RESULTS, NOTIFICATIONS, PROFILE, ONBOARDING_CHECKLIST)

    /** 이 경로가 계정 주체를 요구하는가 — 표면 전수 test 가 선언↔판정 대조에 쓴다. */
    fun covers(path: String): Boolean = ROOTS.any { it == path || path.startsWith("$it/") }
}
