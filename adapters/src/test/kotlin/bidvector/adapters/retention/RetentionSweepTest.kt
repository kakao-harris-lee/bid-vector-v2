package bidvector.adapters.retention

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.sharedkernel.Resolution
import bidvector.workflow.retention.PurgeExpiredData
import bidvector.workflow.retention.RETENTION_POLICY
import bidvector.workflow.retention.RetentionOutcome
import bidvector.workflow.retention.RetentionPolicyData
import bidvector.workflow.retention.RetentionRefusal
import bidvector.workflow.retention.RetentionTreatment
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 경계와 같은 시각을 cutoff 로 만드는 시계 — 술어가 `<` 인지 `<=` 인지가 여기서 갈린다. */
private val CUTOFF_EXACTLY_AT_OLDEST: Instant = JUST_OLDER + RETENTION_ANCHOR

/** 파기 기대 행 수 변화 — 파기 대상은 한 쌍 가운데 하나(기한 넘은 쪽)만 사라진다. */
private const val ONE_ROW_PURGED = -1L

/**
 * `outbox` 는 한 쌍마다 상태 다섯을 심고, 기한 넘은 쪽의 **`DELIVERED` 하나**만 사라진다
 * (D-6B3-3 ③ — `ISOLATED` 는 미발송이라 A-4 의 「미발송 제외」에 든다).
 */
private const val ONE_OUTBOX_ROW_PURGED = -1L

/**
 * P-3 — 보존 러너의 실 SQL 축(경계 ±1초 · 통제군 · 제외 표 불변 · FK 순서 · 분류 등식).
 *
 * adapters test 는 **소유자로 돈다**(`OPEN-6E2A-ADAPTER-TESTS-RUN-AS-OWNER`) — 그것이 바로
 * 출하 배열이다(운영자 결정 A-1: 파기는 풀을 지나지 않는 소유자 비풀링 연결이 한다). 앱 역할이
 * 지우지 못한다는 축은 권한 행렬(`CleanMigrationPrivilegeTest`)과 트리거 거동
 * (`RawObservationMaskingGuardTest`)이 따로 진다.
 */
class RetentionSweepTest : PersistenceTestSupport() {
    private lateinit var fixture: RetentionFixture

    @BeforeEach
    fun seedBothSidesOfTheBoundary() {
        fixture = RetentionFixture(dataSource())
        fixture.seedAll("old", JUST_OLDER)
        fixture.seedAll("new", JUST_NEWER)
    }

    @Test
    fun `기한을 1초 넘긴 행은 사라지고 1초 안쪽 행은 남는다`() {
        val before = fixture.countsByTable()

        val outcome = sweepAt(NOW)

        outcome.shouldBeInstanceOf<RetentionOutcome.Completed>()
        deltaOf(before, fixture.countsByTable()) shouldBe expectedPurgeDelta()
    }

    @Test
    fun `통제군 — cutoff 와 같은 시각의 행도 남는다 (술어는 strict less than)`() {
        val before = fixture.countsByTable()

        val outcome = sweepAt(CUTOFF_EXACTLY_AT_OLDEST)

        outcome.shouldBeInstanceOf<RetentionOutcome.Completed>().report.purgedTotal shouldBe 0
        deltaOf(before, fixture.countsByTable()).values.toSet() shouldBe setOf(0L)
    }

    @Test
    fun `제외 표는 한 행도 줄지 않는다`() {
        val exempt =
            RETENTION_TABLE_RULES.filterIsInstance<RetentionRule.Exempt>().map { it.table }
        val before = fixture.countsByTable()

        sweepAt(NOW)

        val after = fixture.countsByTable()
        exempt.associateWith { after.getValue(it) - before.getValue(it) }.values.toSet() shouldBe setOf(0L)
    }

    @Test
    fun `마스킹 대상 표는 행 수가 줄지 않는다 — 파기하지 않는다 (A-3)`() {
        val before = fixture.countsByTable()

        sweepAt(NOW)

        fixture.countsByTable().getValue("raw_observation") shouldBe before.getValue("raw_observation")
    }

    /**
     * D-6B3-3 ③ — **`DELIVERED` 하나만** 사라진다. `ISOLATED` 는 종단이지만 **한 번도 보내지지
     * 않은** 상태이고(relay 의 「알림을 영구히 잃는 사건」), A-4 의 문면이 미발송을 제외한다.
     * `FAILED` 와 같은 자리다 — 그 행은 잃은 알림의 유일한 흔적이다.
     */
    @Test
    fun `outbox 는 기한 넘은 DELIVERED 하나만 사라진다 — 미발송·격리·임대 중은 남는다`() {
        sweepAt(NOW)

        // 기한 넘은 쪽에서 남은 상태 넷 + 기한 안쪽의 다섯 전부.
        remainingOutboxEntries() shouldContainExactlyInAnyOrder
            listOf(
                "ret-old-PENDING",
                "ret-old-CLAIMED",
                "ret-old-FAILED",
                "ret-old-ISOLATED",
            ) + OUTBOX_STATE_VOCABULARY.map { "ret-new-$it" }
    }

    @Test
    fun `기한 안쪽 자식이 있으면 기한 넘은 부모를 남긴다 — FK 위반으로 run 을 중단시키지 않는다`() {
        fixture.seedLateChild(parentAt = JUST_OLDER, childAt = JUST_NEWER)

        val outcome = sweepAt(NOW)

        outcome.shouldBeInstanceOf<RetentionOutcome.Completed>()
        survivingOpeningResults() shouldContainExactlyInAnyOrder listOf("RET-new", "RET-fk")
    }

    /**
     * 우회 6 ⓑ 가 만드는 **대가의 수렴**을 잰다 — 가드가 남긴 부모가 영원히 남지 않는다.
     * 자식이 기한을 넘는 시계로 한 번 더 돌면, 순서가 자식 → 부모라 **같은 run 안에서** 자식이
     * 먼저 사라지고 부모 가드가 풀린다(두 run 이 더 필요하지 않다).
     */
    @Test
    fun `남은 부모는 자식이 기한을 넘는 다음 run 에서 사라진다 — 수렴한다`() {
        fixture.seedLateChild(parentAt = JUST_OLDER, childAt = JUST_NEWER)
        sweepAt(NOW)
        survivingOpeningResults() shouldContain "RET-fk"

        sweepAt(NOW + Duration.ofSeconds(2))

        survivingOpeningResults().shouldBeEmpty()
        survivingReservePrices().shouldBeEmpty()
    }

    /**
     * 운영자 결정 2026-10-14(D-6B3-3 ②, vr F-3) — **CASCADE 자식에도 기한 가드**. 헤더 upsert 는
     * `created_at` 을 유지하고 행은 delete-then-insert 로 새 `created_at` 을 받으므로, 120일 전
     * 생성된 헤더를 오늘 다시 저장하면 **행만 기한 안쪽**이 된다. 가드가 없으면
     * `ON DELETE CASCADE` 가 그 행을 함께 지운다 — 방어 ②(「기한 안쪽 행은 한 행도 사라지지
     * 않는다」)가 CASCADE 간선에서만 깨지는 자리였다.
     */
    @Test
    fun `기한 안쪽 CASCADE 자식이 있으면 기한 넘은 부모를 남긴다 — 자식도 산다`() {
        fixture.seedLateCascadeChild(parentAt = JUST_OLDER, childAt = JUST_NEWER)

        val outcome = sweepAt(NOW)

        outcome.shouldBeInstanceOf<RetentionOutcome.Completed>()
        survivingRequirementRows() shouldContain "RET-cascade"
        survivingRequirementHeaders() shouldContain "RET-cascade"
    }

    /** 수렴 — 그 자식이 기한을 넘는 run 에서 둘이 함께 사라진다(가드가 영구 잔류를 만들지 않는다). */
    @Test
    fun `기한 안쪽 CASCADE 자식이 기한을 넘으면 부모와 함께 사라진다 — 수렴한다`() {
        fixture.seedLateCascadeChild(parentAt = JUST_OLDER, childAt = JUST_NEWER)
        sweepAt(NOW)

        sweepAt(NOW + Duration.ofSeconds(2))

        survivingRequirementRows() shouldNotContain "RET-cascade"
        survivingRequirementHeaders() shouldNotContain "RET-cascade"
    }

    @Test
    fun `파기 순서는 자식부터다 — 보고가 그 순서를 싣는다`() {
        val report = sweepAt(NOW).shouldBeInstanceOf<RetentionOutcome.Completed>().report

        val order = report.deletionOrder
        (order.indexOf("opening_reserve_price") < order.indexOf("opening_result")) shouldBe true
    }

    @Test
    fun `분류 없는 표가 스키마에 생기면 한 행도 지우지 않는다`() {
        val before = fixture.countsByTable()
        createUnclassifiedTable()

        val outcome =
            try {
                sweepAt(NOW)
            } finally {
                dropUnclassifiedTable()
            }

        val refusal = outcome.shouldBeInstanceOf<RetentionOutcome.Refused>().reason
        refusal
            .shouldBeInstanceOf<RetentionRefusal.UnclassifiedTables>()
            .missingClassification shouldBe setOf("ret_unclassified")
        deltaOf(before, fixture.countsByTable()).values.toSet() shouldBe setOf(0L)
    }

    private fun sweepAt(at: Instant): RetentionOutcome =
        PurgeExpiredData(retentionStore(), Clock { at }, productionPolicy()).run()

    /** **출하 정책 값**을 그대로 쓴다 — 이 자리가 값과 거동을 잇는다. */
    private fun productionPolicy(): RetentionPolicyData =
        (RETENTION_POLICY.resolve(LocalDate.ofInstant(NOW, ZoneOffset.UTC)) as Resolution.Resolved).value

    private fun expectedPurgeDelta(): Map<String, Long> =
        RETENTION_TABLE_RULES.associate { rule ->
            rule.table to
                when {
                    rule.table == "outbox" -> ONE_OUTBOX_ROW_PURGED

                    rule.treatment == RetentionTreatment.PURGE -> ONE_ROW_PURGED

                    // 부모가 사라지면 FK ON DELETE CASCADE 로 함께 사라진다.
                    rule.treatment == RetentionTreatment.CASCADE_WITH_PARENT -> ONE_ROW_PURGED

                    else -> 0L
                }
        }

    private fun deltaOf(
        before: Map<String, Long>,
        after: Map<String, Long>,
    ): Map<String, Long> = before.mapValues { (table, count) -> after.getValue(table) - count }

    private fun remainingOutboxEntries(): List<String> = queryColumn("SELECT entry_id FROM outbox")

    private fun survivingOpeningResults(): List<String> = queryColumn("SELECT notice_number FROM opening_result")

    private fun survivingReservePrices(): List<String> = queryColumn("SELECT notice_number FROM opening_reserve_price")

    private fun survivingRequirementRows(): List<String> =
        queryColumn("SELECT notice_number FROM notice_requirement_row")

    private fun survivingRequirementHeaders(): List<String> =
        queryColumn("SELECT notice_number FROM notice_requirement")

    private fun createUnclassifiedTable() = execute("CREATE TABLE ret_unclassified (id INT PRIMARY KEY)")

    private fun dropUnclassifiedTable() = execute("DROP TABLE ret_unclassified")

    private fun execute(sql: String) =
        dataSource().connection.use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }

    private fun queryColumn(sql: String): List<String> =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    val values = mutableListOf<String>()
                    while (rs.next()) values += rs.getString(1)
                    values
                }
            }
        }

    /** 보존 저장소 — 마스킹이 지울 키를 **계약**에 물으므로 레지스트리를 함께 받는다(D-6B3-3 ①). */
    private fun retentionStore() = JdbcRetentionStore(dataSource(), testFieldContracts())
}
