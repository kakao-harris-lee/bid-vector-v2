package bidvector.app.retention

import bidvector.workflow.retention.RetentionAborted
import bidvector.workflow.retention.RetentionRefusal
import bidvector.workflow.retention.RetentionReport

/**
 * 보존 run 의 로그 줄 — **행 값을 싣지 않는다.** 실리는 것은 표 이름·계수·cutoff 시각뿐이고,
 * 그 셋은 어느 것도 공고 내용이나 담당자 값이 아니다(원문을 마스킹하면서 그 원문을 로그로
 * 흘리는 것이 이 자리의 유일한 자해다).
 *
 * 표별 계수를 **접지 않고** 싣는다 — 「어느 표가 몇 행」을 운영자가 보지 못하면 분류가 의도대로
 * 섰는지 확인할 길이 run 보고 밖으로 나간다.
 */
internal fun retentionStartLine(retentionDays: Int): String = "retention start retentionDays=$retentionDays"

internal fun retentionFinishLine(
    report: RetentionReport,
    exitCode: RetentionExitCode,
): String =
    "retention finish cutoff=${report.cutoff} ${countFields(report)} " +
        "exempt=${report.exempt.sorted()} cascade=${report.cascade.sorted()} " +
        "exit=${exitCode.name}(${exitCode.value})"

/**
 * 계수 칸들 — 마침 줄과 **실패 줄이 같은 어휘를 쓴다**(PR #73 P6). 두 벌로 두면 운영자가 두 줄을
 * 같은 눈으로 읽지 못하고, 실패 줄의 수가 마침 줄의 수와 다른 뜻인지 판단해야 한다.
 */
private fun countFields(report: RetentionReport): String =
    "purged=${report.purgedTotal} masked=${report.maskedTotal} " +
        "maskFailClosed=${report.maskFailClosedTotal} maskSkipped=${report.maskSkippedTotal} " +
        "tables=[${report.tallies.entries.joinToString(" ") { (table, tally) ->
            "$table:purged=${tally.purged},masked=${tally.masked},failClosed=${tally.maskFailClosed}," +
                "skipped=${tally.maskSkipped}"
        }}]"

internal fun retentionRefusedLine(reason: RetentionRefusal): String =
    "retention refused reason=" +
        when (reason) {
            is RetentionRefusal.UnclassifiedTables -> {
                "UNCLASSIFIED_TABLES missingClassification=${reason.missingClassification.sorted()} " +
                    "missingFromSchema=${reason.missingFromSchema.sorted()}"
            }

            is RetentionRefusal.CyclicDependencies -> {
                "CYCLIC_DEPENDENCIES tables=${reason.tables.sorted()}"
            }
        } + " exit=${RetentionExitCode.REFUSED.name}(${RetentionExitCode.REFUSED.value})"

internal fun retentionLeaseBusyLine(): String =
    "retention skipped reason=LEASE_BUSY " +
        "exit=${RetentionExitCode.LEASE_BUSY.name}(${RetentionExitCode.LEASE_BUSY.value})"

/**
 * 실패 줄 — **그때까지 커밋된 계수를 함께 싣는다**(PR #73 P6). 파기는 되돌릴 수 없고 일괄은 각자
 * 커밋되므로, 도중에 죽은 run 이 이미 지운 행 수는 이 줄에 없으면 **백업과 대조해야만** 알 수 있다.
 * [partial] 이 `null` 이면 임대를 쥐기 전에(계획·거부 단계에서) 터진 것이고 그때는 지운 행이 없다.
 *
 * 원인은 **여전히 정제된 코드만** 싣는다 — 두 축이 한 줄에 공존한다(계수는 행 값이 아니다).
 */
internal fun retentionFailureLine(
    causeCode: String,
    partial: RetentionReport? = null,
): String =
    "retention failed causeCode=$causeCode" +
        (partial?.let { " partial cutoff=${it.cutoff} ${countFields(it)}" } ?: "") +
        " exit=${RetentionExitCode.FAILED.name}(${RetentionExitCode.FAILED.value})"

/** 예외가 부분 계수를 싣고 있나 — 싣지 않는 예외는 `null` 이다(relay 의 `partialOf` 와 같은 모양). */
internal fun partialOf(failure: Exception): RetentionReport? = (failure as? RetentionAborted)?.partial

/**
 * 원인 코드는 **포장이 아니라 안쪽 예외**에서 뽑는다. [RetentionAborted] 는 계수를 나르는 포장일
 * 뿐이라 그 클래스 이름을 원인 코드로 적으면 **SQLSTATE 가 사라진다**(실측: 포장을 씌운 첫 판에서
 * `causeCode=…RetentionAborted` 가 되어 42P01 이 로그에서 빠졌고 기존 test 가 그 자리에서 붉었다).
 * 포장이 아니거나 안쪽이 `Exception` 이 아니면 받은 예외를 그대로 쓴다.
 */
internal fun unwrappedFailure(failure: Exception): Exception =
    (failure as? RetentionAborted)?.cause as? Exception ?: failure
