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
 * **설계 검토 (2) #3 실측 판정 — `internal interface`로는 닫히지 않는다.** `internal
 * interface`로 두면 `EditStrategyWorkflow`(공개 클래스)의 생성자가 그 internal 타입을
 * 노출해 `:workflow:compileKotlin`이 즉시 컴파일 에러로 거부한다("public function exposes
 * its 'internal' parameter type", 실측 2026-09-08). `EditStrategyWorkflow` 자체를
 * `internal`로 닫으면 4C/6A 의 실 어댑터가 이 use case 를 배선할 수 없어 slice 존재 이유
 * (⑥ "모든 편집 경로가 이 use case 를 지난다")와 충돌한다. 그래서 이 port 인터페이스
 * 자체는 `public`으로 남는다.
 *
 * **인자 타입이 닫는다 — verifier H-1/H-2 수정.** `save`가 [OperatorStrategy]를 직접
 * 받던 이전 형태는 우회 (3)을 막지 못했다 — `bidvector.strategy.validate()`가 public 이라
 * 어느 모듈에서든 [OperatorStrategy] 값을 얻어 이 port 의 자체 구현에 바로 넘길 수 있었다
 * (verifier 실측: `app` test 에 `StrategyRepository` 를 구현하고 `validate()` 결과를
 * `save()` 에 전달하는 클래스를 심어 `:app:compileTestKotlin` exit 0). `save`가
 * [AppliedStrategy](internal constructor, `workflow` 모듈 밖에서 생성 불가)를 요구하도록
 * 바꾸면 그 우회는 컴파일되지 않는다 — 재현 시도가 `Cannot access '<init>': it is internal
 * in 'AppliedStrategy'` 로 거부됨을 실측했다(evidence checklist.md).
 *
 * `OPEN-4A-WRITE-PATH-GATE` 는 이 수정으로 **종결**한다(evidence checklist.md) — 「4A
 * port 밖에서 자체 persistence 경로를 새로 만드는 것」은 이 slice 의 위협 모델 경계 밖이고
 * 3D/4C 의 write 게이트 소관이다.
 */
interface StrategyRepository {
    fun load(): OperatorStrategy

    fun save(applied: AppliedStrategy)
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
