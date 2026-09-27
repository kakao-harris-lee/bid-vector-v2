package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import bidvector.procurement.SourceEndpoint
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

private val KST: ZoneId = ZoneId.of("Asia/Seoul")
private val SINCE: Instant = Instant.parse("2026-09-20T00:00:00Z")

private fun attempt(
    at: String,
    httpAttempts: Int,
    key: NoticeKeyHash? = null,
    axis: SourceEndpoint = SourceEndpoint.RESERVE_PRICE_DETAIL,
    outcome: AttemptOutcome = AttemptOutcome.Succeeded,
) = CollectionAttempt(key, axis, outcome, Instant.parse(at), httpAttempts)

/**
 * D-6G-45 — 상한이 세는 것은 **나간 호출**이다. 받은 페이지만 세면 재시도·5xx·429·타임아웃이
 * 승인 범위 밖에서 나가고, 원문 행의 존재로 이어 돌기를 판정하면 빈 응답 축이 영원히 다시 불린다.
 */
class AttemptLedgerTest {
    @Test
    fun `상한은 받은 페이지가 아니라 나간 호출을 센다`() {
        val history =
            AttemptHistory(
                listOf(
                    attempt("2026-09-24T01:00:00Z", httpAttempts = 3),
                    attempt("2026-09-24T02:00:00Z", httpAttempts = 1),
                ),
            )

        history.spend(SINCE, Instant.parse("2026-09-23T15:00:00Z")).total shouldBe 4
    }

    @Test
    fun `승인 시작 이전 시도는 계상하지 않는다`() {
        val history =
            AttemptHistory(
                listOf(
                    attempt("2026-09-19T23:00:00Z", httpAttempts = 5),
                    attempt("2026-09-24T01:00:00Z", httpAttempts = 2),
                ),
            )

        history.spend(SINCE, Instant.parse("2026-09-23T15:00:00Z")).total shouldBe 2
    }

    /** KST 자정과 UTC 자정 사이 아홉 시간 — UTC 로 세면 이 시도가 오늘치에서 빠진다. */
    @Test
    fun `오늘치의 경계는 KST 자정이다`() {
        val history =
            AttemptHistory(
                listOf(
                    attempt("2026-09-23T16:00:00Z", httpAttempts = 6),
                    attempt("2026-09-23T14:00:00Z", httpAttempts = 1),
                ),
            )

        val spend = history.spend(SINCE, dayStartOf(java.time.LocalDate.of(2026, 9, 24), KST))

        spend.total shouldBe 7
        spend.today shouldBe 6
    }

    /** 빈 응답은 오류가 아니지만 **시도이기는 하다** — 결말과 무관하게 「불렀다」로 센다. */
    @Test
    fun `시도한 축은 결말과 무관하다`() {
        val key = NoticeKeyHash.of("SYN-6G-0001", "000")
        val history =
            AttemptHistory(
                listOf(
                    attempt("2026-09-24T01:00:00Z", 1, key, SourceEndpoint.RESERVE_PRICE_DETAIL, AttemptOutcome.Empty),
                    attempt(
                        "2026-09-24T01:00:01Z",
                        2,
                        key,
                        SourceEndpoint.OPENING_COMPLETE,
                        AttemptOutcome.Failed("Timeout"),
                    ),
                ),
            )

        val axes = history.attemptedAxes().getValue(key)

        axes.map { it.name }.sorted() shouldContainExactly
            listOf(SourceEndpoint.OPENING_COMPLETE.name, SourceEndpoint.RESERVE_PRICE_DETAIL.name)
    }

    /** 목록 축은 공고 단위가 아니다 — 이어 돌기 대상이 아니지만 상한은 센다. */
    @Test
    fun `공고 키 없는 시도는 이어 돌기에 들지 않는다`() {
        val history =
            AttemptHistory(listOf(attempt("2026-09-24T01:00:00Z", 4, axis = SourceEndpoint.OPENING_RESULT_LIST)))

        history.attemptedAxes().keys.shouldBeEmpty()
        history.spend(SINCE, Instant.parse("2026-09-23T15:00:00Z")).total shouldBe 4
    }

    @Test
    fun `빈 응답을 받은 축은 다시 부르지 않는다`() {
        val fixture = OpeningFixture(sampleSize = 2)
        fixture.listRows(BusinessDivision.SERVICE, "2026-06-03", count = 5)
        fixture.run()
        val calledOnce = fixture.service.reservePriceCalls.size

        // 같은 원장을 들고 다시 돈다. 이어 돌기 저장소(원문 축)는 빈 집합을 내므로, 다시 부르지
        // 않는다면 그것은 **시도 원장**이 「불렀다」를 기억했기 때문이다.
        fixture.run()

        fixture.service.reservePriceCalls.size shouldBe calledOnce
        calledOnce shouldBe 2
    }
}
