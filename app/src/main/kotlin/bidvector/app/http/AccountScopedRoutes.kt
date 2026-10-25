package bidvector.app.http

/**
 * **계정 주체가 있어야 닿는 경로**(M7/7B) — 입찰함이 유일하다.
 *
 * 왜 선언이 필요한가: 7D 의 기본 거부 필터는 「인증됐는가」까지만 보고 **어떤 주체인가**는
 * 보지 않는다. 인증 경로는 둘인데(운영자 자격 헤더 · 브라우저 세션) **계정을 내는 것은
 * 세션뿐이다** — 자격 헤더 경로의 주체는 `operator` 이고 계정 행이 없다. 소유 경계(R-2)를
 * 세우려면 `account.id` 가 있어야 하므로, 입찰함은 세션 경로로만 닿는다.
 *
 * 그 사실이 **컨트롤러 안에만** 있으면(각자 `request.authenticatedAccount() ?: 401`) 표면 전수 test 가
 * 「이 매핑은 왜 401 인가」를 알 수 없다. 그래서 판정이 읽는 값을 한 자리에 두고,
 * `@RequestMapping` 도 이 상수에서 경로를 받는다 — 경로를 두 곳에 적으면 갈린다.
 *
 * **알려진 제한**: 자격 헤더만 쓰는 소비자(운영 스크립트 등)는 입찰함에 닿지 못한다.
 * 그 경로에 계정을 묶는 일은 **R-2 남은 절반**(식별자 모델)이 풀 때까지 열리지 않는다
 * (운영자 결정 2026-10-21 · 계약 r2 D-7B-1).
 */
object AccountScopedRoutes {
    /** 입찰함의 뿌리 — 두 controller 의 `@RequestMapping` 이 이 값을 쓴다. */
    const val BID_CASES: String = "/api/bid-cases"

    /** 계정 전용 뿌리 전수. 늘어나면 여기만 늘린다. */
    val ROOTS: Set<String> = setOf(BID_CASES)

    /** 이 경로가 계정 주체를 요구하는가 — 표면 전수 test 가 선언↔판정 대조에 쓴다. */
    fun covers(path: String): Boolean = ROOTS.any { it == path || path.startsWith("$it/") }
}
