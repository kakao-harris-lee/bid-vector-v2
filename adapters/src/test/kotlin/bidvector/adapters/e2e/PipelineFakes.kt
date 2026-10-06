package bidvector.adapters.e2e

import bidvector.decision.UnitScore
import bidvector.decision.priority.derive.DerivationAbsence
import bidvector.decision.priority.derive.DerivationOutcome
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.WorkloadPort
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.notification.Channel
import bidvector.workflow.notification.ChannelRoute
import bidvector.workflow.notification.ContentRef
import bidvector.workflow.notification.ContentRenderer
import bidvector.workflow.notification.DeliveryRequest
import bidvector.workflow.notification.DeliveryResult
import bidvector.workflow.notification.MaskedTarget
import bidvector.workflow.notification.NotificationDeliveryPolicyData
import bidvector.workflow.notification.NotificationSender
import bidvector.workflow.notification.RenderedContent
import bidvector.workflow.notification.RouteDirectory
import bidvector.workflow.notification.RouteKey
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.OperatorId
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/*
 * 6D-1 E2E 가 **포트 경계에만** 두는 fake 묶음(scope.md D-6D-3). use case·어댑터는 전부
 * production 클래스이고, 이 파일의 타입들은 그 바깥 경계 — 시각·상관관계 식별자·workload
 * 집계·배달 경로/렌더러/sender — 만 대신한다. 그 경계 넷은 **production 구현이 저장소에
 * 아직 없다**(`WorkloadPort`·`RouteDirectory`·`ContentRenderer`·`NotificationSender` 전부
 * `OPEN-STR-12`·후속 slice 소관) — 즉 이 fake 들은 production 을 가리는 것이 아니라
 * 아직 없는 자리를 채운다.
 *
 * `workflow` 모듈 test 에 같은 모양(`FakeNotificationSender`·`EvaluationTestFixtures`)이
 * 이미 있으나 모듈 경계를 넘어 재사용할 수 없다(교차 모듈 test 의존이 저장소에 0 건) —
 * `BidNowFakeMlAnalysisTestConfiguration` 이 같은 사유로 「형태만 옮긴다」고 적은 선례를
 * 따른다.
 */

/** 고정 시각 — 재현 등식(⑤)이 두 run 에 같은 값을 주려면 시각이 run 사이에 움직이면 안 된다. */
internal fun fixedClock(at: Instant): Clock = Clock { at }

/**
 * 순번 상관관계 식별자 — 재현 등식(⑤)의 축. 같은 입력이면 n 번째 후보가 늘 같은 값을
 * 받는다(난수를 쓰면 두 run 의 payload 가 늘 달라 등식 자체가 성립하지 않는다).
 */
internal class SequentialCorrelationIdFactory(
    private val prefix: String,
) : CorrelationIdFactory {
    private val next = AtomicInteger(0)

    override fun newId(): CorrelationId = CorrelationId("$prefix-${next.getAndIncrement()}")
}

/** workload 집계는 아직 수집되지 않는다(4B-5 `WorkloadNotCollected`) — 값을 지어내지 않는다. */
internal class AbsentWorkloadPort : WorkloadPort {
    override fun current(): DerivationOutcome<UnitScore> =
        DerivationOutcome.Absent(DerivationAbsence.WorkloadNotCollected)
}

/** 단일 소유자·단일 채널 경로 — 실 경로 저장소는 `OPEN-STR-12`. */
internal class SingleRouteDirectory(
    private val owner: OperatorId,
    private val channel: Channel,
    private val route: RouteKey,
) : RouteDirectory {
    override fun routesFor(owner: OperatorId): Map<Channel, ChannelRoute> =
        if (owner == this.owner) mapOf(channel to ChannelRoute(enabled = true, key = route)) else emptyMap()
}

/** 렌더러는 내용의 옳음을 재지 않는다(4E 위협 모델 밖) — 참조를 그대로 본문으로 옮긴다. */
internal class EchoContentRenderer : ContentRenderer {
    override fun render(
        contentRef: ContentRef,
        channel: Channel,
    ): RenderedContent = RenderedContent(channel, contentRef.value)
}

/**
 * 발송 기록 sender(scope.md 절대 금지 — 실 발송 0). 같은 `idempotencyKey` 재호출은 앞
 * 결과를 그대로 돌려준다(`NotificationSender` 계약, `SenderContractTest` 와 같은 골격).
 */
internal class RecordingNotificationSender(
    private val at: Instant,
    private val policy: NotificationDeliveryPolicyData,
) : NotificationSender {
    private val delivered = linkedMapOf<String, DeliveryResult>()
    private val requests = mutableListOf<DeliveryRequest>()
    private val bodies = mutableListOf<String>()

    /** 발송 기록 — 단언은 이 목록과 DB 상태로만 한다(로그 문자열 아님). */
    fun sentKeys(): List<String> = requests.map { it.idempotencyKey.value }

    fun sentRoutes(): List<String> = requests.map { it.route.value }

    fun sentBodies(): List<String> = bodies.toList()

    fun callCount(): Int = requests.size

    override fun send(
        request: DeliveryRequest,
        content: RenderedContent,
    ): DeliveryResult {
        requests += request
        bodies += content.body
        return delivered.getOrPut(request.idempotencyKey.value) {
            DeliveryResult.Delivered(at, MaskedTarget.mask(request.route.value, policy))
        }
    }
}
