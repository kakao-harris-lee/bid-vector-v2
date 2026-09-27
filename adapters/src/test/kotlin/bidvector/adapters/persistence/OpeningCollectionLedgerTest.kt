package bidvector.adapters.persistence

import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.CollectionRunMeta
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

private val SINCE = Instant.parse("2026-09-20T00:00:00Z")
private val DAY_START = Instant.parse("2026-09-23T15:00:00Z")

private fun runMeta(
    startedAt: Instant,
    endpoint: SourceEndpoint = SourceEndpoint.OPENING_RESULT_LIST,
): CollectionRunMeta =
    CollectionRunMeta(
        referenceDate = CollectionReferenceDate(LocalDate.of(2026, 9, 24)),
        source = endpoint,
        startedAt = startedAt,
        finishedAt = startedAt.plusSeconds(1),
    )

private fun accounting(pages: Int): CollectionAccounting =
    CollectionAccounting(
        received = 0,
        normalized = 0,
        duplicate = 0,
        dropped = 0,
        dropReasons = emptyMap(),
        sourceTotal = 0,
        pagesFetched = pages,
        truncated = false,
        unknownFields = 0,
    )

private fun observation(
    number: String,
    endpoint: SourceEndpoint,
): RawNoticeObservation =
    RawNoticeObservation.of(
        mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to "000"),
        endpoint,
        Instant.parse("2026-09-24T02:00:00Z"),
    )

private fun noticeId(number: String): NoticeId = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))

/**
 * D-6G-40 — 상한과 이어 돌기가 **출하 경로에서** 서는지. 둘 다 영속을 읽는 자리이고, 조용히 0 을
 * 내면 승인 상한이 아무것도 막지 못하고(매 기동 0 에서 시작) 이어 돌기가 표본 전체를 다시 부른다.
 * fake 로는 그 조용함이 드러나지 않아 실 Postgres 로 잰다.
 */
class OpeningCollectionLedgerTest : PersistenceTestSupport() {
    private fun ledger() = JdbcCollectionCallLedgerStore(dataSource())

    private fun runs() = JdbcCollectionRunStore(dataSource())

    @Test
    fun `쓴 호출 수는 collection_run 의 pages_fetched 합이다`() {
        runs().record(accounting(pages = 3), runMeta(Instant.parse("2026-09-24T01:00:00Z")))
        runs().record(accounting(pages = 4), runMeta(Instant.parse("2026-09-24T02:00:00Z")))

        val spend = ledger().spentSince(SINCE, DAY_START)

        spend.total shouldBe 7
        spend.today shouldBe 7
    }

    /** 승인 시작 전의 수집(다른 slice 의 실수집)은 이 예산에 계상하지 않는다. */
    @Test
    fun `since 이전 실행은 총계에 들어가지 않는다`() {
        runs().record(accounting(pages = 5), runMeta(Instant.parse("2026-09-19T23:00:00Z")))
        runs().record(accounting(pages = 2), runMeta(Instant.parse("2026-09-24T01:00:00Z")))

        ledger().spentSince(SINCE, DAY_START).total shouldBe 2
    }

    /**
     * 하루의 경계는 **호출부가 주는 `dayStart`** 다. KST 자정(= 전날 15:00Z)과 UTC 자정 사이에 난
     * 실행이 오늘치에 드는지가 그 경계의 전부다 — UTC 로 세면 이 행이 빠진다.
     */
    @Test
    fun `오늘치는 dayStart 이후만 센다 — KST 자정과 UTC 자정 사이도 오늘이다`() {
        runs().record(accounting(pages = 6), runMeta(Instant.parse("2026-09-23T16:00:00Z")))
        runs().record(accounting(pages = 1), runMeta(Instant.parse("2026-09-23T14:00:00Z")))

        val spend = ledger().spentSince(SINCE, DAY_START)

        spend.total shouldBe 7
        spend.today shouldBe 6
    }

    @Test
    fun `이어 돌기는 이미 받은 축의 공고를 돌려준다`() {
        val id = noticeId("20260924001-00")
        appendRawObservation(observation("20260924001-00", SourceEndpoint.RESERVE_PRICE_DETAIL))

        val axis = JdbcCollectedAxisStore(dataSource())

        axis.alreadyCollected(SourceEndpoint.RESERVE_PRICE_DETAIL, listOf(id)) shouldBe setOf(id)
        // 같은 공고라도 **다른 축**은 아직 받지 않았다 — 축마다 따로 센다.
        axis.alreadyCollected(SourceEndpoint.OPENING_COMPLETE, listOf(id)).shouldBeEmpty()
    }

    /**
     * 공고번호의 canonical 형태는 ASCII 대문자화다. 원문 payload 는 온 그대로라, 조회가 canonical
     * 값을 원문과 그대로 맞대면 소문자가 든 번호에서 **조용히 빗나간다** — 이어 돌기가 안 되는 것이
     * 아니라 「안 받았다」고 답해 표본 전체를 다시 부른다(승인 상한이 그만큼 사라진다).
     */
    @Test
    fun `원문이 소문자여도 이어 돌기가 맞는다 — canonical 과 원문을 그대로 맞대지 않는다`() {
        appendRawObservation(observation("2026abc-01", SourceEndpoint.OPENING_COMPLETE))

        val found =
            JdbcCollectedAxisStore(dataSource())
                .alreadyCollected(SourceEndpoint.OPENING_COMPLETE, listOf(noticeId("2026abc-01")))

        found shouldBe setOf(noticeId("2026abc-01"))
    }

    @Test
    fun `물어본 공고가 없으면 질의하지 않는다`() {
        JdbcCollectedAxisStore(dataSource())
            .alreadyCollected(SourceEndpoint.OPENING_COMPLETE, emptyList())
            .shouldBeEmpty()
    }
}
