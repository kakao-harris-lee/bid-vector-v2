package bidvector.app.notificationinbox

import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.relay.causeCodeOf
import bidvector.workflow.bidding.NotificationSweepReport
import bidvector.workflow.bidding.SweepNotifications
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner

/**
 * 일회성 알림 훑기 러너(M7/7C③-b · D-7C3B-8, 아홉째 러너) —
 * `bidvector.notification-sweep.mode=once` 일 때만 빈으로 등록된다(배선이 조건을 건다).
 * 주기는 외부 cron 이 진다(`docs/runbook/m6-6e-operations.md` §1.3 관례) — 상주 루프도
 * 스케줄러도 아니다.
 *
 * **같은 프로세스에 다른 일회 러너와 함께 켤 수 없다** — `OneShotRunnerGuard` 가 `ApplicationRunner`
 * 빈 정의가 둘 이상이면 기동을 실패시킨다.
 *
 * **임대도, 실행 상태 파일도 들지 않는다.** 훑기가 멱등이기 때문이다 — 두 run 이 겹쳐도 같은
 * 중복 억제 키를 갱신할 뿐 항목이 늘지 않는다(NOTI-10 의 PK). 보존 러너가 임대를 쥐는 이유는
 * 파기가 되돌릴 수 없어서이고 여기는 그 성질이 없다.
 *
 * 예외를 Spring 에 넘기지 않고 이 자리에서 **종료 코드로 옮긴다**(보존·relay 러너와 같은 규율).
 * 원인 코드는 [causeCodeOf] 가 정제한다 — 저장소 예외 메시지에는 행 값이 실릴 수 있어 원 예외를
 * 로그 경로에 올리지 않는다.
 */
class NotificationSweepRunner(
    internal val sweep: SweepNotifications,
    private val log: CollectionLog,
    private val termination: CollectionTermination,
) : ApplicationRunner {
    @Suppress("TooGenericExceptionCaught")
    override fun run(args: ApplicationArguments) {
        log.write(sweepStartLine(sweep.policy))
        val report =
            try {
                sweep.run()
            } catch (failure: Exception) {
                log.write(sweepFailureLine(causeCodeOf(failure)))
                termination.terminate(NotificationSweepExitCode.FAILED.value)
                return
            }
        val exitCode = exitCodeOf(report)
        log.write(sweepFinishLine(report, exitCode))
        termination.terminate(exitCode.value)
    }
}

/**
 * **기록 실패가 있으면 cron 이 0 을 받지 않는다**(D-7C3B-9 의 「표지·계수」). 훑기는 실패한 건을
 * 건너뛰고 끝까지 돌지만, 그 run 은 알림을 **잃은** run 이다 — 종료 코드가 0 이면 그 사실이
 * 운영 쪽에서 보이지 않는다.
 */
internal fun exitCodeOf(report: NotificationSweepReport): NotificationSweepExitCode =
    if (report.failed > 0) NotificationSweepExitCode.INCOMPLETE else NotificationSweepExitCode.COMPLETE
