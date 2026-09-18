package bidvector.adapters.strategy

import bidvector.workflow.strategy.Clock
import java.time.Instant

/**
 * [Clock] 실 어댑터(M6/6F-2, D-6F2-8) — `Instant.now()` 위임(`JdbcEventIdFactory` 관례와
 * 같은 한 줄 구현). `bidvector.adapters.strategy`에 둔다 — `Clock` 타입이 사는
 * `bidvector.workflow.strategy`를 이미 허용하는 패키지라 게이트 allow-list를 넓히지
 * 않는다(`StrategyAdapterDependencyTest`).
 */
class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
