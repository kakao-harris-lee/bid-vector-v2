package bidvector.adapters.persistence

import bidvector.procurement.AttemptOutcome
import bidvector.procurement.AxisConclusion
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NOTICE_NUMBER_RAW_KEY
import bidvector.procurement.NOTICE_ROUND_RAW_KEY
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ObservationKey
import bidvector.procurement.OpeningPromotionOutcome
import bidvector.procurement.OpeningRankOneOutcome
import bidvector.procurement.OpeningResult
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.promoteOpeningResult
import bidvector.procurement.promotionIsNew
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.export
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

private val WALK: Instant = Instant.parse("2026-10-20T01:00:00Z")

/** 표지 상호 — **값 수준** 단언에 쓴다(키 이름을 바꿔 피하는 길이 없다). */
private const val SENTINEL_BIDDER = "SENTINEL-OPENING-BIDDER"

/**
 * M7/7C⑤-0 — 실 DB 로 재는 승격 왕복. 원문을 **production 적재 경로**로 심고(손으로 짠 SQL 사본을
 * 두지 않는다), 읽기 어댑터로 묶고, 순수 사상으로 canonical 을 만들고, `JdbcOpeningResultRepository`
 * 로 쓴 뒤 다시 읽는다.
 */
class OpeningPromotionRepositoryTest : PersistenceTestSupport() {
    private val policy: KonepsCollectionPolicyData =
        (KONEPS_COLLECTION_POLICY.resolve(LocalDate.of(2026, 10, 23)) as Resolution.Resolved).value

    private fun id(number: String) = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))

    /** 표지 상호를 실은 개찰완료 투찰 행 하나 — 마스킹 축 test 둘이 공유한다. */
    private fun rankOneFields(): Map<String, String> = mapOf("prcbdrNm" to SENTINEL_BIDDER, "opengRank" to "1")

    /** 이 test 가 심은 축과 그 걷기 — 기본 결말의 출처다(심지 않은 축의 결말은 짓지 않는다). */
    private val seeded = mutableMapOf<NoticeId, MutableMap<SourceEndpoint, Instant>>()

    /**
     * 원장이 **심은 축을 그 걷기에서 성공으로 닫았다**고 본 결말. 상세 축은 결말이 있어야 쓰이므로
     * (D-7C5P-11) 이 전제가 없으면 왕복 test 가 사상·저장이 아니라 걷기 선택을 재게 된다. 걷기 선택을
     * 재는 test 는 `conclusions` 를 직접 준다.
     */
    private fun concludedFromSeeds(target: NoticeId): Map<SourceEndpoint, AxisConclusion> =
        seeded[target].orEmpty().mapValues { (_, walk) -> AxisConclusion(AttemptOutcome.Succeeded, walk) }

    private fun seed(
        id: NoticeId,
        endpoint: SourceEndpoint,
        fields: Map<String, String>,
        observedAt: Instant = WALK,
    ): ObservationKey {
        seeded.getOrPut(id) { mutableMapOf() }.merge(endpoint, observedAt) { a, b -> maxOf(a, b) }
        return appendRawObservation(
            RawNoticeObservation.of(
                fields =
                    fields.mapKeys { (name, _) -> RawKey(name) } +
                        mapOf(NOTICE_NUMBER_RAW_KEY to id.number.value, NOTICE_ROUND_RAW_KEY to id.round.value),
                sourceEndpoint = endpoint,
                observedAt = observedAt,
                sourceDivision = null,
            ),
        )
    }

    private fun readAndPromote(
        target: NoticeId,
        collectedSince: Instant = Instant.EPOCH,
        conclusions: Map<SourceEndpoint, AxisConclusion> = concludedFromSeeds(target),
    ): OpeningPromotionOutcome {
        val read = JdbcOpeningRawAxisSource(dataSource()).readOpeningAxes(collectedSince)
        val group = read.groups.single { it.noticeId == target }
        return promoteOpeningResult(group, conclusions, policy)
    }

    private fun persist(outcome: OpeningPromotionOutcome.Promotable) =
        JdbcOpeningResultRepository(dataSource()).persist(outcome.result, outcome.observationKey)

    private fun find(id: NoticeId): OpeningResult? = JdbcOpeningResultRepository(dataSource()).find(id)

    private fun rowStamp(id: NoticeId): Pair<Long, Timestamp> =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT revision, updated_at FROM opening_result WHERE notice_number = ? AND notice_round = ?",
                ).use { statement ->
                    statement.setString(1, id.number.value)
                    statement.setString(2, id.round.value)
                    statement.executeQuery().use { rows ->
                        rows.next() shouldBe true
                        rows.getLong("revision") to rows.getTimestamp("updated_at")
                    }
                }
        }

    private fun markMasked(key: ObservationKey) =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement("UPDATE raw_observation SET pii_masked_at = now() WHERE observation_key = ?")
                .use { statement ->
                    statement.setString(1, key.value)
                    statement.executeUpdate() shouldBe 1
                }
        }

    private fun tableRowCounts(): Map<String, Long> =
        dataSource().connection.use { connection ->
            val names =
                connection
                    .prepareStatement(
                        "SELECT table_name FROM information_schema.tables " +
                            "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
                    ).use { statement ->
                        statement.executeQuery().use { rows ->
                            val out = mutableListOf<String>()
                            while (rows.next()) out += rows.getString("table_name")
                            out
                        }
                    }
            names.associateWith { table ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT count(*) FROM \"$table\"").use { rows ->
                        rows.next()
                        rows.getLong(1)
                    }
                }
            }
        }

    // =========================================================================
    // 왕복 — 심은 원문이 canonical 로 서고 그대로 돌아온다
    // =========================================================================
    @Test
    fun `심은 개찰 원문이 canonical 로 서고 왕복한다`() {
        val notice = id("PROMO-DB-001")
        seed(
            notice,
            SourceEndpoint.RESERVE_PRICE_DETAIL,
            mapOf(
                "plnprc" to "1,234,000",
                "bssamt" to "1,200,000",
                "rlOpengDt" to "2026-10-19 14:00:00",
                "compnoRsrvtnPrceSno" to "3",
                "bsisPlnprc" to "1,210,000",
                "drwtYn" to "Y",
            ),
        )
        seed(notice, SourceEndpoint.OPENING_RESULT_LIST, mapOf("progrsDivCdNm" to "개찰완료", "prtcptCnum" to "7"))

        val outcome = readAndPromote(notice).shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        persist(outcome)
        val stored = requireNotNull(find(notice))

        stored.plannedPrice?.export()?.won shouldBe 1_234_000L
        stored.plannedPrice?.provenance shouldBe Provenance.Undeclared
        stored.baseAmount?.export()?.won shouldBe 1_200_000L
        stored.baseAmount?.provenance shouldBe Provenance.Published(notice.round)
        stored.participantCount shouldBe 7
        stored.progressDivision shouldBe "개찰완료"
        stored.actualOpeningAt shouldBe Instant.parse("2026-10-19T05:00:00Z")
        stored.reservePrices.map { it.sequenceNumber } shouldContainExactly listOf("3")
        stored.reservePrices.single().isDrawn shouldBe true
        stored.totalReservePriceCandidateCount.shouldBeNull()
    }

    @Test
    fun `승격한 값은 사상 결과와 같다`() {
        val notice = id("PROMO-DB-002")
        seed(notice, SourceEndpoint.OPENING_RESULT_LIST, mapOf("prtcptCnum" to "4"))

        val outcome = readAndPromote(notice).shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        persist(outcome)

        // 왕복 등식 — 저장이 값을 바꾸지 않는다(자식 행이 없는 축에서 가장 날카롭다).
        find(notice) shouldBe outcome.result
    }

    // =========================================================================
    // 멱등 — 같은 입력 두 번에 행이 움직이지 않는다
    // =========================================================================
    @Test
    fun `같은 입력을 두 번 승격해도 행이 움직이지 않는다`() {
        val notice = id("PROMO-DB-003")
        seed(notice, SourceEndpoint.OPENING_RESULT_LIST, mapOf("prtcptCnum" to "4"))
        val first = readAndPromote(notice).shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        persist(first)
        val before = rowStamp(notice)

        val second = readAndPromote(notice).shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        // 쓰기 자체를 하지 않는다 — 술어가 upsert 의 WHERE 와 같은 축이다.
        promotionIsNew(find(notice), second.result) shouldBe false

        rowStamp(notice) shouldBe before
    }

    // =========================================================================
    // 보존·개인정보 축
    // =========================================================================
    @Test
    fun `마스킹된 원문은 승격 대상에서 빠지고 수가 공시된다`() {
        val notice = id("PROMO-DB-004")
        val key = seed(notice, SourceEndpoint.OPENING_COMPLETE, rankOneFields())
        markMasked(key)

        val read = JdbcOpeningRawAxisSource(dataSource()).readOpeningAxes(Instant.EPOCH)

        read.groups.none { it.noticeId == notice } shouldBe true
        read.maskedRows shouldBe 1
    }

    @Test
    fun `마스킹은 이미 승격된 상호를 지우지 못한다`() {
        // **알려진 제한을 고정한다**(D-7C5P-3): canonical 사본은 그 행의 적재 시각 기준 파기에
        // 걸리고, 원문 마스킹이 소급해 지우지 않는다. 재승격도 지우지 않는다(부재는 덮지 않는다).
        val notice = id("PROMO-DB-005")
        val key = seed(notice, SourceEndpoint.OPENING_COMPLETE, rankOneFields())
        val outcome = readAndPromote(notice).shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        persist(outcome)
        find(notice)
            ?.openingRankOne
            .shouldBeInstanceOf<OpeningRankOneOutcome.Determined>()
            .bid
            .bidderName shouldBe SENTINEL_BIDDER

        markMasked(key)

        find(notice)
            ?.openingRankOne
            .shouldBeInstanceOf<OpeningRankOneOutcome.Determined>()
            .bid
            .bidderName shouldBe SENTINEL_BIDDER
    }

    @Test
    fun `잠정 1위만 심어도 확정 낙찰 세 칸은 canonical 에서 부재다`() {
        val notice = id("PROMO-DB-010")
        seed(notice, SourceEndpoint.OPENING_COMPLETE, rankOneFields() + mapOf("bidprcAmt" to "900,000"))

        val outcome = readAndPromote(notice).shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        persist(outcome)
        val stored = requireNotNull(find(notice))

        stored.openingRankOne.shouldBeInstanceOf<OpeningRankOneOutcome.Determined>()
        stored.finalAwardAmount.shouldBeNull()
        stored.finalAwardCompanyName.shouldBeNull()
        stored.winningRate.shouldBeNull()
    }

    @Test
    fun `보존 창보다 오래된 원문은 승격 대상에서 빠지고 수가 공시된다`() {
        val notice = id("PROMO-DB-006")
        seed(notice, SourceEndpoint.OPENING_RESULT_LIST, mapOf("prtcptCnum" to "4"))

        // 하한을 지금보다 뒤로 두면 **모든** 적재가 창 밖이다 — 창이 실제로 무는 것을 재는 자리다
        // (원문은 추가 전용이라 적재 시각을 거꾸로 돌릴 수 없다).
        val read =
            JdbcOpeningRawAxisSource(dataSource()).readOpeningAxes(Instant.now().plusSeconds(3600))

        read.groups.size shouldBe 0
        read.expiredRows shouldBe 1
    }

    // =========================================================================
    // 걷기 선택 · 확정 축 · 쓰는 표
    // =========================================================================
    @Test
    fun `원장이 가리킨 걷기의 행만 canonical 이 된다`() {
        val notice = id("PROMO-DB-007")
        val older = Instant.parse("2026-10-18T01:00:00Z")
        val newer = Instant.parse("2026-10-19T01:00:00Z")
        seed(notice, SourceEndpoint.RESERVE_PRICE_DETAIL, mapOf("plnprc" to "100"), observedAt = older)
        seed(notice, SourceEndpoint.RESERVE_PRICE_DETAIL, mapOf("plnprc" to "200"), observedAt = newer)

        val outcome =
            readAndPromote(
                notice,
                conclusions =
                    mapOf(SourceEndpoint.RESERVE_PRICE_DETAIL to AxisConclusion(AttemptOutcome.Succeeded, older)),
            ).shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        persist(outcome)

        find(notice)?.plannedPrice?.export()?.won shouldBe 100L
        find(notice)?.observedAt shouldBe older
    }

    @Test
    fun `낙찰 목록 원문이 들어오면 확정 축이 코드 변경 없이 canonical 에 선다`() {
        // D-7C5P-1 — 오늘 그 축은 걷히지 않는다. 원천이 생기면 계약 행만으로 따라온다.
        val notice = id("PROMO-DB-008")
        seed(
            notice,
            SourceEndpoint.OPENING_AWARD_LIST,
            mapOf("sucsfbidAmt" to "1,000,000", "bidwinnrNm" to SENTINEL_BIDDER, "sucsfbidRate" to "87.995"),
        )

        val outcome = readAndPromote(notice).shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        persist(outcome)
        val stored = requireNotNull(find(notice))

        stored.finalAwardAmount?.export()?.won shouldBe 1_000_000L
        stored.finalAwardCompanyName shouldBe SENTINEL_BIDDER
        stored.winningRate shouldBe Rate.ofPercent(BigDecimal("87.995"))
    }

    @Test
    fun `승격이 쓰는 표는 둘뿐이다`() {
        val notice = id("PROMO-DB-009")
        seed(
            notice,
            SourceEndpoint.RESERVE_PRICE_DETAIL,
            mapOf("plnprc" to "100", "compnoRsrvtnPrceSno" to "1", "bsisPlnprc" to "90"),
        )
        val before = tableRowCounts()

        val outcome = readAndPromote(notice).shouldBeInstanceOf<OpeningPromotionOutcome.Promotable>()
        persist(outcome)

        val changed = tableRowCounts().filter { (table, count) -> before.getValue(table) != count }.keys
        changed shouldBe setOf("opening_result", "opening_reserve_price")
    }
}
