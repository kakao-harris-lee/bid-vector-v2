package bidvector.adapters.event

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyRevision
import bidvector.workflow.event.NotificationEvidencePayload
import bidvector.workflow.event.NotificationRequestedPayload
import bidvector.workflow.event.OutboxConsumerKind
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
                ladderPolicyVersion = PolicyVersion(EffectiveFrom.Initial, "m4-4b2-legacy-behavior-2026-09-09"),
                strategyRevision = StrategyRevision(7),
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
                ladderPolicyVersion =
                    PolicyVersion(EffectiveFrom.On(LocalDate.parse("2026-02-01")), "policy-values.md"),
                strategyRevision = StrategyRevision(0),
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
                ladderPolicyVersion = PolicyVersion(EffectiveFrom.Initial, ""),
                strategyRevision = StrategyRevision(1),
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
                ladderPolicyVersion = PolicyVersion(EffectiveFrom.Initial, "src|a,b;c=d\\e"),
                strategyRevision = StrategyRevision(12),
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
    fun `excludedSamples 키에 구분자 문자가 있어도 왕복된다 — D-6F7-12 이스케이프 실측`() {
        val payload =
            NotificationRequestedPayload(
                noticeId = "N5-000",
                bidNowReasons = emptyList(),
                ladderPolicyVersion = PolicyVersion(EffectiveFrom.Initial, "key-escape"),
                strategyRevision = StrategyRevision(3),
                evidence =
                    NotificationEvidencePayload.Diagnosed(
                        trainingRowCount = 5,
                        segmentSupport = "Direct",
                        shrinkageWeight = "0.1",
                        excludedObservations = 1,
                        agencySampleCount = 2,
                        agencySampleBelowThreshold = false,
                        releaseId = "rel-5",
                        artifactChecksum = "sha256:key-escape",
                        featureSchemaVersion = "v5",
                        codeVersion = "code-5",
                        datasetId = "ds-5",
                        releaseKind = "Artifact",
                        // 키가 MAP_KV_SEPARATOR('=')와 MAP_ENTRY_SEPARATOR(';') 둘 다 담는다 —
                        // decode 의 `check(kv.size == 2)`가 '='만 이스케이프하지 않으면 터진다.
                        excludedSamples = mapOf("A=B;C" to 4, "PLAIN" to 1),
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

    /**
     * D-6F10-16 — **새 형식의 wire 골든.** 이 파일의 다른 test 는 전부 왕복이라 encode·decode
     * 가 **함께** 바뀌면 통과한다(칸 순서를 바꾸거나 구분자를 바꿔도 초록). 축어 문자열이
     * 그 대칭 변이를 막는다 — 저장된 바이트가 바뀌면 이 단언이 먼저 붉어진다.
     *
     * 읽는 법: `noticeId | reasons | effectiveFrom | source | revision | evidenceKind | …`.
     * `effectiveFrom` 이 빈 칸인 것이 `EffectiveFrom.Initial` 이고, `NotPredicted` 는
     * evidenceKind 뒤 열세 칸을 비우고 마지막 칸에 사유를 싣는다.
     */
    @Test
    fun `NotificationRequested 의 wire 형식은 축어로 고정된다 — 새 형식 골든`() {
        val payload =
            NotificationRequestedPayload(
                noticeId = "G1-000",
                bidNowReasons = listOf("ForceBidOverride"),
                ladderPolicyVersion = PolicyVersion(EffectiveFrom.Initial, "golden-source"),
                strategyRevision = StrategyRevision(4),
                evidence = NotificationEvidencePayload.NotPredicted(reason = "CircuitOpen"),
            )

        val encoded = OutboxPayloadCodec.encode(payload)

        encoded shouldBe
            "G1-000|ForceBidOverride||golden-source|4|NOT_PREDICTED||||||||||||||CircuitOpen"
        OutboxPayloadCodec.decode(OutboxPayloadCodec.NOTIFICATION_REQUESTED_TYPE, encoded) shouldBe payload
    }

    /**
     * D-6F10-13 알려진 제한의 잠금 — `payload_type` 으로 가는 길이 둘(payload 클래스 기준 ·
     * kind 기준)이고 타입은 그 둘이 어긋나는 것을 막지 못한다. kind 마다 표본 payload 를
     * 짝지어 **두 길의 결과가 같다**는 등식을 잰다. 짝 표가 소진 `when` 이라 새 kind 가
     * 생기면 컴파일이 깨져 표본을 고르게 한다.
     */
    @Test
    fun `kind 기준 payload_type 과 payload 클래스 기준 payload_type 은 같다 — 집합 등식`() {
        OutboxConsumerKind.entries.forEach { kind ->
            OutboxPayloadCodec.payloadTypeOf(kind) shouldBe OutboxPayloadCodec.payloadTypeOf(sampleFor(kind))
        }
    }
}

/** [OutboxConsumerKind] 마다의 표본 payload — 소진 `when` 이라 kind 가 늘면 여기서 컴파일이 깨진다. */
private fun sampleFor(kind: OutboxConsumerKind): Any =
    when (kind) {
        OutboxConsumerKind.StrategyUpdated -> {
            StrategyEvent.StrategyUpdated(StrategyRevision(1), PolicyVersion(EffectiveFrom.Initial, "sample"))
        }

        OutboxConsumerKind.NotificationRequested -> {
            NotificationRequestedPayload(
                noticeId = "S1-000",
                bidNowReasons = emptyList(),
                ladderPolicyVersion = PolicyVersion(EffectiveFrom.Initial, "sample"),
                strategyRevision = StrategyRevision(1),
                evidence = NotificationEvidencePayload.NotPredicted(reason = "CircuitOpen"),
            )
        }
    }
