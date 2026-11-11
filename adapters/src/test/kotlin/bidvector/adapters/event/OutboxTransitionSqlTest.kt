package bidvector.adapters.event

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.event.OutboxEntryState
import bidvector.workflow.event.OutboxTerminalReason
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException

/**
 * 설계 검토 (3) 미달 위험 1 — `adapters` test는
 * [bidvector.workflow.event.OutboxTransition]의 하위 타입을 지을 수 없어(`internal
 * constructor`) 그 port 메서드를 부르는 test를 쓸 수 없다. 그래서 이 test는 production과
 * **같은 [EventSql] 상수**(사본 아님)를 직접 실행해 전이 UPDATE의 효과와 `WHERE state`
 * 거부(잘못된 이전 상태에서는 영향 행 0, 상태 불변)를 잰다.
 *
 * **M9/OPS-05 — 종단 셋은 사유를 함께 싣는다.** 같은 상수가 이제 인자 둘(사유·entry_id)을
 * 받고, V26 의 CHECK·트리거가 「사유 없는 종단」·「상태와 어긋난 사유」·「사유 바꿔치기」를
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
                    }.message.orEmpty() shouldContain PAIR_CHECK
                }

            setStateAndReason(entryId, reason.terminalState.dbToken(), reason.name)
            outboxTerminalReasonOf(dataSource(), entryId) shouldBe reason.name
        }
    }

    /**
     * **집합 등식 — 받아들여지는 쌍을 거동으로 센다**(verifier r10 M-2). 거부 실측(위 test)은 아는
     * 토큰만 겨누므로 저장 층이 **모르는 쌍을 하나 더 받아들이게** 넓어져도 초록이다. 앞 판은 그
     * 구멍을 제약 **본문의 정규식 토큰**을 둘씩 짝지어 닫으려 했는데, 그 술어는 렌더링 텍스트와
     * 토큰 순서에 걸려 있어 **토큰을 늘리지 않는 확장**을 통과시켰다 — `OR state = terminal_reason`
     * 한 줄은 `(FAILED, 'FAILED')` 를 받아들이면서 그 test 를 초록으로 지난다(verifier 변이 BD2).
     *
     * 그래서 여기서는 **전수 UPDATE 를 실제로 시도하고 savepoint 로 되돌려** 받아들여진 집합을
     * 센다. 단언에 들어오는 것은 통과한 쌍뿐이고 렌더링 모양도 토큰 순서도 들어오지 않는다. 재는
     * 집합은 CHECK 와 트리거를 **함께 지난** 것이다 — 운영 경로가 만나는 관문이 그 둘의 합이다.
     *
     * 후보 사유 어휘는 셋의 합집합이다. ① Kotlin 사유 어휘 ② 상태 토큰 — 동일성 확장이 여는 쌍이
     * 여기 있다 ③ **제약 본문에 적힌 리터럴** — 본문만 아는 토큰(몰래 넓힌 어휘)까지 겨눈다. ③ 은
     * 후보를 **늘리는 데만** 쓰이므로 렌더링이 바뀌어도 단언은 흔들리지 않고, 그 수확이 비면 그
     * 사실로 실패한다(후보가 조용히 좁아지는 자리).
     */
    @Test
    fun `저장 층이 받아들이는 (상태, 사유) 쌍 집합이 Kotlin 어휘 전수와 같다 — 전수 시도`() {
        acceptedStateReasonPairs() shouldBe expectedAcceptedPairs()
    }

    /** 종단 쌍 일곱 + **사유 없는 비종단** 둘. 그 밖의 조합은 CHECK 또는 트리거가 거부한다. */
    private fun expectedAcceptedPairs(): Set<Pair<String, String?>> =
        OutboxTerminalReason.entries.map { it.terminalState.dbToken() to it.name }.toSet() +
            NON_TERMINAL_OUTBOX_STATES.map { it.dbToken() to null }

    private fun acceptedStateReasonPairs(): Set<Pair<String, String?>> {
        insertPendingOutboxRow(dataSource(), entryId = PAIRS_ENTRY, idempotencyKey = "t-pairs-key")
        executeUpdate(EventSql.MARK_CLAIMED, PAIRS_ENTRY)
        val reasons = candidateReasonTokens()
        val accepted = mutableSetOf<Pair<String, String?>>()
        dataSource().connection.use { connection ->
            connection.autoCommit = false
            try {
                probeStateTokens().forEach { state ->
                    reasons.filter { accepts(connection, state, it) }.forEach { accepted += state to it }
                }
            } finally {
                connection.rollback()
            }
        }
        return accepted
    }

    /**
     * 한 조합을 **실제로 시도하고 되돌린다** — 영향 행 1 이 수용의 정의다. savepoint 로 감싸는
     * 이유는 거부가 트랜잭션을 실패 상태로 밀기 때문이다(그러면 뒤 조합이 전부 같은 사유로
     * 거부돼 「아무것도 받아들이지 않는다」가 된다).
     */
    private fun accepts(
        connection: Connection,
        state: String,
        reason: String?,
    ): Boolean {
        val savepoint = connection.setSavepoint("pair_probe")
        return try {
            connection.prepareStatement(SET_STATE_AND_REASON).use { statement ->
                statement.setString(1, state)
                statement.setString(2, reason)
                statement.setString(3, PAIRS_ENTRY)
                statement.executeUpdate() == 1
            }
        } catch (_: SQLException) {
            false
        } finally {
            connection.rollback(savepoint)
            connection.releaseSavepoint(savepoint)
        }
    }

    /** 상태 어휘 전수 — 종단 셋은 사유 어휘가 정하고 비종단 둘이 그 여집합이다. */
    private fun probeStateTokens(): List<String> =
        (OutboxTerminalReason.entries.map { it.terminalState } + NON_TERMINAL_OUTBOX_STATES)
            .distinct()
            .map { it.dbToken() }

    private fun candidateReasonTokens(): List<String?> {
        val literals =
            Regex("'([^']*)'").findAll(constraintDefinition(PAIR_CHECK)).map { it.groupValues[1] }.toList()
        check(literals.isNotEmpty()) { "제약 본문에 리터럴이 없다 — 후보 어휘 ③ 이 조용히 비었다" }
        return (OutboxTerminalReason.entries.map { it.name } + probeStateTokens() + literals).distinct() + null
    }

    @Test
    fun `어휘 밖 사유는 CHECK 가 거부한다`() {
        insertPendingOutboxRow(dataSource(), entryId = "t-vocab-1", idempotencyKey = "t-vocab-key-1")

        val failure = shouldThrow<SQLException> { setStateAndReason("t-vocab-1", "FAILED", "DELIVERY_TIMED_OUT") }

        failure.message.orEmpty() shouldContain PAIR_CHECK
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

    /**
     * **INSERT 갈래의 음성 대조**(verifier M-1). V26 머리말은 「이 표에 다른 경로로 들어오는
     * INSERT·UPDATE 도 같은 쌍만 쓴다」를 약속하는데, 출하 경로는 `PENDING` 으로만 INSERT 하므로
     * 그 약속을 재는 자리가 없었다 — 트리거를 `BEFORE UPDATE` 로 좁히거나 INSERT 갈래를 지우거나
     * 요구 집합에서 `DELIVERED` 를 빼도 adapters 전체가 초록이었다(변이 `gap_all` 실측).
     *
     * 종단 셋을 **어휘에서 받아** 전수로 돈다(값 목록을 베끼지 않는다). `DELIVERED` 를 빼고 재면
     * 요구 집합에서 그 하나만 빠지는 변이가 살아남는다.
     */
    @Test
    fun `사유 없는 종단 INSERT 는 트리거가 거부한다 — 종단 셋 전수`() {
        val terminalStates = OutboxTerminalReason.entries.map { it.terminalState.dbToken() }.distinct()

        terminalStates shouldHaveSize 3
        terminalStates.forEachIndexed { index, state ->
            val rejected = "t-insert-noreason-$index"
            shouldThrow<SQLException> {
                insertOutboxRowDirectly(
                    dataSource(),
                    entryId = rejected,
                    idempotencyKey = "t-insert-noreason-key-$index",
                    gate = OutboxGateValues(state = state),
                )
            }.message.orEmpty() shouldContain "종단 전이는 사유를 요구한다"
            outboxStateOf(dataSource(), rejected) shouldBe null

            // 양성 대조 — 같은 INSERT 가 사유를 싣고 오면 선다(관문이 INSERT 를 늘 막는 것이 아니다).
            val accepted = "t-insert-reason-$index"
            val reason = OutboxTerminalReason.entries.first { it.terminalState.dbToken() == state }
            insertOutboxRowDirectly(
                dataSource(),
                entryId = accepted,
                idempotencyKey = "t-insert-reason-key-$index",
                gate = OutboxGateValues(state = state, terminalReason = reason.name),
            )
            outboxStateOf(dataSource(), accepted) shouldBe state
            outboxTerminalReasonOf(dataSource(), accepted) shouldBe reason.name
        }
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
            connection.prepareStatement(SET_STATE_AND_REASON).use { statement ->
                statement.setString(1, state)
                statement.setString(2, reason)
                statement.setString(3, entryId)
                statement.executeUpdate()
            }
        }
    }

    private companion object {
        /** 쌍 CHECK 의 이름 — 거부 문면 대조와 본문 수확이 같은 자리를 가리킨다. */
        const val PAIR_CHECK = "outbox_terminal_reason_state_check"

        /** 전수 시도가 돌려 쓰는 행 하나 — `CLAIMED` · 사유 `NULL` 에서 매 조합이 출발한다. */
        const val PAIRS_ENTRY = "t-pairs-1"

        const val SET_STATE_AND_REASON = "UPDATE outbox SET state = ?, terminal_reason = ? WHERE entry_id = ?"
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

/**
 * 비종단 상태 — 종단 셋은 [OutboxTerminalReason] 이 정하므로 그 여집합이 여기 둘이다. 상태 어휘가
 * 늘면 위 [dbToken] 의 소진 `when` 이 깨져 **이 파일로** 오게 된다(그 자리에서 이 목록도 본다).
 */
private val NON_TERMINAL_OUTBOX_STATES =
    listOf(OutboxEntryState.Pending, OutboxEntryState.Claimed)
