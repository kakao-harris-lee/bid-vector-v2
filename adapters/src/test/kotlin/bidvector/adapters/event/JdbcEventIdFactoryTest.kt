package bidvector.adapters.event

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/** scope.md ⑥ — 값이 비어 있지 않고 호출마다 새로 난다. */
class JdbcEventIdFactoryTest {
    @Test
    fun `newId 는 빈 값이 아니고 호출마다 서로 다르다`() {
        val factory = JdbcEventIdFactory()

        val first = factory.newId()
        val second = factory.newId()

        first.value.isBlank() shouldBe false
        first shouldNotBe second
    }
}
