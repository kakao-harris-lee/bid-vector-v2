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
import bidvector.workflow.strategy.StrategyReader
import java.util.UUID

/**
 * HTTP 가 부르는 유일한 편집 실행기(D-6A2b-3·8) — 컨트롤러는 이 클래스만 받는다.
 *
 * **(2b) 「경계로 처리」 — 포트를 밖에 내지 않는다.** 이 클래스가 쥔 것은
 * [StrategyEditTransaction] 하나이고, 그 계약의 유일한 메서드는 트랜잭션 **안에서만**
 * use case 를 빌려준다. 저장소·`ConnectionSource`·`TransactionBoundary` 를 돌려주는 공개
 * 경로가 없다(구현 뒤 `javap` 전수로 실측).
 *
 * 모든 쓰기가 한 트랜잭션이다(D-6A2b-3) — 전략 저장·outbox 등록·세션 전진이 함께
 * 커밋되거나 함께 롤백된다.
 *
 * 행위자는 **인증 판정의 결과**다(verifier r1 F2) — 요청 본문·헤더가 아니라 기본 거부 필터가
 * 적은 주체를 컨트롤러가 그대로 넘긴다. 받는 타입이 [OperatorId] 이고 이 클래스가 [Actor.Operator]
 * 로만 감싸므로 **HTTP 로 [Actor.System] 을 만들 길은 여전히 없다**(우회 (8)).
 *
 * 앞 판은 상수 `operator` 하나였다. 그래서 세션(쿠키) 경로로 들어온 편집의 outbox 행위자가
 * `operator` 인데 감사 주체는 `account:<id>` 였다 — 같은 요청의 두 기록이 서로 다른 사람을
 * 가리켰다. 둘이 **같은 속성**에서 오므로 이제 갈릴 수 없다.
 */
class StrategyEditExecutor(
    private val transaction: StrategyEditTransaction,
    private val strategyPolicy: Resolution.Resolved<StrategyPolicyData>,
) {
    /** 세션 id 는 **서버가** 만든다(D-6A2b-1, 우회 (4)) — 요청이 고르면 남의 세션을 겨냥할 자리가 생긴다. */
    fun begin(
        actor: OperatorId,
        field: EditableField,
    ): BeginOutcome =
        transaction.inTransaction { workflow ->
            workflow.begin(EditSessionId(UUID.randomUUID().toString()), actor, field)
        }

    /**
     * 조회 — 접근 시점 만료 fold 를 적용한 세션(D-6A2b-11, 주기 sweep 없음).
     *
     * **그 fold 는 「접근하는 세션」에만 닿는다**(D-6A2b-24).
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
        actor: OperatorId,
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
                    // D-6A2b-28 — 기준은 **이 draft 를 만든 읽기**의 revision 이다. 커널이
                    // 다시 읽은 값을 쓰면 두 읽기 사이의 커밋이 기준을 새 값으로 밀어
                    // 낡은 draft 가 통과한다.
                    val command =
                        EditCommand.ProvideValue(
                            commandId,
                            sessionId,
                            Actor.Operator(actor),
                            field,
                            draft,
                            current.revision,
                        )
                    ProvideValueOutcome.Processed(workflow.provideValue(command))
                }
            }
        }

    fun confirm(
        actor: OperatorId,
        sessionId: EditSessionId,
        commandId: CommandId,
        seenRevision: StrategyRevision,
    ): CommandResult =
        transaction.inTransaction { workflow ->
            workflow.confirm(EditCommand.Confirm(commandId, sessionId, Actor.Operator(actor), seenRevision))
        }

    fun requestEdit(
        actor: OperatorId,
        sessionId: EditSessionId,
        commandId: CommandId,
        field: EditableField,
    ): CommandResult =
        transaction.inTransaction { workflow ->
            workflow.requestEdit(EditCommand.RequestEdit(commandId, sessionId, Actor.Operator(actor), field))
        }

    fun cancel(
        actor: OperatorId,
        sessionId: EditSessionId,
        commandId: CommandId,
    ): CommandResult =
        transaction.inTransaction { workflow ->
            workflow.cancel(
                EditCommand.Cancel(
                    commandId,
                    sessionId,
                    Actor.Operator(actor),
                    CancellationReason.OperatorRequested,
                ),
            )
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
 * 읽기 전용 전략 조회기(D-6A2b-8·41) — `app.http` 가 저장 port 를 직접 받지 않게 하는 자리다.
 * 하는 일은 6A-1 의 `GET /api/strategy` 가 하던 일과 **같다**((2b) 「닫는다(동치)」).
 *
 * 받는 것은 [StrategyReader] 다 — 쓰기 가능한 port 를 쥐면 그것 하나로 편집 use case 를 스스로
 * 조립할 수 있다(그 조립은 트랜잭션·outbox·세션을 전부 건너뛴다).
 */
class StrategyQuery(
    private val strategies: StrategyReader,
) {
    fun current(): OperatorStrategy = strategies.load()
}
