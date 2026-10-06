package bidvector.workflow.event

/**
 * 소비자 상호 배제 port(ADR 0005 D-10, D-6F10-11·14) — 같은 [OutboxConsumerKind]를 두
 * 프로세스가 동시에 소비하지 않게 한다. 구현은 `adapters`(PostgreSQL 세션 advisory lock).
 *
 * **요구는 「홀더가 죽으면 즉시 해제」다**(D-10 ②) — TTL 이 아니다. 고아 판정이
 * 「lease 를 **새로** 잡은 소비자가 첫 claim 전에 보는 `CLAIMED` 전부」라는 **구조적 사실**에
 * 기대기 때문이다(시각 임계가 없다). 임대가 홀더 사망 뒤에도 남으면 그 사실이 거짓이 되고,
 * 반대로 살아 있는 홀더의 임대를 빼앗으면 in-flight 행이 격리된다.
 *
 * **토큰 타입을 두지 않는다** — [withLease]가 받는 [body]는 use case 자신의 private 함수를
 * 감싼 클로저다. 「임대 없이 본문을 부르는 어댑터」는 4A 식 통로 토큰으로 막을 수 없다:
 * 토큰의 `internal constructor`를 어댑터가 만들어야 하므로 그 모순이 그대로 남는다. 그런
 * 어댑터는 `JdbcOutboxPort`가 `SKIP LOCKED`를 빼는 것과 같은 층(정직하지 않은 구현)이고,
 * 그 층의 권한은 배선 주체가 이미 가진 것이다 — 그래서 이 port 는 **클로저 경계**로 닫고
 * 실제 상호 배제는 실 DB 두 연결로 측정한다(`PostgresAdvisoryLockLeaseTest`).
 *
 * `fun interface` 가 아니다 — Kotlin 의 SAM 변환은 **메서드 자신의 타입 매개변수**를
 * 지원하지 않는다(`ConnectionSource` 가 같은 이유로 평범한 `interface` 다). 구현은 이름
 * 있는 클래스로 쓴다.
 *
 * [LeaseAttempt.Busy]는 **값**이다 — 조용히 사라지지 않는다(D-10 ③ 「억제가 관측 가능한
 * 결과」). 호출부는 그 값을 보고서와 종료 코드로 올린다.
 */
interface ConsumerLeasePort {
    fun <T> withLease(
        kind: OutboxConsumerKind,
        body: (LeaseGuard) -> T,
    ): LeaseAttempt<T>
}

/**
 * 본문이 **도중에** 임대를 다시 묻는 자리(R1-M-1) — 「쥐었다」가 run 내내 참이라고 가정하지
 * 않는다.
 *
 * 왜 필요한가: advisory lock 은 홀더의 **연결**이 끊기면 서버가 즉시 놓는다. 그 해제는
 * 프로세스가 죽었을 때만 일어나는 것이 아니다 — `pg_terminate_backend` 나 네트워크 단절로
 * **살아 있는 홀더의 연결만** 끊겨도 일어난다. 그러면 다음 relay 가 임대를 쥐고, 첫째가
 * 아직 발송 중인 `CLAIMED` 를 「고아」로 읽어 격리한다(verifier probe V6b 실측: 발송된 행이
 * `ISOLATED` 로 표기되고 첫째 배치의 미발송 행은 놓친다).
 *
 * 그래서 relay 는 **행마다 발송 전에** 이것을 묻고, 거짓이면 남은 행을 건드리지 않고
 * 멈춘다. 중복 발송을 막는 것이 아니라(at-most-once 는 그대로다) **자기가 더 이상
 * 배타적이지 않다는 것을 알고 멈추는 것**이다.
 */
fun interface LeaseGuard {
    /** 임대를 **아직** 쥐고 있는가. 거짓이면 이 소비자는 더 이상 배타적이지 않다. */
    fun stillHeld(): Boolean
}

/** [ConsumerLeasePort.withLease]의 결과 — 「쥐었고 본문이 났다」와 「못 쥐었다」 둘뿐이다. */
sealed interface LeaseAttempt<out T> {
    data class Held<out T>(
        val result: T,
    ) : LeaseAttempt<T>

    /** 다른 홀더가 쥐고 있다 — [body]는 **부르지 않았다**(claim 0, 격리 0). */
    data object Busy : LeaseAttempt<Nothing>
}
