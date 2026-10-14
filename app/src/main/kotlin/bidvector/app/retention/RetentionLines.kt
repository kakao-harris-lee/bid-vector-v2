package bidvector.app.retention

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
    "retention finish cutoff=${report.cutoff} purged=${report.purgedTotal} masked=${report.maskedTotal} " +
        "maskFailClosed=${report.maskFailClosedTotal} maskSkipped=${report.maskSkippedTotal} " +
        "tables=[${report.tallies.entries.joinToString(" ") { (table, tally) ->
            "$table:purged=${tally.purged},masked=${tally.masked},failClosed=${tally.maskFailClosed}," +
                "skipped=${tally.maskSkipped}"
        }}] exempt=${report.exempt.sorted()} cascade=${report.cascade.sorted()} " +
        "exit=${exitCode.name}(${exitCode.value})"

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

internal fun retentionFailureLine(causeCode: String): String =
    "retention failed causeCode=$causeCode exit=${RetentionExitCode.FAILED.name}(${RetentionExitCode.FAILED.value})"
