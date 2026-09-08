package bidvector.workflow.strategy

import bidvector.strategy.OperatorStrategy
import bidvector.strategy.StrategyEvent
import java.time.Instant

/** 시각 조회 port(scope.md ⑤) — 구현은 이 slice 밖(실 clock adapter 는 4C/후속). */
fun interface Clock {
    fun now(): Instant
}

/**
 * 전략 저장 port(scope.md ⑤, 우회 (3)) — 구현은 test fake 만. 실 저장은 4C/3D.
 *
 * **설계 검토 (2) #3 실측 판정 — 닫히지 않는다.** `internal interface`로 두면
 * `EditStrategyWorkflow`(공개 클래스)의 생성자가 그 internal 타입을 노출해
 * `:workflow:compileKotlin`이 즉시 컴파일 에러로 거부한다("public function exposes its
 * 'internal' parameter type", 실측 2026-09-08). `EditStrategyWorkflow` 자체를 `internal`로
 * 닫으면 4C/6A 의 실 어댑터가 이 use case 를 배선할 수 없어 slice 존재 이유(⑥ "모든 편집
 * 경로가 이 use case 를 지난다")와 충돌한다. 그래서 이 port 는 `public`으로 남기고,
 * 「어댑터가 이 port 를 직접 구현해 `save`를 부르는 경로」 차단은 우회 (3)의 **타입** 근거
 * (저장 인자 `OperatorStrategy`를 내는 유일한 자리가 `TransitionOutcome.Applied`)에만
 * 의존한다 — `OPEN-4A-WRITE-PATH-GATE`로 등재(evidence checklist.md).
 */
interface StrategyRepository {
    fun load(): OperatorStrategy

    fun save(strategy: OperatorStrategy)
}

/** 편집 세션 저장 port(scope.md ⑤) — 낙관적 동시성은 [EditSession.sessionVersion]이 나른다. */
interface EditSessionRepository {
    fun load(id: EditSessionId): EditSession?

    fun save(session: EditSession)
}

/** 적용 이벤트 발행 port(scope.md ⑥) — 봉투·outbox 는 4C, 이 slice 는 payload 를 넘기는 자리까지. */
fun interface EventSink {
    fun publish(event: StrategyEvent)
}
