package bidvector.workflow.retention

import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.strategy.Clock
import java.time.Instant

/** 표별 계수 — 파기·마스킹·fail-closed·건너뜀을 한 숫자로 접지 않는다(처방이 다르다). */
data class TableTally(
    val purged: Int = 0,
    val masked: Int = 0,
    val maskFailClosed: Int = 0,
    val maskSkipped: Int = 0,
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
    val maskSkippedTotal: Int get() = tallies.values.sumOf { it.maskSkipped }
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

/**
 * 일괄 **몇 개를 커밋한 뒤** 멈춘 run (PR #73 P6) — 그때까지 지운 행은 돌아오지 않는다(백업 복원만이
 * 경로다). 그 수를 예외에 실어 올리지 않으면 운영자는 「이미 몇 행이 사라졌나」를 로그에서 읽을 수
 * 없고, 되돌릴 수 없는 경로에서 그 모름은 백업 대조로만 메워진다.
 *
 * [RetentionOutcome.Completed] 와 **같은 모양의 보고**를 싣는다([partial]). 둘을 값과 예외로 가른
 * 이유는 처분이 다르기 때문이다 — 완료는 종료 코드로 끝나는 정상 run 이고, 이쪽은 저장소나 계약이
 * 깨진 것(재시도 전에 원인을 봐야 한다)이라 값으로 접으면 조용해진다. PR #63 의 relay 처방
 * (`RelayAborted`)과 같은 축이다.
 *
 * [partial] 의 계수는 **커밋된 일괄만** 센다 — 터진 일괄 자신은 롤백되므로 들어 있지 않다.
 */
class RetentionAborted(
    val partial: RetentionReport,
    override val cause: Throwable,
) : RuntimeException("보존 run 이 일괄 도중에 멈췄다 — 집계는 partial 에 있다", cause)

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
 * **계획과 실행이 갈려 있다**([planOf] → [sweepUnderLease]): 거부 사유는 임대를 쥐기 전에
 * 결정되므로, 분류가 어긋난 run 은 자물쇠도 잡지 않고 한 행도 건드리지 않는다.
 *
 * 실패를 삼키지 않는다 — 저장소 예외는 호출부(러너)로 올라가 정제된 원인 코드와 종료 코드가
 * 된다. 그때까지 커밋된 일괄은 그대로 남고 **다음 run 이 이어서 지운다**(cutoff 가 시각으로
 * 정해지므로 이어받기에 상태가 필요 없다).
 */
class PurgeExpiredData(
    private val store: RetentionStorePort,
    private val clock: Clock,
    /**
     * **이 조립의 유일한 정책 값**이다(vr F-1 운반). 읽기만 하는 표면으로 공개한다 — 출하 배선이
     * 러너에 넘긴 값이 실제로 이 use case 에 닿았는지 `RetentionWiringTest` 가 그 자리에서 재야
     * 하고, 그 등식이 없으면 배선이 보존 1일로 바뀌어도 app test 전건이 초록이었다.
     * 밖에서 **만들** 수는 없다([RetentionPolicyData] 의 생성자가 이 모듈의 `internal`).
     */
    val policy: RetentionPolicyData,
) {
    /** 거부와 실행 가능을 가르는 내부 값 — 최상위 타입을 늘리지 않는다. */
    private sealed interface Plan {
        data class Ready(
            val targets: List<RetentionTarget>,
            val purgeTargets: Map<String, RetentionTarget>,
            val order: List<String>,
        ) : Plan

        data class Rejected(
            val reason: RetentionRefusal,
        ) : Plan
    }

    fun run(): RetentionOutcome =
        when (val plan = planOf(store.classifiedTargets())) {
            is Plan.Rejected -> RetentionOutcome.Refused(plan.reason)
            is Plan.Ready -> sweepUnderLease(plan)
        }

    private fun planOf(targets: List<RetentionTarget>): Plan {
        val classified = targets.map { it.table }.toSet()
        val stored = store.storedTables()
        if (classified != stored) {
            return Plan.Rejected(
                RetentionRefusal.UnclassifiedTables(
                    missingClassification = stored - classified,
                    missingFromSchema = classified - stored,
                ),
            )
        }
        val purgeTargets = targets.filter { it.treatment == RetentionTreatment.PURGE }
        return when (val outcome = deletionOrderOf(purgeTargets.map { it.table }, store.dependencies())) {
            is DeletionOrder.Cyclic -> {
                Plan.Rejected(RetentionRefusal.CyclicDependencies(outcome.tables))
            }

            is DeletionOrder.Ordered -> {
                Plan.Ready(targets, purgeTargets.associateBy { it.table }, outcome.tables)
            }
        }
    }

    private fun sweepUnderLease(plan: Plan.Ready): RetentionOutcome {
        val cutoff = policy.cutoffFrom(clock.now())
        return when (val attempt = store.withExclusiveLease { writer -> sweep(writer, cutoff, plan) }) {
            is LeaseAttempt.Busy -> RetentionOutcome.LeaseBusy
            is LeaseAttempt.Held -> RetentionOutcome.Completed(reportOf(cutoff, plan, attempt.result))
        }
    }

    private fun reportOf(
        cutoff: RetentionCutoff,
        plan: Plan.Ready,
        tallies: Map<String, TableTally>,
    ): RetentionReport =
        RetentionReport(
            cutoff = cutoff.instant,
            deletionOrder = plan.order,
            tallies = tallies,
            exempt = plan.targets.tablesWith(RetentionTreatment.EXEMPT),
            cascade = plan.targets.tablesWith(RetentionTreatment.CASCADE_WITH_PARENT),
        )

    /**
     * 계수를 **일괄마다 적는다**(PR #73 P6) — 저장소는 문장마다 자기 트랜잭션이라 한 일괄이 커밋된
     * 뒤 다음 일괄이 터지면 **앞 일괄은 남는다**. 표별 합계를 끝에 한 번 적으면 그 run 의 계수가
     * 통째로 사라지고, 되돌릴 수 없는 경로에서 「몇 행이 이미 사라졌나」가 로그 밖으로 나간다.
     * 터진 일괄 자신은 롤백되므로 세지 않는다 — 콜백이 저장소 호출 **뒤에** 돈다.
     */
    private fun sweep(
        writer: RetentionWriter,
        cutoff: RetentionCutoff,
        plan: Plan.Ready,
    ): Map<String, TableTally> {
        val maskTargets = plan.targets.filter { it.treatment == RetentionTreatment.MASK_PII }
        val tallies = linkedMapOf<String, TableTally>()
        plan.order.forEach { tallies[it] = TableTally() }
        maskTargets.forEach { tallies[it.table] = TableTally() }
        try {
            for (table in plan.order) {
                purgeAll(writer, plan.purgeTargets.getValue(table), cutoff) { removed ->
                    tallies[table] = tallies.getValue(table).plusPurged(removed)
                }
            }
            for (target in maskTargets) {
                maskAll(writer, target, cutoff) { counts ->
                    tallies[target.table] = tallies.getValue(target.table).plusMask(counts)
                }
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") failure: Exception,
        ) {
            throw RetentionAborted(reportOf(cutoff, plan, tallies.toMap()), failure)
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
        onBatch: (Int) -> Unit,
    ) {
        while (true) {
            val removed = writer.purge(target, cutoff, policy.batchSize)
            onBatch(removed)
            if (removed < policy.batchSize) return
        }
    }

    /**
     * 끝나는 근거 **둘**이다.
     *
     * ① 「일괄이 가득 차지 않았다」 — 가득 참은 **후보 수**로 센다(cr L-5): UPDATE 가 0 행을 돌린
     * 후보를 세지 않으면 일괄이 가득 찼는데도 루프가 끝나 그 run 이 백로그를 비우지 못한다.
     *
     * ② **「진척이 없다」**(cr M-1) — 가득 찬 일괄인데 한 행도 처리하지 못했으면 다음 일괄도 같다
     * (cutoff 가 run 시작에 고정되고 후보 순서가 `ORDER BY` 로 결정적이다). ① 만 두면 같은 후보가
     * 반복해 `skipped` 로 돌아오는 상태에서 **임대와 소유자 연결을 쥔 채 영구히 돈다** — cron 이
     * 끝나지 않는 프로세스를 얻고 그 뒤 모든 run 이 `LEASE_BUSY` 로 떨어져 파기까지 멈춘다.
     * 조용한 미완보다 나쁜 실패 모양이라 그 가정을 코드 안에서 닫는다.
     */
    private fun maskAll(
        writer: RetentionWriter,
        target: RetentionTarget,
        cutoff: RetentionCutoff,
        onBatch: (MaskBatchCounts) -> Unit,
    ) {
        while (true) {
            val counts = writer.mask(target, cutoff, policy.batchSize)
            onBatch(counts)
            val progressed = counts.masked + counts.failClosed > 0
            if (counts.total < policy.batchSize || !progressed) return
        }
    }
}

private fun List<RetentionTarget>.tablesWith(treatment: RetentionTreatment): Set<String> =
    filter { it.treatment == treatment }.map { it.table }.toSet()

private fun TableTally.plusPurged(removed: Int): TableTally = copy(purged = purged + removed)

private fun TableTally.plusMask(counts: MaskBatchCounts): TableTally =
    copy(
        masked = masked + counts.masked,
        maskFailClosed = maskFailClosed + counts.failClosed,
        maskSkipped = maskSkipped + counts.skipped,
    )
