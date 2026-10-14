package bidvector.workflow.retention

import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

private val NOW = Instant.parse("2026-10-13T00:00:00Z")
private val CUTOFF_AT_90_DAYS = Instant.parse("2026-07-15T00:00:00Z")

private fun policy(batchSize: Int = 10) = RetentionPolicyData(retentionDays = 90, batchSize = batchSize)

/**
 * 호출을 기록하는 fake 저장소 — mock framework 를 쓰지 않는다(v2-지침서 §5: workflow test 는
 * 값과 fake port 로).
 *
 * `purgeBatches`/`maskBatches` 는 **표별 응답 큐**다. 큐가 비면 0 을 돌려준다 — 「더 지울 것이
 * 없다」. 큐를 쓰는 이유: 일괄 루프가 「가득 찬 일괄이면 한 번 더」를 실제로 하는지 재려면
 * 응답이 호출마다 달라야 한다.
 */
private class FakeRetentionStore(
    private val targets: List<RetentionTarget>,
    private val stored: Set<String> = targets.map { it.table }.toSet(),
    private val dependencies: Set<TableDependency> = emptySet(),
    private val leaseBusy: Boolean = false,
    private val purgeBatches: Map<String, MutableList<Int>> = emptyMap(),
    private val maskBatches: Map<String, MutableList<MaskBatchCounts>> = emptyMap(),
    /** 매 일괄이 **가득 찬 채 진척 0** 인 응답만 내는 모드(cr M-1) — 종료 보장을 재는 자리. */
    private val stuckBatch: Boolean = false,
) : RetentionStorePort,
    RetentionWriter {
    val purgeCalls = mutableListOf<Pair<String, Instant>>()
    val maskCalls = mutableListOf<Pair<String, Instant>>()
    var leaseTaken = false

    override fun classifiedTargets(): List<RetentionTarget> = targets

    override fun storedTables(): Set<String> = stored

    override fun dependencies(): Set<TableDependency> = dependencies

    override fun <T> withExclusiveLease(body: (RetentionWriter) -> T): LeaseAttempt<T> {
        if (leaseBusy) return LeaseAttempt.Busy
        leaseTaken = true
        return LeaseAttempt.Held(body(this))
    }

    override fun purge(
        target: RetentionTarget,
        cutoff: RetentionCutoff,
        batchSize: Int,
    ): Int {
        purgeCalls += target.table to cutoff.instant
        return purgeBatches[target.table]?.removeFirstOrNull() ?: 0
    }

    override fun mask(
        target: RetentionTarget,
        cutoff: RetentionCutoff,
        batchSize: Int,
    ): MaskBatchCounts {
        maskCalls += target.table to cutoff.instant
        check(maskCalls.size <= RUNAWAY_CALL_LIMIT) { "마스킹 일괄 루프가 끝나지 않는다" }
        if (stuckBatch) return MaskBatchCounts(masked = 0, failClosed = 0, skipped = batchSize)
        return maskBatches[target.table]?.removeFirstOrNull() ?: MaskBatchCounts(0, 0)
    }
}

/**
 * 끝나지 않는 루프를 **test 가 멈춘다** — 그 자리에 상한이 없으면 cr M-1 의 모양(임대와 소유자 연결을
 * 쥔 채 영구히 도는 run)이 그대로 **빌드를 멈춘다**. 상한을 넘으면 실패로 끝나고 사유가 남는다.
 */
private const val RUNAWAY_CALL_LIMIT = 50

/** P-3 — 보존 use case. 값과 fake port 로만 재고, 실제 SQL 축은 adapters test 가 진다. */
class PurgeExpiredDataTest {
    @Test
    fun `분류 없는 표가 스키마에 있으면 아무것도 지우지 않는다`() {
        val store =
            FakeRetentionStore(
                targets = listOf(RetentionTarget("notice", RetentionTreatment.PURGE)),
                stored = setOf("notice", "v19_new_table"),
            )

        val outcome = PurgeExpiredData(store, Clock { NOW }, policy()).run()

        outcome shouldBe
            RetentionOutcome.Refused(
                RetentionRefusal.UnclassifiedTables(
                    missingClassification = setOf("v19_new_table"),
                    missingFromSchema = emptySet(),
                ),
            )
        store.leaseTaken shouldBe false
        store.purgeCalls shouldBe emptyList()
    }

    @Test
    fun `분류에만 있고 스키마에 없는 표도 거부한다 — 죽은 분류는 등식을 거짓으로 만든다`() {
        val store =
            FakeRetentionStore(
                targets =
                    listOf(
                        RetentionTarget("notice", RetentionTreatment.PURGE),
                        RetentionTarget("dropped_table", RetentionTreatment.PURGE),
                    ),
                stored = setOf("notice"),
            )

        val outcome = PurgeExpiredData(store, Clock { NOW }, policy()).run()

        outcome shouldBe
            RetentionOutcome.Refused(
                RetentionRefusal.UnclassifiedTables(
                    missingClassification = emptySet(),
                    missingFromSchema = setOf("dropped_table"),
                ),
            )
        store.purgeCalls shouldBe emptyList()
    }

    @Test
    fun `FK 순환이면 실행하지 않는다`() {
        val store =
            FakeRetentionStore(
                targets =
                    listOf(
                        RetentionTarget("a", RetentionTreatment.PURGE),
                        RetentionTarget("b", RetentionTreatment.PURGE),
                    ),
                dependencies =
                    setOf(
                        TableDependency("a", "b", cascade = false),
                        TableDependency("b", "a", cascade = false),
                    ),
            )

        val outcome = PurgeExpiredData(store, Clock { NOW }, policy()).run()

        outcome shouldBe RetentionOutcome.Refused(RetentionRefusal.CyclicDependencies(setOf("a", "b")))
        store.leaseTaken shouldBe false
    }

    @Test
    fun `임대를 못 쥐면 한 행도 건드리지 않는다`() {
        val store =
            FakeRetentionStore(
                targets = listOf(RetentionTarget("notice", RetentionTreatment.PURGE)),
                leaseBusy = true,
            )

        val outcome = PurgeExpiredData(store, Clock { NOW }, policy()).run()

        outcome shouldBe RetentionOutcome.LeaseBusy
        store.purgeCalls shouldBe emptyList()
    }

    @Test
    fun `제외 표와 cascade 표는 파기도 마스킹도 부르지 않는다 — 보고에는 분류로 실린다`() {
        val store =
            FakeRetentionStore(
                targets =
                    listOf(
                        RetentionTarget("notice", RetentionTreatment.PURGE),
                        RetentionTarget("inbox", RetentionTreatment.EXEMPT),
                        RetentionTarget("operator_profile", RetentionTreatment.EXEMPT),
                        RetentionTarget("notice_requirement_row", RetentionTreatment.CASCADE_WITH_PARENT),
                    ),
            )

        val outcome = PurgeExpiredData(store, Clock { NOW }, policy()).run()

        store.purgeCalls.map { it.first } shouldContainExactly listOf("notice")
        store.maskCalls shouldBe emptyList()
        val report = outcome.shouldBeInstanceOf<RetentionOutcome.Completed>().report
        report.exempt shouldBe setOf("inbox", "operator_profile")
        report.cascade shouldBe setOf("notice_requirement_row")
    }

    @Test
    fun `cutoff 는 주입된 시계에서 보존 기간을 뺀 값으로 저장소에 내려간다`() {
        val store =
            FakeRetentionStore(
                targets =
                    listOf(
                        RetentionTarget("notice", RetentionTreatment.PURGE),
                        RetentionTarget("raw_observation", RetentionTreatment.MASK_PII),
                    ),
            )

        val outcome = PurgeExpiredData(store, Clock { NOW }, policy()).run()

        store.purgeCalls shouldContainExactly listOf("notice" to CUTOFF_AT_90_DAYS)
        store.maskCalls shouldContainExactly listOf("raw_observation" to CUTOFF_AT_90_DAYS)
        outcome.shouldBeInstanceOf<RetentionOutcome.Completed>().report.cutoff shouldBe CUTOFF_AT_90_DAYS
    }

    @Test
    fun `가득 찬 일괄이면 한 번 더 돈다 — 백로그를 한 run 이 비운다`() {
        val store =
            FakeRetentionStore(
                targets = listOf(RetentionTarget("notice", RetentionTreatment.PURGE)),
                purgeBatches = mapOf("notice" to mutableListOf(10, 10, 3)),
            )

        val outcome = PurgeExpiredData(store, Clock { NOW }, policy(batchSize = 10)).run()

        store.purgeCalls.size shouldBe 3
        val report = outcome.shouldBeInstanceOf<RetentionOutcome.Completed>().report
        report.tallies.getValue("notice") shouldBe TableTally(purged = 23)
        report.purgedTotal shouldBe 23
    }

    @Test
    fun `파기는 자식 표부터 돈다 — 순서가 보고에도 실린다`() {
        val store =
            FakeRetentionStore(
                targets =
                    listOf(
                        RetentionTarget("opening_result", RetentionTreatment.PURGE),
                        RetentionTarget("opening_reserve_price", RetentionTreatment.PURGE),
                    ),
                dependencies =
                    setOf(TableDependency("opening_reserve_price", "opening_result", cascade = false)),
            )

        val outcome = PurgeExpiredData(store, Clock { NOW }, policy()).run()

        store.purgeCalls.map { it.first } shouldContainExactly listOf("opening_reserve_price", "opening_result")
        outcome.shouldBeInstanceOf<RetentionOutcome.Completed>().report.deletionOrder shouldContainExactly
            listOf("opening_reserve_price", "opening_result")
    }

    @Test
    fun `마스킹 성공과 fail-closed 폐기는 따로 센다 — 일괄도 가득 차면 한 번 더`() {
        val store =
            FakeRetentionStore(
                targets = listOf(RetentionTarget("raw_observation", RetentionTreatment.MASK_PII)),
                maskBatches =
                    mapOf(
                        "raw_observation" to
                            mutableListOf(MaskBatchCounts(masked = 8, failClosed = 2), MaskBatchCounts(1, 0)),
                    ),
            )

        val outcome = PurgeExpiredData(store, Clock { NOW }, policy(batchSize = 10)).run()

        store.maskCalls.size shouldBe 2
        val report = outcome.shouldBeInstanceOf<RetentionOutcome.Completed>().report
        report.tallies.getValue("raw_observation") shouldBe TableTally(masked = 9, maskFailClosed = 2)
        report.maskedTotal shouldBe 9
        report.maskFailClosedTotal shouldBe 2
    }

    /**
     * cr L-5 — 일괄의 **가득 참은 후보 수로** 센다. 0 행을 돌린 후보(`skipped`)를 세지 않으면
     * 일괄이 가득 찼는데도 루프가 끝나 그 run 이 백로그를 비우지 못한다. 아래 첫 일괄은
     * 계수(masked+failClosed)가 상한보다 작지만 **후보 수는 상한과 같다** — 그래서 한 번 더 돈다.
     */
    @Test
    fun `건너뛴 후보도 일괄을 채운다 — 그 run 이 백로그를 비운다`() {
        val store =
            FakeRetentionStore(
                targets = listOf(RetentionTarget("raw_observation", RetentionTreatment.MASK_PII)),
                maskBatches =
                    mapOf(
                        "raw_observation" to
                            mutableListOf(
                                MaskBatchCounts(masked = 6, failClosed = 0, skipped = 4),
                                MaskBatchCounts(masked = 2, failClosed = 0, skipped = 0),
                            ),
                    ),
            )

        val outcome = PurgeExpiredData(store, Clock { NOW }, policy(batchSize = 10)).run()

        store.maskCalls.size shouldBe 2
        val report = outcome.shouldBeInstanceOf<RetentionOutcome.Completed>().report
        report.tallies.getValue("raw_observation") shouldBe TableTally(masked = 8, maskSkipped = 4)
        report.maskSkippedTotal shouldBe 4
    }

    /**
     * cr M-1 — 종료 보장. `skipped` 칸이 생기면서 끝나는 근거가 「계수가 단조 감소한다」에서 **「모든
     * 후보가 결국 후보 집합에서 빠진다」**로 바뀌었고, `skipped` 는 정확히 **빠지지 않은 후보**를 센다.
     * 같은 후보가 반복해 돌아오면 `total == batchSize` 가 영원히 참이라 루프가 임대와 소유자 연결을
     * 쥔 채 **영구히 돈다** — 조용한 미완보다 나쁜 실패 모양이다(cron 이 끝나지 않는 프로세스를 받고,
     * 그 뒤 모든 run 이 `LEASE_BUSY` 로 떨어져 파기까지 멈춘다).
     *
     * 그래서 종료 조건에 **「진척이 없으면 끝낸다」**를 둔다: 가득 찬 일괄인데 한 행도 처리하지 못했다면
     * cutoff 가 고정이고 후보 순서가 결정적이라 다음 일괄도 같다.
     */
    @Test
    fun `가득 찬 일괄이 진척 0 이면 끝난다 — 임대를 쥔 채 영구히 돌지 않는다`() {
        val store =
            FakeRetentionStore(
                targets = listOf(RetentionTarget("raw_observation", RetentionTreatment.MASK_PII)),
                stuckBatch = true,
            )

        val outcome = PurgeExpiredData(store, Clock { NOW }, policy(batchSize = 10)).run()

        store.maskCalls.size shouldBe 1
        val report = outcome.shouldBeInstanceOf<RetentionOutcome.Completed>().report
        report.maskSkippedTotal shouldBe 10
        report.maskedTotal shouldBe 0
    }
}
