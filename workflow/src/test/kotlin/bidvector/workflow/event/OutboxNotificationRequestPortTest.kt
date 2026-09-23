package bidvector.workflow.event

import bidvector.decision.BidNowReason
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

/**
 * `VerdictLadder.judge`(public, `decision` 모듈 소유)로 진짜 `Verdict.BidNow`를 얻는다 —
 * `internal` 생성자를 직접 못 지으므로(scope.md 우회 1).
 */
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

/** [bidNowVerdict]의 자매 — `ForceBidOverride` 사유를 내는 입력(priority 는 bidNowThreshold 밑, probability·matched 는 강제 임계 이상). */
private fun forceBidOverrideVerdict(): Verdict.BidNow {
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
            priorityScore = UnitScore(BigDecimal("0.2")),
            probabilityScore = UnitScore(BigDecimal("0.95")),
            matchedScore = UnitScore(BigDecimal("0.95")),
            currentActiveBids = 0,
            maxActiveBids = 10,
        )
    return VerdictLadder.judge(input, policy) as Verdict.BidNow
}

/**
 * scope.md 우회 4 — [BidNowReason][bidvector.decision.BidNowReason]의 `toString()`은
 * `NotificationRequestedPayload.bidNowReasons`에 그대로 실려 outbox에 영속된다(sink
 * KDoc). 그 직렬화가 바뀌어도(필드명 변경 등) 아무것도 안 붉으면 옛 outbox 행은 조용히
 * 다른 형식을 이는 채 남는다 — 이 함수가 그 축을 축어로 잠근다. **`else` 없는 소진
 * `when`** 이라 `BidNowReason`에 새 하위 타입이 생기면 이 함수부터 컴파일이 깨진다.
 */
private fun expectedBidNowReasonToString(reason: BidNowReason): String =
    when (reason) {
        is BidNowReason.PriorityAboveBidNowThreshold -> {
            "PriorityAboveBidNowThreshold(priority=0.9, threshold=0.5)"
        }

        is BidNowReason.ForceBidOverride -> {
            "ForceBidOverride(probability=0.95, matched=0.95, probabilityThreshold=0.9, matchedThreshold=0.9)"
        }
    }

/**
 * scope.md 우회 4 — [MlUnavailableReason]의 `toString()`은
 * `NotificationEvidencePayload.NotPredicted.reason`에 그대로 실린다. 전부 `data object`
 * 라 실제로 "새는" 값은 없지만(클래스명=값) 이름이 바뀌면 영속 행의 뜻이 조용히
 * 바뀐다. **`else` 없는 소진 `when`** — 새 사유가 추가되면 이 함수부터 컴파일이 깨진다.
 */
private fun expectedMlUnavailableReasonToString(reason: MlUnavailableReason): String =
    when (reason) {
        MlUnavailableReason.ScoreNotProvided -> "ScoreNotProvided"
        MlUnavailableReason.DeadlineExceeded -> "DeadlineExceeded"
        MlUnavailableReason.CircuitOpen -> "CircuitOpen"
        MlUnavailableReason.RetryBudgetExhausted -> "RetryBudgetExhausted"
        MlUnavailableReason.TransportFailed -> "TransportFailed"
        MlUnavailableReason.ModelNotReady -> "ModelNotReady"
        MlUnavailableReason.ReleaseMismatch -> "ReleaseMismatch"
        MlUnavailableReason.ContractViolation -> "ContractViolation"
        MlUnavailableReason.UnsupportedSchema -> "UnsupportedSchema"
        MlUnavailableReason.UnsupportedRelease -> "UnsupportedRelease"
        MlUnavailableReason.InvalidRequest -> "InvalidRequest"
    }

private val ALL_ML_UNAVAILABLE_REASONS =
    listOf(
        MlUnavailableReason.ScoreNotProvided,
        MlUnavailableReason.DeadlineExceeded,
        MlUnavailableReason.CircuitOpen,
        MlUnavailableReason.RetryBudgetExhausted,
        MlUnavailableReason.TransportFailed,
        MlUnavailableReason.ModelNotReady,
        MlUnavailableReason.ReleaseMismatch,
        MlUnavailableReason.ContractViolation,
        MlUnavailableReason.UnsupportedSchema,
        MlUnavailableReason.UnsupportedRelease,
        MlUnavailableReason.InvalidRequest,
    )

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
        val sink =
            OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val notification =
            NotificationRequest(noticeId(), CorrelationId("corr-1"), bidNowVerdict(), diagnosedEvidence())

        val outcome = sink.request(notification)

        outcome shouldBe NotificationRequestOutcome.Requested
        outbox.registered shouldHaveSize 1
    }

    @Test
    fun `outbox 쓰기가 SQLException 으로 실패하면 Failed 를 낸다 — 조용히 삼키지 않는다`() {
        val outbox = NotificationInMemoryOutboxPort(failRegister = true)
        val sink =
            OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val notification =
            NotificationRequest(noticeId(), CorrelationId("corr-1"), bidNowVerdict(), diagnosedEvidence())

        val outcome = sink.request(notification)

        outcome shouldBe NotificationRequestOutcome.Failed
        outbox.registered shouldHaveSize 0
    }

    @Test
    fun `봉투는 요청의 correlationId 를 그대로 나른다 — trace 유지`() {
        val outbox = NotificationInMemoryOutboxPort()
        val sink =
            OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val notification =
            NotificationRequest(noticeId(), CorrelationId("corr-trace"), bidNowVerdict(), diagnosedEvidence())

        sink.request(notification)

        outbox.registered.single().correlationId shouldBe CorrelationId("corr-trace")
        outbox.registered.single().occurredAt shouldBe NOW
    }

    @Test
    fun `aggregateVersion 은 0 이다 — 실측 결과 낙관적 잠금·순서 어디에도 안 쓰인다(D-6F7-4)`() {
        val outbox = NotificationInMemoryOutboxPort()
        val sink =
            OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val notification =
            NotificationRequest(noticeId(), CorrelationId("corr-1"), bidNowVerdict(), diagnosedEvidence())

        sink.request(notification)

        outbox.registered.single().aggregateVersion shouldBe AggregateVersion(0)
    }

    @Test
    fun `idempotencyKey 는 noticeId 로 고정된다 — 권고적, 멱등을 주장하지 않는다(D-6F7-3)`() {
        val outbox = NotificationInMemoryOutboxPort()
        val sink =
            OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
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
        val sink =
            OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val verdict = bidNowVerdict()
        val notification = NotificationRequest(noticeId("N42"), CorrelationId("corr-1"), verdict, diagnosedEvidence())

        sink.request(notification)

        val payload = outbox.registered.single().payload as NotificationRequestedPayload
        payload.noticeId shouldBe "N42-000"
        // verdict.reasons.map{it.toString()} 가 아니라 독립 잠금 함수와 대조한다 —
        // 그렇지 않으면 이 assertion 은 production 코드의 같은 호출을 그대로
        // 복사할 뿐이라 toString 형식이 바뀌어도 항상 통과한다(우회 4 재발 형태).
        payload.bidNowReasons shouldBe verdict.reasons.map(::expectedBidNowReasonToString)
    }

    @Test
    fun `payload 는 Diagnosed evidence 의 진단·release·표본제외를 전부 나른다(D-6F7-2)`() {
        val outbox = NotificationInMemoryOutboxPort()
        val sink =
            OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
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
        val sink =
            OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val evidence = PredictionEvidence.NotPredicted(MlUnavailableReason.CircuitOpen)
        val notification = NotificationRequest(noticeId(), CorrelationId("corr-1"), bidNowVerdict(), evidence)

        sink.request(notification)

        val payload = outbox.registered.single().payload as NotificationRequestedPayload
        val payloadEvidence = payload.evidence as NotificationEvidencePayload.NotPredicted
        payloadEvidence.reason shouldBe expectedMlUnavailableReasonToString(MlUnavailableReason.CircuitOpen)
    }

    @Test
    fun `payload 는 ForceBidOverride 사유도 나른다 — BidNowReason 두 case 전수(D-6F7-2)`() {
        val outbox = NotificationInMemoryOutboxPort()
        val sink =
            OutboxNotificationRequestPort(outbox, NotificationSequentialEventIdFactory(), NotificationFixedClock(NOW))
        val verdict = forceBidOverrideVerdict()
        val notification = NotificationRequest(noticeId("N7"), CorrelationId("corr-1"), verdict, diagnosedEvidence())

        sink.request(notification)

        val payload = outbox.registered.single().payload as NotificationRequestedPayload
        payload.bidNowReasons shouldBe verdict.reasons.map(::expectedBidNowReasonToString)
    }

    /**
     * scope.md 우회 4 — `BidNowReason` 두 case 의 `toString()` 직렬화를 축어로 잠근다.
     * `expectedBidNowReasonToString`의 소진 `when`이 `BidNowReason`에 새 하위 타입이
     * 생기는 순간 컴파일을 깬다(이 test 파일 자체가 컴파일 안 됨) — 리스트를 갱신하지
     * 않아도 걸린다.
     */
    @Test
    fun `BidNowReason 두 case 의 직렬화가 축어로 고정된다 — 우회 4, 소진 when`() {
        bidNowVerdict().reasons.single().toString() shouldBe "PriorityAboveBidNowThreshold(priority=0.9, threshold=0.5)"
        forceBidOverrideVerdict().reasons.single().toString() shouldBe
            "ForceBidOverride(probability=0.95, matched=0.95, probabilityThreshold=0.9, matchedThreshold=0.9)"

        // 잠금 함수 자신도 같은 값을 낸다는 것을 재확인 — 함수와 literal 이 갈라지면
        // 다음에 sink test 가 잠금 함수만 보고 안심하는 것을 막는다.
        bidNowVerdict().reasons.single().let { it.toString() shouldBe expectedBidNowReasonToString(it) }
        forceBidOverrideVerdict().reasons.single().let { it.toString() shouldBe expectedBidNowReasonToString(it) }
    }

    /**
     * scope.md 우회 4 — `MlUnavailableReason` 열한 case 전수. `expectedMlUnavailableReasonToString`
     * 의 소진 `when`이 새 사유가 추가되는 순간 컴파일을 깬다.
     */
    @Test
    fun `MlUnavailableReason 전 case 의 직렬화가 축어로 고정된다 — 우회 4, 소진 when`() {
        ALL_ML_UNAVAILABLE_REASONS.size shouldBe 11
        ALL_ML_UNAVAILABLE_REASONS.forEach { reason ->
            reason.toString() shouldBe expectedMlUnavailableReasonToString(reason)
        }
    }

    /**
     * scope.md 우회 4(민감값) — `data class`의 합성 `toString()`이 조용히 새 필드를
     * 흘리는 축(6A-1 교훈)을 field 집합 자체를 잠가 막는다. 지금 필드는 전부
     * 비민감(코드·수치·식별자, checklist.md "toString·민감값 실측" 절) — 이 test는
     * 그 사실이 아니라 **field 집합이 바뀌면 조용히 지나가지 않는다**를 보장한다.
     * 새 필드가 추가되면 이 test가 깨져 「그 필드가 민감한가」를 다시 묻게 만든다.
     */
    @Test
    fun `payload 필드 집합은 고정돼 있다 — 새 필드가 조용히 안 늘어난다(우회 4)`() {
        val payloadFields =
            NotificationRequestedPayload::class.java.declaredFields
                .map { it.name }
                .toSet()
        val diagnosedFields =
            NotificationEvidencePayload.Diagnosed::class.java.declaredFields
                .map { it.name }
                .toSet()
        val notPredictedFields =
            NotificationEvidencePayload.NotPredicted::class.java.declaredFields
                .map { it.name }
                .toSet()

        payloadFields shouldBe setOf("noticeId", "bidNowReasons", "evidence")
        diagnosedFields shouldBe
            setOf(
                "trainingRowCount",
                "segmentSupport",
                "shrinkageWeight",
                "excludedObservations",
                "agencySampleCount",
                "agencySampleBelowThreshold",
                "releaseId",
                "artifactChecksum",
                "featureSchemaVersion",
                "codeVersion",
                "datasetId",
                "releaseKind",
                "excludedSamples",
            )
        notPredictedFields shouldBe setOf("reason")
    }
}
