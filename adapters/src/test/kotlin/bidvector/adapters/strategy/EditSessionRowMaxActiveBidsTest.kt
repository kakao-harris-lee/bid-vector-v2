package bidvector.adapters.strategy

import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.EditSessionSnapshot
import bidvector.workflow.strategy.EditableFieldSnapshot
import bidvector.workflow.strategy.OperatorId
import bidvector.workflow.strategy.StrategyDraftSnapshot
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * [EditSessionRow]의 `state_payload` JSON 코덱 — D-6A3-12(계약 갱신 (1)) 「빠뜨리면 세션
 * 왕복에서 값이 조용히 사라진다」의 저장 표현(DB에 실제로 실리는 JSON) 쪽을 잠근다.
 * `EditSessionSnapshotTest`(workflow 모듈)는 메모리 객체 왕복만 잰다 — `state_payload`
 * 문자열 자체를 거치는 이 test가 실 영속 경로(JSON 직렬화·역직렬화)의 회귀를 막는다.
 * `EditSessionRow`가 `internal object`라 이 모듈(test source set은 main의 friend) 안에서만
 * 직접 부를 수 있다 — DB 없이 순수 문자열 변환만 잰다(Testcontainers 불필요).
 */
class EditSessionRowMaxActiveBidsTest {
    private val operator = Actor.Operator(OperatorId("operator-1"))
    private val expiresAt: Instant = Instant.parse("2026-09-23T00:00:00Z")

    @Test
    fun `state_payload JSON 왕복에서 maxActiveBids 가 보존된다`() {
        val draft =
            StrategyDraftSnapshot(candidateLimit = 10, maxActiveBids = 5)
        val original =
            EditSessionSnapshot(
                id = EditSessionId("session-1"),
                operator = operator,
                expiresAt = expiresAt,
                sessionVersion = 1,
                stateKind = "WAITING_FOR_CONFIRMATION",
                stateField = EditableFieldSnapshot("CANDIDATE_LIMIT", null),
                stateDraft = draft,
                stateRevision = null,
                stateCancelReasonKind = null,
                stateCancelReasonNote = null,
                lastCommand = null,
            )

        val payload = EditSessionRow.encodeStatePayload(original)
        val restored =
            EditSessionRow.toSnapshot(
                id = original.id,
                operator = original.operator,
                stateKind = original.stateKind,
                statePayload = payload,
                expiresAt = original.expiresAt,
                sessionVersion = original.sessionVersion,
                lastCommand = null,
            )

        restored.stateDraft?.maxActiveBids shouldBe 5
        restored shouldBe original
    }

    @Test
    fun `maxActiveBids 가 없는 draft 는 payload 에 그 키가 없고 복원도 null 이다`() {
        val draft = StrategyDraftSnapshot(candidateLimit = 10)
        val original =
            EditSessionSnapshot(
                id = EditSessionId("session-2"),
                operator = operator,
                expiresAt = expiresAt,
                sessionVersion = 1,
                stateKind = "WAITING_FOR_CONFIRMATION",
                stateField = EditableFieldSnapshot("CANDIDATE_LIMIT", null),
                stateDraft = draft,
                stateRevision = null,
                stateCancelReasonKind = null,
                stateCancelReasonNote = null,
                lastCommand = null,
            )

        val payload = EditSessionRow.encodeStatePayload(original)
        val restored =
            EditSessionRow.toSnapshot(
                id = original.id,
                operator = original.operator,
                stateKind = original.stateKind,
                statePayload = payload,
                expiresAt = original.expiresAt,
                sessionVersion = original.sessionVersion,
                lastCommand = null,
            )

        restored.stateDraft?.maxActiveBids shouldBe null
    }
}
