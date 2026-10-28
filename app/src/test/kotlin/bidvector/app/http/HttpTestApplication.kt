package bidvector.app.http

import bidvector.adapters.evaluation.NoticeWatchSubjectPort
import bidvector.adapters.ml.UnavailableMlAnalysis
import bidvector.app.wiring.BrowserAccessExecutor
import bidvector.app.wiring.EvaluationDryRunFactory
import bidvector.app.wiring.NoticeSearchQuery
import bidvector.app.wiring.NotificationInboxQuery
import bidvector.app.wiring.OpeningResultQuery
import bidvector.app.wiring.StrategyEditExecutor
import bidvector.app.wiring.StrategyQuery
import bidvector.app.wiring.SuggestionQuery
import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyPolicyData
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.opening.OPENING_PROGRESS_POLICY
import bidvector.workflow.opening.ReadOpeningResult
import bidvector.workflow.strategy.Clock
import bidvector.workflow.suggestion.ListSuggestionsUseCase
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.Ordered
import java.time.Instant
import java.time.LocalDate

/**
 * 실 DB 없이 HTTP 층만 올리는 test 전용 조립 — `PersistenceWiring`(다른 패키지
 * `bidvector.app.wiring`)을 스캔 범위 밖에 둔다(`@SpringBootApplication`의 기본 컴포넌트
 * 스캔이 이 클래스의 패키지 `bidvector.app.http`와 그 하위만 본다). `StrategyReadController`·
 * `GlobalErrorHandler`는 같은 패키지의 main 소스라 스캔으로 자동 등록된다 — 이 클래스는
 * fake 의존과 필터 등록만 배선한다.
 *
 * **M6/6E-2a — `DataSourceAutoConfiguration` 을 끈다.** 커넥션 풀 구현이 classpath 에 오면서
 * Boot 의 자동설정이 **`DataSource` 빈이 없는 조립에서** 스스로 풀을 만들려 든다(그 전에는 지원
 * 풀 구현이 없어 조용히 물러나 있었다). 이 조립은 DB 가 없는 HTTP 층 전용이라 접속 정보가 없고,
 * 자동설정은 「드라이버를 정할 수 없다」로 컨텍스트 기동을 깬다. 출하 조립은
 * `PersistenceWiring` 의 빈이 있어 같은 자동설정이 물러난다(그 사실은
 * [bidvector.app.wiring.ProductionPoolRoleTest] 의 빈 개수 단언이 잠근다).
 */
@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
// **병합 주의**: `@Import` 는 반복할 수 없는 애너테이션이다. 레인이 각자 자기 이중체 조립을 들이므로
// 충돌 해소는 **두 줄이 아니라 한 줄의 합집합**이다(7C② 의 입찰가 조립과 7C④ 의 프로필 조립).
@Import(BidPriceTestWiring::class, ProfileTestWiring::class)
open class HttpTestApplication {
    /**
     * M7/7C③ — 알림함 컨트롤러의 협력자. 이 빈이 없으면 **컨텍스트가 기동에 실패**한다:
     * 컨트롤러는 main 의 `bidvector.app.http` 라 이 조립의 스캔에 자동 등록되는데 협력자는
     * `bidvector.app.wiring` 이라 스캔 범위 밖이다.
     *
     * **둘째 `@Configuration` 으로 두지 않는다**(실측으로 걷어낸 길). 그렇게 두면 출하 조립을
     * 띄우는 E2E(`SpringApplicationBuilder(BidVectorApplication::class, …)`)의 스캔에도 걸려
     * 같은 이름의 빈이 두 번 정의되고 `BeanDefinitionOverrideException` 으로 **E2E 여럿이
     * 기동 실패**한다. 빈을 **조립 자신**이 들면 출하 조립은 이 class 를 아예 모르므로 그
     * 충돌이 생길 자리가 없다.
     *
     * **fake 를 빈으로 따로 든다**(r1 검토 verifier F1 뒤). 표면 test 가 한 페이지를 꽂아
     * 직렬화를 실제로 돌려야 「문서 ↔ wire 키 등식」을 잴 수 있고, 그러려면 test 가 그 fake 를
     * 손에 쥐어야 한다 — main 의 `fakeOpeningResultFactsPort` · `openingResultQuery` 짝과 같은
     * 모양이다. 기본값은 여전히 빈 목록이라 꽂지 않는 test 의 거동은 그대로다.
     */
    @Bean
    open fun fakeNotificationInboxStore(): FakeNotificationInboxStore = FakeNotificationInboxStore()

    @Bean
    open fun notificationInboxQuery(
        fakeNotificationInboxStore: FakeNotificationInboxStore,
        clock: FixedClock,
    ): NotificationInboxQuery = NotificationInboxQuery(fakeNotificationInboxStore, clock)

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

    /** 7D — 세션 경로의 이중체 조립(실 DB 없이 쿠키·CSRF 거동만 잰다). */
    @Bean
    open fun browserAccessTransaction(clock: FixedClock): InMemoryBrowserAccessTransaction =
        InMemoryBrowserAccessTransaction(clock)

    @Bean
    open fun browserAccessExecutor(transaction: InMemoryBrowserAccessTransaction): BrowserAccessExecutor =
        BrowserAccessExecutor(transaction)

    /** 출하 조립과 **같은 속성**(Secure 포함)을 쓴다 — 속성 단언이 test 전용 값을 재지 않게. */
    @Bean
    open fun sessionCookieWriter(): SessionCookieWriter =
        SessionCookieWriter(secure = true, absoluteWindow = TEST_ACCESS_POLICY.absoluteWindow)

    /** 7C ① — 공고 검색의 이중체. */
    @Bean
    open fun fakeNoticeSearchPort(): FakeNoticeSearchPort = FakeNoticeSearchPort()

    /** D-6A2b-8 — `NoticeSearchController` 는 포트가 아니라 조회기를 받는다(출하 조립과 같은 형태). */
    @Bean
    open fun noticeSearchQuery(fakeNoticeSearchPort: FakeNoticeSearchPort): NoticeSearchQuery =
        NoticeSearchQuery(fakeNoticeSearchPort)

    /**
     * 7C⑤-a — 개찰 결과 조회의 이중체와 조립. **이 class 안에 둔다**: 바깥의 `@Configuration` 은
     * 출하 조립(`BidVectorApplication`)의 component scan 에도 걸려 같은 이름 빈이 둘이 된다(실측 —
     * `BeanDefinitionOverrideException`). 이 class 는 `@SpringBootApplication` 어노테이션 필터가
     * 출하 scan 에서 제외하므로, test 전용 조립의 자리는 여기다.
     */
    @Bean
    open fun fakeOpeningResultFactsPort(): FakeOpeningResultFactsPort = FakeOpeningResultFactsPort()

    @Bean
    open fun openingResultQuery(
        fakeOpeningResultFactsPort: FakeOpeningResultFactsPort,
        clock: FixedClock,
    ): OpeningResultQuery =
        OpeningResultQuery(ReadOpeningResult(fakeOpeningResultFactsPort, clock, OPENING_PROGRESS_POLICY))

    @Bean // 7B — 입찰 건 이중체 · 옮기는 공고 마감 · 출하 모양의 워크플로(조립은 `testBidCaseWorkflow`)
    open fun inMemoryBidCaseStore(): InMemoryBidCaseStore = InMemoryBidCaseStore()

    @Bean
    open fun movableNoticeDeadlines(clock: FixedClock): MovableNoticeDeadlines = MovableNoticeDeadlines(clock)

    @Bean
    open fun bidCaseWorkflow(
        inMemoryBidCaseStore: InMemoryBidCaseStore,
        movableNoticeDeadlines: MovableNoticeDeadlines,
        clock: FixedClock,
    ) = testBidCaseWorkflow(inMemoryBidCaseStore, movableNoticeDeadlines, clock)

    /** 7C①b — 제안 목록의 이중체 원천(기본 빈 목록)과 면허 게이트(기본 미결). */
    @Bean
    open fun fakeSuggestionCandidates(): FakeSuggestionCandidates = FakeSuggestionCandidates()

    @Bean
    open fun fakeSuggestionLicenseGate(): FakeSuggestionLicenseGate = FakeSuggestionLicenseGate()

    @Bean
    open fun suggestionQuery(
        strategyRepository: TestStrategyRepository,
        fakeSuggestionCandidates: FakeSuggestionCandidates,
        fakeSuggestionLicenseGate: FakeSuggestionLicenseGate,
        clock: FixedClock,
    ): SuggestionQuery =
        SuggestionQuery(
            ListSuggestionsUseCase(
                strategies = strategyRepository,
                profiles = OperatorProfilePort { null },
                candidateSource = fakeSuggestionCandidates,
                watchSubjects = NoticeWatchSubjectPort(),
                licenseGate = fakeSuggestionLicenseGate,
                clock = clock,
            ),
        )

    /**
     * **기본 거부 등록 하나**(출하 조립과 같은 모양) — 인증 경로 둘을 구성으로 받는다.
     * 순서까지 출하와 같게 둔다(자격 헤더 먼저).
     *
     * **main 통합**: 7C ① 쪽은 여기에 옛 `operatorCredentialFilterRegistration` 을 두고 있었는데,
     * 7D 가 그 필터를 기본 거부 등록 하나로 **대체**했다(`OperatorCredentialFilter` →
     * `RequestAuthenticationFilter`, 자격 헤더 경로는 인증기로 그 안에 산다). 등록은 여전히
     * 하나이고 자격 헤더 경로도 그대로다 — 합집합에서 사라진 축이 없다.
     */
    @Bean
    open fun requestAuthenticationFilterRegistration(
        browserAccessExecutor: BrowserAccessExecutor,
        sessionCookieWriter: SessionCookieWriter,
    ): FilterRegistrationBean<RequestAuthenticationFilter> =
        FilterRegistrationBean(
            RequestAuthenticationFilter(
                listOf(
                    OperatorCredentialAuthenticator(OperatorCredential.of(TEST_CREDENTIAL)),
                    BrowserSessionAuthenticator(browserAccessExecutor, sessionCookieWriter),
                ),
            ),
        ).apply {
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
