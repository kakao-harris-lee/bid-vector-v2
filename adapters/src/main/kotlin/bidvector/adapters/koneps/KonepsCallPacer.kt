package bidvector.adapters.koneps

import java.time.Duration
import java.time.Instant

/**
 * **연속 호출 사이의 최소 간격**(OPS-08 (a)) — 계수 기반 상한 옆의 **시간 축**이다.
 *
 * 왜 계수로 안 되는가: rate limiter 는 「창 하나에 몇 번」을 센다. 창이 바뀌는 순간 앞 창의
 * 마지막 호출과 새 창의 첫 호출이 **붙어** 나갈 수 있고, legacy 에서 429 를 부른 원인이 바로 그
 * 동시성이었다(조사 a-4 — 「~2분 안에 회복, 원인은 동시성」). 계수는 평균을 지키고 간격은
 * 순간을 지킨다. 둘은 다른 보증이다.
 *
 * **기존 라이브러리를 쓰지 않은 근거**(조사 뒤 채택): Resilience4j 의 `RateLimiter` 는 주기 갱신
 * 토큰 버킷이라 위의 창 경계 붙음이 그 모양 그대로 남고(그래서 이 축을 대신하지 못한다), Guava
 * `RateLimiter` 는 간격을 지키긴 하지만 유휴 뒤 버스트를 허용하고 **시계를 주입할 수 없다**
 * (`SleepingStopwatch` 가 package-private). 이 slice 의 acceptance 는 「주입 시계로 간격 위반
 * 없음」이라 시계를 못 주입하는 도구는 요건을 못 만족한다. 그래서 상태 한 칸짜리 이 타입을 둔다.
 *
 * 시계와 기다림을 **둘 다 주입**받는다 — 그래서 test 가 실제로 잠들지 않고, 그 둘이 함수인 것은
 * 관문([KonepsCallGate])이 시계를 함수로 받는 것과 같은 이유다(시계 타입을 받으면 어느 모듈의
 * 시계인지가 경계 문제가 된다).
 *
 * **스레드 안전을 주장하지 않는다.** 수집은 직렬이고(legacy 의 직렬 self-chaining 을 V2 가
 * 계약으로 들었다 — 동시 청크가 과거 429 를 유발했다) 관문 하나를 한 걷기가 차례로 쓴다.
 * 동시 호출이 생기는 날 필요한 것은 이 칸의 잠금이 아니라 **직렬성 계약의 재검토**다.
 *
 * **보장은 프로세스 전역이다**(cr r1 권고). 출하 배선은 관문을 한 자리에서 만들고 그 관문이 이 칸
 * 하나를 쥐므로, 간격은 소스 하나가 아니라 **그 프로세스의 모든 KONEPS 호출**에 걸린다. rate
 * limiter 가 소스 어댑터마다 하나인 것과 다른 성질이고, 소스가 여럿인 실행에서 집계 속도가
 * 1/간격으로 묶이는 것이 그 차이의 값이다.
 */
class KonepsCallPacer(
    private val minimumInterval: Duration,
    private val now: () -> Instant,
    private val waitFor: (Duration) -> Unit,
) {
    init {
        require(!minimumInterval.isNegative) { "최소 호출 간격은 음수일 수 없다: $minimumInterval" }
    }

    private var nextAllowedAt: Instant? = null

    /**
     * 이번 호출이 나갈 수 있을 때까지 기다린다 — 간격이 이미 지났으면 기다림이 **0 회**다
     * (0 을 기다리라고 부르지 않는다: 그러면 「기다렸다」와 「기다릴 것이 없었다」가 같은 값이 된다).
     *
     * 다음 하한을 **기다린 뒤의 시각**에서 센다. 기다리기 전 시각에서 세면 쉼 없는 연속 호출에서
     * 하한이 뒤로 밀리지 않아 간격이 겹쳐 쌓이고, 반대로 시계가 멈춘 환경에서는 매 호출이 간격을
     * 꽉 기다린다 — 둘 다 선언값과 다른 거동이다.
     */
    fun awaitTurn() {
        val at = now()
        val earliest = nextAllowedAt
        if (earliest != null && earliest.isAfter(at)) {
            waitFor(Duration.between(at, earliest))
        }
        nextAllowedAt = maxOf(at, earliest ?: at).plus(minimumInterval)
    }
}
