package bidvector.adapters.opening

import bidvector.adapters.notice.ReadOnlyWitness
import bidvector.adapters.notice.beginReadOnlySearchTransaction
import bidvector.adapters.notice.witnessedDataSource
import bidvector.procurement.ObservedBidAmount
import bidvector.procurement.OpeningRankOneBid
import bidvector.procurement.OpeningRankOneOutcome
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.export
import bidvector.workflow.opening.NoticeOpeningFacts
import bidvector.workflow.opening.ProvisionalRankOne
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.SQLException
import java.time.Instant

/**
 * [JdbcOpeningResultView] 가 canonical 행에서 **무엇을 읽어 내는가**와 **읽기만 하는가**(실제
 * PostgreSQL). 상태 판정은 `OpeningResultViewTest` 가 DB 없이 전수로 잰다.
 *
 * 원문 존재·승격 축(「승격 전 ↔ 수집 전」)은 [JdbcOpeningAxisObservationTest] 가 잰다 — 그쪽 입력은
 * 상류 원문 표기와 마스킹 표지이고, 이 class 의 입력은 canonical 행이다.
 *
 * 여기서 실측되는 우회: ② 상호가 응답 경로에 오르지 않는다(읽은 열 집합 + 표지 글자) ·
 * ③ 잠정 관측이 확정 칸을 바꾸지 않는다 · ⑤ 조회가 쓰기를 쥐지 않는다(읽기 전용 트랜잭션 +
 * 세 표 무쓰기 지문, 음성 대조 포함).
 */
class JdbcOpeningResultViewTest : OpeningViewDbFixture() {
    @Test
    fun `공고 행이 없으면 공고 부재다 — 개찰 결과 질의도 돌지 않는다`() {
        view().lookup(id) shouldBe NoticeOpeningFacts.NoticeAbsent
    }

    @Test
    fun `canonical 행이 있으면 진행구분 라벨 원문과 관측값을 그대로 낸다`() {
        seedNotice()
        seedCanonical(canonicalOf(progressDivision = "유찰", participantCount = 0))

        val facts = present(view().lookup(id))
        val canonical = requireNotNull(facts.canonical)
        canonical.progressLabel shouldBe "유찰"
        canonical.figures.participantCount shouldBe 0
        canonical.figures.actualOpeningAt shouldBe Instant.parse("2026-10-21T06:00:00Z")
        canonical.figures.observedAt shouldBe T1
        facts.deadlineAt shouldBe DEADLINE
    }

    @Test
    fun `우회 ③ — 잠정 1위만 있으면 확정 칸은 비어 있다`() {
        seedNotice()
        seedCanonical(
            canonicalOf(
                finalAwardAmount = null,
                openingRankOne =
                    OpeningRankOneOutcome.Determined(
                        OpeningRankOneBid(
                            bidderName = SENTINEL_COMPANY,
                            bidAmount = ObservedBidAmount(912_000_000L, Currency.KRW),
                            bidRate = Rate.ofPercent(BigDecimal("87.995")),
                        ),
                        T1,
                    ),
            ),
        )

        val canonical = requireNotNull(present(view().lookup(id)).canonical)
        canonical.figures.confirmedAward shouldBe null
        val determined = canonical.figures.provisionalRankOne.shouldBeInstanceOf<ProvisionalRankOne.Determined>()
        determined.bidAmount shouldBe ObservedBidAmount(912_000_000L, Currency.KRW)
        determined.bidRate shouldBe Rate.ofPercent(BigDecimal("87.995"))
        determined.observedAt shouldBe T1
    }

    @Test
    fun `우회 ② — 잠정 1위 상호는 어느 칸에도 실리지 않는다`() {
        seedNotice()
        seedCanonical(
            canonicalOf(
                finalAwardAmount = AwardAmount(900_000_000L, Currency.KRW, Provenance.Published(id.round)),
                openingRankOne =
                    OpeningRankOneOutcome.Determined(OpeningRankOneBid(bidderName = SENTINEL_COMPANY), T1),
            ),
        )

        val facts = present(view().lookup(id))
        withClue("읽은 사실 전문에 표지 상호가 없다") {
            facts.toString() shouldNotContain SENTINEL_COMPANY
        }
    }

    @Test
    fun `확정 낙찰금액은 통화·provenance 와 함께 왕복된다`() {
        seedNotice()
        val provenance = Provenance.Published(NoticeRound.of("001"))
        seedCanonical(canonicalOf(finalAwardAmount = AwardAmount(880_000_000L, Currency.KRW, provenance)))

        val award = requireNotNull(requireNotNull(present(view().lookup(id)).canonical).figures.confirmedAward)
        val exported = award.export()
        exported.won shouldBe 880_000_000L
        exported.currency shouldBe Currency.KRW
        exported.provenance shouldBe provenance
    }

    @Test
    fun `순위 부재·중복은 그대로 낸다 — 투찰금액으로 순위를 재계산하지 않는다`() {
        seedNotice()
        seedCanonical(canonicalOf(openingRankOne = OpeningRankOneOutcome.RankMissing(T1)))
        requireNotNull(present(view().lookup(id)).canonical).figures.provisionalRankOne shouldBe
            ProvisionalRankOne.RankMissing(T1)

        seedCanonical(canonicalOf(openingRankOne = OpeningRankOneOutcome.RankDuplicated(3, T2), observedAt = T2), T2)
        requireNotNull(present(view().lookup(id)).canonical).figures.provisionalRankOne shouldBe
            ProvisionalRankOne.RankDuplicated(3, T2)
    }

    /** SET-02 acceptance — 잠정 1위 수집이 확정 낙찰 필드를 **변경하지 않는다**(V5 축 단위 UPSERT 의 귀결). */
    @Test
    fun `우회 ③ — 잠정 관측이 뒤에 와도 확정 낙찰금액은 그대로다`() {
        seedNotice()
        val award = AwardAmount(870_000_000L, Currency.KRW, Provenance.Published(NoticeRound.of("001")))
        seedCanonical(canonicalOf(finalAwardAmount = award))
        val before = requireNotNull(present(view().lookup(id)).canonical).figures.confirmedAward

        seedCanonical(
            canonicalOf(
                finalAwardAmount = null,
                openingRankOne =
                    OpeningRankOneOutcome.Determined(OpeningRankOneBid(bidderName = SENTINEL_COMPANY), T2),
                observedAt = T2,
            ),
            T2,
        )

        val after = requireNotNull(present(view().lookup(id)).canonical)
        after.figures.confirmedAward shouldBe before
        after.figures.provisionalRankOne.shouldBeInstanceOf<ProvisionalRankOne.Determined>()
    }

    @Test
    fun `우회 ⑤ — lookup 의 모든 문장이 읽기 전용 트랜잭션 안에서 돈다`() {
        seedNotice()
        rawKey(SourceEndpoint.OPENING_COMPLETE, T1)

        val witness = ReadOnlyWitness()
        JdbcOpeningResultView(witnessedDataSource(dataSource(), witness)).lookup(id)

        witness.queries().shouldNotBeEmpty()
        witness.statementsOutsideReadOnly() shouldBe emptyList()
    }

    @Test
    fun `우회 ⑤ — 조회가 세 표에 쓰기 흔적을 남기지 않는다`() {
        seedNotice()
        seedCanonical(canonicalOf())
        val before = writeFingerprint()

        view().lookup(id)
        view().lookup(other)

        writeFingerprint() shouldContainExactly before
    }

    /** 음성 대조 — 같은 지문이 쓰기를 **실제로 본다**(항진 단언이 아니다). */
    @Test
    fun `지문은 쓰기를 잡는다 — 읽기 전용이 아닌 연결의 갱신이 지문을 바꾼다`() {
        seedNotice()
        seedCanonical(canonicalOf())
        val before = writeFingerprint()

        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("UPDATE opening_result SET participant_count = participant_count")
            }
        }

        (writeFingerprint() == before) shouldBe false
    }

    @Test
    fun `읽기 전용 트랜잭션에서는 DB 가 쓰기를 거부한다`() {
        seedNotice()
        var rejected = false
        dataSource().connection.use { connection ->
            connection.beginReadOnlySearchTransaction()
            try {
                connection.createStatement().use { statement ->
                    statement.executeUpdate("DELETE FROM opening_result")
                }
            } catch (failure: SQLException) {
                rejected = failure.message?.contains("read-only") == true
            } finally {
                connection.rollback()
            }
        }
        rejected shouldBe true
    }

    @Test
    fun `질의가 실제로 읽는 열 집합에 업체 상호가 없다`() {
        seedNotice()
        seedCanonical(canonicalOf())

        val read =
            dataSource().connection.use { connection ->
                connection.prepareStatement(OpeningResultViewSql.SELECT_RESULT).use { statement ->
                    statement.setString(1, id.number.value)
                    statement.setString(2, id.round.value)
                    statement.executeQuery().use { rows ->
                        (1..rows.metaData.columnCount).map { rows.metaData.getColumnName(it) }
                    }
                }
            }

        read shouldContainExactly OpeningResultViewSql.RESULT_COLUMNS
        withClue("상호 두 열은 읽는 자리에서 먼저 막힌다") {
            read.none { it.contains("company_name") || it.contains("bidder_name") } shouldBe true
        }
    }

    /**
     * **두 slice 를 잇는 자리**(7C⑤-0 승격 → 7C⑤-a 조회). canonical 행을 test 가 직접 심지 않고
     * 승격 경로가 쓰므로, ⓐ 승격이 저장하는 진행구분 라벨과 이 조회의 정책 열거가 같은 어휘인지
     * ⓑ 승격이 저장한 투찰자 상호가 이 조회를 지나지 못하는지가 함께 측정된다. 두 slice 중 한쪽이
     * 어휘를 바꾸면 이 test 가 먼저 붉어진다.
     */
    @Test
    fun `승격 경로가 쓴 행을 이 조회가 개찰완료로 읽고 상호는 지나지 못한다`() {
        seedNotice()
        promoteViaProductionPath(progressLabel = "  개찰완료  ", bidderName = SENTINEL_COMPANY)

        val facts = present(view().lookup(id))
        val canonical = requireNotNull(facts.canonical)
        withClue("승격이 라벨을 trim 해서 저장하고 이 조회는 리터럴로 비교한다") {
            canonical.progressLabel shouldBe "개찰완료"
        }
        canonical.figures.participantCount shouldBe 7
        canonical.figures.provisionalRankOne.shouldBeInstanceOf<ProvisionalRankOne.Determined>()
        withClue("승격이 저장한 상호가 이 조회의 사실에 없다") {
            facts.toString() shouldNotContain SENTINEL_COMPANY
        }
        withClue("확정 낙찰 칸은 비어 있다 — 낙찰 목록 적재는 다른 slice 몫이다") {
            canonical.figures.confirmedAward shouldBe null
        }
    }
}
