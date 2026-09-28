package bidvector.procurement

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

/** 나간 호출의 결말 줄 — 상한이 세지 않는다(의도 줄이 이미 세었다). */
private fun settledHttp(at: String) =
    CollectionAttempt(null, AXIS, AttemptOutcome.Succeeded, Instant.parse(at), AttemptKind.HTTP)

private val AXIS: SourceEndpoint = SourceEndpoint.RESERVE_PRICE_DETAIL
private val SINCE: Instant = Instant.parse("2026-09-20T00:00:00Z")

/** 나가려는 호출 한 줄 — 한 줄이 한 호출이다(D-6G-61 ①). */
private fun attempt(
    at: String,
    key: String? = null,
    axis: SourceEndpoint = SourceEndpoint.RESERVE_PRICE_DETAIL,
    outcome: AttemptOutcome = AttemptOutcome.Succeeded,
) = CollectionAttempt(key, axis, outcome, Instant.parse(at), AttemptKind.PENDING)

/** 축의 결말 줄 — 호출이 아니므로 상한에 계상되지 않는다(D-6G-49). */
private fun settled(
    at: String,
    key: String?,
    axis: SourceEndpoint,
    outcome: AttemptOutcome,
) = CollectionAttempt(key, axis, outcome, Instant.parse(at), AttemptKind.AXIS)

private fun walked(
    received: Int,
    sourceTotal: Int,
) = CollectionAccounting(
    received = received,
    normalized = received,
    duplicate = 0,
    dropped = 0,
    dropReasons = emptyMap(),
    sourceTotal = sourceTotal,
    pagesFetched = 1,
    truncated = false,
    unknownFields = 0,
)

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
                    attempt("2026-09-24T01:00:00Z"),
                    attempt("2026-09-24T01:30:00Z"),
                    attempt("2026-09-24T02:00:00Z"),
                    // 결말 줄은 호출이 아니다 — 같은 호출을 두 번 세지 않는다.
                    CollectionAttempt(
                        null,
                        AXIS,
                        AttemptOutcome.Succeeded,
                        Instant.parse("2026-09-24T02:00:01Z"),
                        AttemptKind.HTTP,
                    ),
                ),
            )

        history.spend(SINCE, Instant.parse("2026-09-23T15:00:00Z")).total shouldBe 3
    }

    @Test
    fun `승인 시작 이전 시도는 계상하지 않는다`() {
        val history =
            AttemptHistory(
                listOf(
                    attempt("2026-09-19T23:00:00Z"),
                    attempt("2026-09-24T01:00:00Z"),
                    attempt("2026-09-24T01:10:00Z"),
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
                    attempt("2026-09-23T16:00:00Z"),
                    attempt("2026-09-23T17:00:00Z"),
                    attempt("2026-09-23T14:00:00Z"),
                ),
            )

        val spend = history.spend(SINCE, dayStartOf(java.time.LocalDate.of(2026, 9, 24), KST))

        spend.total shouldBe 3
        spend.today shouldBe 2
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

    /**
     * D-6G-58 — (공고, 축)에 줄이 여럿이면 **마지막**이 이긴다. 앞 실행이 실패로 닫은 축을 이번
     * 실행이 받으면 완료이고, 앞 실행이 받은 축을 이번 실행이 짧게 걸었으면 다시 불려야 한다.
     */
    @Test
    fun `축의 결말은 마지막 줄이 이긴다`() {
        val key = "0".repeat(64)
        val history =
            AttemptHistory(
                listOf(
                    settled("2026-09-24T01:00:00Z", key, SourceEndpoint.OPENING_COMPLETE, AttemptOutcome.Failed("X")),
                    settled("2026-09-24T02:00:00Z", key, SourceEndpoint.OPENING_COMPLETE, AttemptOutcome.Succeeded),
                    settled("2026-09-24T01:00:00Z", key, SourceEndpoint.BASE_AMOUNT_DETAIL, AttemptOutcome.Succeeded),
                    settled(
                        "2026-09-24T02:00:00Z",
                        key,
                        SourceEndpoint.BASE_AMOUNT_DETAIL,
                        AttemptOutcome.Failed("SHORT_WALK"),
                    ),
                ),
            )

        history.axisConclusions().getValue(key) shouldBe
            mapOf(SourceEndpoint.OPENING_COMPLETE to true, SourceEndpoint.BASE_AMOUNT_DETAIL to false)
    }

    /**
     * D-6G-58 ⓒ — 원천이 총수를 말했는데 그만큼 받지 못하고 끝난 걷기는 **짧은 걷기**다. 빈
     * 페이지나 `NoData` 로 곱게 멈추면 절단 사유가 없어 앞 판은 이것을 성공·빈 응답으로 적었고,
     * 그 축은 다시 불리지 않았다(조용한 결측).
     */
    @Test
    fun `받은 수가 원천 총수보다 적으면 짧은 걷기다`() {
        attemptOutcomeOf(walked(received = 0, sourceTotal = 250)) shouldBe AttemptOutcome.Failed("SHORT_WALK")
        attemptOutcomeOf(walked(received = 100, sourceTotal = 250)) shouldBe AttemptOutcome.Failed("SHORT_WALK")
        attemptOutcomeOf(walked(received = 250, sourceTotal = 250)) shouldBe AttemptOutcome.Succeeded
        // 빈 응답이 끝난 답인 것은 원천 총수가 0 일 때뿐이다.
        attemptOutcomeOf(walked(received = 0, sourceTotal = 0)) shouldBe AttemptOutcome.Empty
    }

    /** HTTP 줄은 이어 돌기가 보지 않는다 — 나간 호출이지 축의 결말이 아니다. */
    @Test
    fun `HTTP 시도 줄은 이어 돌기에 들지 않는다`() {
        val key = "0".repeat(64)
        val history =
            AttemptHistory(listOf(attempt("2026-09-24T01:00:00Z", key, SourceEndpoint.OPENING_COMPLETE)))

        history.settledAxes().keys.shouldBeEmpty()
        history.spend(SINCE, Instant.parse("2026-09-23T15:00:00Z")).total shouldBe 1
    }

    /** 목록 축은 공고 단위가 아니다 — 이어 돌기 대상이 아니지만 상한은 센다. */
    @Test
    fun `공고 키 없는 시도는 이어 돌기에 들지 않는다`() {
        val history =
            AttemptHistory(
                listOf(
                    attempt("2026-09-24T01:00:00Z", axis = SourceEndpoint.OPENING_RESULT_LIST),
                    attempt("2026-09-24T01:00:01Z", axis = SourceEndpoint.OPENING_RESULT_LIST),
                    attempt("2026-09-24T01:00:02Z", axis = SourceEndpoint.OPENING_RESULT_LIST),
                    attempt("2026-09-24T01:00:03Z", axis = SourceEndpoint.OPENING_RESULT_LIST),
                ),
            )

        history.settledAxes().keys.shouldBeEmpty()
        history.spend(SINCE, Instant.parse("2026-09-23T15:00:00Z")).total shouldBe 4
    }
}
