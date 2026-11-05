package bidvector.workflow.collection

import bidvector.procurement.BudgetLimit
import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.PageCursor
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceBatch
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.sharedkernel.Resolution
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * M9/award-ingest D-1 — 낙찰 목록 수집 use case 의 계약: 개찰일 × 업무마다 목록을 끝까지 걷고 항목마다
 * **원문 적재만** 하며 슬롯마다 `OPENING_AWARD_LIST` 회계를 남긴다. canonical 저장소를 받지 않으므로
 * 승격 경로는 구조적으로 없다. 멈춤은 예산·쿼터 둘뿐이고 그 밖의 절단은 다음 슬롯으로 간다.
 */
class CollectAwardListUseCaseTest {
    private val construction = sourceName("construction")
    private val service = sourceName("service")
    private val day1 = LocalDate.of(2026, 9, 1)
    private val day2 = LocalDate.of(2026, 9, 2)

    @Test
    fun `개찰일 × 업무마다 한 번 회계를 남기고 항목마다 원문을 한 번씩 적재한다`() {
        val fixture = AwardFixture()
        val a =
            ScriptedAwardSource { date, _ ->
                awardBatch(listOf(awardObservation("A-$date-1"), awardObservation("A-$date-2")))
            }
        val b = ScriptedAwardSource { date, _ -> awardBatch(listOf(awardObservation("B-$date-1"))) }

        val report =
            fixture.useCase.collect(
                awardRange("2026-09-01", "2026-09-02"),
                listOf(AwardCollectionSource(construction, a), AwardCollectionSource(service, b)),
            )

        fixture.runs.recorded.map { it.second.referenceDate.date } shouldContainExactly listOf(day1, day1, day2, day2)
        // 적재 endpoint 와 회계 축이 **낙찰 목록**이다 — 다른 토큰으로 적히면 승격 슬롯이 바뀐다.
        fixture.runs.recorded.map { it.second.source }.toSet() shouldBe setOf(SourceEndpoint.OPENING_AWARD_LIST)
        fixture.raw.appended.size shouldBe 6
        fixture.raw.appended.map { it.sourceEndpoint }.toSet() shouldBe setOf(SourceEndpoint.OPENING_AWARD_LIST)
        report.slots shouldBe 4
        report.stored shouldBe 6
        report.received shouldBe 6
        report.halted shouldBe null
    }

    @Test
    fun `쪽 상한으로 잘린 슬롯은 커서를 이어 읽고 같은 커서를 다시 부르지 않는다`() {
        val fixture = AwardFixture()
        val cursors = mutableListOf<PageCursor?>()
        val source =
            ScriptedAwardSource { _, cursor ->
                cursors += cursor
                if (cursor == null) {
                    awardBatch(
                        listOf(awardObservation("P1")),
                        accounting = sourceAccounting(normalized = 1, truncationCause = TruncationCause.MaxPages),
                        next = PageCursor("2"),
                    )
                } else {
                    awardBatch(listOf(awardObservation("P2")))
                }
            }

        val report =
            fixture.useCase.collect(
                awardRange("2026-09-01", "2026-09-01"),
                listOf(AwardCollectionSource(construction, source)),
            )

        cursors shouldContainExactly listOf(null, PageCursor("2"))
        report.stored shouldBe 2
        report.halted shouldBe null
    }

    @Test
    fun `예산 소진은 실행을 멈추고 남은 슬롯 수를 사유에 싣는다`() {
        val fixture = AwardFixture()
        val source =
            ScriptedAwardSource { date, _ ->
                if (date == day1) {
                    awardBatch(listOf(awardObservation("D1")))
                } else {
                    awardBatch(
                        emptyList(),
                        accounting =
                            sourceAccounting(
                                normalized = 0,
                                truncationCause = TruncationCause.BudgetExhausted(BudgetLimit.TOTAL),
                            ),
                    )
                }
            }

        val report =
            fixture.useCase.collect(
                awardRange("2026-09-01", "2026-09-03"),
                listOf(AwardCollectionSource(construction, source)),
            )

        report.halted?.budgetLimit shouldBe BudgetLimit.TOTAL
        report.halted?.truncationCause shouldBe null
        // 셋 가운데 둘째에서 멈췄다 — 셋째 슬롯은 손대지 않았다.
        report.halted?.notAttemptedSlots shouldBe 1
        report.slots shouldBe 2
        report.stored shouldBe 1
    }

    @Test
    fun `쿼터 거절도 멈추지만 전송 실패는 다음 슬롯으로 간다`() {
        val fixture = AwardFixture()
        val failing =
            ScriptedAwardSource { date, _ ->
                if (date == day1) {
                    awardBatch(
                        emptyList(),
                        accounting =
                            sourceAccounting(normalized = 0, truncationCause = TruncationCause.TransportFailure),
                    )
                } else {
                    awardBatch(listOf(awardObservation("D2")))
                }
            }

        val report =
            fixture.useCase.collect(
                awardRange("2026-09-01", "2026-09-02"),
                listOf(AwardCollectionSource(construction, failing)),
            )

        report.halted shouldBe null
        report.truncatedSlots shouldBe 1
        report.stored shouldBe 1

        val quotaFixture = AwardFixture()
        val quota =
            ScriptedAwardSource { _, _ ->
                awardBatch(
                    emptyList(),
                    accounting = sourceAccounting(normalized = 0, truncationCause = TruncationCause.QuotaExhausted),
                )
            }

        val quotaReport =
            quotaFixture.useCase.collect(
                awardRange("2026-09-01", "2026-09-02"),
                listOf(AwardCollectionSource(construction, quota)),
            )

        quotaReport.halted?.truncationCause shouldBe TruncationCause.QuotaExhausted
        quotaReport.halted?.budgetLimit shouldBe null
    }
}

/** 낙찰 목록 항목 하나 — 계약이 등재한 키로 원문을 만든다(식별자 둘과 낙찰자 상호). */
private fun awardObservation(number: String): RawNoticeObservation =
    RawNoticeObservation.of(
        mapOf(
            RawKey("bidNtceNo") to number,
            RawKey("bidNtceOrd") to "000",
            RawKey("bidwinnrNm") to "SYN-WINNER",
        ),
        SourceEndpoint.OPENING_AWARD_LIST,
        COLLECTION_NOW,
    )

private fun awardBatch(
    items: List<RawNoticeObservation>,
    accounting: CollectionAccounting = sourceAccounting(normalized = items.size),
    next: PageCursor? = null,
): SourceBatch<RawNoticeObservation> = SourceBatch(items, accounting, next, COLLECTION_NOW)

/** 호출 기록을 남기는 낙찰 목록 소스 — `script` 가 (개찰일, 커서)마다 배치를 낸다. */
private class ScriptedAwardSource(
    private val script: (LocalDate, PageCursor?) -> SourceBatch<RawNoticeObservation>,
) : AwardListSourcePort {
    override fun fetchAwardList(
        referenceDate: CollectionReferenceDate,
        cursor: PageCursor?,
    ): SourceBatch<RawNoticeObservation> = script(referenceDate.date, cursor)
}

private class AwardFixture(
    val raw: RecordingRawStore = RecordingRawStore(),
    val runs: RecordingRunStore = RecordingRunStore(),
) {
    val useCase = CollectAwardListUseCase(rawObservations = raw, runs = runs, clock = Clock { COLLECTION_NOW })
}

/** 이 갈래의 조각 상한으로 범위를 짓는다 — 형제 갈래의 정책을 쓰지 않는다. */
private fun awardRange(
    from: String,
    to: String,
): CollectionRange {
    val policy = AWARD_COLLECTION_RANGE_POLICY.resolve(LocalDate.parse(to))
    val data = (policy as Resolution.Resolved).value
    val outcome = CollectionRange.of(LocalDate.parse(from), LocalDate.parse(to), LocalDate.parse(to), data)
    return (outcome as CollectionRangeOutcome.Valid).range
}
