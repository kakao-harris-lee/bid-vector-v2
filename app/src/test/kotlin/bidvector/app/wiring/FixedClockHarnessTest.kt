package bidvector.app.wiring

import bidvector.procurement.COLLECTION_BUDGET_ZONE
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * M6/6G-2c D-6G2c-19 (c) (cr r5-t L-4) — **고정 시계 harness 가 걷기 이름을 충돌시키지 않는다.**
 *
 * 걷기의 이름은 시계 값을 마이크로초로 절삭한 것이다(D-6G-80). 하루 경계를 재려고 시각을 못 박은
 * E2E 가 그 시계로 **여러 걷기**를 만들면 이름이 같아지고, (공고, 축)마다 한 걷기만 쓰는 선별이 두
 * 걷기를 하나로 본다 — 행이 늘 뿐 오류가 없어 어느 단언도 붉어지지 않는다.
 *
 * 그래서 harness 쪽에서 시계를 전진시킨다. 여기서 재는 것은 둘이다: 이름이 **갈리는가**, 그러면서도
 * 하루 경계가 **움직이지 않는가**(움직이면 상한 test 가 재려던 것을 잃는다).
 */
class FixedClockHarnessTest {
    private val pinned: Instant = Instant.parse("2026-09-24T00:30:00+09:00")

    @Test
    fun `못 박은 시계는 읽을 때마다 전진한다 — 절삭 뒤에도 이름이 갈린다`() {
        E2E_FIXED_NOW.set(pinned)
        try {
            val clock = FixedClockTestConfiguration().fixedClock()

            val walkNames = List(WALKS) { clock.now().truncatedTo(ChronoUnit.MICROS) }

            walkNames.toSet() shouldHaveSize WALKS
            walkNames.sorted() shouldBe walkNames
        } finally {
            E2E_FIXED_NOW.set(null)
        }
    }

    @Test
    fun `전진은 하루 경계를 넘기지 않는다 — KST 00시 30분 기동이 그대로 남는다`() {
        E2E_FIXED_NOW.set(pinned)
        try {
            val clock = FixedClockTestConfiguration().fixedClock()

            val days = List(WALKS) { LocalDate.ofInstant(clock.now(), COLLECTION_BUDGET_ZONE) }

            days.toSet() shouldBe setOf(LocalDate.ofInstant(pinned, COLLECTION_BUDGET_ZONE))
        } finally {
            E2E_FIXED_NOW.set(null)
        }
    }

    @Test
    fun `못 박지 않으면 실 시계다 — 이 배선이 다른 test 의 시각을 바꾸지 않는다`() {
        E2E_FIXED_NOW.set(null)

        val now = FixedClockTestConfiguration().fixedClock().now()

        (now.isAfter(pinned)) shouldBe true
    }
}

/** 서로 다른 걷기 셋 — 둘이면 「한 번만 전진한다」는 구현도 통과한다. */
private const val WALKS = 3
