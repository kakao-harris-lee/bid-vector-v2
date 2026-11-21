package bidvector.app.http

import bidvector.workflow.strategy.Clock
import java.time.Duration
import java.time.Instant

/**
 * 상한이 선 사실의 **한 줄 출구**(`AccountCommandLog` 관례) — 조립이 로거를 꽂고 이 층은 로거를
 * 모른다. **식별 값을 싣지 않는다**: 원격 주소도 아이디도 문면에 없고, 사유와 창 계수만 나간다.
 */
fun interface SignUpThrottleLog {
    fun write(line: String)
}

/**
 * 열린 가입의 **남용 상한**(M15/15B) — 한 원격 주소가 창 하나 동안 받는 가입 **시도** 수를 센다.
 *
 * **세는 것은 성공이 아니라 시도다.** 「만들어진 계정 N건」으로 세면 틀린 입력·이미 쓰는 이름으로
 * 오는 요청이 **무한정** bcrypt 를 돌린다(cost 12 는 한 번에 수백 ms 다) — 무인증으로 닿는
 * 자리에서 그것이 곧 증폭이다. 그 순서(상한이 KDF 보다 앞)는 `AccountSignUpEndpointTest` 의
 * 「무효 시도가 칸을 먹는다」 test 가 잠근다.
 *
 * **키가 `remoteAddr` 이고, 프록시 뒤에서는 그 값이 하나다**(code-review r1 H-1). 그래서 상한은
 * 사실상 **전역**이고, 싸구려 요청 N건이면 유일한 가입 경로가 창 하나 동안 닫힌다. 이 slice 가
 * 그 비용에 두는 손잡이는 둘이다: ⓐ 수를 **설정 속성**으로 빼 운영자가 올릴 수 있게 하고
 * ([SignUpThrottleProperties]) ⓑ 거부가 **로그 한 줄**을 남겨 「가입이 안 된다」가 전역 잠금인지
 * 고장인지 구별되게 한다. 신뢰 프록시 + `X-Forwarded-For` 로 키를 IP 로 되돌리는 일은 이 slice
 * 밖이고 `OPEN-M15-SIGNUP-HARDENING` 이 받는다 — 그 설정 없이 그 헤더를 믿으면 공격자가 값을
 * 지어 상한을 **0 으로** 만든다(둘 가운데 닫히는 쪽을 골랐다).
 *
 * **테스트 단계 상한이다.** 계수가 **프로세스 메모리**에만 있으므로 재기동이면 초기화되고
 * 인스턴스가 둘이면 각자 센다. 영속 rate limit 은 같은 OPEN 이 받는다.
 *
 * **이 층에 사는 이유**: 키가 원격 주소라 도메인 어휘가 아니다. use case 는 「누가 계정을 만들 수
 * 있는가」만 알고, 「같은 주소에서 몇 번인가」는 HTTP 가 아는 사실이다.
 */
class SignUpThrottle(
    private val clock: Clock,
    private val attemptsPerWindow: Int,
    private val window: Duration,
    private val trackedAddressLimit: Int,
    private val log: SignUpThrottleLog,
) {
    init {
        require(attemptsPerWindow >= 1) { "가입 상한은 1 이상이어야 한다: $attemptsPerWindow" }
        require(window.isPositive()) { "가입 창은 양수여야 한다: $window" }
        require(trackedAddressLimit >= 1) { "추적 주소 상한은 1 이상이어야 한다: $trackedAddressLimit" }
    }

    /**
     * 서블릿 컨테이너가 요청을 동시에 돌리므로 계수는 **한 번에 하나**만 본다. 잠금을 객체 하나로
     * 두고 `synchronized` 블록만 쓴다 — 창 안의 일은 목록 몇 개를 훑는 것뿐이라 경합이 짧다.
     */
    private val lock = Any()

    /**
     * 창 안에 시도를 가진 주소만 산다 — 창 밖 기록은 매 호출에서 걷고, 비워진 항목은 지운다.
     * 그래서 이 맵의 크기는 「창 안에 활동한 주소 수」이고 조용히 자라지 않는다.
     */
    private val attempts: MutableMap<String, MutableList<Instant>> = LinkedHashMap()

    /**
     * 이 주소의 시도 하나를 받아들일 수 있는가. 받아들이면 **그 시각을 적는다** — 호출자가 따로
     * 적을 자리가 없어야 「세지 않고 지나가는」 경로가 생기지 않는다.
     *
     * **기억이 모자라면 거부한다**(code-review r1 N-3, fail-closed). 앞 판은 상한을 넘으면 가장
     * 오래 전에 **처음 본** 주소를 버렸고, 그 축출이 두 가지를 동시에 했다: ⓐ 상한에 걸린 주소의
     * 창을 **지워 풀어 주고** ⓑ 순서가 삽입 순이라 **창 안에서 계속 시도하는 주소가 1순위**로
     * 버려졌다(조용한 주소가 더 오래 살았다). 상한이 지키려는 방향의 반대다. 오늘은 프록시 뒤라
     * 키가 하나뿐이지만, 키 축을 IP 로 되돌리는 라운드(`OPEN-M15-SIGNUP-HARDENING` ④)가 그
     * 우회를 여는 라운드가 된다 — 그래서 **그 전에** 닫는다.
     *
     * 닫은 모양: 이미 세고 있는 주소는 **언제나** 그 창으로 판정하고, **모르는 주소**는 기억이
     * 찼을 때 거부한다. 기억이 모자란 것이 상한을 **푸는** 이유가 되지 않는다.
     */
    fun admit(address: String): Boolean {
        // 로그는 잠금 **밖**에서 쓴다(cr r1 N-6) — 로거가 막히면 그동안 모든 가입 시도가 이
        // 잠금에 줄을 서고, 하필 공격 중에 가장 많이 도는 경로가 거부 경로다. 줄은 잠금 안에서
        // 만든다(계수가 그 순간의 값이어야 한다).
        val refusal: String?
        val admitted: Boolean
        synchronized(lock) {
            val now = clock.now()
            forget(now.minus(window))
            val recorded = attempts[address]
            refusal = refusalOf(recorded)
            admitted = refusal == null
            if (admitted) {
                (recorded ?: mutableListOf<Instant>().also { attempts[address] = it }) += now
            }
        }
        refusal?.let { log.write(it) }
        return admitted
    }

    /**
     * 이 시도를 거부해야 하는 **사유 문면**, 받아들일 수 있으면 `null`. 거부는 **기록하지 않는다**
     * — 거부가 창을 밀면 상한이 「창 동안 조용히 있어야 풀리는」 다른 규칙이 된다(계약은 그것을
     * 말하지 않았다).
     *
     * 사유 값에 **공백을 두지 않는다**(r3 CR L-1) — 저장소의 cause 어휘는 전부 붙임표이고
     * (`account-absent`·`consecutive-account-failures`·`audience-unavailable:<클래스>`), 같은 줄이
     * `limit=` 도 실으므로 공백으로 토막내는 파서에서 값의 뒷조각이 다음 필드와 섞인다.
     */
    private fun refusalOf(recorded: MutableList<Instant>?): String? =
        when {
            recorded == null && attempts.size >= trackedAddressLimit -> {
                line("tracked-address-capacity", recorded = 0)
            }

            recorded != null && recorded.size >= attemptsPerWindow -> {
                line("window-limit", recorded.size)
            }

            else -> {
                null
            }
        }

    /**
     * 운영자가 읽는 한 줄 — **사유와 계수만**이다. 주소·아이디는 싣지 않는다(그 둘이 이 줄의
     * 쓸모를 늘리지 않으면서 로그를 식별 정보로 만든다).
     */
    private fun line(
        cause: String,
        recorded: Int,
    ): String =
        "signup throttle refused cause=$cause limit=$attemptsPerWindow window=$window " +
            "recorded=$recorded tracked-addresses=${attempts.size}"

    private fun forget(floor: Instant) {
        attempts.values.forEach { recorded -> recorded.removeAll { at -> !at.isAfter(floor) } }
        attempts.entries.removeAll { entry -> entry.value.isEmpty() }
    }
}
