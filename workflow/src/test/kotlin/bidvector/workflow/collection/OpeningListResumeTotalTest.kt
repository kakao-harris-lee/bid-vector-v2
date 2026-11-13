package bidvector.workflow.collection

import bidvector.procurement.BusinessDivision
import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.DetailFetchDecision
import bidvector.procurement.OpeningResultSourcePort
import bidvector.procurement.PageCursor
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceBatch
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * **원천이 한 번 말한 총수는 뒤 배치가 지우지 못한다** — 개찰결과 목록 갈래
 * (`OPEN-M10-OPENING-LIST-RESUME-TOTAL`). 형제 두 갈래가 같은 자리를 이미 잠갔고 이 파일이 셋째다.
 *
 * 쪽 상한으로 이어 돌 때 공유 누산기는 **마지막** 배치의 총수를 쓰는데(`source.sourceTotal ?: sourceTotal`),
 * 어댑터는 무자료 경로에서 그 칸을 **0** 으로 채운다. 그 0 은 `?:` 를 통과해 앞 배치의 총수를 덮고,
 * 그러면 슬롯 회계 행이 「받은 수 ≥ 총수」가 되어 덜 받은 슬롯이 다 받은 것으로 적힌다 — 운영 판독
 * (`received < source_total`)이 무너지는 자리다.
 *
 * **「없음」의 꼴은 둘이고 보호의 출처가 서로 다르다.** 0 은 어느 자리도 막지 않아 이 slice 가 닫는
 * 축이고(고치기 전 RED), 총수 칸을 아예 비운 `null` 은 누산기의 `?:` 가 이미 막고 있었다. 고친 뒤에는
 * 그 보호가 [widerTotalOf] 의 `batch == null` 갈래로 **옮겨 가므로**, null 단언은 결함을 드러내는
 * 자리가 아니라 **옮겨 간 보호를 잠그는** 자리다(형제 갈래의 v r1 V-1 과 같은 이중성).
 *
 * 별도 파일인 이유는 대역이다 — 수집 계약 test 가 쓰는 개찰 대역은 커서를 보지 않고 `next = null` 만
 * 내므로 재개 배치를 지을 수 없다. 여기 대역은 **커서별로** 배치를 짓는 목록 전용이다.
 */
class OpeningListResumeTotalTest {
    @Test
    fun `쪽 상한 재개는 배치마다 말한 총수의 최댓값을 남긴다 — 「없음」이 앞 총수를 지우지 않고 더 큰 총수는 이긴다`() {
        val (erased, erasedCursors) = resumedWith(secondTotal = 0)

        // 재개가 **실제로 돌았다** — 한 배치로 끝났다면 아래 단언들은 보호 없이도 통과한다.
        erasedCursors shouldContainExactly listOf(null, PageCursor("51"))
        erased.sourceTotal shouldBe 7
        // 그 행이 「덜 받았다」로 읽혀야 한다 — 총수가 0 으로 덮이면 이 비교가 뒤집힌다.
        erased.received shouldBe 3
        // 총수 칸을 아예 비운 재개 배치(null) — 0 과 다른 꼴이고 같은 보호를 받아야 한다.
        resumedWith(secondTotal = null).first.sourceTotal shouldBe 7
        // 최댓값이지 「첫 값 고정」이 아니다 — 뒤 배치가 더 큰 총수를 말하면 그 값이 남는다.
        resumedWith(secondTotal = 9).first.sourceTotal shouldBe 9
    }

    /** 쪽 상한으로 끊긴 첫 배치(총수 7·둘 받음)에 이어 재개 배치 하나 — 그 슬롯의 회계 행과 커서 자취. */
    private fun resumedWith(secondTotal: Int?): Pair<CollectionAccounting, List<PageCursor?>> {
        val runs = RecordingRunStore()
        val framer =
            OpeningSampleFramer(
                rawObservations = RecordingRawStore(),
                runs = runs,
                policyFor = { COLLECTION_POLICY },
                clock = Clock { COLLECTION_NOW },
            )
        val port =
            ResumeScriptedOpeningListPort { cursor ->
                if (cursor == null) {
                    openingListBatch(
                        listOf(openingListRow("W1"), openingListRow("W2")),
                        claimedTotalAccounting(
                            received = 2,
                            sourceTotal = 7,
                            truncationCause = TruncationCause.MaxPages,
                        ),
                        next = PageCursor("51"),
                    )
                } else {
                    // 재개 배치도 항목을 나른다 — 총수만 달리 말한다.
                    openingListBatch(
                        listOf(openingListRow("W3")),
                        claimedTotalAccounting(received = 1, sourceTotal = secondTotal),
                        next = null,
                    )
                }
            }
        val source = OpeningCollectionSource(sourceName("construction"), BusinessDivision.CONSTRUCTION, port)

        framer.frame(rangeOf("2026-09-01", "2026-09-01"), listOf(source))

        return runs.recorded.single().first to port.cursors.toList()
    }
}

/** 커서별로 배치를 내는 목록 전용 대역 — 상세 축은 이 판에 오지 않으므로 부르면 터진다. */
private class ResumeScriptedOpeningListPort(
    private val script: (PageCursor?) -> SourceBatch<RawNoticeObservation>,
) : OpeningResultSourcePort {
    val cursors = mutableListOf<PageCursor?>()

    override fun fetchOpeningResults(
        referenceDate: CollectionReferenceDate,
        cursor: PageCursor?,
    ): SourceBatch<RawNoticeObservation> {
        cursors += cursor
        return script(cursor)
    }

    override fun fetchReservePrices(evidence: DetailFetchDecision.Fetch) = error("이 판은 목록 축만 걷는다")

    override fun fetchOpeningCompleteResults(evidence: DetailFetchDecision.Fetch) = error("이 판은 목록 축만 걷는다")

    override fun fetchBidPriceFormulaA(evidence: DetailFetchDecision.Fetch) = error("이 판은 목록 축만 걷는다")

    override fun fetchBaseAmount(evidence: DetailFetchDecision.Fetch) = error("이 판은 목록 축만 걷는다")
}

private fun openingListBatch(
    items: List<RawNoticeObservation>,
    accounting: CollectionAccounting,
    next: PageCursor?,
): SourceBatch<RawNoticeObservation> = SourceBatch(items, accounting, next, observedAt = COLLECTION_NOW)

private fun openingListRow(number: String): RawNoticeObservation =
    RawNoticeObservation.of(
        mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to "000"),
        SourceEndpoint.OPENING_RESULT_LIST,
        COLLECTION_NOW,
    )
