package bidvector.workflow.collection

import bidvector.procurement.CollectionDropReason
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeTitle
import bidvector.procurement.PageCursor
import bidvector.procurement.ParseFailureKind
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RejectionReason
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.TruncationCause
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * D-6F8-1 — 수집 use case 의 계약: 조회일 × 업종마다 「원문 저장 → canonicalize → 영속」을 항목별로 부르고
 * 조회일 × 업종마다 회계를 남긴다. 부분 실패는 다음 조회일로 넘어가고 쿼터 소진만 실행을 멈춘다.
 */
class CollectNoticesUseCaseTest {
    private val construction = sourceName("construction")
    private val service = sourceName("service")
    private val day1 = LocalDate.of(2026, 9, 1)
    private val day2 = LocalDate.of(2026, 9, 2)

    @Test
    fun `조회일 × 업종마다 정확히 한 번 회계를 남기고 항목마다 원문 저장과 영속을 한 번씩 부른다`() {
        val fixture = CollectionFixture()
        val a = ScriptedSource { date, _ -> batchOf(listOf(observation("A-$date-1"), observation("A-$date-2"))) }
        val b = ScriptedSource { date, _ -> batchOf(listOf(observation("B-$date-1"))) }

        val report =
            fixture.useCase.collect(
                rangeOf("2026-09-01", "2026-09-02"),
                listOf(CollectionSource(construction, a), CollectionSource(service, b)),
            )

        report.slots.map { it.slot } shouldContainExactly
            listOf(
                CollectionSlot(day1, construction),
                CollectionSlot(day1, service),
                CollectionSlot(day2, construction),
                CollectionSlot(day2, service),
            )
        fixture.runs.recorded.map { it.second.referenceDate.date } shouldContainExactly listOf(day1, day1, day2, day2)
        fixture.runs.recorded
            .map { it.second.source }
            .toSet() shouldBe setOf(SourceEndpoint.NOTICE_LIST)
        fixture.raw.appended.size shouldBe 6
        fixture.notices.persisted.size shouldBe 6
        report.halted shouldBe null
    }

    @Test
    fun `영속되는 명령은 canonicalize 의 결과 그대로다 — 공고명 슬롯까지 계약 경유로 채워진다`() {
        val fixture = CollectionFixture()
        val source = ScriptedSource { _, _ -> batchOf(listOf(observation("N-1", title = "  청사 신축 공사 "))) }

        fixture.useCase.collect(rangeOf("2026-09-01", "2026-09-01"), listOf(CollectionSource(construction, source)))

        val (command, key) = fixture.notices.persisted.single()
        command.title shouldBe NoticeTitle.of("청사 신축 공사")
        command.raw shouldBe fixture.raw.appended.single()
        key.value shouldBe "raw-1"
    }

    @Test
    fun `회계 등식 — 수신 = 정규화 + 중복 + 탈락, 사유별 합 = 탈락 — 소스 탈락과 정규화 탈락을 함께 센다`() {
        val fixture = CollectionFixture()
        val badDate = observation("BAD-DATE", extra = mapOf("bidClseDt" to "not-a-date"))
        val sourceDrop: Map<CollectionDropReason, Int> = mapOf(CollectionDropReason.CollectionMissingNoticeNumber to 1)
        val source =
            ScriptedSource { _, _ ->
                batchOf(
                    items = listOf(observation("OK-1"), observation("OK-2"), badDate),
                    accounting =
                        sourceAccounting(normalized = 3, duplicate = 2, dropped = 1, dropReasons = sourceDrop),
                )
            }

        val report =
            fixture.useCase.collect(rangeOf("2026-09-01", "2026-09-01"), listOf(CollectionSource(construction, source)))

        val accounting = report.slots.single().accounting
        accounting.received shouldBe 6
        accounting.normalized shouldBe 2
        accounting.duplicate shouldBe 2
        accounting.dropped shouldBe 2
        accounting.dropReasons shouldBe
            mapOf(
                CollectionDropReason.CollectionMissingNoticeNumber to 1,
                CollectionDropReason.CollectionParseFailure(ParseFailureKind.DATE_TIME) to 1,
            )
        accounting.received shouldBe accounting.normalized + accounting.duplicate + accounting.dropped
        accounting.dropReasons.values.sum() shouldBe accounting.dropped
        fixture.runs.recorded
            .single()
            .first shouldBe accounting
    }

    @Test
    fun `정규화에서 탈락한 항목도 원문은 먼저 저장된다 — 영속은 하지 않는다`() {
        val fixture = CollectionFixture()
        val badDate = observation("BAD-DATE", extra = mapOf("bidClseDt" to "not-a-date"))
        val source = ScriptedSource { _, _ -> batchOf(listOf(badDate)) }

        fixture.useCase.collect(rangeOf("2026-09-01", "2026-09-01"), listOf(CollectionSource(construction, source)))

        fixture.raw.appended shouldContainExactly listOf(badDate)
        fixture.notices.persisted shouldBe emptyList()
    }

    @Test
    fun `저장 결과가 Inserted·Updated 면 정규화, Unchanged 는 중복, Rejected 는 중복이면서 거부 건수로도 센다`() {
        val outcomes =
            mapOf(
                "N-INSERT" to PersistOutcome.Inserted,
                "N-UPDATE" to PersistOutcome.Updated(2),
                "N-SAME" to PersistOutcome.Unchanged,
                "N-REJECT" to PersistOutcome.Rejected(RejectionReason.NON_AUTHORITATIVE_OVERWRITE),
            )
        val fixture =
            CollectionFixture(
                notices = RecordingNoticeRepository(outcomeFor = { outcomes.getValue(it.id.number.value) }),
            )
        val source = ScriptedSource { _, _ -> batchOf(outcomes.keys.map { observation(it) }) }

        val slot =
            fixture.useCase
                .collect(rangeOf("2026-09-01", "2026-09-01"), listOf(CollectionSource(construction, source)))
                .slots
                .single()

        slot.accounting.normalized shouldBe 2
        slot.accounting.duplicate shouldBe 2
        slot.accounting.dropped shouldBe 0
        slot.writes shouldBe WriteTally(inserted = 1, updated = 1, unchanged = 1, rejected = 1)
    }

    @Test
    fun `한 조회일이 전송 실패로 잘려도 truncated 와 원인을 싣고 다음 조회일로 넘어간다`() {
        val fixture = CollectionFixture()
        val source =
            ScriptedSource { date, _ ->
                if (date == day1) {
                    batchOf(
                        emptyList(),
                        sourceAccounting(normalized = 0, truncationCause = TruncationCause.Timeout),
                    )
                } else {
                    batchOf(listOf(observation("OK-$date")))
                }
            }

        val report =
            fixture.useCase.collect(rangeOf("2026-09-01", "2026-09-02"), listOf(CollectionSource(construction, source)))

        report.slots.map { it.accounting.truncationCause } shouldContainExactly listOf(TruncationCause.Timeout, null)
        source.calls.map { it.first } shouldContainExactly listOf(day1, day2)
        report.halted shouldBe null
        fixture.runs.recorded.size shouldBe 2
    }

    @Test
    fun `쿼터 소진은 실행을 멈춘다 — 그 뒤 소스 호출은 0 이고 멈춘 자리와 남은 슬롯이 결과에 실린다`() {
        val fixture = CollectionFixture()
        val quotaBatch =
            batchOf(
                emptyList(),
                sourceAccounting(normalized = 0, truncationCause = TruncationCause.QuotaExhausted, quotaExceeded = 4),
            )
        val a = ScriptedSource { date, _ -> if (date == day1) quotaBatch else batchOf(listOf(observation("A-$date"))) }
        val b = ScriptedSource { date, _ -> batchOf(listOf(observation("B-$date"))) }

        val report =
            fixture.useCase.collect(
                rangeOf("2026-09-01", "2026-09-02"),
                listOf(CollectionSource(construction, a), CollectionSource(service, b)),
            )

        a.calls.size shouldBe 1
        b.calls.size shouldBe 0
        report.slots.map { it.slot } shouldContainExactly listOf(CollectionSlot(day1, construction))
        report.slots
            .single()
            .accounting.quotaExceeded shouldBe 4
        fixture.runs.recorded.size shouldBe 1
        report.halted shouldBe
            CollectionHalt(
                cause = TruncationCause.QuotaExhausted,
                at = CollectionSlot(day1, construction),
                notAttempted =
                    listOf(
                        CollectionSlot(day1, service),
                        CollectionSlot(day2, construction),
                        CollectionSlot(day2, service),
                    ),
            )
    }

    @Test
    fun `쿼터가 아닌 모든 절단 원인은 실행을 멈추지 않는다 — 원인마다 다음 슬롯을 부른다`() {
        val nonQuota =
            listOf(
                TruncationCause.RepeatedPage,
                TruncationCause.Timeout,
                TruncationCause.TransportFailure,
                TruncationCause.ServerError,
                TruncationCause.NotRetryable,
                TruncationCause.InputError,
                TruncationCause.Unclassified,
                TruncationCause.StructureFailure,
                TruncationCause.SelfThrottled,
            )
        nonQuota.forEach { cause ->
            val source =
                ScriptedSource { _, _ ->
                    batchOf(emptyList(), sourceAccounting(normalized = 0, truncationCause = cause))
                }

            val report =
                CollectionFixture().useCase.collect(
                    rangeOf("2026-09-01", "2026-09-02"),
                    listOf(CollectionSource(construction, source)),
                )

            source.calls.size shouldBe 2
            report.halted shouldBe null
        }
    }

    @Test
    fun `페이지 상한으로 잘렸으면 같은 조회일을 커서로 이어 읽고 회계를 합산한다`() {
        val fixture = CollectionFixture()
        val source =
            ScriptedSource { _, cursor ->
                if (cursor == null) {
                    batchOf(
                        listOf(observation("P1-1"), observation("P1-2")),
                        sourceAccounting(normalized = 2, pagesFetched = 50, truncationCause = TruncationCause.MaxPages),
                        next = PageCursor("51"),
                    )
                } else {
                    batchOf(listOf(observation("P2-1")), sourceAccounting(normalized = 1, pagesFetched = 3))
                }
            }

        val report =
            fixture.useCase.collect(rangeOf("2026-09-01", "2026-09-01"), listOf(CollectionSource(construction, source)))

        source.calls.map { it.second } shouldContainExactly listOf(null, PageCursor("51"))
        val accounting = report.slots.single().accounting
        accounting.received shouldBe 3
        accounting.normalized shouldBe 3
        accounting.pagesFetched shouldBe 53
        accounting.truncated shouldBe false
        fixture.runs.recorded.size shouldBe 1
    }

    @Test
    fun `이어 읽기가 진전이 없으면(같은 커서·커서 없음) 멈추고 절단으로 기록한다 — 무한 호출 방지`() {
        val sameCursor =
            ScriptedSource { _, _ ->
                batchOf(
                    listOf(observation("LOOP")),
                    sourceAccounting(normalized = 1, truncationCause = TruncationCause.MaxPages),
                    next = PageCursor("2"),
                )
            }
        val noCursor =
            ScriptedSource { _, _ ->
                batchOf(emptyList(), sourceAccounting(normalized = 0, truncationCause = TruncationCause.MaxPages))
            }

        val stalled =
            CollectionFixture().useCase.collect(
                rangeOf("2026-09-01", "2026-09-01"),
                listOf(CollectionSource(construction, sameCursor)),
            )
        val cursorless =
            CollectionFixture().useCase.collect(
                rangeOf("2026-09-01", "2026-09-01"),
                listOf(CollectionSource(construction, noCursor)),
            )

        sameCursor.calls.size shouldBe 2
        stalled.slots
            .single()
            .accounting.truncationCause shouldBe TruncationCause.MaxPages
        noCursor.calls.size shouldBe 1
        cursorless.slots
            .single()
            .accounting.truncated shouldBe true
    }

    @Test
    fun `저장소 장애는 삼키지 않고 전파한다 — 앞서 끝난 슬롯의 회계는 이미 남아 있다`() {
        val fixture =
            CollectionFixture(
                notices =
                    RecordingNoticeRepository(throwOn = {
                        it.id.number.value
                            .startsWith("BOOM")
                    }),
            )
        val source =
            ScriptedSource { date, _ ->
                batchOf(listOf(observation(if (date == day1) "OK-1" else "BOOM-1")))
            }

        shouldThrow<IllegalStateException> {
            fixture.useCase.collect(rangeOf("2026-09-01", "2026-09-02"), listOf(CollectionSource(construction, source)))
        }

        fixture.runs.recorded.map { it.second.referenceDate.date } shouldContainExactly listOf(day1)
    }

    @Test
    fun `슬롯이 끝날 때마다 진행 콜백이 순서대로 불린다`() {
        val fixture = CollectionFixture()
        val source = ScriptedSource { date, _ -> batchOf(listOf(observation("P-$date"))) }
        val seen = mutableListOf<CollectionSlot>()

        val report =
            fixture.useCase.collect(
                rangeOf("2026-09-01", "2026-09-02"),
                listOf(CollectionSource(construction, source)),
            ) { seen += it.slot }

        seen shouldContainExactly report.slots.map { it.slot }
    }

    @Test
    fun `같은 업종 이름을 둘 이상 받으면 거부한다 — 슬롯이 모호해진다`() {
        val source = ScriptedSource { _, _ -> batchOf(emptyList()) }

        shouldThrow<IllegalArgumentException> {
            CollectionFixture().useCase.collect(
                rangeOf("2026-09-01", "2026-09-01"),
                listOf(CollectionSource(construction, source), CollectionSource(construction, source)),
            )
        }
    }

    @Test
    fun `NoticeCollected 를 직접 조립하지 않는다 — 영속 명령의 원문은 저장된 관측 그 객체다`() {
        val fixture = CollectionFixture()
        val observed = observation("SAME-OBJECT")
        val source = ScriptedSource { _, _ -> batchOf(listOf(observed)) }

        fixture.useCase.collect(rangeOf("2026-09-01", "2026-09-01"), listOf(CollectionSource(construction, source)))

        val command: NoticeCollected =
            fixture.notices.persisted
                .single()
                .first
        (command.raw === observed) shouldBe true
    }
}
