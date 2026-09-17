package bidvector.workflow.strategy

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyRevision
import bidvector.strategy.ThresholdField
import bidvector.strategy.WatchRuleId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * D-6B1-7(계약 갱신 (2)) — [EditSessionSnapshot]은 어댑터가 만드는 유일한 타입이고
 * [restoreEditSession]은 `workflow` 안에서만 호출 가능한 유일한 복원 경로다. 이 test는
 * (a) 왕복이 원래 [EditSession]과 값 동등함을 (b) 미지·불량 값이 조용히 기본값으로
 * 채워지지 않고 거부됨을 고정한다(팀장 판정 — "조용히 기본값을 채우는 것만 금지").
 */
class EditSessionSnapshotTest {
    private val id = EditSessionId("session-1")
    private val operator = Actor.Operator(OperatorId("operator-1"))
    private val expiresAt: Instant = Instant.parse("2026-09-17T00:00:00Z")

    /** `internal constructor`는 같은 모듈(test source set 은 main 의 friend)이라 여기서 직접 부른다. */
    private fun session(
        state: EditSessionState,
        sessionVersion: Int = 3,
        lastCommand: EditCommand? = null,
    ): EditSession = EditSession(id, operator, state, expiresAt, sessionVersion, lastCommand)

    @Test
    fun `WaitingForValue 상태를 왕복하면 값이 그대로 복원된다`() {
        val original = session(EditSessionState.WaitingForValue(EditableField.CandidateLimit))

        restoreEditSession(original.toSnapshot()) shouldBe original
    }

    @Test
    fun `WaitingForValue Watch 필드도 왕복한다`() {
        val original = session(EditSessionState.WaitingForValue(EditableField.Watch(WatchRuleId.FocusCategory)))

        restoreEditSession(original.toSnapshot()) shouldBe original
    }

    @Test
    fun `WaitingForConfirmation 상태(예산 한계 포함)를 왕복하면 값이 그대로 복원된다`() {
        val draft =
            StrategyDraft(
                focusCategories = listOf("catA", "catB"),
                requiredKeywordTerms = listOf("keyword"),
                minBudget = BaseAmount(1_000_000L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared),
                maxBudget = BaseAmount(5_000_000L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared),
                minimumMatchScore = java.math.BigDecimal("0.5"),
                candidateLimit = 10,
            )
        val original =
            session(
                EditSessionState.WaitingForConfirmation(EditableField.Threshold(ThresholdField.BidNowThreshold), draft),
            )

        restoreEditSession(original.toSnapshot()) shouldBe original
    }

    @Test
    fun `WaitingForConfirmation 예산 한계가 없는 draft 도 왕복한다`() {
        val original =
            session(
                EditSessionState.WaitingForConfirmation(EditableField.CandidateLimit, StrategyDraft()),
            )

        restoreEditSession(original.toSnapshot()) shouldBe original
    }

    @Test
    fun `Applied 상태를 왕복하면 값이 그대로 복원된다`() {
        val original = session(EditSessionState.Applied(StrategyRevision(7)))

        restoreEditSession(original.toSnapshot()) shouldBe original
    }

    @Test
    fun `Cancelled OperatorRequested 상태를 왕복한다`() {
        val original = session(EditSessionState.Cancelled(CancellationReason.OperatorRequested))

        restoreEditSession(original.toSnapshot()) shouldBe original
    }

    @Test
    fun `Cancelled Other 상태(사유 문구 포함)를 왕복한다`() {
        val original = session(EditSessionState.Cancelled(CancellationReason.Other("고객 요청")))

        restoreEditSession(original.toSnapshot()) shouldBe original
    }

    @Test
    fun `Expired 상태를 왕복한다`() {
        val original = session(EditSessionState.Expired)

        restoreEditSession(original.toSnapshot()) shouldBe original
    }

    @Test
    fun `lastCommand(ProvideValue)이 있는 세션을 왕복한다`() {
        val draft = StrategyDraft(candidateLimit = 3)
        val command =
            EditCommand.ProvideValue(CommandId("cmd-1"), id, operator, EditableField.CandidateLimit, draft)
        val original =
            session(EditSessionState.WaitingForConfirmation(EditableField.CandidateLimit, draft), lastCommand = command)

        restoreEditSession(original.toSnapshot()) shouldBe original
    }

    @Test
    fun `lastCommand(Confirm)이 있는 세션을 왕복한다`() {
        val command = EditCommand.Confirm(CommandId("cmd-2"), id, operator, StrategyRevision(2))
        val original = session(EditSessionState.Applied(StrategyRevision(3)), lastCommand = command)

        restoreEditSession(original.toSnapshot()) shouldBe original
    }

    @Test
    fun `lastCommand(RequestEdit)이 있는 세션을 왕복한다`() {
        val command =
            EditCommand.RequestEdit(CommandId("cmd-3"), id, operator, EditableField.Watch(WatchRuleId.MinBudget))
        val original =
            session(EditSessionState.WaitingForValue(EditableField.Watch(WatchRuleId.MinBudget)), lastCommand = command)

        restoreEditSession(original.toSnapshot()) shouldBe original
    }

    @Test
    fun `lastCommand(Cancel)이 있는 세션을 왕복한다`() {
        val command = EditCommand.Cancel(CommandId("cmd-4"), id, operator, CancellationReason.Other("취소"))
        val original = session(EditSessionState.Cancelled(CancellationReason.Other("취소")), lastCommand = command)

        restoreEditSession(original.toSnapshot()) shouldBe original
    }

    @Test
    fun `알 수 없는 state kind 는 거부된다 — 지어내지 않는다`() {
        val bogus = session(EditSessionState.Expired).toSnapshot().copy(stateKind = "BOGUS_STATE")

        shouldThrow<IllegalStateException> { restoreEditSession(bogus) }
    }

    @Test
    fun `WaitingForValue 인데 field 가 없으면 거부된다`() {
        val bogus =
            session(EditSessionState.WaitingForValue(EditableField.CandidateLimit)).toSnapshot().copy(stateField = null)

        shouldThrow<IllegalArgumentException> { restoreEditSession(bogus) }
    }

    @Test
    fun `Applied 인데 revision 이 없으면 거부된다`() {
        val bogus = session(EditSessionState.Applied(StrategyRevision(1))).toSnapshot().copy(stateRevision = null)

        shouldThrow<IllegalArgumentException> { restoreEditSession(bogus) }
    }

    @Test
    fun `Cancelled 인데 reason kind 가 없으면 거부된다`() {
        val bogus =
            session(EditSessionState.Cancelled(CancellationReason.OperatorRequested))
                .toSnapshot()
                .copy(stateCancelReasonKind = null)

        shouldThrow<IllegalArgumentException> { restoreEditSession(bogus) }
    }

    @Test
    fun `Cancelled Other 인데 note 가 없으면 거부된다`() {
        val bogus =
            session(EditSessionState.Cancelled(CancellationReason.Other("사유")))
                .toSnapshot()
                .copy(stateCancelReasonNote = null)

        shouldThrow<IllegalArgumentException> { restoreEditSession(bogus) }
    }

    @Test
    fun `알 수 없는 CancellationReason kind 는 거부된다`() {
        val bogus =
            session(EditSessionState.Cancelled(CancellationReason.OperatorRequested))
                .toSnapshot()
                .copy(stateCancelReasonKind = "MADE_UP")

        shouldThrow<IllegalStateException> { restoreEditSession(bogus) }
    }

    @Test
    fun `알 수 없는 EditableField kind 는 거부된다`() {
        val bogus =
            session(EditSessionState.WaitingForValue(EditableField.CandidateLimit))
                .toSnapshot()
                .copy(stateField = EditableFieldSnapshot("MADE_UP", null))

        shouldThrow<IllegalStateException> { restoreEditSession(bogus) }
    }

    @Test
    fun `알 수 없는 WatchRuleId 토큰은 거부된다`() {
        val bogus =
            session(EditSessionState.WaitingForValue(EditableField.Watch(WatchRuleId.FocusCategory)))
                .toSnapshot()
                .copy(stateField = EditableFieldSnapshot("WATCH", "MadeUp"))

        shouldThrow<IllegalStateException> { restoreEditSession(bogus) }
    }

    @Test
    fun `알 수 없는 ThresholdField 토큰은 거부된다`() {
        val bogus =
            session(EditSessionState.WaitingForValue(EditableField.Threshold(ThresholdField.BidNowThreshold)))
                .toSnapshot()
                .copy(stateField = EditableFieldSnapshot("THRESHOLD", "MadeUp"))

        shouldThrow<IllegalStateException> { restoreEditSession(bogus) }
    }

    @Test
    fun `예산 한계의 provenance 가 OperatorDeclared 가 아니면 거부된다 — validate() 전제 위반`() {
        val draft =
            StrategyDraft(
                minBudget = BaseAmount(1L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared),
            )
        val bogus =
            session(EditSessionState.WaitingForConfirmation(EditableField.CandidateLimit, draft))
                .toSnapshot()
                .let { snapshot ->
                    snapshot.copy(
                        stateDraft =
                            snapshot.stateDraft!!.copy(
                                minBudget = snapshot.stateDraft.minBudget!!.copy(provenanceKind = "UNDECLARED"),
                            ),
                    )
                }

        shouldThrow<IllegalArgumentException> { restoreEditSession(bogus) }
    }

    @Test
    fun `예산 한계의 won 이 음수면 BaseAmount 자체 불변식이 거부한다`() {
        val draft =
            StrategyDraft(
                minBudget = BaseAmount(1L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared),
            )
        val bogus =
            session(EditSessionState.WaitingForConfirmation(EditableField.CandidateLimit, draft))
                .toSnapshot()
                .let { snapshot ->
                    snapshot.copy(
                        stateDraft =
                            snapshot.stateDraft!!.copy(
                                minBudget = snapshot.stateDraft.minBudget!!.copy(won = -1L),
                            ),
                    )
                }

        shouldThrow<IllegalArgumentException> { restoreEditSession(bogus) }
    }

    @Test
    fun `알 수 없는 EditCommand kind 는 거부된다`() {
        val command = EditCommand.Cancel(CommandId("cmd-9"), id, operator, CancellationReason.OperatorRequested)
        val original = session(EditSessionState.Cancelled(CancellationReason.OperatorRequested), lastCommand = command)
        val bogus = original.toSnapshot().let { it.copy(lastCommand = it.lastCommand!!.copy(kind = "MADE_UP")) }

        shouldThrow<IllegalStateException> { restoreEditSession(bogus) }
    }
}
