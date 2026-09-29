package bidvector.app.http

import bidvector.adapters.ml.UnavailableMlAnalysis
import bidvector.adapters.strategy.StrategyEditTransaction
import bidvector.app.wiring.EvaluationDryRunFactory
import bidvector.app.wiring.StrategyEditExecutor
import bidvector.app.wiring.StrategyQuery
import bidvector.procurement.Notice
import bidvector.qualification.LicenseVerdict
import bidvector.sharedkernel.Resolution
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.WatchSubjectOutcome
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.AppliedStrategy
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.EDIT_SESSION_POLICY
import bidvector.workflow.strategy.EditSession
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.EditSessionPolicyData
import bidvector.workflow.strategy.EditSessionRepository
import bidvector.workflow.strategy.EditSessionSnapshot
import bidvector.workflow.strategy.EditStrategyWorkflow
import bidvector.workflow.strategy.EventSink
import bidvector.workflow.strategy.StrategyRepository
import bidvector.workflow.strategy.toSnapshot
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

/**
 * 매핑 패턴의 경로 변수(`{sessionId}` 등)를 구체 값으로 바꿀 때 쓰는 정규식 — 기계 전수
 * test 들이 공유한다(같은 값을 파일마다 베끼지 않는다).
 */
val PATH_VARIABLE_PATTERN: Regex = Regex("\\{[^/}]+}")

/** 실 자격증명과 다른, test 고정값 — 운영 값과 헷갈리지 않게 문구를 담는다(비밀값이 아니다). */
const val TEST_CREDENTIAL = "http-layer-test-fixture-credential"

/**
 * `BidVectorApplication.main()`이 굳히는 두 속성을 `HttpTestApplication` 기반 test에도
 * 그대로 준다 — `@SpringBootTest`는 `main()`을 부르지 않고, annotation 속성은 컴파일
 * 시간 상수만 받아 `main()`의 [bidvector.app.PRODUCTION_DISPATCH_PROPERTIES]를 직접
 * 참조할 수 없어 이 두 `const val`로 값만 복제한다.
 *
 * **D-6A1-27 — 이 복제는 드리프트를 스스로 드러내지 않는다.**
 * `main()`의 `PRODUCTION_DISPATCH_PROPERTIES`에서 두 속성을 지워도 이 test들은 **자기
 * 사본**을 그대로 쓰므로 영향받지 않고 exit 0이다. 실제 drift 감지는
 * `ProductionAssemblyAuthAuditTest`(production 조립을 직접 부팅하고
 * `PRODUCTION_DISPATCH_PROPERTIES`를 참조로 공유한다)가 진다 — 이 두 `const val`은
 * `HttpTestApplication` 계열 test의 **재현 사본**일 뿐, drift 게이트가 아니다.
 */
const val PROP_THROW_EXCEPTION_IF_NO_HANDLER_FOUND = "spring.mvc.throw-exception-if-no-handler-found=true"
const val PROP_NO_STATIC_RESOURCE_MAPPINGS = "spring.web.resources.add-mappings=false"

/**
 * **이 test 전용 조립에는 관리 서버가 없다.** `-1` 은 Boot 의 `ManagementPortType
 * .DISABLED` 로, actuator 의 web endpoint 배선 자체가 올라오지 않는다.
 *
 * 왜 필요한가(실측): actuator 좌표가 들어오자 이 조립에 `RequestMappingHandlerMapping`
 * **빈이 둘**(우리 것 + actuator 의 controller endpoint 매핑)이 되어 `OperatorAuthenticationTest`
 * 의 타입 주입이 `NoUniqueBeanDefinitionException` 으로 깨졌다. 이름으로 한정하는 대신 조립에서
 * 빼는 쪽을 고른다 — 이 test 들이 재는 것은 **API 포트의 필터 체인**이고, 그 조립에 actuator 가
 * 있으면 「등록된 모든 endpoint」 기계 전수의 모집단이 우리 것이 아닌 경로로 오염된다.
 *
 * 출하 조립의 actuator 표면은 `ManagementHealthSurfaceTest`(별도 관리 포트)와 CI 스모크가 잰다 —
 * 그쪽은 포트가 갈려 actuator 매핑이 child context 에 살고 이 애매성이 애초에 없다.
 */
const val PROP_NO_MANAGEMENT_SERVER = "management.server.port=-1"

/**
 * `TestRestTemplate`을 **직접 만든다** — Boot 4.1의 `@AutoConfigureTestRestTemplate`
 * 자동 배선이 `@ConditionalOnMissingBean`의 타입 추론에서 예외를 던지는 것을 실측했다
 * (`org.springframework.boot.resttestclient.autoconfigure.TestRestTemplateTestAutoConfiguration
 * .testRestTemplate` — 이 Boot 버전 자체의 결함으로 보인다). 이 slice는 그
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

/**
 * `load()`를 제어할 수 있는 fake. **`save()`가 실제로 저장한다**: 편집 endpoint test 가 「confirm 뒤 `GET /api/strategy`
 * 가 새 값을 낸다」를 같은 저장소에서 확인한다.
 */
class TestStrategyRepository(
    @Volatile var strategy: OperatorStrategy = freshStrategy(),
) : StrategyRepository {
    @Volatile var loadFailure: (() -> Throwable)? = null

    override fun load(): OperatorStrategy = loadFailure?.let { throw it() } ?: strategy

    override fun save(applied: AppliedStrategy) {
        strategy = applied.strategy
    }
}

/**
 * `EvaluationDryRunController`(같은 패키지 main)가 `EvaluationDryRunFactory`
 * 를 요구해 `HttpTestApplication` 기반 test(auth·OpenAPI 계약 등, 후보 거동을 보지 않는
 * test)가 빈 후보 목록으로 최소 배선을 한다. 후보가 비어 있어 [watchSubjects]·[licenseGate]
 * 는 절대 안 불린다 — `error()`로 그 사실 자체를 잠근다(우연히 불리면 test가 곧바로 실패).
 *
 * **D-6A3-20 — [failure]는 `TestStrategyRepository.
 * loadFailure`와 같은 관례다.** `OpenApiContractTest`가 409 `CANDIDATE_CAP_EXCEEDED`
 * (D-6A3-7)의 HTTP 층 형태를 잴 때만 이 값을 채운다 — 기본값은 `null`(빈 목록 그대로).
 */
class EmptyCandidateSource : CandidateSourcePort {
    @Volatile var failure: (() -> Throwable)? = null

    override fun openCandidates(): List<Notice> = failure?.let { throw it() } ?: emptyList()
}

private class UnreachableWatchSubjectPort : WatchSubjectPort {
    override fun subjectFor(notice: Notice): WatchSubjectOutcome = error("빈 후보 목록에서는 불릴 수 없다")
}

private class UnreachableLicenseGatePort : LicenseGatePort {
    override fun verdictFor(notice: Notice): LicenseVerdict = error("빈 후보 목록에서는 불릴 수 없다")
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
 * 실 DB 없이 편집 endpoint 를 도는 트랜잭션 경계 이중체. **use case 는 실물**
 * 이다(상태 기계·멱등·만료 판정을 test 사본으로 다시 짓지 않는다) — 바뀌는 것은 커넥션
 * 경계뿐이라, 이 이중체 위에서 재는 것은 「HTTP 층이 결과를 어떤 상태 코드로 옮기는가」다.
 * 원자성 자체(전략+outbox+세션 한 커밋)는 이 자리가 아니라 실 DB test 가 잰다.
 */
class InMemoryStrategyEditTransaction(
    private val strategies: StrategyRepository,
    private val clock: Clock,
) : StrategyEditTransaction {
    val sessions: InMemoryEditSessionRepository = InMemoryEditSessionRepository()
    val events: RecordingStrategyEventSink = RecordingStrategyEventSink()

    /** Spring 컨텍스트가 test 사이에 재사용되므로 세션·발행 이력을 매 test 전에 비운다. */
    fun reset() {
        sessions.clear()
        events.published.clear()
        events.actors.clear()
    }

    override fun <T> inTransaction(action: (EditStrategyWorkflow) -> T): T {
        val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
        val sessionPolicy =
            (EDIT_SESSION_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<EditSessionPolicyData>).value
        return action(EditStrategyWorkflow(sessions, strategies, clock, events, policy, sessionPolicy))
    }
}

/** `EditSession` 은 `internal constructor` 라 저장은 값 그대로, 반환은 원시 스냅숏으로 한다(D-6B1-7). */
class InMemoryEditSessionRepository : EditSessionRepository {
    private val stored = mutableMapOf<EditSessionId, EditSession>()

    override fun load(id: EditSessionId): EditSessionSnapshot? = stored[id]?.toSnapshot()

    override fun save(session: EditSession) {
        stored[session.id] = session
    }

    fun clear() = stored.clear()
}

class RecordingStrategyEventSink : EventSink {
    val published: MutableList<StrategyEvent> = Collections.synchronizedList(mutableListOf())

    override fun publish(
        event: StrategyEvent,
        actor: Actor,
    ) {
        published += event
        actors += actor
    }

    val actors: MutableList<Actor> = Collections.synchronizedList(mutableListOf())
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

    /** D-6A2b-8 — `StrategyReadController` 는 포트가 아니라 이 조회기를 받는다. */
    @Bean
    open fun strategyQuery(strategyRepository: TestStrategyRepository): StrategyQuery =
        StrategyQuery(strategyRepository)

    @Bean
    open fun strategyEditTransaction(
        strategyRepository: TestStrategyRepository,
        clock: FixedClock,
    ): InMemoryStrategyEditTransaction = InMemoryStrategyEditTransaction(strategyRepository, clock)

    @Bean
    open fun strategyEditExecutor(strategyEditTransaction: InMemoryStrategyEditTransaction): StrategyEditExecutor =
        StrategyEditExecutor(
            strategyEditTransaction,
            STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>,
        )

    @Bean
    open fun testClock(): FixedClock = FixedClock(Instant.parse("2026-09-19T00:00:00Z"))

    @Bean
    open fun correlationIdFactory(): CorrelationIdFactory = SequentialCorrelationIdFactory()

    @Bean
    open fun auditSink(): RecordingAuditSink = RecordingAuditSink()

    /** D-6A3-20 — `OpenApiContractTest`가 [EmptyCandidateSource.failure]를 autowire 해 제어한다. */
    @Bean
    open fun emptyCandidateSource(): EmptyCandidateSource = EmptyCandidateSource()

    /**
     * `EvaluationDryRunController`(같은 패키지 main)가 요구하는 최소 배선 — 후보가
     * 비어 있어 [watchSubjects]·[licenseGate]는 절대 안 불린다(auth·OpenAPI 계약
     * test 는 후보 거동을 보지 않는다). 실 후보·판정 거동은 production 조립 E2E
     * (`ProductionAssemblyAuthAuditTest` 계열)가 잰다.
     */
    @Bean
    open fun evaluationDryRunFactory(
        strategyRepository: TestStrategyRepository,
        candidateSource: EmptyCandidateSource,
        clock: Clock,
        correlationIdFactory: CorrelationIdFactory,
    ): EvaluationDryRunFactory =
        EvaluationDryRunFactory(
            strategyRepository = strategyRepository,
            candidateSource = candidateSource,
            watchSubjects = UnreachableWatchSubjectPort(),
            licenseGate = UnreachableLicenseGatePort(),
            mlAnalysis = UnavailableMlAnalysis(),
            correlationIds = correlationIdFactory,
            clock = clock,
        )

    @Bean
    open fun operatorCredentialFilterRegistration(): FilterRegistrationBean<OperatorCredentialFilter> =
        FilterRegistrationBean(OperatorCredentialFilter(OperatorCredential.of(TEST_CREDENTIAL))).apply {
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
