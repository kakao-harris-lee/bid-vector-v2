package bidvector.app.http

/**
 * **계정 주체가 있어야 닿는 경로** — 선언을 한 자리에 둔다.
 *
 * 왜 선언이 필요한가: 7D 의 기본 거부 필터는 「인증됐는가」까지만 보고 **어떤 주체인가**는
 * 보지 않는다. 인증 경로는 둘인데(운영자 자격 헤더 · 브라우저 세션) **계정을 내는 것은
 * 세션뿐이다** — 자격 헤더 경로의 주체는 `operator` 이고 계정 행이 없다. 소유 경계(R-2)를
 * 세우려면 `account.id` 가 있어야 하므로 이 경로들은 세션으로만 닿는다.
 *
 * 그 사실이 **컨트롤러 안에만** 있으면(각자 `SessionAccount.of(...) ?: 401`) 표면 전수 test 가
 * 「이 매핑은 왜 401 인가」를 알 수 없다. 그래서 판정이 읽는 값을 한 자리에 두고,
 * `@RequestMapping` 도 이 상수에서 경로를 받는다 — 경로를 두 곳에 적으면 갈린다.
 *
 * **알려진 제한**: 자격 헤더만 쓰는 소비자(운영 스크립트 등)는 이 경로들에 닿지 못한다. 그
 * 경로에 계정을 묶는 일은 **R-2 남은 절반**(식별자 모델)이 풀 때까지 열리지 않는다.
 *
 * ---
 * **머지 주의(M7/7C③ · 운영자 결정 D-7C3-1 ①)**: 같은 M7 라운드의 **7B 가 이 파일을 먼저
 * 세운다**(뿌리 `/api/bid-cases`). 두 레인이 같은 파일을 만들므로 머지에서 충돌하고, **뒤에
 * rebase 하는 레인이 두 뿌리를 합친다** — 선언은 끝까지 **하나**여야 한다(7B 의 표면 전수
 * test 와 문서 계약 test 가 「계정 전용 == 세션 축 − 자격 축」을 이 집합과 맞대기 때문에,
 * 선언이 둘이면 그 대조가 반쪽만 본다). 7C③ 는 7B 뒤에 머지되는 쪽이므로 rebase 에서
 * `BID_CASES` 를 되살리고 `ROOTS` 를 둘로 만든다.
 */
object AccountScopedRoutes {
    /** 알림함의 뿌리(M7/7C③) — `NotificationInboxController` 의 `@RequestMapping` 이 쓴다. */
    const val NOTIFICATIONS: String = "/api/notifications"

    /** 계정 전용 뿌리 전수. 늘어나면 여기만 늘린다. */
    val ROOTS: Set<String> = setOf(NOTIFICATIONS)

    /** 이 경로가 계정 주체를 요구하는가 — 표면 전수 test 가 선언↔판정 대조에 쓴다. */
    fun covers(path: String): Boolean = ROOTS.any { it == path || path.startsWith("$it/") }
}
