package bidvector.workflow.event

import bidvector.workflow.evaluation.NotificationRequest
import bidvector.workflow.evaluation.NotificationRequestOutcome
import bidvector.workflow.evaluation.NotificationRequestPort
import bidvector.workflow.evaluation.PredictionEvidence
import bidvector.workflow.strategy.Clock
import java.sql.SQLException

/**
 * `NotificationRequestPort`의 production 구현(D-6F7-1, scope.md) — `NotificationRequest`를
 * 봉투에 실어 [outbox]에 등록한다. **`workflow` 모듈에 있는 이유는 저장 위치 선호가
 * 아니라 폐쇄다** — 봉투 생성 경로([newEnvelope])가 `workflow` 모듈에 `internal`이고
 * `adapters`는 별도 Gradle 모듈이라 그 경로를 부를 수 없다(`EventInternalClosureCompileTest`가
 * 상시 확인). 실 DB write는 주입받는 [outbox](어댑터 모듈의 `JdbcOutboxPort`가 실 구현)가
 * 진다 — 이 클래스 자신은 SQL을 모른다.
 *
 * **`NotificationRequest`는 소비만 한다** — `internal constructor`라 이 클래스가 새로
 * 짓거나 `copy()`할 권한도 필요도 없다(scope.md 우회 1, 값 획득 축 §5 "경계로 처리").
 *
 * **`aggregateVersion`은 실측 결과 `0`으로 고정한다(D-6F7-4)** — `EventSql`·`JdbcOutboxPort`
 * 를 전수한 결과 이 값은 claim 순서([EventSql.SELECT_PENDING_FOR_UPDATE_SKIP_LOCKED]가
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
        } catch (@Suppress("SwallowedException") failure: SQLException) {
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
 * 각 `BidNowReason` 요소의 `toString()`이다 — 그 타입을 이름으로 참조하지 않고(파일 KDoc)
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

        is PredictionEvidence.NotPredicted -> NotificationEvidencePayload.NotPredicted(reason = reason.toString())
    }
