package bidvector.app.relay

import bidvector.adapters.relay.NotificationRelayRun
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.workflow.notification.RelayReport
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import java.sql.SQLException

/**
 * 실행 실패의 정제된 표현 — 원인 코드만 싣고 **원 예외를 잇지 않는다**(수집 러너의
 * `CollectionRunFailedException` 과 같은 근거): 저장소 예외의 메시지에는 행 값이, 전송 계층
 * 예외에는 요청 URI 가 실릴 수 있어 원 예외가 스택에 붙어 로그로 나가는 경로를 만들지 않는다.
 */
class RelayRunFailedException(
    val causeCode: String,
) : RuntimeException("relay run failed cause=$causeCode")

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
    override fun run(args: ApplicationArguments) {
        log.write(relayStartLine(limit))
        val report = relayOrFail()
        val exitCode = exitCodeOf(report)
        log.write(relayFinishLine(report, exitCode))
        termination.terminate(exitCode.value)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun relayOrFail(): RelayReport =
        try {
            run.relay(limit)
        } catch (failure: Exception) {
            val causeCode = causeCodeOf(failure)
            log.write(relayFailureLine(causeCode))
            throw RelayRunFailedException(causeCode)
        }
}
