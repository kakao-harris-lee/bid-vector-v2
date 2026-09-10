package bidvector.adapters.event

import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.OperatorId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** [ActorCodec] 왕복 — null·Operator·System 세 갈래. */
class ActorCodecTest {
    @Test
    fun `actor 가 null 이면 kind detail 도 null 이고 왕복도 null 이다`() {
        ActorCodec.kindOf(null) shouldBe null
        ActorCodec.detailOf(null) shouldBe null
        ActorCodec.decode(null, null) shouldBe null
    }

    @Test
    fun `Operator actor 는 kind detail 로 왕복된다`() {
        val actor = Actor.Operator(OperatorId("op-9"))

        val kind = ActorCodec.kindOf(actor)
        val detail = ActorCodec.detailOf(actor)

        ActorCodec.decode(kind, detail) shouldBe actor
    }

    @Test
    fun `System actor 는 kind detail 로 왕복된다`() {
        val actor = Actor.System("maintenance")

        val kind = ActorCodec.kindOf(actor)
        val detail = ActorCodec.detailOf(actor)

        ActorCodec.decode(kind, detail) shouldBe actor
    }

    @Test
    fun `알 수 없는 kind 는 예외다 — fail-closed`() {
        shouldThrow<IllegalStateException> { ActorCodec.decode("UNKNOWN", "x") }
    }
}
