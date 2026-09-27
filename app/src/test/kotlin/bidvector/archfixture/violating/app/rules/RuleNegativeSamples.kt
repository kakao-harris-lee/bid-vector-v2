package bidvector.archfixture.violating.app.rules

import bidvector.adapters.archfixture.UnlistedAdapterException
import bidvector.adapters.strategy.InvalidStoredStrategyException
import bidvector.adapters.strategy.StrategyEditTransaction
import bidvector.sharedkernel.Resolution
import bidvector.strategy.StrategyPolicyData
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.EditSessionPolicyData
import bidvector.workflow.strategy.EditSessionRepository
import bidvector.workflow.strategy.EditStrategyWorkflow
import bidvector.workflow.strategy.EventSink
import bidvector.workflow.strategy.StrategyRepository
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// D-6A2b-45 — **규칙마다 하나씩** 두는 영구 음성 fixture.
//
// verifier r4 F-r4-4: 규칙 넷을 항상 공집합이 되게 바꿔도 `AppHttpDependencyGateTest` 의 RED 는
// 한 건뿐이었다. production 이 오늘 그 규칙들을 어기지 않으니 **항진식이 되어도 조용하다**.
// 여기 표본은 규칙별로 평가되고(`AppRuleId`), 어느 규칙 하나를 공집합으로 바꾸면 그 규칙의
// 단언이 RED 다.
//
// 층은 test 가 배정한다 — 규칙 값은 production 과 같고 배정만 바뀐다.

/** ① 층 규칙(D-6A2b-34) — 배선 층이 HTTP 확장 API 에 의존한다. */
class RogueTier1HttpExtension : WebMvcConfigurer

/** ② 층 능력 규칙(D-6A2b-41 ①) — 요청 스코프 층이 쓰기 능력 포트를 쥔다. */
class RogueTier2CapabilityHolder(
    private val strategies: StrategyRepository,
) {
    fun peek(): Any = strategies
}

/** use case 조립 규칙(D-6A2b-41 ③) — 등재되지 않은 호출자가 편집 use case 를 짓는다. */
class RogueUseCaseAssembler(
    private val sessions: EditSessionRepository,
    private val strategies: StrategyRepository,
    private val clock: Clock,
    private val events: EventSink,
    private val strategyPolicy: Resolution.Resolved<StrategyPolicyData>,
    private val sessionPolicy: EditSessionPolicyData,
) {
    fun assemble(): EditStrategyWorkflow =
        EditStrategyWorkflow(sessions, strategies, clock, events, strategyPolicy, sessionPolicy)
}

/** 어댑터 멤버 규칙(D-6A2b-36·43·44) — 등재된 쌍에 없는 어댑터 인터페이스 메서드를 부른다. */
class RogueAdapterMemberCaller(
    private val transaction: StrategyEditTransaction,
) {
    fun call(): Int = transaction.inTransaction { it.hashCode() }
}

/** 예외 멤버 규칙(D-6A2b-37) — 목록 **안**의 예외라도 자기 멤버는 부를 수 없다. */
class RogueExceptionOwnMember {
    fun violationsOf(e: InvalidStoredStrategyException): Int = e.violations.size
}

/** 예외 타입 규칙(D-6A2b-42) — 목록 **밖**의 어댑터 예외 타입을 참조하고 그 멤버를 부른다. */
class RogueUnlistedExceptionUser {
    fun reasonOf(e: UnlistedAdapterException): String = e.rejectionReason()
}

/** 주입 표면 규칙(D-6A2b-49) — 제한 층이 **함수 값**을 생성자로 받는다. 참조 축은 `kotlin` 접두라 조용하다. */
class RogueInjectedClosure(
    private val touch: () -> Int,
) {
    fun run(): Int = touch()
}
