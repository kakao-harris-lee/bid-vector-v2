package bidvector.workflow.collection

import bidvector.procurement.BudgetLimit
import bidvector.procurement.CollectionAccounting
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
 * 승격 경로는 구조적으로 없다. 멈춤은 예산·쿼터 둘뿐이고 그 밖에 끝내지 못한 슬롯(절단 · **사유 없는
 * 짧은 걷기**)은 미완 계수만 올리고 다음 슬롯으로 간다.
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
            ScriptedSource { date, _ ->
                awardBatch(listOf(awardObservation("A-$date-1"), awardObservation("A-$date-2")))
            }
        val b = ScriptedSource { date, _ -> awardBatch(listOf(awardObservation("B-$date-1"))) }

        val report =
            fixture.useCase.collect(
                awardRange("2026-09-01", "2026-09-02"),
                listOf(AwardCollectionSource(construction, a), AwardCollectionSource(service, b)),
            )

        fixture.runs.recorded.map { it.second.referenceDate.date } shouldContainExactly listOf(day1, day1, day2, day2)
        // 적재 endpoint 와 회계 축이 **낙찰 목록**이다 — 다른 토큰으로 적히면 승격 슬롯이 바뀐다.
        fixture.runs.recorded
            .map { it.second.source }
            .toSet() shouldBe setOf(SourceEndpoint.OPENING_AWARD_LIST)
        fixture.raw.appended.size shouldBe 6
        fixture.raw.appended
            .map { it.sourceEndpoint }
            .toSet() shouldBe setOf(SourceEndpoint.OPENING_AWARD_LIST)
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
            ScriptedSource { _, cursor ->
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
            ScriptedSource { date, _ ->
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

    /**
     * cr r2 H-2 회귀 — **사유 없이 짧게 끝난 걷기도 미완이다.** 원천이 총수를 말한 뒤 빈 쪽이나
     * `NoData` 로 「곱게」 멈추면 절단 사유가 **없다**(어댑터가 `markTruncated` 를 부르지 않는다).
     * 앞 판은 사유만 봐서 그 슬롯을 완료로 적었고, 이 갈래에는 슬롯 이어 돌기가 없어 그 개찰일의
     * 원문이 영영 빠졌다 — 절단과 같은 결말이다.
     *
     * 두 문을 함께 잠근다: 첫 슬롯은 **총수 미달로 멈춘 한 쪽**(빈 쪽·`NoData` 가 내는 모양),
     * 둘째 슬롯은 **총수만큼 받은** 정상 걷기다(그 슬롯이 미완으로 세어지면 과잉이다).
     */
    @Test
    fun `총수를 말한 원천에서 덜 받고 끝난 슬롯은 사유가 없어도 미완이다`() {
        val fixture = AwardFixture()
        val source =
            ScriptedSource { date, _ ->
                if (date == day1) {
                    // 총수 3 을 말한 쪽에서 둘만 받고 걷기가 끝났다 — 절단 사유는 없다.
                    awardBatch(
                        listOf(awardObservation("S1"), awardObservation("S2")),
                        accounting = shortWalkAccounting(received = 2, sourceTotal = 3),
                    )
                } else {
                    awardBatch(listOf(awardObservation("F1")))
                }
            }

        val report =
            fixture.useCase.collect(
                awardRange("2026-09-01", "2026-09-02"),
                listOf(AwardCollectionSource(construction, source)),
            )

        // 멈춘 것이 아니다(사유가 없다) — 그래도 끝내지 못한 슬롯 하나다.
        report.halted shouldBe null
        report.unfinishedSlots shouldBe 1
        report.slots shouldBe 2
        report.stored shouldBe 3
    }

    /**
     * 빈 응답은 **오류가 아니다** — 그 개찰일에 낙찰이 없었을 뿐이다(원천 총수도 0). 위 단언이
     * 「받은 수 < 총수」가 아니라 「0 행」을 미완으로 세게 되면 낙찰 없는 날마다 운영자가 같은
     * 조각을 영원히 다시 돌린다.
     */
    @Test
    fun `낙찰이 없는 날의 빈 응답은 끝낸 것이다`() {
        val fixture = AwardFixture()
        val empty = ScriptedSource { _, _ -> awardBatch(emptyList(), accounting = sourceAccounting(normalized = 0)) }

        val report =
            fixture.useCase.collect(
                awardRange("2026-09-01", "2026-09-01"),
                listOf(AwardCollectionSource(construction, empty)),
            )

        report.unfinishedSlots shouldBe 0
        report.halted shouldBe null
        report.stored shouldBe 0
    }

    @Test
    fun `쿼터 거절도 멈추지만 전송 실패는 다음 슬롯으로 간다`() {
        val fixture = AwardFixture()
        val failing =
            ScriptedSource { date, _ ->
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
        report.unfinishedSlots shouldBe 1
        report.stored shouldBe 1

        val quotaFixture = AwardFixture()
        val quota =
            ScriptedSource { _, _ ->
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

/**
 * **총수와 받은 수가 어긋난** 회계 — 공유 helper(`sourceAccounting`)는 총수를 받은 수로 두므로 이
 * 모양을 지을 수 없다. 짧은 걷기가 사유 **없이** 끝난다는 것이 이 모양의 요점이다.
 */
private fun shortWalkAccounting(
    received: Int,
    sourceTotal: Int,
): CollectionAccounting =
    CollectionAccounting(
        received = received,
        normalized = received,
        duplicate = 0,
        dropped = 0,
        dropReasons = emptyMap(),
        sourceTotal = sourceTotal,
        pagesFetched = 1,
        truncated = false,
        unknownFields = 0,
        truncationCause = null,
    )

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
