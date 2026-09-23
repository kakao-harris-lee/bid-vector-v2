package bidvector.workflow.evaluation

import bidvector.workflow.event.AggregateId
import bidvector.workflow.event.AggregateVersion
import bidvector.workflow.event.EventIdFactory
import bidvector.workflow.event.IdempotencyKey
import bidvector.workflow.event.NotificationEvidencePayload
import bidvector.workflow.event.NotificationRequestedPayload
import bidvector.workflow.event.OutboxPort
import bidvector.workflow.event.newEnvelope
import bidvector.workflow.strategy.Clock
import java.sql.SQLException

/**
 * `NotificationRequestPort`의 production 구현(D-6F7-1, scope.md, **패키지 정정** —
 * 아래 참고) — `NotificationRequest`를 봉투에 실어 [outbox]에 등록한다.
 *
 * **이 클래스는 `workflow.event`가 아니라 `workflow.evaluation`에 있다(운영자 승인
 * 대상 정정 — `ArchitectureGateTest` 실측).** 착수 계약(D-6F7-1)은 "봉투 생성이
 * `workflow` 모듈에 `internal`이라 `workflow` 안 아무 패키지에나 둘 수 있다"까지만
 * 확인했고 **어느 패키지**가 안전한지는 실측하지 않았다 — `workflow.event`에 두면
 * 이 클래스가 `NotificationRequest`(`workflow.evaluation`)·`PredictionEvidence`의
 * `diagnostics`/`release`(`workflow.prediction`)를 참조하는 순간 `workflow.event ->
 * workflow.evaluation`·`workflow.event -> workflow.prediction` 간선이 새로 생긴다.
 * `workflow.evaluation -> workflow.event`(`NotificationRequest.correlationId:
 * CorrelationId`)·`workflow.evaluation -> workflow.prediction`(`PredictionEvidence.
 * Diagnosed`의 필드)·`workflow.prediction -> workflow.event`(`BidPredictionRequest.
 * correlationId`)는 **이미 있는 간선**이라, `workflow.event`에서 반대 방향 간선을
 * 더하면 순환이 닫힌다(`ArchitectureGateTest`가 다섯 순환을 실측 — `workflow.event
 * <-> workflow.evaluation`·`workflow.event <-> workflow.prediction`과 `workflow.
 * embedding`을 지나는 파생 순환 셋). **`workflow.evaluation`은 이미 두 간선(→event,
 * →prediction)을 갖고 있어 이 클래스를 여기 두어도 새 간선이 없다** — 소비만 하던
 * 기존 참조를 그대로 재사용할 뿐이다. [NotificationRequestedPayload]·
 * [NotificationEvidencePayload](payload 타입 자체)는 원시 타입만 나르므로(D-6F7-5,
 * `EventAdapterDependencyTest` 허용 루트 제약) `workflow.event`에 그대로 둔다 — 이
 * 정정은 **sink 클래스에만** 적용된다.
 *
 * 실 DB write는 주입받는 [outbox](어댑터 모듈의 `JdbcOutboxPort`가 실 구현)가 진다
 * — 이 클래스 자신은 SQL을 모른다.
 *
 * **`NotificationRequest`는 소비만 한다** — `internal constructor`라 이 클래스가 새로
 * 짓거나 `copy()`할 권한도 필요도 없다(scope.md 우회 1, 값 획득 축 §5 "경계로 처리").
 *
 * **`aggregateVersion`은 실측 결과 `0`으로 고정한다(D-6F7-4)** — `EventSql`·`JdbcOutboxPort`
 * 를 전수한 결과 이 값은 claim 순서(`EventSql.SELECT_PENDING_FOR_UPDATE_SKIP_LOCKED`가
 * `inserted_at`으로만 정렬한다)·낙관적 잠금 어디에도 쓰이지 않는다 — 그저 왕복되는
 * 서술 필드다(`OutboxEntry.restore`가 원시 값을 그대로 옮길 뿐). 알림 요청에는
 * aggregate의 낙관적 버전 개념이 없어 `0`이 정직하다.
 *
 * **`idempotencyKey`는 noticeId 고정이지만 멱등을 주장하지 않는다(D-6F7-3, D-6F7-6)** —
 * `outbox` 표는 `idempotency_key`에 UNIQUE가 없다(4C-1의 기존 한계, 이 slice가 고치는
 * 계약이 아니다). 같은 notice가 여러 evaluate run에서 반복 `BidNow`를 받으면 매번 새
 * outbox 행이 생긴다 — 이 클래스는 그것을 막지 않는다.
 */
class OutboxNotificationRequestPort(
    private val outbox: OutboxPort,
    private val ids: EventIdFactory,
    private val clock: Clock,
) : NotificationRequestPort {
    override fun request(notification: NotificationRequest): NotificationRequestOutcome {
        val envelope =
            newEnvelope(
                eventId = ids.newId(),
                aggregateId = aggregateIdFor(notification),
                aggregateVersion = AGGREGATE_VERSION_NO_OPTIMISTIC_LOCK,
                occurredAt = clock.now(),
                correlationId = notification.correlationId,
                causationId = null,
                idempotencyKey = idempotencyKeyFor(notification),
                actor = null,
                payload = notification.toOutboxPayload(),
            )
        return try {
            outbox.register(envelope)
            NotificationRequestOutcome.Requested
        } catch (
            @Suppress("SwallowedException") failure: SQLException,
        ) {
            // scope.md 우회 3 — outbox 쓰기 실패를 조용히 삼키지 않는다. `Failed`가 그
            // 축이고, 좁은 예외 타입만 잡는다(선례 `JdbcCompetitionSampleSource`).
            NotificationRequestOutcome.Failed
        }
    }
}

/** claim 순서·낙관적 잠금 어디에도 쓰이지 않는다(D-6F7-4 실측, 클래스 KDoc). */
private val AGGREGATE_VERSION_NO_OPTIMISTIC_LOCK = AggregateVersion(0)

private fun aggregateIdFor(notification: NotificationRequest): AggregateId =
    AggregateId("notice-${notification.noticeId.number.value}-${notification.noticeId.round.value}")

/** D-6F7-3 — 업무 사실(이 notice에 대한 BidNow 요청) 단위로 고정한다. 권고적 키다(우회 2, 클래스 KDoc). */
private fun idempotencyKeyFor(notification: NotificationRequest): IdempotencyKey =
    IdempotencyKey("notification-requested-${notification.noticeId.number.value}-${notification.noticeId.round.value}")

/**
 * `NotificationRequest` → [NotificationRequestedPayload] 투영(D-6F7-2). `bidNowReasons`는
 * 각 `BidNowReason` 요소의 `toString()`이다 — 그 타입을 이름으로 참조하지 않고(클래스 KDoc)
 * 값은 그대로 보존한다.
 */
private fun NotificationRequest.toOutboxPayload(): NotificationRequestedPayload =
    NotificationRequestedPayload(
        noticeId = "${noticeId.number.value}-${noticeId.round.value}",
        bidNowReasons = verdict.reasons.map { it.toString() },
        evidence = evidence.toOutboxPayload(),
    )

private fun PredictionEvidence.toOutboxPayload(): NotificationEvidencePayload =
    when (this) {
        is PredictionEvidence.Diagnosed -> {
            NotificationEvidencePayload.Diagnosed(
                trainingRowCount = diagnostics.trainingRowCount,
                segmentSupport = diagnostics.segmentSupport.name,
                shrinkageWeight = diagnostics.shrinkageWeight.value.toPlainString(),
                excludedObservations = diagnostics.excludedObservations,
                agencySampleCount = diagnostics.agencySampleCount,
                agencySampleBelowThreshold = diagnostics.agencySampleBelowThreshold,
                releaseId = release.releaseId,
                artifactChecksum = release.artifactChecksum,
                featureSchemaVersion = release.featureSchemaVersion,
                codeVersion = release.codeVersion,
                datasetId = release.datasetId,
                releaseKind = release.kind.name,
                excludedSamples = excludedSamples.entries.associate { (reason, count) -> reason.name to count },
            )
        }

        is PredictionEvidence.NotPredicted -> {
            NotificationEvidencePayload.NotPredicted(reason = reason.toString())
        }
    }
