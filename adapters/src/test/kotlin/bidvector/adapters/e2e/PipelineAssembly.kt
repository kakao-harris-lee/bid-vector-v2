package bidvector.adapters.e2e

import bidvector.adapters.evaluation.JdbcCandidateSource
import bidvector.adapters.evaluation.NoticeWatchSubjectPort
import bidvector.adapters.evaluation.RequestCapacityPort
import bidvector.adapters.event.ConsumerTransactions
import bidvector.adapters.event.JdbcEventIdFactory
import bidvector.adapters.event.JdbcInboxPort
import bidvector.adapters.event.JdbcOutboxPort
import bidvector.adapters.event.PostgresAdvisoryLockLease
import bidvector.adapters.ml.GrpcBidPredictionGateway
import bidvector.adapters.ml.GrpcEmbeddingGateway
import bidvector.adapters.ml.JdbcCompetitionSampleSource
import bidvector.adapters.ml.MlCallPolicyData
import bidvector.adapters.ml.testEmbeddingCallEffectivePolicy
import bidvector.adapters.ml.testMlCallEffectivePolicy
import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcOpeningResultRepository
import bidvector.adapters.persistence.OwnTransactionConnectionSource
import bidvector.adapters.persistence.TransactionBoundary
import bidvector.adapters.profile.JdbcOperatorProfileRepository
import bidvector.adapters.qualification.JdbcRequirementStore
import bidvector.adapters.qualification.StoredRequirementLicenseGate
import bidvector.adapters.strategy.JdbcStrategyRepository
import bidvector.qualification.LICENSE_QUALIFICATION_POLICY
import bidvector.qualification.LicenseQualificationPolicyData
import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyPolicyData
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.EvaluateCandidatesUseCase
import bidvector.workflow.evaluation.OpportunityAnalysis
import bidvector.workflow.evaluation.OutboxNotificationRequestPort
import bidvector.workflow.notification.Channel
import bidvector.workflow.notification.DispatchNotification
import bidvector.workflow.notification.NOTIFICATION_DELIVERY_POLICY
import bidvector.workflow.notification.NotificationDeliveryPolicyData
import bidvector.workflow.notification.RelayOutboxNotifications
import bidvector.workflow.notification.RelayReport
import bidvector.workflow.notification.RelayTarget
import bidvector.workflow.notification.RouteKey
import bidvector.workflow.notification.RuntimeEnvironment
import bidvector.workflow.strategy.OperatorId
import io.grpc.ManagedChannel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.sql.DataSource

/**
 * 평가 → outbox → relay → 발송 구간의 **production 조립**(6D-1 축 ①). `EvaluateCandidatesUseCase`·
 * `OpportunityAnalysis`·두 gRPC gateway·`StoredRequirementLicenseGate`·`JdbcCandidateSource`·
 * `OutboxNotificationRequestPort`·`JdbcOutboxPort`·`JdbcInboxPort`·`DispatchNotification` 은
 * 전부 production 클래스다 — 이 클래스는 그것들을 잇기만 한다.
 *
 * **협력자의 생성 자리는 하나다**(review G-2). 앞 판은 배선과 출처 단언이 각자 생성자를 불러
 * 같은 타입의 **다른 인스턴스 둘**을 만들었고, 그래서 배선만 대역으로 바꿔도 단언이 초록이었다.
 * 지금은 전부 `val` 필드이고 [useCase]·[dispatcher] 도 한 번만 만든다 — [wiredCollaborators] 가
 * 그 살아 있는 객체에서 필드 그래프를 따라 내려간다.
 *
 * **production 에 relay 를 두지 않는다**(D-6D-3). 6F-10 이 그 자리를 갖는다 — [relay] 는 그
 * slice 가 받을 **소비자 모양**을 test 코드로 미리 보여 주는 것이다.
 */
internal class PipelineAssembly(
    private val dataSource: DataSource,
    mlChannel: ManagedChannel,
    at: Instant,
    correlationPrefix: String,
    mlPolicy: MlCallPolicyData,
    currentActiveBids: Int = 0,
    maxActiveBids: Int = E2E_MAX_ACTIVE_BIDS,
) {
    private val clock = fixedClock(at)
    private val javaClock: java.time.Clock = java.time.Clock.fixed(at, ZoneOffset.UTC)
    private val connections = OwnTransactionConnectionSource(dataSource)
    private val notificationPolicy: NotificationDeliveryPolicyData = resolvedNotificationPolicy(at)

    val outbox = JdbcOutboxPort(connections)
    val inbox = JdbcInboxPort(connections)
    val sender = RecordingNotificationSender(at, notificationPolicy)

    private val profiles = JdbcOperatorProfileRepository(dataSource)
    private val watchSubjects = NoticeWatchSubjectPort()
    private val capacity = RequestCapacityPort(currentActiveBids, maxActiveBids)

    private val mlAnalysis =
        OpportunityAnalysis(
            embed = GrpcEmbeddingGateway(mlChannel, testEmbeddingCallEffectivePolicy(mlPolicy), javaClock),
            prediction = GrpcBidPredictionGateway(mlChannel, testMlCallEffectivePolicy(mlPolicy), javaClock),
            profile = profiles,
            workload = AbsentWorkloadPort(),
            watchSubjects = watchSubjects,
            capacity = capacity,
            samples =
                JdbcCompetitionSampleSource(
                    dataSource,
                    JdbcNoticeRepository(dataSource),
                    JdbcOpeningResultRepository(dataSource),
                    javaClock,
                ),
            clock = clock,
        )

    private val useCase =
        EvaluateCandidatesUseCase(
            strategies = JdbcStrategyRepository(dataSource, resolvedStrategyPolicy(at)),
            candidateSource = JdbcCandidateSource(dataSource, clock, CANDIDATE_CAP),
            watchSubjects = watchSubjects,
            licenseGate =
                StoredRequirementLicenseGate(
                    JdbcRequirementStore(dataSource),
                    profiles,
                    resolvedLicensePolicy(at),
                ),
            mlAnalysis = mlAnalysis,
            capacity = capacity,
            notifications = OutboxNotificationRequestPort(outbox, JdbcEventIdFactory(), clock),
            correlationIds = SequentialCorrelationIdFactory(correlationPrefix),
            clock = clock,
        )

    private val dispatcher =
        DispatchNotification(
            routes = SingleRouteDirectory(E2E_OWNER, E2E_CHANNEL, E2E_ROUTE),
            renderer = EchoContentRenderer(),
            sender = sender,
            policyData = notificationPolicy,
            // 발송이 실제로 일어나는 유일한 모드. sender 는 fake 라 외부 호출은 0 이다.
            environment = RuntimeEnvironment.Production,
        )

    /**
     * relay 의 커밋 경계는 **평가 경로와 다르다** — 평가는 요청 하나 = 트랜잭션 하나
     * ([OwnTransactionConnectionSource])이고, relay 는 T1/T2 를 스스로 갈라야 하므로
     * [TransactionBoundary] 를 쥔다(그 경계를 outbox·inbox·transactions 셋에 **같은 객체로**
     * 넘긴다). 이 비대칭이 production 조립의 비대칭과 같다.
     */
    private val relayBoundary = TransactionBoundary(dataSource)

    private val relayUseCase =
        RelayOutboxNotifications(
            outbox = JdbcOutboxPort(relayBoundary),
            inbox = JdbcInboxPort(relayBoundary),
            dispatcher = dispatcher,
            leases = PostgresAdvisoryLockLease(dataSource),
            transactions = ConsumerTransactions(relayBoundary),
            target = RelayTarget(E2E_OWNER, E2E_CHANNEL),
            environment = RuntimeEnvironment.Production,
            policy = notificationPolicy,
        )

    /**
     * 요청 스코프 평가 — `EvaluationDryRunFactory` 와 달리 `RecordingNotificationRequestPort`
     * 가 아니라 **production `OutboxNotificationRequestPort`** 를 꽂는다. 그래서 알림 요청이
     * 실제 `outbox` 행으로 남고, 그 행이 relay 의 입력이 된다.
     */
    suspend fun evaluate(): List<CandidateEvaluation> = useCase.evaluate()

    /**
     * 평가와 발송이 **실제로 쓰는** 두 객체에서 출발해 닿는 협력자 전수. 목록이 아니라 그래프라,
     * 어느 자리를 대역으로 바꾸면 그 대역이 여기 나타난다 — 패키지 이름과 무관하다.
     */
    fun wiredCollaborators(): CollaboratorGraph = collaboratorGraph(listOf(useCase, dispatcher))

    /**
     * **production relay**(6F-10 ⓐ) — 6D-1 이 이 자리에 두었던 test relay 를 교체했다.
     * `claim` → inbox 판정 → `DispatchNotification` → 종단 전이 전부가 production
     * [RelayOutboxNotifications] 의 것이고, 이 클래스가 바꿔 끼우는 것은 **발송 축 셋**
     * (route·renderer·sender)뿐이다 — 실 채널이 없기 때문이다(`OPEN-STR-12`).
     *
     * 사라진 것 둘: ① `EventSql.MARK_DELIVERED` 를 raw 로 실행하던 사본(종단 전이가 이제
     * `OutboxPort.markDelivered` 를 지난다 — `OPEN-4C2-MARK-UNEXERCISED` 종결) ② inbox
     * **선기록**(production 은 `Delivered` 뒤에만 기록한다 — 6D-1 이 인계한 좌초·키 소진
     * 자리). 반환도 건수(Int)가 아니라 [RelayReport] 다.
     *
     * 환경이 `Production` 인 이유는 그것이 `DeliveryMode.Live` 인 유일한 환경이라서다 —
     * 다른 환경에서는 relay 가 claim 자체를 하지 않는다(억제). sender 는 fake 라 외부 호출은
     * 0 이다.
     */
    fun relay(limit: Int = RELAY_CLAIM_LIMIT): RelayReport = relayUseCase.relay(limit)
}

private fun resolvedStrategyPolicy(at: Instant): Resolution.Resolved<StrategyPolicyData> {
    val resolution = STRATEGY_POLICY.resolve(LocalDate.ofInstant(at, ZoneOffset.UTC))
    check(resolution is Resolution.Resolved) { "STRATEGY_POLICY 가 해소되지 않았다: $resolution" }
    return resolution
}

private fun resolvedLicensePolicy(at: Instant): Resolution.Resolved<LicenseQualificationPolicyData> {
    val resolution = LICENSE_QUALIFICATION_POLICY.resolve(LocalDate.ofInstant(at, ZoneOffset.UTC))
    check(resolution is Resolution.Resolved) { "LICENSE_QUALIFICATION_POLICY 가 해소되지 않았다: $resolution" }
    return resolution
}

private fun resolvedNotificationPolicy(at: Instant): NotificationDeliveryPolicyData {
    val resolution = NOTIFICATION_DELIVERY_POLICY.resolve(LocalDate.ofInstant(at, ZoneOffset.UTC))
    check(resolution is Resolution.Resolved) { "NOTIFICATION_DELIVERY_POLICY 가 해소되지 않았다: $resolution" }
    return resolution.value
}

internal val E2E_OWNER = OperatorId("e2e-operator")
internal val E2E_CHANNEL = Channel.Telegram
internal val E2E_ROUTE = RouteKey("e2e-telegram-route")
internal const val CANDIDATE_CAP = 100
internal const val RELAY_CLAIM_LIMIT = 50
