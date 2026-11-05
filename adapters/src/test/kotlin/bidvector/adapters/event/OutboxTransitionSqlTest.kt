package bidvector.adapters.event

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.event.OutboxEntryState
import bidvector.workflow.event.OutboxTerminalReason
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * 설계 검토 (3) 미달 위험 1 — `adapters` test는
 * [bidvector.workflow.event.OutboxTransition]의 하위 타입을 지을 수 없어(`internal
 * constructor`) 그 port 메서드를 부르는 test를 쓸 수 없다. 그래서 이 test는 production과
 * **같은 [EventSql] 상수**(사본 아님)를 직접 실행해 전이 UPDATE의 효과와 `WHERE state`
 * 거부(잘못된 이전 상태에서는 영향 행 0, 상태 불변)를 잰다.
 *
 * **M9/OPS-05 — 종단 셋은 사유를 함께 싣는다.** 같은 상수가 이제 인자 둘(사유·entry_id)을
 * 받고, V27 의 CHECK·트리거가 「사유 없는 종단」·「상태와 어긋난 사유」·「사유 바꿔치기」를
 * 거부한다. 어휘 전수는 [OutboxTerminalReason] 에서 받아 돈다 — 값 목록을 베끼지 않는다.
 */
class OutboxTransitionSqlTest : PersistenceTestSupport() {
    private fun executeUpdate(
        sql: String,
        entryId: String,
    ): Int =
        dataSource().connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, entryId)
                statement.executeUpdate()
            }
        }

    /** 종단 전이 — production `JdbcOutboxPort.transitionState` 와 같은 인자 순서(사유·entry_id). */
    private fun terminate(
        sql: String,
        reason: OutboxTerminalReason,
        entryId: String,
    ): Int =
        dataSource().connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, reason.name)
                statement.setString(2, entryId)
                statement.executeUpdate()
            }
        }

    @Test
    fun `MARK_CLAIMED 는 Pending 행만 옮기고 영향 행 1이다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-claim-1", idempotencyKey = "t-claim-key-1")

        val affected = executeUpdate(EventSql.MARK_CLAIMED, "t-claim-1")

        affected shouldBe 1
        outboxStateOf(dataSource(), "t-claim-1") shouldBe "CLAIMED"
    }

    @Test
    fun `MARK_CLAIMED 는 이미 Claimed 인 행을 다시 옮기지 않는다 — WHERE state 거부`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-claim-2", idempotencyKey = "t-claim-key-2")
        executeUpdate(EventSql.MARK_CLAIMED, "t-claim-2")

        val affected = executeUpdate(EventSql.MARK_CLAIMED, "t-claim-2")

        affected shouldBe 0
        outboxStateOf(dataSource(), "t-claim-2") shouldBe "CLAIMED"
    }

    @Test
    fun `claim 은 사유를 적지 않는다 — 비종단 행의 사유는 NULL 이다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-claim-3", idempotencyKey = "t-claim-key-3")

        executeUpdate(EventSql.MARK_CLAIMED, "t-claim-3")

        outboxTerminalReasonOf(dataSource(), "t-claim-3") shouldBe null
    }

    @Test
    fun `MARK_DELIVERED 는 Claimed 행만 옮기고 사유를 함께 적는다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-delivered-1", idempotencyKey = "t-delivered-key-1")
        executeUpdate(EventSql.MARK_CLAIMED, "t-delivered-1")

        val affected = terminate(EventSql.MARK_DELIVERED, OutboxTerminalReason.DELIVERED, "t-delivered-1")

        affected shouldBe 1
        outboxStateOf(dataSource(), "t-delivered-1") shouldBe "DELIVERED"
        outboxTerminalReasonOf(dataSource(), "t-delivered-1") shouldBe "DELIVERED"
    }

    @Test
    fun `MARK_DELIVERED 는 Pending 행을 옮기지 않는다 — 종단 전이표 밖 거부`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-delivered-2", idempotencyKey = "t-delivered-key-2")

        val affected = terminate(EventSql.MARK_DELIVERED, OutboxTerminalReason.DELIVERED, "t-delivered-2")

        affected shouldBe 0
        outboxStateOf(dataSource(), "t-delivered-2") shouldBe "PENDING"
        outboxTerminalReasonOf(dataSource(), "t-delivered-2") shouldBe null
    }

    @Test
    fun `같은 DELIVERED 상태의 두 사유가 서로 구별돼 남는다 — 중복 생략도 전달이다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-dup-1", idempotencyKey = "t-dup-key-1")
        executeUpdate(EventSql.MARK_CLAIMED, "t-dup-1")

        terminate(EventSql.MARK_DELIVERED, OutboxTerminalReason.DUPLICATE_SKIPPED, "t-dup-1")

        outboxStateOf(dataSource(), "t-dup-1") shouldBe "DELIVERED"
        outboxTerminalReasonOf(dataSource(), "t-dup-1") shouldBe "DUPLICATE_SKIPPED"
    }

    @Test
    fun `MARK_FAILED 는 Claimed 행만 옮기고, Delivered 로 옮긴 뒤에는 다시 옮기지 않는다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-failed-1", idempotencyKey = "t-failed-key-1")
        executeUpdate(EventSql.MARK_CLAIMED, "t-failed-1")
        terminate(EventSql.MARK_DELIVERED, OutboxTerminalReason.DELIVERED, "t-failed-1")

        val affected = terminate(EventSql.MARK_FAILED, OutboxTerminalReason.DELIVERY_REJECTED, "t-failed-1")

        affected shouldBe 0
        outboxStateOf(dataSource(), "t-failed-1") shouldBe "DELIVERED"
        outboxTerminalReasonOf(dataSource(), "t-failed-1") shouldBe "DELIVERED"
    }

    @Test
    fun `MARK_ISOLATED 는 Claimed 행만 옮기고 영향 행 1이다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-isolated-1", idempotencyKey = "t-isolated-key-1")
        executeUpdate(EventSql.MARK_CLAIMED, "t-isolated-1")

        val affected = terminate(EventSql.MARK_ISOLATED, OutboxTerminalReason.UNKNOWN_PAYLOAD, "t-isolated-1")

        affected shouldBe 1
        outboxStateOf(dataSource(), "t-isolated-1") shouldBe "ISOLATED"
        outboxTerminalReasonOf(dataSource(), "t-isolated-1") shouldBe "UNKNOWN_PAYLOAD"
    }

    @Test
    fun `Isolated 에서는 어느 전이도 다시 나가지 않는다 — 종단 재실행 없음`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-isolated-2", idempotencyKey = "t-isolated-key-2")
        executeUpdate(EventSql.MARK_CLAIMED, "t-isolated-2")
        terminate(EventSql.MARK_ISOLATED, OutboxTerminalReason.ORPHANED_CLAIM, "t-isolated-2")

        terminate(EventSql.MARK_DELIVERED, OutboxTerminalReason.DELIVERED, "t-isolated-2") shouldBe 0
        terminate(EventSql.MARK_FAILED, OutboxTerminalReason.DELIVERY_REJECTED, "t-isolated-2") shouldBe 0
        outboxStateOf(dataSource(), "t-isolated-2") shouldBe "ISOLATED"
        outboxTerminalReasonOf(dataSource(), "t-isolated-2") shouldBe "ORPHANED_CLAIM"
    }

    /**
     * 사유 전수 — 어휘 일곱 각각이 **자기 종단 상태에서만** 받아들여진다. production SQL 은
     * 상태를 리터럴로 들고 있어 짝이 어긋난 조합을 낼 수 없으므로, 여기서는 CHECK 자신을
     * 겨눠 UPDATE 를 직접 짓는다. 맞는 쌍 하나를 이어서 통과시켜 **술어가 늘 거부만 하는
     * 회귀**를 막는다(양성 대조).
     */
    @Test
    fun `사유와 상태가 어긋난 쌍은 CHECK 가 거부한다 — 어휘 일곱 전수`() {
        val terminalStates = listOf("DELIVERED", "FAILED", "ISOLATED")

        OutboxTerminalReason.entries.forEachIndexed { index, reason ->
            val entryId = "t-pair-$index"
            insertPendingOutboxRow(dataSource(), entryId = entryId, idempotencyKey = "t-pair-key-$index")
            terminalStates
                .filter { it != reason.terminalState.dbToken() }
                .forEach { mismatched ->
                    shouldThrow<SQLException> {
                        setStateAndReason(entryId, mismatched, reason.name)
                    }.message.orEmpty() shouldContain "outbox_terminal_reason_state_check"
                }

            setStateAndReason(entryId, reason.terminalState.dbToken(), reason.name)
            outboxTerminalReasonOf(dataSource(), entryId) shouldBe reason.name
        }
    }

    /**
     * **집합 등식** — 거부 실측(위 test)은 아는 토큰만 겨누므로, CHECK 가 **모르는 쌍을 하나
     * 더 받아들이게** 넓어져도 초록이다. 그래서 제약 본문에서 쌍을 뽑아 Kotlin 어휘 전수와
     * 같은지 본다(본문 문자열 자체를 고정하지 않는다 — 그 형태는 PostgreSQL 이 정한다).
     */
    @Test
    fun `CHECK 가 허용하는 (상태, 사유) 쌍 집합이 Kotlin 어휘 전수와 같다`() {
        val definition = constraintDefinition("outbox_terminal_reason_state_check")

        val tokens = Regex("'([A-Z_]+)'::text").findAll(definition).map { it.groupValues[1] }.toList()
        val pairs = tokens.chunked(2).map { (state, reason) -> state to reason }.toSet()

        pairs shouldBe OutboxTerminalReason.entries.map { it.terminalState.dbToken() to it.name }.toSet()
    }

    @Test
    fun `어휘 밖 사유는 CHECK 가 거부한다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-vocab-1", idempotencyKey = "t-vocab-key-1")

        val failure = shouldThrow<SQLException> { setStateAndReason("t-vocab-1", "FAILED", "DELIVERY_TIMED_OUT") }

        failure.message.orEmpty() shouldContain "outbox_terminal_reason_state_check"
    }

    @Test
    fun `사유 없이 종단으로 옮기는 갱신은 트리거가 거부한다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-noreason-1", idempotencyKey = "t-noreason-key-1")
        executeUpdate(EventSql.MARK_CLAIMED, "t-noreason-1")

        val failure =
            shouldThrow<SQLException> {
                executeUpdate("UPDATE outbox SET state = 'FAILED' WHERE entry_id = ?", "t-noreason-1")
            }

        failure.message.orEmpty() shouldContain "종단 전이는 사유를 요구한다"
        outboxStateOf(dataSource(), "t-noreason-1") shouldBe "CLAIMED"
    }

    @Test
    fun `적힌 사유는 바꿀 수도 지울 수도 없다 — 트리거가 거부한다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-once-1", idempotencyKey = "t-once-key-1")
        executeUpdate(EventSql.MARK_CLAIMED, "t-once-1")
        terminate(EventSql.MARK_ISOLATED, OutboxTerminalReason.DELIVERY_OUTCOME_UNKNOWN, "t-once-1")

        shouldThrow<SQLException> {
            setStateAndReason("t-once-1", "ISOLATED", "ORPHANED_CLAIM")
        }.message.orEmpty() shouldContain "한 번만 적는다"
        shouldThrow<SQLException> {
            setStateAndReason("t-once-1", "ISOLATED", null)
        }.message.orEmpty() shouldContain "한 번만 적는다"

        outboxTerminalReasonOf(dataSource(), "t-once-1") shouldBe "DELIVERY_OUTCOME_UNKNOWN"
    }

    private fun constraintDefinition(name: String): String =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?")
                .use { statement ->
                    statement.setString(1, name)
                    statement.executeQuery().use { rs ->
                        check(rs.next()) { "제약을 찾지 못했다: $name" }
                        rs.getString(1)
                    }
                }
        }

    private fun setStateAndReason(
        entryId: String,
        state: String,
        reason: String?,
    ) {
        dataSource().connection.use { connection ->
            connection
                .prepareStatement("UPDATE outbox SET state = ?, terminal_reason = ? WHERE entry_id = ?")
                .use { statement ->
                    statement.setString(1, state)
                    statement.setString(2, reason)
                    statement.setString(3, entryId)
                    statement.executeUpdate()
                }
        }
    }
}

/** 상태 어휘의 DB 토큰 — V6 CHECK 가 쓰는 이름이고 어댑터의 `state` 리터럴과 같은 값이다. */
private fun OutboxEntryState.dbToken(): String =
    when (this) {
        OutboxEntryState.Pending -> "PENDING"
        OutboxEntryState.Claimed -> "CLAIMED"
        OutboxEntryState.Delivered -> "DELIVERED"
        OutboxEntryState.Failed -> "FAILED"
        OutboxEntryState.Isolated -> "ISOLATED"
    }
