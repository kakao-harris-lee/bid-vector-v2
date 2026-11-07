package bidvector.workflow.collection

import bidvector.procurement.BudgetLimit
import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionDropReason
import bidvector.procurement.PageCursor
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceBatch
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import bidvector.sharedkernel.Resolution
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
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

    /**
     * v r3 L-1 회귀 — **재개 배치가 총수를 0 으로 덮지 못한다.** 쪽 상한으로 이어 돌 때 공유 누산기는
     * 마지막 배치의 총수를 쓰는데(`source.sourceTotal ?: sourceTotal`), 어댑터는 배치마다 새 누산기라
     * 재개 첫 쪽이 「없음」이면 총수 **0** 을 낸다. 그러면 「받은 2 ≥ 0」이 되어 다섯 중 둘만 받은
     * 슬롯이 완료로 적힌다. use case 가 배치마다 본 총수의 **최댓값**을 쥐어 그 자리를 닫는다.
     */
    @Test
    fun `쪽 상한 재개 뒤 총수를 말하지 않는 배치가 와도 앞 배치의 총수를 잃지 않는다`() {
        val fixture = AwardFixture()
        val source =
            ScriptedSource { _, cursor ->
                if (cursor == null) {
                    awardBatch(
                        listOf(awardObservation("R1"), awardObservation("R2")),
                        accounting =
                            shortWalkAccounting(
                                received = 2,
                                sourceTotal = 5,
                                truncationCause = TruncationCause.MaxPages,
                            ),
                        next = PageCursor("51"),
                    )
                } else {
                    awardBatch(emptyList(), accounting = shortWalkAccounting(received = 0, sourceTotal = 0))
                }
            }

        val report =
            fixture.useCase.collect(
                awardRange("2026-09-01", "2026-09-01"),
                listOf(AwardCollectionSource(construction, source)),
            )

        report.received shouldBe 2
        report.unfinishedSlots shouldBe 1
        // 회계 행에도 **넓은 쪽**이 남아야 runbook 의 `received < source_total` 판독이 선다.
        val (accounting, _) = fixture.runs.recorded.single()
        accounting.sourceTotal shouldBe 5
    }

    /**
     * cr r3 M-1·M-2 — **다 받고도 덜 적재한 슬롯**의 계수가 보고에 선다. 종료 코드는 움직이지 않는다
     * (팀장 결정: 재실행으로 풀리지 않는 결측이라 미완으로 세지 않는다) — 대신 `duplicates`·`dropped`
     * 가 그 자리를 들고 `received − stored = duplicates + dropped` 등식이 성립한다.
     */
    @Test
    fun `받았지만 적재되지 않은 항목은 계수로 서고 미완으로 세지 않는다`() {
        val fixture = AwardFixture()
        val source =
            ScriptedSource { _, _ ->
                awardBatch(
                    listOf(awardObservation("K1"), awardObservation("K2")),
                    accounting = partialStoreAccounting(normalized = 2, duplicate = 1, dropped = 1),
                )
            }

        val report =
            fixture.useCase.collect(
                awardRange("2026-09-01", "2026-09-01"),
                listOf(AwardCollectionSource(construction, source)),
            )

        report.received shouldBe 4
        report.stored shouldBe 2
        report.duplicates shouldBe 1
        report.dropped shouldBe 1
        report.received - report.stored shouldBe report.duplicates + report.dropped
        // 원천이 말한 총수를 다 받았으므로 **끝낸** 슬롯이다 — 결측은 적재 단계의 것이고 재실행이 풀지 않는다.
        report.unfinishedSlots shouldBe 0
        report.halted shouldBe null
    }

    /**
     * **멈춤 집합의 등식**(ops-code 레인 교차 지적) — 절단 사유마다 「실행을 멈추는가」를 전수로 잠근다.
     * `haltOf` 가 소진 `when` 이라 새 사유가 생기면 컴파일이 그 자리를 가리키지만, 컴파일은 **어느
     * 쪽으로** 보낼지 묻지 않는다. 이 등식이 그 선택을 고정한다.
     *
     * 멈추는 것은 **승인 범위·계정 한도를 다 쓴** 둘뿐이다(예산·쿼터). 그 둘은 다음 슬롯을 불러도 같은
     * 거절이 오므로 창을 더 걷는 의미가 없다. 나머지는 그 슬롯만 접고 다음 슬롯으로 간다 — 한 날의
     * 전송 실패로 창 전체를 버리면 다시 걷는 호출이 그만큼 더 나간다.
     *
     * 403 계열 차단(`AccessBlocked`)이 들어오면 **멈춤 쪽**이다(근거는 `haltOf` KDoc).
     *
     * **집합이 아니라 사유마다의 결정을 맞춘다**(실측으로 고친 자리) — 「멈춤 집합의 등식」만 두면 새 사유를
     * **계속 걷기** 쪽에 넣은 판이 초록이다(그 사유가 멈춤 집합에 안 들어오므로 등식이 그대로 성립한다).
     * 그런데 그 방향이 바로 위험한 방향이다(403 이 조용히 계속 걷는다). 그래서 기대값을 **사유 → 멈추는가**
     * 의 전수 표로 두고 모수를 sealed 계층에서 도출한다.
     *
     * **새 사유는 어느 쪽에 넣어도 붉지만 기제가 둘이다**(v r6 L-4, 실측). `data object` 면 모수에 들어와
     * **키가 하나 늘어** 이 표가 잡는다(1/9). 값을 나르는 사유면 폴백 한정 때문에 모수 초기화가
     * `NoSuchFieldException` 으로 죽어 **클래스 전체**가 선다(9/9) — 이 표가 아니라 초기화가 잡는 것이다.
     */
    @Test
    fun `사유마다 멈추는지가 전수 표로 고정된다`() {
        // 모수가 sealed 전수와 **같은지**를 먼저 잰다(`AxisLastObservedAtTest` 선례 · v r5 H-1).
        // 폴백 한정만으로도 값을 나르는 새 사유는 예외로 죽지만, 이 두 줄은 그것을 **단언**으로 남긴다 —
        // 모수가 한 벌이라도 접히거나 빠지면 표를 보기 전에 여기서 붉어진다.
        val permitted = TruncationCause::class.java.permittedSubclasses.toSet()
        ALL_TRUNCATION_CAUSES shouldHaveSize permitted.size - 1 + BudgetLimit.entries.size
        ALL_TRUNCATION_CAUSES.map { it.javaClass }.toSet() shouldBe permitted

        val decision = ALL_TRUNCATION_CAUSES.associateWith { cause -> haltedBy(cause) }

        decision shouldBe
            mapOf(
                // 멈춤 — 승인 범위·계정 한도를 다 썼다(더 불러도 같은 거절이 온다).
                TruncationCause.BudgetExhausted(BudgetLimit.DAILY) to true,
                TruncationCause.BudgetExhausted(BudgetLimit.TOTAL) to true,
                TruncationCause.QuotaExhausted to true,
                // 그 슬롯만 접고 다음 슬롯으로 — 한 날의 실패로 창 전체를 버리지 않는다.
                TruncationCause.MaxPages to false,
                TruncationCause.RepeatedPage to false,
                TruncationCause.Timeout to false,
                TruncationCause.TransportFailure to false,
                TruncationCause.ServerError to false,
                TruncationCause.NotRetryable to false,
                TruncationCause.InputError to false,
                TruncationCause.Unclassified to false,
                TruncationCause.StructureFailure to false,
                TruncationCause.SelfThrottled to false,
            )
    }

    /** 그 사유 하나로 끝나는 슬롯이 실행을 멈추는가 — 멈추지 않으면 미완 계수만 올라야 한다. */
    private fun haltedBy(cause: TruncationCause): Boolean {
        val fixture = AwardFixture()
        val source =
            ScriptedSource { _, _ ->
                awardBatch(emptyList(), accounting = sourceAccounting(normalized = 0, truncationCause = cause))
            }

        val report =
            fixture.useCase.collect(
                awardRange("2026-09-01", "2026-09-02"),
                listOf(AwardCollectionSource(construction, source)),
            )

        // 멈추지 않는 사유는 두 슬롯 다 돌고 둘 다 미완이다(조용히 완료로 세지 않는다).
        if (report.halted == null) report.unfinishedSlots shouldBe 2
        return report.halted != null
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

/**
 * `TruncationCause` **전수** — 위 등식의 모수이고 **sealed 계층에서 도출한다**(cr r4 M-2 · 같은 패키지
 * `CollectNoticesUseCaseTest` 와 `AxisLastObservedAtTest` 의 선례).
 *
 * 손으로 적던 앞 판은 **새 사유에 조용히 초록**이었다 — 사유를 더하고 이 목록에 넣지 않으면 등식이 그
 * 선택을 아예 **측정하지 않는다**(D-6G2d-30 이 적은 그 실패 모양이다).
 *
 * **폴백을 `BudgetExhausted` 하나로 한정한다**(v r5 H-1 · cr r5 H-2). 앞 판은 `INSTANCE` 가 없는 **모든**
 * 하위 타입을 `runCatching` 으로 받아 `BudgetExhausted` 를 한 벌 더 냈다 — 값을 나르는 새 사유는 목록에
 * 한 번도 나타나지 않고 `associateWith` 가 그 중복 키를 접어 표가 오늘과 똑같아졌다. 실측에서 그 판
 * (`data class` 13번째 사유)이 **초록**이었다(`data object` 판만 붉었다). 이제 그런 사유는
 * `NoSuchFieldException` 으로 **소리 내며 죽는다**.
 */
private val ALL_TRUNCATION_CAUSES: List<TruncationCause> =
    requireNotNull(TruncationCause::class.java.permittedSubclasses) { "TruncationCause 는 sealed 여야 한다" }
        .flatMap { subclass ->
            if (subclass == TruncationCause.BudgetExhausted::class.java) {
                // 값을 나르는 유일한 사유 — 상한마다 한 인스턴스이고 그 축도 `BudgetLimit.entries` 에서 온다.
                BudgetLimit.entries.map { limit -> TruncationCause.BudgetExhausted(limit) }
            } else {
                listOf(subclass.getField("INSTANCE").get(null) as TruncationCause)
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
    truncationCause: TruncationCause? = null,
): CollectionAccounting =
    CollectionAccounting(
        received = received,
        normalized = received,
        duplicate = 0,
        dropped = 0,
        dropReasons = emptyMap(),
        sourceTotal = sourceTotal,
        pagesFetched = 1,
        truncated = truncationCause != null,
        unknownFields = 0,
        truncationCause = truncationCause,
    )

/**
 * **다 받고도 덜 적재한** 회계 — 총수는 받은 수와 같고(끝낸 슬롯이다) 그 안에서 중복·드롭이 갈린다.
 * 공유 helper 는 `normalized = received` 라 이 모양을 지을 수 없다.
 */
private fun partialStoreAccounting(
    normalized: Int,
    duplicate: Int,
    dropped: Int,
): CollectionAccounting {
    val received = normalized + duplicate + dropped
    return CollectionAccounting(
        received = received,
        normalized = normalized,
        duplicate = duplicate,
        dropped = dropped,
        dropReasons = mapOf(CollectionDropReason.CollectionMissingNoticeNumber to dropped),
        sourceTotal = received,
        pagesFetched = 1,
        truncated = false,
        unknownFields = 0,
        truncationCause = null,
    )
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
