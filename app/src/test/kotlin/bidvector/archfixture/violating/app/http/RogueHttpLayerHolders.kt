package bidvector.archfixture.violating.app.http

import bidvector.adapters.strategy.SystemClock
import bidvector.workflow.strategy.StrategyRepository
import org.springframework.jdbc.core.simple.JdbcClient
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

class RogueHttpSqlUser {
    fun run(connection: Connection) = connection.createStatement()
}

/**
 * D-6A2b-19 위반 표본(MU1) — **자동 구성이 올린 JDBC 클라이언트**를 HTTP 층이
 * 쥔다. 금지 열거 술어는 이 좌표를 몰랐고 전건 `check` 가 초록이었다(실측). 허용 목록 ⊆ 에서는
 * 목록에 없다는 사실만으로 걸린다 — 새 좌표는 기본이 거부다.
 */
class RogueHttpJdbcShortcut(
    private val jdbc: JdbcClient,
) {
    fun bump(): Int = jdbc.sql("UPDATE operator_strategy SET revision = revision + 1 WHERE id = 1").update()
}
