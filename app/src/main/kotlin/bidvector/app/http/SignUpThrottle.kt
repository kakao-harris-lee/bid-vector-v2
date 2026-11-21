package bidvector.app.http

import bidvector.workflow.access.SignUpPolicyData
import bidvector.workflow.strategy.Clock
import java.time.Instant

/**
 * 열린 가입의 **남용 상한**(M15/15B) — 같은 원격 주소에서 창 하나 동안 받는 가입 시도 수를 센다.
 *
 * **테스트 단계 상한이다.** 계수가 **프로세스 메모리**에만 있으므로 ⓐ 재기동하면 초기화되고
 * ⓑ 인스턴스가 둘이면 각자 센다. 영속 rate limit 은 범위 밖이고 상용 전 재결정 항목이다
 * (`OPEN-M15-SIGNUP-HARDENING`).
 *
 * **키가 `remoteAddr` 다.** 역방향 프록시 뒤에서는 그 값이 하나라 상한이 사실상 전역이 된다 —
 * `X-Forwarded-For` 를 읽지 **않는** 것이 그 대가의 반대쪽이다: 신뢰 프록시 설정이 없는 상태에서
 * 그 헤더를 믿으면 공격자가 요청마다 값을 지어 상한을 **0 으로** 만든다. 둘 가운데 닫히는 쪽을
 * 고른다(fail-closed).
 *
 * **세는 것은 성공이 아니라 시도다.** 「만들어진 계정 10건」으로 세면 틀린 입력·이미 쓰는 이름으로
 * 오는 요청이 **무한정** bcrypt 를 돌린다(cost 12 는 한 번에 수백 ms 다) — 무인증으로 닿는 자리에서
 * 그것이 곧 증폭이다. 사람이 쓰는 가입은 한두 번이면 끝나므로 이 선택이 정상 사용을 막지 않는다.
 *
 * **이 층에 사는 이유**: 키가 원격 주소라 도메인 어휘가 아니다. use case 는 「누가 계정을 만들 수
 * 있는가」만 알고, 「같은 주소에서 몇 번인가」는 HTTP 가 아는 사실이다.
 */
class SignUpThrottle(
    private val clock: Clock,
    private val policy: SignUpPolicyData,
) {
    /**
     * 서블릿 컨테이너가 요청을 동시에 돌리므로 계수는 **한 번에 하나**만 본다. 잠금을 객체 하나로
     * 두고 `synchronized` 블록만 쓴다 — 창 안의 일은 목록 몇 개를 훑는 것뿐이라 경합이 짧다.
     */
    private val lock = Any()

    private val attempts: MutableMap<String, MutableList<Instant>> = mutableMapOf()

    /**
     * 이 주소의 시도 하나를 받아들일 수 있는가. 받아들이면 **그 시각을 적는다** — 호출자가 따로
     * 적을 자리가 없어야 「세지 않고 지나가는」 경로가 생기지 않는다.
     *
     * 창 밖으로 나간 기록은 매 호출에서 걷어 낸다 — 그래서 이 맵이 담는 것은 「창 안에 시도가 있는
     * 주소」뿐이고, 조용히 자라지 않는다.
     */
    fun admit(address: String): Boolean =
        synchronized(lock) {
            val now = clock.now()
            forget(now.minus(policy.signUpWindow))
            val recorded = attempts.getOrPut(address) { mutableListOf() }
            if (recorded.size >= policy.signUpsPerAddress) {
                // 거부도 기록하지 않는다 — 거부가 창을 밀면 상한이 「창 동안 조용히 있어야 풀리는」
                // 다른 규칙이 된다(계약은 그것을 말하지 않았다).
                return@synchronized false
            }
            recorded += now
            true
        }

    private fun forget(floor: Instant) {
        attempts.values.forEach { recorded -> recorded.removeAll { at -> !at.isAfter(floor) } }
        attempts.entries.removeAll { entry -> entry.value.isEmpty() }
    }
}
