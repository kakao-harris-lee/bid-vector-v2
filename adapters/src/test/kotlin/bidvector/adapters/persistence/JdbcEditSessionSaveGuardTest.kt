package bidvector.adapters.persistence

import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyRevision
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.BeginOutcome
import bidvector.workflow.strategy.CancellationReason
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.CommandId
import bidvector.workflow.strategy.CommandResult
import bidvector.workflow.strategy.EditCommand
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.RejectionReason
import bidvector.workflow.strategy.TransitionOutcome
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

private val OPERATOR = EDIT_SESSION_TEST_OPERATOR
private val FIELD = EDIT_SESSION_TEST_FIELD

/**
 * verifier r3 HIGH-3 재현·회귀 보호(D-6B1-10, v2-지침서.md §5 「파일 500줄 한도」로
 * [JdbcEditSessionRepositoryTest]에서 갈렸다 — 설계 변경 아님, 공용 fixture 는
 * [EditSessionWorkflowTestSupport]). 실 저장소 + 실 [bidvector.workflow.strategy.
 * EditStrategyWorkflow]로 다섯 경로(`R2 1`·`2b`·`3b`·`3c`·`4b`, verifier report)를 돌려
 * 전부 예외 없이 문서화된 [CommandResult.Processed] 결과를 낸다는 것과, 저장소
 * `session_version`이 그 호출로 **바뀌지 않는다**(재저장이 일어나지 않았다)는 것을 함께
 * 잠근다. 고친 전(`EditStrategyWorkflow.process`가 무조건 `sessions.save`)에는 다섯 다
 * `EditSessionConflictException`으로 터졌다(verifier r3 재현).
 */
class JdbcEditSessionSaveGuardTest : EditSessionWorkflowTestSupport() {
    @Test
    fun `잘못된 전이(WaitingForValue 에 Confirm)는 예외 없이 거부로 반환되고 버전은 그대로다 — HIGH-3 R2 1`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-high3-invalid-transition")
        val flow = workflow(sessions)
        val begun = flow.begin(id, OPERATOR, FIELD)
        check(begun is BeginOutcome.Started)
        val versionBefore = sessions.load(id)!!.sessionVersion

        val result =
            shouldNotThrowAny {
                flow.confirm(EditCommand.Confirm(CommandId("cmd-x"), id, Actor.Operator(OPERATOR), StrategyRevision(1)))
            }

        result.shouldBeInstanceOf<CommandResult.Processed>()
        val outcome = result.outcome
        outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        outcome.reason shouldBe RejectionReason.InvalidTransition
        sessions.load(id)!!.sessionVersion shouldBe versionBefore
    }

    @Test
    fun `같은 command 를 재전달하면 예외 없이 Accepted(중복)로 반환되고 버전은 그대로다 — HIGH-3 R2 2b`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-high3-idempotent-redelivery")
        val flow = workflow(sessions)
        val begun = flow.begin(id, OPERATOR, FIELD)
        check(begun is BeginOutcome.Started)
        val command =
            EditCommand.ProvideValue(
                CommandId("cmd-1"),
                id,
                Actor.Operator(OPERATOR),
                FIELD,
                StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
            )
        val first = flow.provideValue(command)
        check(first is CommandResult.Processed)
        val versionAfterFirst = sessions.load(id)!!.sessionVersion

        val redelivered = shouldNotThrowAny { flow.provideValue(command) }

        redelivered.shouldBeInstanceOf<CommandResult.Processed>()
        redelivered.outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        sessions.load(id)!!.sessionVersion shouldBe versionAfterFirst
    }

    @Test
    fun `APPLIED 뒤 같은 confirm 을 재전달하면 예외 없이 Accepted(중복)로 반환된다 — HIGH-3 R2 3b`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-high3-applied-redelivery")
        val flow = workflow(sessions)
        flow.begin(id, OPERATOR, FIELD)
        flow.provideValue(
            EditCommand.ProvideValue(
                CommandId("cmd-1"),
                id,
                Actor.Operator(OPERATOR),
                FIELD,
                StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
            ),
        )
        val confirmCommand = EditCommand.Confirm(CommandId("cmd-2"), id, Actor.Operator(OPERATOR), StrategyRevision(1))
        val confirmed = flow.confirm(confirmCommand)
        check(confirmed is CommandResult.Processed)
        val versionAfterApplied = sessions.load(id)!!.sessionVersion

        val redelivered = shouldNotThrowAny { flow.confirm(confirmCommand) }

        redelivered.shouldBeInstanceOf<CommandResult.Processed>()
        redelivered.outcome.shouldBeInstanceOf<TransitionOutcome.Accepted>()
        sessions.load(id)!!.sessionVersion shouldBe versionAfterApplied
    }

    @Test
    fun `APPLIED 뒤 cancel(잘못된 전이)은 예외 없이 거부로 반환된다 — HIGH-3 R2 3c`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-high3-applied-cancel")
        val flow = workflow(sessions)
        flow.begin(id, OPERATOR, FIELD)
        flow.provideValue(
            EditCommand.ProvideValue(
                CommandId("cmd-1"),
                id,
                Actor.Operator(OPERATOR),
                FIELD,
                StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
            ),
        )
        flow.confirm(EditCommand.Confirm(CommandId("cmd-2"), id, Actor.Operator(OPERATOR), StrategyRevision(1)))
        val versionAfterApplied = sessions.load(id)!!.sessionVersion
        val cancelCommand =
            EditCommand.Cancel(CommandId("cmd-3"), id, Actor.Operator(OPERATOR), CancellationReason.OperatorRequested)

        val result = shouldNotThrowAny { flow.cancel(cancelCommand) }

        result.shouldBeInstanceOf<CommandResult.Processed>()
        val outcome = result.outcome
        outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        outcome.reason shouldBe RejectionReason.InvalidTransition
        sessions.load(id)!!.sessionVersion shouldBe versionAfterApplied
    }

    @Test
    fun `EXPIRED 뒤 cancel 은 예외 없이 SessionExpired 거부로 반환된다 — HIGH-3 R2 4b`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-high3-expired-cancel")
        val startClock = Clock { Instant.parse("2026-09-17T00:00:00Z") }
        val begun = workflow(sessions, clock = startClock, timeout = Duration.ofMinutes(1)).begin(id, OPERATOR, FIELD)
        check(begun is BeginOutcome.Started)
        val afterExpiry = Clock { Instant.parse("2026-09-17T00:10:00Z") }
        workflow(sessions, clock = afterExpiry).expire(id)
        val versionAfterExpiry = sessions.load(id)!!.sessionVersion
        val cancelCommand =
            EditCommand.Cancel(CommandId("cmd-1"), id, Actor.Operator(OPERATOR), CancellationReason.OperatorRequested)

        val result = shouldNotThrowAny { workflow(sessions, clock = afterExpiry).cancel(cancelCommand) }

        result.shouldBeInstanceOf<CommandResult.Processed>()
        val outcome = result.outcome
        outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        outcome.reason shouldBe RejectionReason.SessionExpired
        sessions.load(id)!!.sessionVersion shouldBe versionAfterExpiry
    }

    /**
     * 경계 회귀 — 명령 처리 **중** 만료로 접히는 경로(verifier r3 「R2-FOLD」)는 위 다섯과
     * 달리 `outcome.session`이 새 인스턴스(버전 +1)라 저장이 그대로 일어나야 한다. HIGH-3
     * 수정이 이 정당한 저장까지 막지 않는지를 실 저장소로 확인한다.
     */
    @Test
    fun `명령 처리 중 만료로 접히면 Rejected(SessionExpired) 가 반환되고 fold 는 저장된다`() {
        val sessions = repository()
        val id = EditSessionId("jdbc-high3-fold-during-process")
        val clock = Clock { Instant.parse("2026-09-17T00:00:00Z") }
        val begun = workflow(sessions, clock = clock, timeout = Duration.ofMinutes(1)).begin(id, OPERATOR, FIELD)
        check(begun is BeginOutcome.Started)
        val versionBefore = sessions.load(id)!!.sessionVersion

        val afterExpiry = Clock { Instant.parse("2026-09-17T00:10:00Z") }
        val result =
            workflow(sessions, clock = afterExpiry).provideValue(
                EditCommand.ProvideValue(
                    CommandId("cmd-1"),
                    id,
                    Actor.Operator(OPERATOR),
                    FIELD,
                    StrategyDraft(bidNowThreshold = BigDecimal("0.7")),
                ),
            )

        result.shouldBeInstanceOf<CommandResult.Processed>()
        val outcome = result.outcome
        outcome.shouldBeInstanceOf<TransitionOutcome.Rejected>()
        outcome.reason shouldBe RejectionReason.SessionExpired
        val loaded = sessions.load(id)!!
        loaded.stateKind shouldBe "EXPIRED"
        loaded.sessionVersion shouldBe versionBefore + 1
    }
}
