package bidvector.workflow.collection

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** D-6F8-3 — 수집 범위 상한: `to - from ≤ 상한`(정책 데이터)·`to ≤ 오늘`. 설정 오류는 조용히 자르지 않고 거부한다. */
class CollectionRangeTest {
    private val today = LocalDate.of(2026, 9, 24)
    private val maxSpan = COLLECTION_RANGE_POLICY_DATA.maxSpanDays.toLong()

    private fun rangeOutcome(
        from: LocalDate,
        to: LocalDate,
    ) = CollectionRange.of(from, to, today, COLLECTION_RANGE_POLICY_DATA)

    @Test
    fun `상한 안의 범위는 양 끝을 포함한 날짜 목록이다`() {
        val outcome = rangeOutcome(today.minusDays(2), today)

        outcome.shouldBeInstanceOf<CollectionRangeOutcome.Valid>().range.dates shouldContainExactly
            listOf(today.minusDays(2), today.minusDays(1), today)
    }

    @Test
    fun `하루짜리 범위(from == to)도 유효하다`() {
        rangeOutcome(today, today).shouldBeInstanceOf<CollectionRangeOutcome.Valid>().range.dates shouldContainExactly
            listOf(today)
    }

    @Test
    fun `상한 정확히는 유효하고 하루 넘으면 거부한다 — 경계`() {
        rangeOutcome(today.minusDays(maxSpan), today).shouldBeInstanceOf<CollectionRangeOutcome.Valid>()
        rangeOutcome(today.minusDays(maxSpan + 1), today) shouldBe
            CollectionRangeOutcome.Rejected(CollectionRangeViolation.SPAN_TOO_LONG)
    }

    @Test
    fun `미래 날짜는 거부한다`() {
        rangeOutcome(today, today.plusDays(1)) shouldBe
            CollectionRangeOutcome.Rejected(CollectionRangeViolation.TO_IN_FUTURE)
    }

    @Test
    fun `from 이 to 보다 늦으면 거부한다`() {
        rangeOutcome(today, today.minusDays(1)) shouldBe
            CollectionRangeOutcome.Rejected(CollectionRangeViolation.FROM_AFTER_TO)
    }

    @Test
    fun `상한 초과가 미래와 겹쳐도 사유는 하나로 정해진다 — 미래가 먼저`() {
        rangeOutcome(today.minusDays(maxSpan + 5), today.plusDays(3)) shouldBe
            CollectionRangeOutcome.Rejected(CollectionRangeViolation.TO_IN_FUTURE)
    }

    /**
     * **D-6G2e-1 — 갈래별 정책 인스턴스의 값.** 두 인스턴스가 **다른 수**를 든다는 것이 이 slice 의
     * 산출물이다: 하나가 다른 하나를 덮으면(또는 같은 수가 되면) 개찰 갈래의 16주 창과 공고 목록
     * 갈래의 호출 폭주 방지 중 하나가 틀린다. 수는 여기서 읽고 **배선에는 적지 않는다**.
     */
    @Test
    fun `개찰 갈래의 범위 정책은 공고 목록 갈래보다 길다 — 같은 값이 아니다`() {
        // 수를 여기 다시 적지 않는다 — 그러면 같은 값이 두 자리에 서고 둘이 갈린다. 승인된 수
        // 자체는 배선 test 가 **거동으로** 못 박는다(120일 창은 뜨고 121일은 기동 거부).
        val opening = OPENING_COLLECTION_RANGE_POLICY_DATA.maxSpanDays.toLong()

        (opening > maxSpan) shouldBe true
        CollectionRange
            .of(today.minusDays(opening), today, today, OPENING_COLLECTION_RANGE_POLICY_DATA)
            .shouldBeInstanceOf<CollectionRangeOutcome.Valid>()
        CollectionRange.of(today.minusDays(opening + 1), today, today, OPENING_COLLECTION_RANGE_POLICY_DATA) shouldBe
            CollectionRangeOutcome.Rejected(CollectionRangeViolation.SPAN_TOO_LONG)
    }

    @Test
    fun `업종 이름은 소문자 영문으로 시작하는 짧은 토큰만 유효하다 — 로그에 그대로 실려도 안전하다`() {
        CollectionSourceName.of("construction")?.value shouldBe "construction"
        CollectionSourceName.of("service-2")?.value shouldBe "service-2"
        listOf("", " ", "Construction", "1service", "a b", "a\nb", "a=b", "x".repeat(33)).forEach {
            CollectionSourceName.of(it) shouldBe null
        }
    }
}
