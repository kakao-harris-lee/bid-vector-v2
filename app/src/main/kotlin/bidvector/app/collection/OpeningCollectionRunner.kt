package bidvector.app.collection

import bidvector.adapters.persistence.CollectionRunLease
import bidvector.adapters.persistence.RunLease
import bidvector.workflow.collection.CallBudgetLedger
import bidvector.workflow.collection.CollectOpeningResultsUseCase
import bidvector.workflow.collection.CollectionRange
import bidvector.workflow.collection.OpeningCollectionReport
import bidvector.workflow.collection.OpeningCollectionSource
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner

/**
 * 개찰결과 수집 러너(M6/6G D-6G-1) — `bidvector.opening-collection.mode=once` 일 때만 빈으로 등록된다.
 * use case 를 한 번 돌리고 요약을 로그로 남긴 뒤 프로세스를 끝낸다. HTTP 로 이 갈래를 여는 endpoint 는
 * 없다. 이 클래스는 포트·원문·정규화에 닿지 않는다 — use case 만 부른다(구조 게이트).
 */
class OpeningCollectionRunner(
    private val useCase: CollectOpeningResultsUseCase,
    private val range: CollectionRange,
    private val sources: List<OpeningCollectionSource>,
    private val budget: CallBudgetLedger,
    private val lease: CollectionRunLease,
    private val log: CollectionLog,
    private val termination: CollectionTermination,
) : ApplicationRunner {
    /**
     * **한 번에 한 실행만**(D-6G-42 M-3). 겹쳐 돌면 같은 표본을 두 번 부르고 두 상한 회계가 서로의
     * 호출을 못 봐 승인 상한이 사실상 두 배가 된다 — 호출이 나간 뒤에 아는 사고다.
     */
    override fun run(args: ApplicationArguments) {
        when (val held = lease.acquire()) {
            is RunLease.Busy -> {
                log.write("opening-collection skipped reason=ALREADY_RUNNING")
                termination.terminate(CollectionExitCode.ALREADY_RUNNING.value)
            }

            is RunLease.Acquired -> {
                try {
                    collectUnderLease()
                } finally {
                    held.release()
                }
            }
        }
    }

    private fun collectUnderLease() {
        log.write(openingStartLine(range, sources))
        val report = collectOrFail()
        report.halted?.let { log.write(openingHaltLine(it)) }
        val exitCode = openingExitCodeOf(report)
        log.write(openingFinishLine(report, exitCode))
        termination.terminate(exitCode.value)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun collectOrFail(): OpeningCollectionReport =
        try {
            useCase.collect(range, sources, budget)
        } catch (failure: Exception) {
            val causeCode = openingCauseCodeOf(failure)
            log.write(failureLine(causeCode))
            throw CollectionRunFailedException(causeCode)
        }
}
