package bidvector.procurement

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
    key: String? = null,
    axis: SourceEndpoint = SourceEndpoint.RESERVE_PRICE_DETAIL,
    outcome: AttemptOutcome = AttemptOutcome.Succeeded,
) = CollectionAttempt(key, axis, outcome, Instant.parse(at), httpAttempts)

/** 축의 결말 줄 — 호출이 아니므로 상한에 계상되지 않는다(D-6G-49). */
private fun settled(
    at: String,
    key: String?,
    axis: SourceEndpoint,
    outcome: AttemptOutcome,
) = CollectionAttempt(key, axis, outcome, Instant.parse(at), httpAttempts = 0, kind = AttemptKind.AXIS)

/**
 * D-6G-45 — 상한이 세는 것은 **나간 호출**이다. 받은 페이지만 세면 재시도·5xx·429·타임아웃이
 * 승인 범위 밖에서 나가고, 원문 행의 존재로 이어 돌기를 판정하면 빈 응답 축이 영원히 다시 불린다.
 */
class CollectionAttemptLedgerTest {
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

    /**
     * D-6G-49 — **성공과 빈 응답만** 다시 부르지 않는다. 실패·타임아웃·5xx·쿼터 거절은 다시
     * 부른다: 한 번 실패한 축을 영구히 포기하면 결측이 무작위가 아니게 되고(느린 시간대에 몰린
     * 공고만 빠진다) 그 행은 값 결측 제외로 계수되어 사유 귀속까지 틀린다.
     */
    @Test
    fun `다시 부르지 않는 축은 성공과 빈 응답뿐이다`() {
        val key = "0".repeat(64)
        val history =
            AttemptHistory(
                listOf(
                    settled("2026-09-24T01:00:00Z", key, SourceEndpoint.RESERVE_PRICE_DETAIL, AttemptOutcome.Empty),
                    settled("2026-09-24T01:00:01Z", key, SourceEndpoint.BASE_AMOUNT_DETAIL, AttemptOutcome.Succeeded),
                    settled(
                        "2026-09-24T01:00:02Z",
                        key,
                        SourceEndpoint.OPENING_COMPLETE,
                        AttemptOutcome.Failed("TIMEOUT"),
                    ),
                ),
            )

        val axes = history.settledAxes().getValue(key)

        axes.map { it.name }.sorted() shouldContainExactly
            listOf(SourceEndpoint.BASE_AMOUNT_DETAIL.name, SourceEndpoint.RESERVE_PRICE_DETAIL.name)
    }

    /** HTTP 줄은 이어 돌기가 보지 않는다 — 나간 호출이지 축의 결말이 아니다. */
    @Test
    fun `HTTP 시도 줄은 이어 돌기에 들지 않는다`() {
        val key = "0".repeat(64)
        val history =
            AttemptHistory(listOf(attempt("2026-09-24T01:00:00Z", 1, key, SourceEndpoint.OPENING_COMPLETE)))

        history.settledAxes().keys.shouldBeEmpty()
        history.spend(SINCE, Instant.parse("2026-09-23T15:00:00Z")).total shouldBe 1
    }

    /** 목록 축은 공고 단위가 아니다 — 이어 돌기 대상이 아니지만 상한은 센다. */
    @Test
    fun `공고 키 없는 시도는 이어 돌기에 들지 않는다`() {
        val history =
            AttemptHistory(listOf(attempt("2026-09-24T01:00:00Z", 4, axis = SourceEndpoint.OPENING_RESULT_LIST)))

        history.settledAxes().keys.shouldBeEmpty()
        history.spend(SINCE, Instant.parse("2026-09-23T15:00:00Z")).total shouldBe 4
    }
}
