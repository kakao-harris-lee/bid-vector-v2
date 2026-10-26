package bidvector.app.http

import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.strategy.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * 시각을 직접 미는 fake — `Duration.between` 계산이 재현 가능해야 한다.
 *
 * 이 파일은 HTTP 층 test 의 **시계 · correlation id 이중체** 자리다. `HttpTestSupport.kt` 에서 갈라
 * 나왔고(M7/7C④ 2b) — 그 파일이 500줄 한도에 닿아 있었다. 같은 패키지라 옮김만으로 끝난다.
 */
class FixedClock(
    private var instant: Instant,
) : Clock {
    override fun now(): Instant = instant

    fun advance(millis: Long) {
        instant = instant.plusMillis(millis)
    }
}

class SequentialCorrelationIdFactory : CorrelationIdFactory {
    private val counter = AtomicInteger(0)

    override fun newId(): CorrelationId = CorrelationId("test-corr-${counter.incrementAndGet()}")
}
