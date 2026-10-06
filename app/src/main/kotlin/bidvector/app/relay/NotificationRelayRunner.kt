package bidvector.app.relay

import bidvector.adapters.relay.NotificationRelayRun
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import java.sql.SQLException

/** 예외에서 로그에 실어도 되는 원인 코드만 뽑는다 — SQL 예외는 SQLSTATE 5자리만 더한다(메시지 없음). */
private fun causeCodeOf(failure: Exception): String =
    when (failure) {
        is SQLException -> "${failure.javaClass.name}:sqlState=${failure.sqlState}"
        else -> failure.javaClass.name
    }

/**
 * 일회성 relay 러너(D-6F10-18 ⑧) — `bidvector.relay.mode=once` 일 때만 빈으로 등록된다
 * (배선이 조건을 건다). 상주 루프도 스케줄러도 아니다: cron 이 프로세스를 돌린다
 * (db-scheduler 도입은 `OPEN-6F10-SCHEDULER`).
 *
 * **run-state 파일 잠금을 쓰지 않는다** — 수집 러너와 갈리는 자리다. relay 의 상호 배제는
 * use case 안의 `ConsumerLeasePort`(PostgreSQL 세션 advisory lock)가 지고, 그것이 **DB
 * 범위**라야 고아 판정이 성립한다(파일 잠금은 다른 호스트의 relay 를 막지 못한다). 잠금이
 * 두 겹이면 어느 쪽이 배제의 근거인지가 흐려진다.
 *
 * 이 클래스는 조립에 닿지 않는다 — [NotificationRelayRun](어댑터 조립 경계)의 `relay` 만
 * 부른다(구조 게이트: `app` production 은 outbox 쓰기 타입을 이름으로 볼 수 없다).
 */
class NotificationRelayRunner(
    private val run: NotificationRelayRun,
    private val limit: Int,
    private val log: CollectionLog,
    private val termination: CollectionTermination,
) : ApplicationRunner {
    /**
     * **예외를 Spring 에 넘기지 않고 이 자리에서 종료 코드로 옮긴다**(cr L-4) — 앞 판은
     * 정제된 예외를 다시 던져 종료 코드를 Spring 의 기동 실패 처리에 맡겼고, 그래서
     * [RelayExitCode.FAILED] 를 **아무 코드도 만들지 않는** 죽은 열거 값으로 두었다.
     * 지금은 사유 토큰(정제된 원인 코드)과 코드가 한 쌍으로 나간다.
     *
     * 결과를 담는 보조 타입을 두지 않는다 — app 에 최상위 타입이 하나 늘면 조립 층 등재와
     * 주입 표면 집합 등식이 함께 움직인다(실측: 게이트 셋이 붉었다). `catch` 안에서 끝내는
     * 쪽이 장부를 건드리지 않는다.
     */
    @Suppress("TooGenericExceptionCaught")
    override fun run(args: ApplicationArguments) {
        log.write(relayStartLine(limit))
        val report =
            try {
                run.relay(limit)
            } catch (failure: Exception) {
                log.write(relayFailureLine(causeCodeOf(failure)))
                termination.terminate(RelayExitCode.FAILED.value)
                return
            }
        val exitCode = exitCodeOf(report)
        log.write(relayFinishLine(report, exitCode))
        termination.terminate(exitCode.value)
    }
}
