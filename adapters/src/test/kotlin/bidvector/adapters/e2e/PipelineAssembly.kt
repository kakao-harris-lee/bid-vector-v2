package bidvector.adapters.e2e

import bidvector.adapters.evaluation.JdbcCandidateSource
import bidvector.adapters.evaluation.NoticeWatchSubjectPort
import bidvector.adapters.evaluation.RequestCapacityPort
import bidvector.adapters.event.EventSql
import bidvector.adapters.event.JdbcEventIdFactory
import bidvector.adapters.event.JdbcInboxPort
import bidvector.adapters.event.JdbcOutboxPort
import bidvector.adapters.ml.GrpcBidPredictionGateway
import bidvector.adapters.ml.GrpcEmbeddingGateway
import bidvector.adapters.ml.JdbcCompetitionSampleSource
import bidvector.adapters.ml.MlCallPolicyData
import bidvector.adapters.ml.testEmbeddingCallEffectivePolicy
import bidvector.adapters.ml.testMlCallEffectivePolicy
import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.JdbcOpeningResultRepository
import bidvector.adapters.persistence.OwnTransactionConnectionSource
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
import bidvector.workflow.event.IdempotencyKey
import bidvector.workflow.event.InboxDecision
import bidvector.workflow.event.NotificationRequestedPayload
import bidvector.workflow.event.decideInbox
import bidvector.workflow.notification.Channel
import bidvector.workflow.notification.ContentRef
import bidvector.workflow.notification.DeliveryOutcome
import bidvector.workflow.notification.DeliveryResult
import bidvector.workflow.notification.DispatchNotification
import bidvector.workflow.notification.NOTIFICATION_DELIVERY_POLICY
import bidvector.workflow.notification.NotificationDeliveryPolicyData
import bidvector.workflow.notification.NotificationIntent
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
 * 전부 production 클래스다 — 이 클래스는 그것들을 잇기만 한다(`EvaluationWiring`·
 * `EvaluationDryRunFactory` 가 production 에서 하는 일을 test 소스셋에서 같은 모양으로).
 *
 * **production 에 relay 를 두지 않는다**(D-6D-3). 6F-10 이 그 자리를 갖는다 — 이 클래스의
 * [relay] 는 그 slice 가 받을 **소비자 모양**을 test 코드로 미리 보여 주는 것이다.
 */
internal class PipelineAssembly(
    private val dataSource: DataSource,
    mlChannel: ManagedChannel,
    private val at: Instant,
    correlationPrefix: String,
    mlPolicy: MlCallPolicyData,
    private val currentActiveBids: Int = 0,
    private val maxActiveBids: Int = E2E_MAX_ACTIVE_BIDS,
) {
    private val clock = fixedClock(at)
    private val javaClock: java.time.Clock = java.time.Clock.fixed(at, ZoneOffset.UTC)
    private val connections = OwnTransactionConnectionSource(dataSource)
    private val notificationPolicy: NotificationDeliveryPolicyData = resolvedNotificationPolicy(at)

    val outbox = JdbcOutboxPort(connections)
    val inbox = JdbcInboxPort(connections)
    val sender = RecordingNotificationSender(at, notificationPolicy)

    private val prediction =
        GrpcBidPredictionGateway(mlChannel, testMlCallEffectivePolicy(mlPolicy), javaClock)
    private val embedding =
        GrpcEmbeddingGateway(mlChannel, testEmbeddingCallEffectivePolicy(mlPolicy), javaClock)
    private val correlationIds = SequentialCorrelationIdFactory(correlationPrefix)

    /**
     * 요청 스코프 평가 — `EvaluationDryRunFactory` 와 달리 `RecordingNotificationRequestPort`
     * 가 아니라 **production `OutboxNotificationRequestPort`** 를 꽂는다. 그래서 알림 요청이
     * 실제 `outbox` 행으로 남고, 그 행이 relay 의 입력이 된다.
     */
    suspend fun evaluate(): List<CandidateEvaluation> = useCase().evaluate()

    /**
     * 이 조립이 실제로 쥐고 있는 **협력자 인스턴스**(설계 검토 (2) 우회 6). 손으로 적은 이름
     * 목록이 아니라 살아 있는 객체라, 어느 자리를 fake 로 바꾸면 그 객체의 출처가 바뀐다 —
     * test 가 각 객체의 `CodeSource` 를 보고 production 출력에서 왔는지 잰다.
     */
    fun productionCollaborators(): List<Any> =
        listOf(
            useCase(),
            opportunityAnalysis(),
            prediction,
            embedding,
            licenseGate(),
            JdbcCandidateSource(dataSource, clock, CANDIDATE_CAP),
            OutboxNotificationRequestPort(outbox, JdbcEventIdFactory(), clock),
            outbox,
            inbox,
            dispatcher(),
            JdbcStrategyRepository(dataSource, resolvedStrategyPolicy(at)),
            JdbcOperatorProfileRepository(dataSource),
        )

    /** 포트 경계 fake 전수 — 이 넷 밖에 fake 가 있으면 경계를 넘은 것이다. */
    fun portBoundaryFakes(): List<Any> =
        listOf(
            sender,
            SingleRouteDirectory(E2E_OWNER, E2E_CHANNEL, E2E_ROUTE),
            EchoContentRenderer(),
            AbsentWorkloadPort(),
        )

    private fun useCase(): EvaluateCandidatesUseCase =
        EvaluateCandidatesUseCase(
            strategies = JdbcStrategyRepository(dataSource, resolvedStrategyPolicy(at)),
            candidateSource = JdbcCandidateSource(dataSource, clock, CANDIDATE_CAP),
            watchSubjects = NoticeWatchSubjectPort(),
            licenseGate = licenseGate(),
            mlAnalysis = opportunityAnalysis(),
            capacity = RequestCapacityPort(currentActiveBids, maxActiveBids),
            notifications = OutboxNotificationRequestPort(outbox, JdbcEventIdFactory(), clock),
            correlationIds = correlationIds,
            clock = clock,
        )

    private fun licenseGate(): StoredRequirementLicenseGate =
        StoredRequirementLicenseGate(
            JdbcRequirementStore(dataSource),
            JdbcOperatorProfileRepository(dataSource),
            resolvedLicensePolicy(at),
        )

    private fun opportunityAnalysis(): OpportunityAnalysis =
        OpportunityAnalysis(
            embed = embedding,
            prediction = prediction,
            profile = JdbcOperatorProfileRepository(dataSource),
            workload = AbsentWorkloadPort(),
            watchSubjects = NoticeWatchSubjectPort(),
            capacity = RequestCapacityPort(currentActiveBids, maxActiveBids),
            samples =
                JdbcCompetitionSampleSource(
                    dataSource,
                    JdbcNoticeRepository(dataSource),
                    JdbcOpeningResultRepository(dataSource),
                    javaClock,
                ),
            clock = clock,
        )

    /**
     * test relay(축 ①의 마지막 구간) — `claim` → inbox 중복 제거 → `DispatchNotification` →
     * fake sender → 종단 전이. 반환은 **실제로 발송까지 간 건수**다.
     *
     * **종단 전이는 `OutboxPort.markDelivered` 가 아니라 production 전이 SQL 상수를 직접
     * 실행한다.** `OutboxTransition.ToDelivered` 의 생성자가 `workflow` 의 `internal` 이라
     * `adapters` 는 그 인자를 만들 수 없고(4C-2 `OPEN-4C2-MARK-UNEXERCISED`), 그 통로를
     * 열어 해결하지 않는다는 것이 4C-2 가 명시한 결정이다. 사본이 아니라 production 과
     * **같은 상수**를 쓴다(선례 `OutboxTransitionSqlTest`) — 전이표 자체의 거부는
     * `OutboxTransitionTableTest`(workflow)와 `OutboxTransitionSqlTest`(adapters)가 각각
     * 이미 잠근다. port 메서드 자신의 호출은 6F-10 이 받는다(알려진 제한).
     */
    fun relay(limit: Int = RELAY_CLAIM_LIMIT): Int {
        var delivered = 0
        outbox.claim(limit).forEach { row ->
            if (decideInbox(inbox.hasProcessed(row.idempotencyKey)) == InboxDecision.Process) {
                inbox.markProcessed(row.idempotencyKey)
                if (dispatchFor(row.payload, row.idempotencyKey.value)) {
                    markDelivered(row.entryId.value)
                    delivered += 1
                }
            }
        }
        return delivered
    }

    private fun dispatchFor(
        payload: Any?,
        idempotencyKey: String,
    ): Boolean {
        val requested = payload as? NotificationRequestedPayload ?: return false
        val intent =
            NotificationIntent(
                idempotencyKey = IdempotencyKey(idempotencyKey),
                owner = E2E_OWNER,
                channel = E2E_CHANNEL,
                contentRef = ContentRef(requested.noticeId),
            )
        val outcome = dispatcher().dispatch(intent)
        return outcome is DeliveryOutcome.Attempted && outcome.result is DeliveryResult.Delivered
    }

    private fun dispatcher(): DispatchNotification =
        DispatchNotification(
            routes = SingleRouteDirectory(E2E_OWNER, E2E_CHANNEL, E2E_ROUTE),
            renderer = EchoContentRenderer(),
            sender = sender,
            policyData = notificationPolicy,
            // 발송이 실제로 일어나는 유일한 모드. sender 는 fake 라 외부 호출은 0 이다.
            environment = RuntimeEnvironment.Production,
        )

    private fun markDelivered(entryId: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(EventSql.MARK_DELIVERED).use { statement ->
                statement.setString(1, entryId)
                check(statement.executeUpdate() == 1) { "DELIVERED 전이가 행을 옮기지 못했다: $entryId" }
            }
        }
    }
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
