package bidvector.workflow.retention

import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.strategy.Clock
import java.time.Instant

/** 표별 계수 — 파기·마스킹·fail-closed 를 한 숫자로 접지 않는다(처방이 다르다). */
data class TableTally(
    val purged: Int = 0,
    val masked: Int = 0,
    val maskFailClosed: Int = 0,
)

/**
 * 한 run 의 보고 — 운영자가 cron 로그 한 줄에서 「무엇이 몇 행 사라졌고 무엇이 분류상
 * 대상이 아니었나」를 읽는다. [exempt]·[cascade] 를 싣는 이유: 제외는 **매 run 재확인되는
 * 사실**이라야 「분류를 빠뜨렸다」와 구별된다.
 */
data class RetentionReport(
    val cutoff: Instant,
    val deletionOrder: List<String>,
    val tallies: Map<String, TableTally>,
    val exempt: Set<String>,
    val cascade: Set<String>,
) {
    val purgedTotal: Int get() = tallies.values.sumOf { it.purged }
    val maskedTotal: Int get() = tallies.values.sumOf { it.masked }
    val maskFailClosedTotal: Int get() = tallies.values.sumOf { it.maskFailClosed }
}

/** 실행하지 않은 사유 — 둘 다 **처방이 다르다**(한쪽은 마이그레이션, 한쪽은 재실행). */
sealed interface RetentionRefusal {
    /**
     * 스키마에 분류 없는 표가 있거나 분류에 없는 표가 있다(설계 검토 우회 7). 어느 쪽이든
     * **아무것도 지우지 않는다** — 분류를 모르는 표를 옆에 두고 지우는 것은 「무엇이 남는가」를
     * 모르는 채 지우는 것이다.
     */
    data class UnclassifiedTables(
        val missingClassification: Set<String>,
        val missingFromSchema: Set<String>,
    ) : RetentionRefusal

    /** FK 간선에 순환이 있어 자식 → 부모 순서가 없다. */
    data class CyclicDependencies(
        val tables: Set<String>,
    ) : RetentionRefusal
}

sealed interface RetentionOutcome {
    data class Completed(
        val report: RetentionReport,
    ) : RetentionOutcome

    /** 다른 보존 러너가 임대를 쥐고 있다 — 아무것도 지우지 않았다(설계 검토 우회 9). */
    data object LeaseBusy : RetentionOutcome

    data class Refused(
        val reason: RetentionRefusal,
    ) : RetentionOutcome
}

/**
 * 보존 use case(M6/6B-3 P-3) — 「적재 시각 + 90일」이 지난 행을 지우거나(PURGE) PII 를
 * 지운다(MASK_PII).
 *
 * **기준 시각은 주입된 [Clock] 하나에서 나온다** — 표마다 `now()` 를 다시 묻지 않는다(한 run
 * 안에서 cutoff 가 표마다 다르면 「무엇이 90일인가」가 표마다 달라진다).
 *
 * 실패를 삼키지 않는다 — 저장소 예외는 호출부(러너)로 올라가 정제된 원인 코드와 종료 코드가
 * 된다. 그때까지 커밋된 일괄은 그대로 남고 **다음 run 이 이어서 지운다**(cutoff 가 시각으로
 * 정해지므로 이어받기에 상태가 필요 없다).
 */
class PurgeExpiredData(
    private val store: RetentionStorePort,
    private val clock: Clock,
    private val policy: RetentionPolicyData,
) {
    fun run(): RetentionOutcome {
        val targets = store.classifiedTargets()
        val classified = targets.map { it.table }.toSet()
        val stored = store.storedTables()
        if (classified != stored) {
            return RetentionOutcome.Refused(
                RetentionRefusal.UnclassifiedTables(
                    missingClassification = stored - classified,
                    missingFromSchema = classified - stored,
                ),
            )
        }
        val purgeTargets = targets.filter { it.treatment == RetentionTreatment.PURGE }
        val order =
            when (val outcome = deletionOrderOf(purgeTargets.map { it.table }, store.dependencies())) {
                is DeletionOrderOutcome.Cyclic ->
                    return RetentionOutcome.Refused(RetentionRefusal.CyclicDependencies(outcome.tables))

                is DeletionOrderOutcome.Ordered -> outcome.tables
            }
        val cutoff = policy.cutoffFrom(clock.now())
        val attempt =
            store.withExclusiveLease { writer ->
                sweep(writer, cutoff, order, purgeTargets.associateBy { it.table }, targets)
            }
        return when (attempt) {
            is LeaseAttempt.Busy -> RetentionOutcome.LeaseBusy
            is LeaseAttempt.Held ->
                RetentionOutcome.Completed(
                    RetentionReport(
                        cutoff = cutoff.instant,
                        deletionOrder = order,
                        tallies = attempt.result,
                        exempt = targets.filter { it.treatment == RetentionTreatment.EXEMPT }.map { it.table }.toSet(),
                        cascade =
                            targets
                                .filter { it.treatment == RetentionTreatment.CASCADE_WITH_PARENT }
                                .map { it.table }
                                .toSet(),
                    ),
                )
        }
    }

    private fun sweep(
        writer: RetentionWriter,
        cutoff: RetentionCutoff,
        order: List<String>,
        purgeTargets: Map<String, RetentionTarget>,
        targets: List<RetentionTarget>,
    ): Map<String, TableTally> {
        val tallies = linkedMapOf<String, TableTally>()
        for (table in order) {
            val target = purgeTargets.getValue(table)
            tallies[table] = TableTally(purged = purgeAll(writer, target, cutoff))
        }
        for (target in targets.filter { it.treatment == RetentionTreatment.MASK_PII }) {
            val counts = maskAll(writer, target, cutoff)
            tallies[target.table] = TableTally(masked = counts.masked, maskFailClosed = counts.failClosed)
        }
        return tallies
    }

    /**
     * 일괄이 가득 차지 않을 때까지 돈다 — 한 run 이 백로그를 비운다. 끝나는 근거는 cutoff 가
     * **run 시작에 고정된 값**이라는 것이다: 도는 동안 새로 기한을 넘는 행이 생기지 않는다.
     */
    private fun purgeAll(
        writer: RetentionWriter,
        target: RetentionTarget,
        cutoff: RetentionCutoff,
    ): Int {
        var total = 0
        while (true) {
            val removed = writer.purge(target, cutoff, policy.batchSize)
            total += removed
            if (removed < policy.batchSize) return total
        }
    }

    private fun maskAll(
        writer: RetentionWriter,
        target: RetentionTarget,
        cutoff: RetentionCutoff,
    ): MaskBatchCounts {
        var masked = 0
        var failClosed = 0
        while (true) {
            val counts = writer.mask(target, cutoff, policy.batchSize)
            masked += counts.masked
            failClosed += counts.failClosed
            if (counts.total < policy.batchSize) return MaskBatchCounts(masked, failClosed)
        }
    }
}
