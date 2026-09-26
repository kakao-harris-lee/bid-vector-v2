package bidvector.archfixture.violating.app.http

import bidvector.adapters.strategy.SystemClock
import bidvector.workflow.strategy.StrategyRepository
import java.sql.Connection

/**
 * D-6A2b-8 위반 표본 셋 — HTTP 층이 쥐어서는 안 되는 것들. production classpath 에는
 * 오르지 않는다(test 소스). 부르지 않고 **쥐기만** 해도 잡혀야 한다(호출 층 게이트
 * D-6A3-25 와 다른 축 — 저장 능력을 손에 든 상태 자체를 막는다).
 */
class RogueHttpPortHolder(
    private val strategies: StrategyRepository,
)

/** 어댑터 구현을 직접 참조한다 — `Throwable` 이 아니므로 예외 통로에 들지 않는다. */
class RogueHttpAdapterUser {
    private val clock = SystemClock()
}

/** HTTP 층의 원시 SQL — 6A-3 r3 MEDIUM 의 지름길 셋째. */
class RogueHttpSqlUser {
    fun run(connection: Connection) = connection.createStatement()
}
