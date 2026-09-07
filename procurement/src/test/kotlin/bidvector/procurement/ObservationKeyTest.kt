package bidvector.procurement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/** ③ — [ObservationKey]가 재수집 재시도의 멱등 단위를 결정론적으로 낸다는 것을 증명한다. */
class ObservationKeyTest {
    private fun observation(
        noticeNumber: String = "20260101001",
        observedAt: Instant = Instant.parse("2026-09-07T00:00:00Z"),
    ): RawNoticeObservation =
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to noticeNumber, RawKey("bidNtceOrd") to "000"),
            SourceEndpoint.NOTICE_LIST,
            observedAt,
        )

    @Test
    fun `같은 내용의 관측은 같은 ObservationKey 를 낸다 — 재시도 멱등의 근거`() {
        val first = ObservationKey.of(observation())
        val second = ObservationKey.of(observation())

        first shouldBe second
    }

    @Test
    fun `observedAt 이 다르면 다른 ObservationKey`() {
        val first = ObservationKey.of(observation(observedAt = Instant.parse("2026-09-07T00:00:00Z")))
        val second = ObservationKey.of(observation(observedAt = Instant.parse("2026-09-07T00:00:01Z")))

        (first == second) shouldBe false
    }

    @Test
    fun `내용(값)이 다르면 다른 ObservationKey — hashCode 가 fields 값을 반영한다`() {
        val first = observation(noticeNumber = "20260101001")
        val second = observation(noticeNumber = "20260101002")

        (ObservationKey.of(first) == ObservationKey.of(second)) shouldBe false
    }

    @Test
    fun `sourceEndpoint 가 다르면 다른 ObservationKey`() {
        val fromList =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to "20260101001"),
                SourceEndpoint.NOTICE_LIST,
                Instant.parse("2026-09-07T00:00:00Z"),
            )
        val fromDetail =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to "20260101001"),
                SourceEndpoint.NOTICE_DETAIL,
                Instant.parse("2026-09-07T00:00:00Z"),
            )

        (ObservationKey.of(fromList) == ObservationKey.of(fromDetail)) shouldBe false
    }

    @Test
    fun `ObservationKey 는 빈 문자열을 거부한다`() {
        shouldThrow<IllegalArgumentException> { ObservationKey("") }
    }
}
