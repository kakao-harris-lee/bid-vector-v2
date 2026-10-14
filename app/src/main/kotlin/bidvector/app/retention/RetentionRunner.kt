package bidvector.app.retention

import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.relay.causeCodeOf
import bidvector.workflow.retention.PurgeExpiredData
import bidvector.workflow.retention.RetentionOutcome
import bidvector.workflow.retention.RetentionReport
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner

/**
 * 일회성 보존 러너(M6/6B-3 P-3, 여섯째 러너) — `bidvector.retention.mode=once` 일 때만 빈으로
 * 등록된다(배선이 조건을 건다). 주기는 외부 cron 이 진다(`docs/runbook/m6-6e-operations.md`
 * §1.3 관례) — 상주 루프도 스케줄러도 아니다.
 *
 * **같은 프로세스에 다른 일회 러너와 함께 켤 수 없다** — `OneShotRunnerGuard` 가 `ApplicationRunner`
 * 빈 **정의**가 둘 이상이면 기동을 실패시킨다. 보존이 수집·relay 와 같은 프로세스에서 돌면
 * 먼저 끝난 쪽이 JVM 을 끝내 나머지가 조용히 안 돈다.
 *
 * 예외를 Spring 에 넘기지 않고 이 자리에서 **종료 코드로 옮긴다**(relay 러너 cr L-4 와 같은
 * 규율). 원인 코드는 [causeCodeOf] 가 정제한다 — 저장소 예외 메시지에는 행 값(공고명·담당자
 * 값)이 실릴 수 있어 원 예외를 로그 경로에 올리지 않는다.
 */
class RetentionRunner(
    /**
     * **보존 기간을 따로 받지 않는다**(vr F-1 운반). 조립 안에 정책 값이 두 벌 있으면 로그가
     * 말하는 보존 기간과 실제로 지우는 cutoff 가 갈릴 수 있고, 그 둘이 갈려도 아무 test 도
     * 지나지 않았다. 지금은 값이 **한 벌**이고 그 한 벌을 `RetentionWiringTest` 가 센다.
     */
    internal val purge: PurgeExpiredData,
    private val log: CollectionLog,
    private val termination: CollectionTermination,
) : ApplicationRunner {
    @Suppress("TooGenericExceptionCaught")
    override fun run(args: ApplicationArguments) {
        log.write(retentionStartLine(purge.policy.retentionDays))
        val outcome =
            try {
                purge.run()
            } catch (failure: Exception) {
                log.write(retentionFailureLine(causeCodeOf(unwrappedFailure(failure)), partialOf(failure)))
                termination.terminate(RetentionExitCode.FAILED.value)
                return
            }
        termination.terminate(reportAndCode(outcome).value)
    }

    private fun reportAndCode(outcome: RetentionOutcome): RetentionExitCode =
        when (outcome) {
            is RetentionOutcome.Completed -> {
                val exitCode = exitCodeOf(outcome.report)
                log.write(retentionFinishLine(outcome.report, exitCode))
                exitCode
            }

            RetentionOutcome.LeaseBusy -> {
                log.write(retentionLeaseBusyLine())
                RetentionExitCode.LEASE_BUSY
            }

            is RetentionOutcome.Refused -> {
                log.write(retentionRefusedLine(outcome.reason))
                RetentionExitCode.REFUSED
            }
        }
}

/**
 * **미완의 두 꼴**(cr L-3 · M-1 처방 2).
 *
 * ⓐ 판독 실패 — 그 행들은 원문을 잃었고(fail-closed 로 값 전체를 버렸다) 그것은 상류 형태가 바뀌었다는
 * 신호다. ⓑ **건너뜀** — 후보로 골랐는데 처리하지 못한 행이고, 그 run 이 백로그를 비우지 못했다는
 * 뜻이다. 둘 중 하나라도 0 이 아니면 cron 이 **0 을 받지 않는다**(앞 판은 ⓑ 를 마침 줄에만 실어
 * 종료 코드에 영향이 없었다).
 */
internal fun exitCodeOf(report: RetentionReport): RetentionExitCode =
    if (report.maskFailClosedTotal > 0 || report.maskSkippedTotal > 0) {
        RetentionExitCode.INCOMPLETE
    } else {
        RetentionExitCode.COMPLETE
    }
