package bidvector.adapters.event

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyRevision
import bidvector.workflow.event.NotificationEvidencePayload
import bidvector.workflow.event.NotificationRequestedPayload
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 설계 검토 (1) 「payload 직렬화의 타입 판별」 — 등록·복원 둘 다 알 수 없는 타입은
 * fail-closed 로 던진다. `StrategyEvent.StrategyUpdated`의 왕복(`EffectiveFrom.Initial`·
 * `EffectiveFrom.On` 둘 다)을 확인한다.
 */
class OutboxPayloadCodecTest {
    @Test
    fun `StrategyUpdated 는 effectiveFrom Initial 로 왕복된다`() {
        val event = StrategyEvent.StrategyUpdated(StrategyRevision(7), PolicyVersion(EffectiveFrom.Initial, "test"))

        val payloadType = OutboxPayloadCodec.payloadTypeOf(event)
        val encoded = OutboxPayloadCodec.encode(event)
        val decoded = OutboxPayloadCodec.decode(payloadType, encoded)

        decoded shouldBe event
    }

    @Test
    fun `StrategyUpdated 는 effectiveFrom On 날짜로도 왕복된다`() {
        val effectiveFrom = EffectiveFrom.On(LocalDate.of(2026, 9, 10))
        val event = StrategyEvent.StrategyUpdated(StrategyRevision(3), PolicyVersion(effectiveFrom, "policy-v3"))

        val encoded = OutboxPayloadCodec.encode(event)
        val decoded = OutboxPayloadCodec.decode(OutboxPayloadCodec.payloadTypeOf(event), encoded)

        decoded shouldBe event
    }

    @Test
    fun `source 에 구분자 문자가 있어도 왕복된다 — 이스케이프 실측`() {
        val event =
            StrategyEvent.StrategyUpdated(StrategyRevision(1), PolicyVersion(EffectiveFrom.Initial, "a|b\\c|d"))

        val encoded = OutboxPayloadCodec.encode(event)
        val decoded = OutboxPayloadCodec.decode(OutboxPayloadCodec.payloadTypeOf(event), encoded)

        decoded shouldBe event
    }

    @Test
    fun `알 수 없는 payload 타입은 등록 시 예외다 — fail-closed`() {
        shouldThrow<IllegalStateException> { OutboxPayloadCodec.payloadTypeOf("not-a-strategy-event") }
        shouldThrow<IllegalStateException> { OutboxPayloadCodec.encode(42) }
    }

    @Test
    fun `알 수 없는 payload_type 은 복원 시 예외다 — fail-closed`() {
        shouldThrow<IllegalStateException> { OutboxPayloadCodec.decode("UnknownType", "whatever") }
    }

    @Test
    fun `NotificationRequested 는 Diagnosed evidence 와 함께 왕복된다(D-6F7-2, 우회 6·7)`() {
        val payload =
            NotificationRequestedPayload(
                noticeId = "N1-000",
                bidNowReasons = listOf("PriorityAboveBidNowThreshold(priority=0.90, threshold=0.50)"),
                evidence =
                    NotificationEvidencePayload.Diagnosed(
                        trainingRowCount = 120,
                        segmentSupport = "Direct",
                        shrinkageWeight = "0.25",
                        excludedObservations = 3,
                        agencySampleCount = 8,
                        agencySampleBelowThreshold = true,
                        releaseId = "rel-1",
                        artifactChecksum = "sha256:abc",
                        featureSchemaVersion = "v1",
                        codeVersion = "code-1",
                        datasetId = "ds-1",
                        releaseKind = "Artifact",
                        excludedSamples = mapOf("RANK_ONE_RATE_MISSING" to 2, "BASE_AMOUNT_MISSING" to 1),
                    ),
            )

        val payloadType = OutboxPayloadCodec.payloadTypeOf(payload)
        val encoded = OutboxPayloadCodec.encode(payload)
        val decoded = OutboxPayloadCodec.decode(payloadType, encoded)

        decoded shouldBe payload
    }

    @Test
    fun `NotificationRequested 는 NotPredicted evidence 와 함께 왕복된다(D-6F7-2)`() {
        val payload =
            NotificationRequestedPayload(
                noticeId = "N2-001",
                bidNowReasons = listOf("ForceBidOverride(probability=0.95, matched=0.95)"),
                evidence = NotificationEvidencePayload.NotPredicted(reason = "CircuitOpen"),
            )

        val encoded = OutboxPayloadCodec.encode(payload)
        val decoded = OutboxPayloadCodec.decode(OutboxPayloadCodec.payloadTypeOf(payload), encoded)

        decoded shouldBe payload
    }

    @Test
    fun `NotificationRequested 는 excludedSamples 가 비어 있어도 왕복된다`() {
        val payload =
            NotificationRequestedPayload(
                noticeId = "N3-000",
                bidNowReasons = emptyList(),
                evidence =
                    NotificationEvidencePayload.Diagnosed(
                        trainingRowCount = 0,
                        segmentSupport = "Global",
                        shrinkageWeight = "0",
                        excludedObservations = 0,
                        agencySampleCount = 0,
                        agencySampleBelowThreshold = false,
                        releaseId = "rel-2",
                        artifactChecksum = "sha256:def",
                        featureSchemaVersion = "v2",
                        codeVersion = "code-2",
                        datasetId = "ds-2",
                        releaseKind = "Derived",
                        excludedSamples = emptyMap(),
                    ),
            )

        val encoded = OutboxPayloadCodec.encode(payload)
        val decoded = OutboxPayloadCodec.decode(OutboxPayloadCodec.payloadTypeOf(payload), encoded)

        decoded shouldBe payload
    }

    @Test
    fun `NotificationRequested 는 구분자 문자가 값에 있어도 왕복된다 — 이스케이프 실측`() {
        val payload =
            NotificationRequestedPayload(
                noticeId = "N4|a,b;c=d",
                bidNowReasons = listOf("reason|with,special;chars=1", "reason\\with\\backslash"),
                evidence =
                    NotificationEvidencePayload.Diagnosed(
                        trainingRowCount = 1,
                        segmentSupport = "ParentCategory",
                        shrinkageWeight = "0.5",
                        excludedObservations = 0,
                        agencySampleCount = 1,
                        agencySampleBelowThreshold = false,
                        releaseId = "rel|3",
                        artifactChecksum = "sha256:xyz",
                        featureSchemaVersion = "v3",
                        codeVersion = "code-3",
                        datasetId = "ds-3",
                        releaseKind = "Artifact",
                        excludedSamples = mapOf("RANK_ONE_RATE_MISSING" to 1),
                    ),
            )

        val encoded = OutboxPayloadCodec.encode(payload)
        val decoded = OutboxPayloadCodec.decode(OutboxPayloadCodec.payloadTypeOf(payload), encoded)

        decoded shouldBe payload
    }

    @Test
    fun `StrategyUpdated 왕복은 NotificationRequested 신설 뒤에도 무변화다 — 회귀 없음`() {
        val event = StrategyEvent.StrategyUpdated(StrategyRevision(9), PolicyVersion(EffectiveFrom.Initial, "test"))

        val encoded = OutboxPayloadCodec.encode(event)
        val decoded = OutboxPayloadCodec.decode(OutboxPayloadCodec.payloadTypeOf(event), encoded)

        decoded shouldBe event
    }
}
