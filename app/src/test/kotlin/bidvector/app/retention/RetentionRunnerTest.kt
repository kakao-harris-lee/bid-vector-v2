package bidvector.app.retention

import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.sharedkernel.Resolution
import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.retention.MaskBatchCounts
import bidvector.workflow.retention.PurgeExpiredData
import bidvector.workflow.retention.RETENTION_POLICY
import bidvector.workflow.retention.RetentionCutoff
import bidvector.workflow.retention.RetentionPolicyData
import bidvector.workflow.retention.RetentionStorePort
import bidvector.workflow.retention.RetentionTarget
import bidvector.workflow.retention.RetentionTreatment
import bidvector.workflow.retention.RetentionWriter
import bidvector.workflow.retention.TableDependency
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val NOW = Instant.parse("2026-10-14T00:00:00Z")
private const val RETENTION_DAYS = 90

/** 예외 메시지에 심는 자리값 — 로그 줄에 이것이 나오면 원 예외가 로그 경로에 올라간 것이다. */
private const val LEAKY_MESSAGE = "SECRET-ROW-VALUE-FIXTURE"

/**
 * 호출을 흉내내는 저장소 — [purgeBatch]·[maskBatch] 가 한 번만 응답하고 그 뒤 0 이다
 * (use case 의 일괄 루프가 끝나게).
 */
private class StubStore(
    private val targets: List<RetentionTarget>,
    private val stored: Set<String> = targets.map { it.table }.toSet(),
    private val leaseBusy: Boolean = false,
    private val purgeBatch: Int = 0,
    private val maskBatch: MaskBatchCounts = MaskBatchCounts(0, 0),
    private val thrown: Exception? = null,
) : RetentionStorePort,
    RetentionWriter {
    private var purgeServed = false
    private var maskServed = false

    override fun classifiedTargets(): List<RetentionTarget> = targets

    override fun storedTables(): Set<String> = stored

    override fun dependencies(): Set<TableDependency> = emptySet()

    override fun <T> withExclusiveLease(body: (RetentionWriter) -> T): LeaseAttempt<T> {
        if (leaseBusy) return LeaseAttempt.Busy
        return LeaseAttempt.Held(body(this))
    }

    override fun purge(
        target: RetentionTarget,
        cutoff: RetentionCutoff,
        batchSize: Int,
    ): Int {
        thrown?.let { throw it }
        if (purgeServed) return 0
        purgeServed = true
        return purgeBatch
    }

    override fun mask(
        target: RetentionTarget,
        cutoff: RetentionCutoff,
        batchSize: Int,
    ): MaskBatchCounts {
        if (maskServed) return MaskBatchCounts(0, 0)
        maskServed = true
        return maskBatch
    }
}

/**
 * P-3 — 러너는 결과를 **종료 코드와 한 줄의 로그**로 옮긴다. cron 이 그 코드로 처방을 가르므로
 * 네 결과가 한 값으로 접히지 않아야 한다.
 */
class RetentionRunnerTest {
    private val lines = mutableListOf<String>()
    private val exitCodes = mutableListOf<Int>()

    private fun runWith(store: StubStore) {
        RetentionRunner(
            // 정책 값을 **만들지 않는다** — 생성자가 `workflow` 의 `internal` 이다(cr M-2 · vr F-1).
            // 이 자리에서 90일을 흉내낼 수 없다는 것이 그 벽의 실측이기도 하다.
            purge = PurgeExpiredData(store, { NOW }, productionPolicy()),
            log = CollectionLog { lines += it },
            termination = CollectionTermination { exitCodes += it },
        ).run(DefaultApplicationArguments())
    }

    private fun productionPolicy(): RetentionPolicyData =
        (RETENTION_POLICY.resolve(LocalDate.ofInstant(NOW, ZoneOffset.UTC)) as Resolution.Resolved).value

    @Test
    fun `파기만 하고 판독 실패가 없으면 0 이다`() {
        runWith(StubStore(listOf(RetentionTarget("notice", RetentionTreatment.PURGE)), purgeBatch = 3))

        exitCodes shouldBe listOf(RetentionExitCode.COMPLETE.value)
        lines.last() shouldContain "purged=3"
        lines.last() shouldContain "exit=COMPLETE(0)"
    }

    @Test
    fun `판독 실패가 하나라도 있으면 미완이다 — cron 이 0 으로 읽으면 신호가 사라진다`() {
        runWith(
            StubStore(
                listOf(RetentionTarget("raw_observation", RetentionTreatment.MASK_PII)),
                maskBatch = MaskBatchCounts(masked = 2, failClosed = 1),
            ),
        )

        exitCodes shouldBe listOf(RetentionExitCode.INCOMPLETE.value)
        lines.last() shouldContain "maskFailClosed=1"
    }

    @Test
    fun `임대를 못 쥐면 사유를 남기고 다른 코드로 끝난다 — 기다리면 풀리는 상황이다`() {
        runWith(StubStore(listOf(RetentionTarget("notice", RetentionTreatment.PURGE)), leaseBusy = true))

        exitCodes shouldBe listOf(RetentionExitCode.LEASE_BUSY.value)
        lines.last() shouldContain "reason=LEASE_BUSY"
    }

    @Test
    fun `분류 등식이 깨지면 거부 코드로 끝나고 어느 표가 빠졌는지 남긴다`() {
        runWith(
            StubStore(
                targets = listOf(RetentionTarget("notice", RetentionTreatment.PURGE)),
                stored = setOf("notice", "v19_new_table"),
            ),
        )

        exitCodes shouldBe listOf(RetentionExitCode.REFUSED.value)
        lines.last() shouldContain "UNCLASSIFIED_TABLES"
        lines.last() shouldContain "v19_new_table"
    }

    @Test
    fun `실패는 정제된 원인 코드만 남긴다 — 예외 메시지를 로그로 흘리지 않는다`() {
        runWith(
            StubStore(
                listOf(RetentionTarget("notice", RetentionTreatment.PURGE)),
                thrown = SQLException(LEAKY_MESSAGE, "42P01"),
            ),
        )

        exitCodes shouldBe listOf(RetentionExitCode.FAILED.value)
        lines.last() shouldContain "sqlState=42P01"
        lines.last() shouldNotContain LEAKY_MESSAGE
    }

    @Test
    fun `보존 기간이 시작 줄에 실린다 — 어느 정책으로 돌았는지 로그만 보고 안다`() {
        runWith(StubStore(emptyList(), stored = emptySet()))

        lines.first() shouldContain "retentionDays=$RETENTION_DAYS"
    }
}
