package bidvector.workflow.event

import bidvector.decision.LadderInput
import bidvector.decision.MlUnavailableReason
import bidvector.decision.UnitScore
import bidvector.decision.Verdict
import bidvector.decision.VerdictLadder
import bidvector.decision.VerdictLadderPolicyData
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.workflow.evaluation.NotificationRequest
import bidvector.workflow.evaluation.NotificationRequestOutcome
import bidvector.workflow.evaluation.PredictionEvidence
import bidvector.workflow.evaluation.SampleExclusionReason
import bidvector.workflow.prediction.ModelReleaseRef
import bidvector.workflow.prediction.PredictionDiagnostics
import bidvector.workflow.prediction.ReleaseKind
import bidvector.workflow.prediction.SegmentSupport
import bidvector.workflow.prediction.Weight
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.SQLException
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-09-23T00:00:00Z")

private fun noticeId(number: String = "N1") = NoticeId(NoticeNumber(number), NoticeRound("000"))

/** `VerdictLadder.judge`(public, `decision` 모듈 소유)로 진짜 `Verdict.BidNow`를 얻는다 — `internal` 생성자를 직접 못 지으므로(scope.md 우회 1). */
private fun bidNowVerdict(): Verdict.BidNow {
    val policy =
        Resolution.Resolved(
            VerdictLadderPolicyData(
                capacityHoldPriorityThreshold = BigDecimal("0.1"),
                bidNowThreshold = BigDecimal("0.5"),
                reviewThreshold = BigDecimal("0.3"),
                forceBidProbabilityThreshold = BigDecimal("0.9"),
                forceBidMatchedThreshold = BigDecimal("0.9"),
            ),
            PolicyVersion(EffectiveFrom.Initial, "test"),
        )
    val input =
        LadderInput(
            priorityScore = UnitScore(BigDecimal("0.9")),
            probabilityScore = null,
            matchedScore = null,
            currentActiveBids = 0,
            maxActiveBids = 10,
        )
    return VerdictLadder.judge(input, policy) as Verdict.BidNow
}

private fun diagnosedEvidence(): PredictionEvidence.Diagnosed =
    PredictionEvidence.Diagnosed(
        diagnostics =
            PredictionDiagnostics(
                trainingRowCount = 120,
                segmentSupport = SegmentSupport.Direct,
                shrinkageWeight = Weight(BigDecimal("0.25")),
                excludedObservations = 3,
                agencySampleCount = 8,
                agencySampleBelowThreshold = true,
            ),
        release =
            ModelReleaseRef(
                releaseId = "rel-1",
                artifactChecksum = "sha256:abc",
                featureSchemaVersion = "v1",
                codeVersion = "code-1",
                datasetId = "ds-1",
                kind = ReleaseKind.Artifact,
            ),
        excludedSamples = mapOf(SampleExclusionReason.RANK_ONE_RATE_MISSING to 2),
    )

private class NotificationFixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}

private class NotificationSequentialEventIdFactory : EventIdFactory {
    private var counter = 0

    override fun newId(): EventId {
        counter += 1
        return EventId("evt-$counter")
    }
}

/** register 를 강제 실패시킬 수 있는 fake — scope.md 우회 3(쓰기 실패 삼킴)을 잰다. */
private class NotificationInMemoryOutboxPort(
    private val failRegister: Boolean = false,
) : OutboxPort {
    val registered = mutableListOf<EventEnvelope<*>>()

    override fun register(envelope: EventEnvelope<*>): OutboxEntryId {
        if (failRegister) throw SQLException("simulated write failure")
        registered += envelope
        return OutboxEntryId("outbox-${registered.size}")
    }

    override fun claim(limit: Int): List<ClaimedOutboxRow<*>> = emptyList()

    override fun markDelivered(transition: OutboxTransition.ToDelivered) = Unit

    override fun markFailed(transition: OutboxTransition.ToFailed) = Unit

    override fun markIsolated(transition: OutboxTransition.ToIsolated) = Unit
}

class OutboxNotificationRequestPortTest {
    @Test
    fun `outbox 등록에 성공하면 Requested 를 낸다`() {
        val outbox = NotificationInMemoryOutboxPort()
        val sink = OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val notification =
            NotificationRequest(noticeId(), CorrelationId("corr-1"), bidNowVerdict(), diagnosedEvidence())

        val outcome = sink.request(notification)

        outcome shouldBe NotificationRequestOutcome.Requested
        outbox.registered shouldHaveSize 1
    }

    @Test
    fun `outbox 쓰기가 SQLException 으로 실패하면 Failed 를 낸다 — 조용히 삼키지 않는다`() {
        val outbox = NotificationInMemoryOutboxPort(failRegister = true)
        val sink = OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val notification =
            NotificationRequest(noticeId(), CorrelationId("corr-1"), bidNowVerdict(), diagnosedEvidence())

        val outcome = sink.request(notification)

        outcome shouldBe NotificationRequestOutcome.Failed
        outbox.registered shouldHaveSize 0
    }

    @Test
    fun `봉투는 요청의 correlationId 를 그대로 나른다 — trace 유지`() {
        val outbox = NotificationInMemoryOutboxPort()
        val sink = OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val notification =
            NotificationRequest(noticeId(), CorrelationId("corr-trace"), bidNowVerdict(), diagnosedEvidence())

        sink.request(notification)

        outbox.registered.single().correlationId shouldBe CorrelationId("corr-trace")
        outbox.registered.single().occurredAt shouldBe NOW
    }

    @Test
    fun `aggregateVersion 은 0 이다 — 실측 결과 낙관적 잠금·순서 어디에도 안 쓰인다(D-6F7-4)`() {
        val outbox = NotificationInMemoryOutboxPort()
        val sink = OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val notification =
            NotificationRequest(noticeId(), CorrelationId("corr-1"), bidNowVerdict(), diagnosedEvidence())

        sink.request(notification)

        outbox.registered.single().aggregateVersion shouldBe AggregateVersion(0)
    }

    @Test
    fun `idempotencyKey 는 noticeId 로 고정된다 — 권고적, 멱등을 주장하지 않는다(D-6F7-3)`() {
        val outbox = NotificationInMemoryOutboxPort()
        val sink = OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val first = NotificationRequest(noticeId("N9"), CorrelationId("corr-a"), bidNowVerdict(), diagnosedEvidence())
        val second = NotificationRequest(noticeId("N9"), CorrelationId("corr-b"), bidNowVerdict(), diagnosedEvidence())

        sink.request(first)
        sink.request(second)

        // 두 호출 모두 outbox 에 들었다 — 이 slice 는 이중 요청을 막지 않는다(D-6F7-6).
        outbox.registered shouldHaveSize 2
        val keys = outbox.registered.map { it.idempotencyKey }.toSet()
        keys shouldHaveSize 1
    }

    @Test
    fun `payload 는 noticeId 와 bidNowReason 스냅샷을 나른다 — 복원 가능(D-6F7-2, 우회 6)`() {
        val outbox = NotificationInMemoryOutboxPort()
        val sink = OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val verdict = bidNowVerdict()
        val notification = NotificationRequest(noticeId("N42"), CorrelationId("corr-1"), verdict, diagnosedEvidence())

        sink.request(notification)

        val payload = outbox.registered.single().payload as NotificationRequestedPayload
        payload.noticeId shouldBe "N42-000"
        payload.bidNowReasons shouldBe verdict.reasons.map { it.toString() }
    }

    @Test
    fun `payload 는 Diagnosed evidence 의 진단·release·표본제외를 전부 나른다(D-6F7-2)`() {
        val outbox = NotificationInMemoryOutboxPort()
        val sink = OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val notification =
            NotificationRequest(noticeId(), CorrelationId("corr-1"), bidNowVerdict(), diagnosedEvidence())

        sink.request(notification)

        val payload = outbox.registered.single().payload as NotificationRequestedPayload
        val evidence = payload.evidence as NotificationEvidencePayload.Diagnosed
        evidence.trainingRowCount shouldBe 120
        evidence.segmentSupport shouldBe "Direct"
        evidence.shrinkageWeight shouldBe "0.25"
        evidence.excludedObservations shouldBe 3
        evidence.agencySampleCount shouldBe 8
        evidence.agencySampleBelowThreshold shouldBe true
        evidence.releaseId shouldBe "rel-1"
        evidence.artifactChecksum shouldBe "sha256:abc"
        evidence.featureSchemaVersion shouldBe "v1"
        evidence.codeVersion shouldBe "code-1"
        evidence.datasetId shouldBe "ds-1"
        evidence.releaseKind shouldBe "Artifact"
        evidence.excludedSamples shouldBe mapOf("RANK_ONE_RATE_MISSING" to 2)
    }

    @Test
    fun `payload 는 NotPredicted evidence 의 사유를 나른다(D-6F7-2)`() {
        val outbox = NotificationInMemoryOutboxPort()
        val sink = OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val evidence = PredictionEvidence.NotPredicted(MlUnavailableReason.CircuitOpen)
        val notification = NotificationRequest(noticeId(), CorrelationId("corr-1"), bidNowVerdict(), evidence)

        sink.request(notification)

        val payload = outbox.registered.single().payload as NotificationRequestedPayload
        val payloadEvidence = payload.evidence as NotificationEvidencePayload.NotPredicted
        payloadEvidence.reason shouldBe "CircuitOpen"
    }
}
