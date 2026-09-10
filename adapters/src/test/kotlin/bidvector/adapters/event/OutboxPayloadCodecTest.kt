package bidvector.adapters.event

import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyRevision
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
}
