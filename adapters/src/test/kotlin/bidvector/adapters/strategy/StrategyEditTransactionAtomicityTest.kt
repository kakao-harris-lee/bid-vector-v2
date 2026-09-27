package bidvector.adapters.strategy

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.strategy.BudgetBoundInclusivity
import bidvector.strategy.ScoreRange
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.BeginOutcome
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.CommandId
import bidvector.workflow.strategy.CommandResult
import bidvector.workflow.strategy.EditCommand
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.EditSessionPolicyData
import bidvector.workflow.strategy.EditableField
import bidvector.workflow.strategy.OperatorId
import bidvector.workflow.strategy.TransitionOutcome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.SQLException
import java.time.Duration
import java.time.Instant

private val OPERATOR = OperatorId("op-atomic-edit")
private val ACTOR = Actor.Operator(OPERATOR)
private val SESSION = EditSessionId("session-atomic-1")
private val FIXED_NOW: Instant = Instant.parse("2026-09-26T00:00:00Z")

private class FixedClock : Clock {
    override fun now(): Instant = FIXED_NOW
}

/**
 * D-6A2b-3 판정 — **전략 저장 · outbox 등록 · 세션 전진이 한 커밋**이다. 4A 가 알려진
 * 제한으로 남긴 잔여 창(발행 실패 뒤 세션 미전진)을 이 자리에서 닫았는지 실 DB 로 잰다.
 *
 * 장애는 **production 에 구멍을 내지 않고** 주입한다 — 코드에 실패 훅을 심는 대신 DB 에
 * `outbox` INSERT 를 거부하는 트리거를 잠깐 건다(설계 검토 우회 ⑤). 그래서 실패 지점이
 * 실제 SQL 실행 자리이고, 조립·경계·어댑터가 전부 production 그대로다.
 */
class StrategyEditTransactionAtomicityTest : PersistenceTestSupport() {
    private fun policy(): Resolution.Resolved<StrategyPolicyData> =
        Resolution.Resolved(
            StrategyPolicyData(
                matchScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
                probabilityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
                priorityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
                budgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
            ),
            PolicyVersion(EffectiveFrom.Initial, "test-edit-transaction"),
        )

    private fun transaction(): JdbcStrategyEditTransaction =
        JdbcStrategyEditTransaction(
            transactions = TransactionBoundary(dataSource()),
            strategyPolicy = policy(),
            sessionPolicy = EditSessionPolicyData(Duration.ofMinutes(15)),
            clock = FixedClock(),
        )

    private fun execute(sql: String) =
        dataSource().connection.use { connection -> connection.createStatement().use { it.execute(sql) } }

    private fun countRows(sql: String): Long =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }

    private fun storedRevision(): Long? =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT revision FROM operator_strategy WHERE id = 1").use { rs ->
                    if (rs.next()) rs.getLong(1) else null
                }
            }
        }

    private fun storedSessionVersion(): Int? =
        dataSource().connection.use { connection ->
            connection.prepareStatement("SELECT session_version FROM edit_session WHERE id = ?").use { statement ->
                statement.setString(1, SESSION.value)
                statement.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
            }
        }

    /** `outbox` INSERT 만 거부한다 — 전략 저장은 성공한 뒤 발행이 실패하는 4A 의 그 창이다. */
    private fun blockOutboxInserts() {
        execute(
            """
            CREATE OR REPLACE FUNCTION reject_outbox_insert() RETURNS trigger AS ${'$'}${'$'}
            BEGIN RAISE EXCEPTION 'outbox 등록 강제 실패(원자성 실측)'; END;
            ${'$'}${'$'} LANGUAGE plpgsql
            """.trimIndent(),
        )
        execute(
            "CREATE TRIGGER reject_outbox BEFORE INSERT ON outbox " +
                "FOR EACH ROW EXECUTE FUNCTION reject_outbox_insert()",
        )
    }

    private fun unblockOutboxInserts() = execute("DROP TRIGGER IF EXISTS reject_outbox ON outbox")

    private fun beginAndProvideValue(transaction: JdbcStrategyEditTransaction) {
        val started = transaction.inTransaction { it.begin(SESSION, OPERATOR, EditableField.CandidateLimit) }
        started.shouldBeStarted()
        val provided =
            transaction.inTransaction { workflow ->
                workflow.provideValue(
                    EditCommand.ProvideValue(
                        CommandId("value-1"),
                        SESSION,
                        ACTOR,
                        EditableField.CandidateLimit,
                        StrategyDraft(candidateLimit = 9),
                        // 전략 표가 비어 있으므로 현재 revision 은 0 이다(D-6F1-4).
                        StrategyRevision(0),
                    ),
                )
            }
        (provided as CommandResult.Processed).outcome.shouldBeAccepted()
    }

    @Test
    fun `outbox 등록이 실패하면 전략 revision 도 세션도 움직이지 않는다 — 한 트랜잭션`() {
        val transaction = transaction()
        beginAndProvideValue(transaction)
        val revisionBefore = storedRevision()
        val sessionVersionBefore = storedSessionVersion()
        sessionVersionBefore shouldNotBe null

        blockOutboxInserts()
        try {
            shouldThrow<SQLException> {
                transaction.inTransaction { workflow ->
                    workflow.confirm(confirmCommand())
                }
            }
        } finally {
            unblockOutboxInserts()
        }

        storedRevision() shouldBe revisionBefore
        storedSessionVersion() shouldBe sessionVersionBefore
        countRows("SELECT count(*) FROM outbox") shouldBe 0L
    }

    @Test
    fun `정상 경로는 전략·outbox·세션이 함께 커밋된다`() {
        val transaction = transaction()
        beginAndProvideValue(transaction)

        val confirmed =
            transaction.inTransaction { workflow ->
                workflow.confirm(confirmCommand())
            }

        (confirmed as CommandResult.Processed).outcome.shouldBeApplied()
        storedRevision() shouldBe 1L
        storedSessionVersion() shouldBe 2
        countRows("SELECT count(*) FROM outbox WHERE idempotency_key = 'strategy-updated-1'") shouldBe 1L
    }
}

private fun confirmCommand(): EditCommand.Confirm =
    EditCommand.Confirm(CommandId("confirm-1"), SESSION, ACTOR, StrategyRevision(0))

private fun BeginOutcome.shouldBeStarted() {
    check(this is BeginOutcome.Started) { "세션이 열리지 않았다: $this" }
}

private fun TransitionOutcome.shouldBeAccepted() {
    check(this is TransitionOutcome.Accepted) { "값 제출이 수용되지 않았다: $this" }
}

private fun TransitionOutcome.shouldBeApplied() {
    check(this is TransitionOutcome.Applied) { "확인이 적용되지 않았다: $this" }
}
