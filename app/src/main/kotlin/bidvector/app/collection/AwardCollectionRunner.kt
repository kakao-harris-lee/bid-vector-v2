package bidvector.app.collection

import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.workflow.collection.AwardCollectionHalt
import bidvector.workflow.collection.AwardCollectionReport
import bidvector.workflow.collection.AwardCollectionSource
import bidvector.workflow.collection.CollectAwardListUseCase
import bidvector.workflow.collection.CollectionRange
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner

/** 잠금·건너뜀 줄의 이름 — 운영자가 로그에서 갈래를 가리는 토큰이고 한 자리에서만 나온다. */
private const val AWARD_RUN_LABEL = "award-collection"

/**
 * 낙찰 목록 수집 러너(M9/award-ingest D-1) — `bidvector.award-collection.mode=once` 일 때만 빈으로
 * 등록된다. use case 를 한 번 돌리고 요약을 로그로 남긴 뒤 프로세스를 끝낸다. HTTP 로 이 갈래를 여는
 * endpoint 는 없다. 포트·원문·정규화에 닿지 않는다 — use case 만 부른다(구조 게이트).
 *
 * **잠금 안에서만 돈다**(형제 갈래와 같은 자리) — 겹쳐 돌면 두 상한 회계가 서로의 호출을 못 봐 승인
 * 상한이 사실상 두 배가 된다. 호출이 나간 뒤에 아는 사고다.
 */
class AwardCollectionRunner(
    private val useCase: CollectAwardListUseCase,
    private val range: CollectionRange,
    private val sources: List<AwardCollectionSource>,
    private val runState: RunStateDirectory,
    private val log: CollectionLog,
    private val termination: CollectionTermination,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) =
        underRunStateLock(runState, AWARD_RUN_LABEL, log, termination) { sweepUnderLock() }

    private fun sweepUnderLock() {
        log.write(awardStartLine(range, sources))
        val report = sweepOrFail()
        report.halted?.let { log.write(awardHaltLine(it)) }
        val exitCode = awardExitCodeOf(report)
        log.write(awardFinishLine(report, exitCode))
        termination.terminate(exitCode.value)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun sweepOrFail(): AwardCollectionReport =
        try {
            useCase.collect(range, sources)
        } catch (failure: Exception) {
            // 원 예외를 잇지 않는다(형제 갈래와 같은 규율) — SQL 메시지에 행 값이, 전송 예외에 요청
            // URI(서비스 키)가 실릴 수 있다. 사유는 닫힌 어휘의 토큰으로만 나간다.
            val causeCode = openingCauseCodeOf(failure)
            log.write(failureLine(causeCode))
            throw CollectionRunFailedException(causeCode)
        }
}

/**
 * 러너가 남기는 줄들 — 건수·날짜·업종 이름·열거값·원인 코드만 실을 수 있다. 입력 타입이 공고 식별자도
 * 상호도 나르지 않으므로 여기서 새는 값은 구조적으로 없다.
 */
internal fun awardStartLine(
    range: CollectionRange,
    sources: List<AwardCollectionSource>,
): String =
    "award-collection start openingFrom=${range.from} openingTo=${range.to} " +
        "sources=${sources.joinToString(",") { it.name.value }}"

internal fun awardHaltLine(halt: AwardCollectionHalt): String =
    "award-collection halted budgetLimit=${halt.budgetLimit ?: "none"} " +
        "truncation=${halt.truncationCause ?: "none"} notAttemptedSlots=${halt.notAttemptedSlots}"

internal fun awardFinishLine(
    report: AwardCollectionReport,
    exitCode: CollectionExitCode,
): String =
    "award-collection finished slots=${report.slots} received=${report.received} stored=${report.stored} " +
        "truncatedSlots=${report.truncatedSlots} halted=${report.halted != null} exit=${exitCode.value}"

/**
 * **절단도 미완이다**(cr H-1) — 멈춤(예산·쿼터)뿐 아니라 **잘린 슬롯이 하나라도 있으면** 미완으로
 * 끝낸다. 형제 공고 목록 갈래(`exitCodeOf`)와 같은 술어이고, 개찰결과 갈래가 절단을 무시하는 선례를
 * 물려받지 않는다: 그 갈래에는 확정 표본 원장이 있어 끝나지 않은 축을 다음 실행이 받지만, 이 갈래에는
 * **슬롯 단위 이어 돌기가 없다**. 종료 코드 0 이 나가면 runbook 의 「0 = 다음 조각」이 운영자를 넘기고
 * 그 개찰일의 낙찰 원문은 사람이 그 조각을 다시 지목하지 않으면 영영 들어오지 않는다 — 결측이 조용해진다.
 *
 * 절단과 멈춤에 **같은 코드**를 쓰는 이유: 운영자의 다음 행동이 같다(그 조각을 다시 돌린다). 갈리는 것은
 * 「언제」뿐이고 그 사유는 halted 줄과 `truncatedSlots` 계수가 로그에서 말한다(runbook §3 이 둘을 가른다).
 */
internal fun awardExitCodeOf(report: AwardCollectionReport): CollectionExitCode =
    if (report.halted == null && report.truncatedSlots == 0) {
        CollectionExitCode.COMPLETE
    } else {
        CollectionExitCode.INCOMPLETE
    }
