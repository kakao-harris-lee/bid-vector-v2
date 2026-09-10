package bidvector.decision.priority.derive

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration

/** [Ladder]·[resolveBand] 불변식(설계 검토 (1), 위협 모델 우회 (2)(4)). */
class BandTest {
    private val ascending =
        Ladder.Ascending(
            bands =
                listOf(
                    Band(Duration.ofHours(6), BigDecimal("1.0")),
                    Band(Duration.ofHours(24), BigDecimal("0.8")),
                    Band(Duration.ofHours(72), BigDecimal("0.55")),
                ),
            beyondBandsScore = BigDecimal("0.25"),
        )

    private val descending =
        Ladder.Descending(
            bands =
                listOf(
                    Band(500_000_000L, BigDecimal("0.92")),
                    Band(200_000_000L, BigDecimal("0.78")),
                    Band(100_000_000L, BigDecimal("0.62")),
                ),
            beyondBandsScore = BigDecimal("0.38"),
        )

    // ---- 오름 사다리 경계 양쪽(우회 (2)) ----

    @Test
    fun `오름 사다리 — 경계값 6h 는 그 rung 값`() {
        resolveBand(Duration.ofHours(6), ascending) shouldBe BigDecimal("1.0")
    }

    @Test
    fun `오름 사다리 — 6h+1ns 는 다음 rung 값`() {
        resolveBand(Duration.ofHours(6).plusNanos(1), ascending) shouldBe BigDecimal("0.8")
    }

    @Test
    fun `오름 사다리 — 경계값 24h 는 그 rung 값`() {
        resolveBand(Duration.ofHours(24), ascending) shouldBe BigDecimal("0.8")
    }

    @Test
    fun `오름 사다리 — 경계값 72h 는 그 rung 값`() {
        resolveBand(Duration.ofHours(72), ascending) shouldBe BigDecimal("0.55")
    }

    @Test
    fun `오름 사다리 — 72h 초과는 beyondBandsScore`() {
        resolveBand(Duration.ofHours(72).plusNanos(1), ascending) shouldBe BigDecimal("0.25")
    }

    @Test
    fun `오름 사다리 — 0h 는 첫 rung 값`() {
        resolveBand(Duration.ZERO, ascending) shouldBe BigDecimal("1.0")
    }

    // ---- 내림 사다리 경계 양쪽(우회 (2)) ----

    @Test
    fun `내림 사다리 — 경계값 5억은 그 rung 값`() {
        resolveBand(500_000_000L, descending) shouldBe BigDecimal("0.92")
    }

    @Test
    fun `내림 사다리 — 5억-1원은 다음 rung 값`() {
        resolveBand(499_999_999L, descending) shouldBe BigDecimal("0.78")
    }

    @Test
    fun `내림 사다리 — 경계값 1억 미만은 beyondBandsScore`() {
        resolveBand(99_999_999L, descending) shouldBe BigDecimal("0.38")
    }

    @Test
    fun `내림 사다리 — 0은 beyondBandsScore`() {
        resolveBand(0L, descending) shouldBe BigDecimal("0.38")
    }

    // ---- 생성 불변식(우회 (4)) ----

    @Test
    fun `오름 사다리 — bound 역순이면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            Ladder.Ascending(
                bands =
                    listOf(
                        Band(Duration.ofHours(24), BigDecimal("0.8")),
                        Band(Duration.ofHours(6), BigDecimal("1.0")),
                    ),
                beyondBandsScore = BigDecimal("0.25"),
            )
        }
    }

    @Test
    fun `오름 사다리 — bound 중복이면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            Ladder.Ascending(
                bands =
                    listOf(
                        Band(Duration.ofHours(6), BigDecimal("1.0")),
                        Band(Duration.ofHours(6), BigDecimal("0.8")),
                    ),
                beyondBandsScore = BigDecimal("0.25"),
            )
        }
    }

    @Test
    fun `내림 사다리 — bound 오름차순(역순)이면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            Ladder.Descending(
                bands = listOf(Band(100_000_000L, BigDecimal("0.62")), Band(500_000_000L, BigDecimal("0.92"))),
                beyondBandsScore = BigDecimal("0.38"),
            )
        }
    }

    @Test
    fun `빈 bands 는 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            Ladder.Ascending<Duration>(bands = emptyList(), beyondBandsScore = BigDecimal("0.25"))
        }
    }
}
