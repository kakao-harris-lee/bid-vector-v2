package bidvector.app.http

import bidvector.sharedkernel.Resolution
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.strategy.AppliedStrategy
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import java.time.Instant
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** 실 자격증명과 다른, test 고정값 — 운영 값과 헷갈리지 않게 문구를 담는다(비밀값이 아니다). */
const val TEST_CREDENTIAL = "http-layer-test-fixture-credential"

/**
 * `BidVectorApplication.main()`이 굳히는 두 속성을 test에도 그대로 준다 —
 * `@SpringBootTest`는 `main()`을 부르지 않으므로 이 값들을 test가 직접 반복한다(`const
 * val`이라 annotation 인자로 그대로 참조된다. 작은 중복이지만 드리프트가 나면 D-6A1-21
 * test가 곧바로 실패하며 드러난다 — 조용히 갈라지지 않는다).
 */
const val PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND = "spring.mvc.throw-exception-if-no-handler-found=true"
const val PROP_NO_STATIC_RESOURCE_MAPPINGS = "spring.web.resources.add-mappings=false"

/**
 * `TestRestTemplate`을 **직접 만든다** — Boot 4.1의 `@AutoConfigureTestRestTemplate`
 * 자동 배선이 `@ConditionalOnMissingBean`의 타입 추론에서 예외를 던지는 것을 실측했다
 * (`org.springframework.boot.resttestclient.autoconfigure.TestRestTemplateTestAutoConfiguration
 * .testRestTemplate`, 2026-09-19 — 이 Boot 버전 자체의 결함으로 보인다). 이 slice는 그
 * 자동 배선에 기대지 않고 `TestRestTemplate()`(빈 생성자, HttpClientOption vararg 0개)와
 * `@LocalServerPort`만으로 직접 URL을 조립한다 — 표준 API 조합이라 그 결함을 우회한다.
 */
abstract class HttpIntegrationTestBase {
    @LocalServerPort
    protected var port: Int = 0

    protected val restTemplate: TestRestTemplate = TestRestTemplate()

    protected fun url(path: String): String = "http://localhost:$port$path"
}

/** [bidvector.strategy.validate]를 지나 만든 유효한 fixture — `OperatorStrategy`를 직접 짓지 않는다. */
fun freshStrategy(revision: Int = 1): OperatorStrategy {
    val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
    val draft = StrategyDraft(focusCategories = listOf("CAT-1"))
    return when (val result = validate(draft, StrategyRevision(revision), policy)) {
        is StrategyValidation.Valid -> result.strategy
        is StrategyValidation.Invalid -> error("test fixture가 유효하지 않다: ${result.violations}")
    }
}

/** 시각을 직접 미는 fake — `Duration.between` 계산이 재현 가능해야 한다. */
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

/** `load()`를 제어할 수 있는 fake — `save()`는 이 slice가 쓰지 않는다(읽기 하나, D-6A1-4). */
class TestStrategyRepository(
    @Volatile var strategy: OperatorStrategy = freshStrategy(),
) : StrategyRepository {
    @Volatile var loadFailure: (() -> Throwable)? = null

    override fun load(): OperatorStrategy = loadFailure?.let { throw it() } ?: strategy

    override fun save(applied: AppliedStrategy) {
        error("이 slice의 test double은 save()를 쓰지 않는다 — 읽기 하나(D-6A1-4)")
    }
}

/** audit 쓰기 하나를 실패시킬 수 있는 recording sink — D-6A1-17 fail-closed 대조에 쓴다. */
class RecordingAuditSink {
    val records: MutableList<ApiAuditRecord> = Collections.synchronizedList(mutableListOf())

    @Volatile var failNext: Boolean = false

    fun record(record: ApiAuditRecord) {
        if (failNext) {
            failNext = false
            throw IllegalStateException("의도된 audit 쓰기 실패(test)")
        }
        records += record
    }
}

/**
 * 실 DB 없이 HTTP 층만 올리는 test 전용 조립 — `PersistenceWiring`(다른 패키지
 * `bidvector.app.wiring`)을 스캔 범위 밖에 둔다(`@SpringBootApplication`의 기본 컴포넌트
 * 스캔이 이 클래스의 패키지 `bidvector.app.http`와 그 하위만 본다). `StrategyReadController`·
 * `GlobalErrorHandler`는 같은 패키지의 main 소스라 스캔으로 자동 등록된다 — 이 클래스는
 * fake 의존과 필터 등록만 배선한다.
 */
@SpringBootApplication
open class HttpTestApplication {
    @Bean
    open fun strategyRepository(): TestStrategyRepository = TestStrategyRepository()

    @Bean
    open fun testClock(): FixedClock = FixedClock(Instant.parse("2026-09-19T00:00:00Z"))

    @Bean
    open fun correlationIdFactory(): CorrelationIdFactory = SequentialCorrelationIdFactory()

    @Bean
    open fun auditSink(): RecordingAuditSink = RecordingAuditSink()

    @Bean
    open fun operatorCredentialFilterRegistration(): FilterRegistrationBean<OperatorCredentialFilter> =
        FilterRegistrationBean(OperatorCredentialFilter(TEST_CREDENTIAL)).apply {
            urlPatterns = listOf("/*")
            order = Ordered.HIGHEST_PRECEDENCE + 1
        }

    @Bean
    open fun requestAuditFilterRegistration(
        clock: FixedClock,
        correlationIdFactory: CorrelationIdFactory,
        auditSink: RecordingAuditSink,
    ): FilterRegistrationBean<RequestAuditFilter> =
        FilterRegistrationBean(RequestAuditFilter(clock, correlationIdFactory, auditSink::record)).apply {
            urlPatterns = listOf("/*")
            order = Ordered.HIGHEST_PRECEDENCE
        }
}
