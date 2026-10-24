package bidvector.adapters.bidding

import bidvector.adapters.persistence.FloorRateOriginCodec
import bidvector.bidding.BidKind
import bidvector.procurement.ProvenanceKind
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.workflow.bidding.BidActor
import bidvector.workflow.bidding.BidCommandResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * **초안이 얼린 공고 스냅샷이 실 DB 에 실제로 남는가**(계약 D-7B-3, 검토 B-1).
 *
 * 앞 판은 `notice` 의 출처 어휘(`ProvenanceKind` 의 UPPER_SNAKE)를 클래스 이름 표기(`Published` 등)로
 * 맞대어 **기초금액이 production 에서 늘 NULL** 이었다. 어느 test 도 기초금액이 있는 공고로 초안을 만들지
 * 않았고, 짝 CHECK 는 네 열이 함께 NULL 이면 만족하므로 조용했다. 여기서는 기초금액 · 하한율이 **있는**
 * 공고를 심고 초안의 스냅샷 열을 하나하나 단언한다 — 출처 두 갈래(공고 판 · 예산 키, 공고 값 · 법정표)를
 * 각각 지난다.
 *
 * 그리고 표의 출처 어휘 CHECK 가 **코덱의 어휘와 같은 집합**인지를 등식으로 본다 — 한쪽에만 어휘가
 * 늘면 붉다(셋째 어휘가 다시 생기는 길을 닫는다).
 */
class BidCaseSnapshotTest : BidCaseDbFixture() {
    @Test
    fun `기초금액과 하한율이 있는 공고 — 공고 판 출처 · 공고 값 하한율이 스냅샷 열에 그대로 남는다`() {
        val draft =
            draftColumnsAfterDraft(
                "snap-1",
                NoticeMoney(
                    baseWon = 123_456_789L,
                    baseProvenance = Provenance.Published(NoticeRound.of("000")),
                    floorFraction = BigDecimal("0.87745"),
                    floorOrigin = FloorRateOrigin.NoticeValue(NoticeRound.of("000")),
                ),
            )

        draft shouldBe
            mapOf(
                "snapshot_base_amount_won" to "123456789",
                "snapshot_base_amount_currency" to "KRW",
                "snapshot_base_amount_vat" to "INCLUSIVE",
                "snapshot_base_amount_provenance" to "PUBLISHED",
                "snapshot_base_amount_provenance_detail" to "000",
                "snapshot_floor_rate_fraction" to "0.87745",
                "snapshot_floor_rate_origin_kind" to "NOTICE_VALUE",
                "snapshot_floor_rate_origin_detail" to "000",
            )
    }

    /** 앞 판에서 `when` 에 자리 자체가 없던 출처(예산 키)와, 앞 판이 버리던 하한율 출처(법정표). */
    @Test
    fun `예산 키 출처 · 법정표 하한율도 detail 과 함께 남는다`() {
        val draft =
            draftColumnsAfterDraft(
                "snap-2",
                NoticeMoney(
                    baseWon = 50_000_000L,
                    baseProvenance = Provenance.FilledFromBudgetKey("budget-key-7"),
                    floorFraction = BigDecimal("0.8"),
                    floorOrigin = FloorRateOrigin.StatutoryTable(EffectiveFrom.Initial),
                ),
            )

        draft.getValue("snapshot_base_amount_provenance") shouldBe "FILLED_FROM_BUDGET_KEY"
        draft.getValue("snapshot_base_amount_provenance_detail") shouldBe "budget-key-7"
        draft.getValue("snapshot_floor_rate_origin_kind") shouldBe "STATUTORY_TABLE"
        draft.getValue("snapshot_floor_rate_origin_detail") shouldBe "INITIAL"
    }

    @Test
    fun `기초금액 출처 CHECK 어휘는 ProvenanceKind 전수와 같다`() {
        checkVocabulary("bid_draft_snapshot_base_amount_provenance_check") shouldBe
            ProvenanceKind.entries.map { it.name }.toSet()
    }

    @Test
    fun `하한율 출처 CHECK 어휘는 코덱의 두 어휘와 같다`() {
        checkVocabulary("bid_draft_snapshot_floor_rate_origin_kind_check") shouldBe
            setOf(FloorRateOriginCodec.NOTICE_VALUE_KIND, FloorRateOriginCodec.STATUTORY_TABLE_KIND)
    }

    private fun draftColumnsAfterDraft(
        suffix: String,
        money: NoticeMoney,
    ): Map<String, String?> {
        val mine = account("snap-$suffix")
        val key = notice(suffix)
        seedNotice(key, now.plusSeconds(DEADLINE_AHEAD_SECONDS), money)
        val workflow = workflow()
        val opened = workflow.open(mine, key, BidKind.REAL)
        workflow
            .draft(mine, opened.view.case.id, amount(1_000_000L), now, BidActor.User(mine))
            .shouldBeInstanceOf<BidCommandResult.Processed>()
        return dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT ${SNAPSHOT_COLUMNS.joinToString(", ")} FROM bid_draft WHERE case_id = ?",
                ).use { statement ->
                    statement.setLong(1, opened.view.case.id.value)
                    statement.executeQuery().use { rs ->
                        rs.next()
                        SNAPSHOT_COLUMNS.associateWith { column ->
                            if (column.endsWith("_fraction")) {
                                rs.getBigDecimal(column)?.stripTrailingZeros()?.toPlainString()
                            } else {
                                rs.getString(column)
                            }
                        }
                    }
                }
        }
    }

    private companion object {
        const val DEADLINE_AHEAD_SECONDS = 3_600L
        val SNAPSHOT_COLUMNS =
            listOf(
                "snapshot_base_amount_won",
                "snapshot_base_amount_currency",
                "snapshot_base_amount_vat",
                "snapshot_base_amount_provenance",
                "snapshot_base_amount_provenance_detail",
                "snapshot_floor_rate_fraction",
                "snapshot_floor_rate_origin_kind",
                "snapshot_floor_rate_origin_detail",
            )
    }
}
