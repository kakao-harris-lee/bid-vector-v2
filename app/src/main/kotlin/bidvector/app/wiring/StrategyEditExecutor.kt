package bidvector.app.wiring

import bidvector.adapters.strategy.StrategyEditTransaction
import bidvector.sharedkernel.Resolution
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.StrategyViolation
import bidvector.strategy.toDraft
import bidvector.strategy.validate
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.BeginOutcome
import bidvector.workflow.strategy.CancellationReason
import bidvector.workflow.strategy.CommandId
import bidvector.workflow.strategy.CommandResult
import bidvector.workflow.strategy.EditCommand
import bidvector.workflow.strategy.EditSession
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.EditableField
import bidvector.workflow.strategy.OperatorId
import bidvector.workflow.strategy.StrategyRepository
import java.util.UUID

/**
 * HTTP 가 부르는 유일한 편집 실행기(M6/6A-2b D-6A2b-3·8) — 컨트롤러는 이 클래스만 받는다.
 *
 * **(2b) 「경계로 처리」 — 포트를 밖에 내지 않는다.** 이 클래스가 쥔 것은
 * [StrategyEditTransaction] 하나이고, 그 계약의 유일한 메서드는 트랜잭션 **안에서만**
 * use case 를 빌려준다. 저장소·`ConnectionSource`·`TransactionBoundary` 를 돌려주는 공개
 * 경로가 없다(구현 뒤 `javap` 전수로 실측).
 *
 * 모든 쓰기가 한 트랜잭션이다(D-6A2b-3) — 전략 저장·outbox 등록·세션 전진이 함께
 * 커밋되거나 함께 롤백된다.
 *
 * 행위자는 **상수**다(D-6A2b-5, 우회 (8)) — 요청에서 받지 않으므로 HTTP 로
 * [Actor.System] 을 만들 길이 없다. 값은 인증 필터가 audit 주체로 적는 라벨과 같다.
 */
class StrategyEditExecutor(
    private val transaction: StrategyEditTransaction,
    private val strategyPolicy: Resolution.Resolved<StrategyPolicyData>,
    private val sessionIds: () -> String = { UUID.randomUUID().toString() },
) {
    /** 세션 id 는 **서버가** 만든다(D-6A2b-1, 우회 (4)) — 요청이 고르면 남의 세션을 겨냥할 자리가 생긴다. */
    fun begin(field: EditableField): BeginOutcome =
        transaction.inTransaction { workflow ->
            workflow.begin(EditSessionId(sessionIds()), OPERATOR.id, field)
        }

    /**
     * 조회 — 접근 시점 만료 fold 를 적용한 세션(D-6A2b-11, 주기 sweep 없음).
     *
     * **그 fold 는 「접근하는 세션」에만 닿는다**(D-6A2b-24, code-review r1 MEDIUM-1 정정).
     * 세션 id 를 서버가 만들므로 운영자가 열어 두고 떠난 세션에는 다시 접근할 주체가 없고,
     * 그 행은 비종단 상태로 남는다 — 이 slice 는 그것을 치우지 않는다
     * (`OPEN-6A2B-ABANDONED-SESSIONS`, 받는 쪽은 6B-3 보존·파기). 남아도 해가 없다:
     * 만료된 세션의 command 는 fold 를 지나 거부되고, 낡은 기준 revision 은 D-6A2b-18 이
     * 다시 막는다.
     */
    fun view(sessionId: EditSessionId): EditSession? = transaction.inTransaction { it.view(sessionId) }

    /**
     * 값 제출 — 현재 전략을 초안으로 내보내 **그 필드 하나만** 바꾼 뒤 command 로 넘긴다
     * (D-6A2b-2). 초안 읽기와 command 처리가 같은 트랜잭션 안이라 그 사이에 전략이 바뀌어
     * 다른 필드가 조용히 되돌아가는 일이 없다.
     *
     * 값 불변식 위반은 **영속 전에** 거부한다(우회 (7)) — 판정은 [validate] 하나가 한다
     * (D-10, 여기서 재구현하지 않는다). use case 도 같은 함수로 다시 판정하므로(심층
     * 방어) 이 앞선 거부는 판정을 바꾸지 않고 **사유를 HTTP 로 옮길 자리**를 만든다.
     */
    fun provideValue(
        sessionId: EditSessionId,
        commandId: CommandId,
        field: EditableField,
        value: EditValue,
    ): ProvideValueOutcome =
        transaction.inTransaction { workflow ->
            val current: OperatorStrategy = workflow.currentStrategy()
            val draft = current.toDraft().withFieldValue(field, value)
            when (val validation = validate(draft, current.revision, strategyPolicy)) {
                is StrategyValidation.Invalid -> {
                    ProvideValueOutcome.Invalid(validation.violations)
                }

                is StrategyValidation.Valid -> {
                    val command = EditCommand.ProvideValue(commandId, sessionId, OPERATOR, field, draft)
                    ProvideValueOutcome.Processed(workflow.provideValue(command))
                }
            }
        }

    fun confirm(
        sessionId: EditSessionId,
        commandId: CommandId,
        seenRevision: StrategyRevision,
    ): CommandResult =
        transaction.inTransaction { workflow ->
            workflow.confirm(EditCommand.Confirm(commandId, sessionId, OPERATOR, seenRevision))
        }

    fun requestEdit(
        sessionId: EditSessionId,
        commandId: CommandId,
        field: EditableField,
    ): CommandResult =
        transaction.inTransaction { workflow ->
            workflow.requestEdit(EditCommand.RequestEdit(commandId, sessionId, OPERATOR, field))
        }

    fun cancel(
        sessionId: EditSessionId,
        commandId: CommandId,
    ): CommandResult =
        transaction.inTransaction { workflow ->
            workflow.cancel(EditCommand.Cancel(commandId, sessionId, OPERATOR, CancellationReason.OperatorRequested))
        }

    private companion object {
        /**
         * 단일 운영자 토큰(6A-1 운영자 결정 ②) — 라벨은 `OperatorCredentialFilter` 가 audit
         * 주체로 적는 값과 같다. 그 파일은 이 slice 에서 **무편집**이라(D-6A2b-5) 상수를
         * 공유하도록 옮기지 않았다 — 두 값이 같다는 것은 문면이 아니라 실 조립 E2E 가
         * 잰다(audit 행의 subject == outbox 봉투의 actor).
         */
        val OPERATOR = Actor.Operator(OperatorId("operator"))
    }
}

/**
 * [StrategyEditExecutor.provideValue] 의 결과 — 값 불변식 위반은 command 가 되기 전에
 * 갈린다(400), 나머지는 use case 결과 그대로다.
 */
sealed interface ProvideValueOutcome {
    data class Invalid(
        val violations: List<StrategyViolation>,
    ) : ProvideValueOutcome

    data class Processed(
        val result: CommandResult,
    ) : ProvideValueOutcome
}

/**
 * 읽기 전용 전략 조회기(D-6A2b-8) — `app.http` 가 포트([bidvector.workflow.strategy.StrategyRepository])
 * 를 직접 받지 않게 하는 자리다. 하는 일은 6A-1 의 `GET /api/strategy` 가 하던 일과 **같다**
 * ((2b) 「닫는다(동치)」) — 쓰기 메서드가 없다.
 */
class StrategyQuery(
    private val strategies: StrategyRepository,
) {
    fun current(): OperatorStrategy = strategies.load()
}
