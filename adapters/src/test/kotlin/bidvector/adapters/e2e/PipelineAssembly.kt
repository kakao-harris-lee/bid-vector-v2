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
     * 요청 스코프 평가 — `EvaluationDryRunFactory` 와 달리 `RecordingNotificationRequestPort`
     * 가 아니라 **production `OutboxNotificationRequestPort`** 를 꽂는다. 그래서 알림 요청이
     * 실제 `outbox` 행으로 남고, 그 행이 relay 의 입력이 된다.
     */
    suspend fun evaluate(): List<CandidateEvaluation> = useCase.evaluate()

    /**
     * 평가와 발송이 **실제로 쓰는** 두 객체에서 출발해 닿는 `bidvector.*` 협력자 전수
     * (verifier r1 F-1). 목록이 아니라 그래프라, 어느 자리를 대역으로 바꾸면 그 대역이 여기
     * 나타난다.
     */
    fun wiredCollaborators(): List<Any> = collaboratorGraph(listOf(useCase, dispatcher))

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

    /**
     * 모르는 payload 는 **조용히 건너뛰지 않는다**(review L-3) — production codec 이 모르는
     * `payload_type` 에 fail-closed 인 것과 같은 처분이다. 건너뛰면 relay 계수만 줄어 사유가
     * 사라진다.
     */
    private fun dispatchFor(
        payload: Any?,
        idempotencyKey: String,
    ): Boolean {
        val requested =
            payload as? NotificationRequestedPayload
                ?: error("relay 가 모르는 outbox payload 를 받았다: ${payload?.let { it::class.qualifiedName }}")
        val intent =
            NotificationIntent(
                idempotencyKey = IdempotencyKey(idempotencyKey),
                owner = E2E_OWNER,
                channel = E2E_CHANNEL,
                contentRef = ContentRef(requested.noticeId),
            )
        val outcome = dispatcher.dispatch(intent)
        return outcome is DeliveryOutcome.Attempted && outcome.result is DeliveryResult.Delivered
    }

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
