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
 * D-6G-40 — 이어 돌기가 **출하 경로에서** 서는지. 조용히 빈 집합을 내면 표본 전체를 다시 불러
 * 승인 상한을 그만큼 태운다. fake 로는 그 조용함이 드러나지 않아 실 Postgres 로 잰다.
 *
 * 상한 seed 는 이 자리에 없다 — `collection_run` 이 아니라 **시도 원장 파일**이 센다(D-6G-45).
 */
class OpeningCollectionLedgerTest : PersistenceTestSupport() {
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

    /**
     * **D-6G2d-8 ⓐ — 번호가 빈 원문 행이 이어 돌기 조회를 던지지 않는다.** 이 조회는 축마다 원문
     * 전건을 훑으므로, 번호 없는 행 하나가 `NoticeNumber.of` 를 던지면 **표본 전체를 다시 불러**
     * 승인 상한을 그만큼 태운다. 그 행은 어떤 공고와도 맞지 않으므로 버린다.
     */
    @Test
    fun `번호가 빈 원문 행은 이어 돌기를 멈추지 않는다`() {
        val id = noticeId("20260924001-00")
        appendRawObservation(observation("20260924001-00", SourceEndpoint.RESERVE_PRICE_DETAIL))
        appendRawObservation(observation(" ", SourceEndpoint.RESERVE_PRICE_DETAIL))

        JdbcCollectedAxisStore(dataSource())
            .alreadyCollected(SourceEndpoint.RESERVE_PRICE_DETAIL, listOf(id)) shouldBe setOf(id)
    }

    @Test
    fun `물어본 공고가 없으면 질의하지 않는다`() {
        JdbcCollectedAxisStore(dataSource())
            .alreadyCollected(SourceEndpoint.OPENING_COMPLETE, emptyList())
            .shouldBeEmpty()
    }
}
