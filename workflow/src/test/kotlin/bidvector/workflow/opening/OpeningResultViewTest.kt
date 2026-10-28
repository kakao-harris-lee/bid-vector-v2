package bidvector.workflow.opening

import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ObservedBidAmount
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.export
import bidvector.workflow.strategy.Clock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * 상태 아홉과 그 판정 근거(M7/7C⑤-a) — **mock framework 없이** 값과 fake port 로만 잰다.
 *
 * 여기서 잠기는 것: 「데이터 없음」·「결과 없음」·「미개찰」이 서로 다른 값이라는 것(우회 ④) ·
 * 미등재 진행구분 라벨이 조용히 개찰완료가 되지 않는다는 것(우회 ⑥) · 잠정 축에 상호를 담을
 * 자리가 없다는 것(우회 ②의 타입 층) · 마감 경계와 관측 우선순위(우회 ⑫).
 */
class OpeningResultViewTest {
    private val now = Instant.parse("2026-10-23T02:00:00Z")
    private val observedAt = Instant.parse("2026-10-22T23:00:00Z")
    private val id = NoticeId(NoticeNumber("20261001234"), NoticeRound("001"))

    private fun figures(
        participantCount: Int? = 7,
        actualOpeningAt: Instant? = Instant.parse("2026-10-22T05:00:00Z"),
        confirmedAward: AwardAmount? = null,
        provisionalRankOne: ProvisionalRankOne = ProvisionalRankOne.NotObserved,
    ) = OpeningResultFigures(
        participantCount = participantCount,
        actualOpeningAt = actualOpeningAt,
        confirmedAward = confirmedAward,
        provisionalRankOne = provisionalRankOne,
        observedAt = observedAt,
    )

    private fun view(
        facts: NoticeOpeningFacts,
        at: Instant = now,
    ): OpeningResultView = resolveOpeningResultView(facts, at, OPENING_PROGRESS_POLICY)

    private fun present(
        deadlineAt: Instant? = Instant.parse("2026-10-20T05:00:00Z"),
        canonical: CanonicalOpeningFacts? = null,
        openingAxisObserved: Boolean = false,
    ) = NoticeOpeningFacts.Present(deadlineAt, canonical, openingAxisObserved)

    @Test
    fun `공고 행이 없으면 공고 부재다 — 개찰 결과의 부재와 다른 값이다`() {
        view(NoticeOpeningFacts.NoticeAbsent) shouldBe OpeningResultView.NoticeAbsent
    }

    @Test
    fun `우회 ④ — 수집 전·승격 전·미개찰은 서로 다른 값이다`() {
        view(present(openingAxisObserved = false)) shouldBe OpeningResultView.NotCollected
        view(present(openingAxisObserved = true)) shouldBe OpeningResultView.ObservedNotPromoted
        view(present(deadlineAt = Instant.parse("2026-10-25T05:00:00Z"))) shouldBe OpeningResultView.NotOpenedYet
    }

    @Test
    fun `관측이 시계 추론보다 강하다 — 마감이 뒤라도 개찰 축 원문이 있으면 승격 전이다`() {
        view(
            present(deadlineAt = Instant.parse("2026-10-25T05:00:00Z"), openingAxisObserved = true),
        ) shouldBe OpeningResultView.ObservedNotPromoted
    }

    @Test
    fun `마감을 모르면 미개찰로 유도하지 않는다`() {
        view(present(deadlineAt = null)) shouldBe OpeningResultView.NotCollected
    }

    @Test
    fun `우회 ⑫ — 마감 경계에서 마감 시각에 이르면 미개찰이 아니다`() {
        val deadline = Instant.parse("2026-10-23T02:00:00Z")
        view(present(deadlineAt = deadline), at = deadline.minusMillis(1)) shouldBe OpeningResultView.NotOpenedYet
        view(present(deadlineAt = deadline), at = deadline) shouldBe OpeningResultView.NotCollected
    }

    @Test
    fun `등재 라벨 셋이 상태 셋으로 간다`() {
        progressOf("개찰완료") shouldBe OpeningProgress.OPENED
        progressOf("유찰") shouldBe OpeningProgress.FAILED_BID
        progressOf("재입찰") shouldBe OpeningProgress.REBID
    }

    /**
     * 출하 정책의 **version 과 등재 집합을 한 자리에서** 잠근다(cr r1 LOW-4 — 앞 판에서 `version` 은
     * 아무도 읽지 않는 칸이었다). 라벨을 더하거나 빼면서 version 을 그대로 두면 이 단언이 먼저
     * 깨진다 — evidence 의 정책 값 기록과 코드가 갈리는 것을 막는 자리다.
     */
    @Test
    fun `출하 정책은 version 과 등재 집합이 함께 고정된다`() {
        OPENING_PROGRESS_POLICY.version shouldBe "opening-progress-v1"
        OPENING_PROGRESS_POLICY.labels.keys shouldBe setOf("개찰완료", "유찰", "재입찰")
    }

    @Test
    fun `우회 ⑥ — 등재 밖 라벨은 개찰완료로 접히지 않고 판정 불가다`() {
        progressOf("개찰완료 ") shouldBe OpeningProgress.UNRECOGNIZED
        progressOf("개찰취소") shouldBe OpeningProgress.UNRECOGNIZED
        progressOf("") shouldBe OpeningProgress.UNRECOGNIZED
    }

    @Test
    fun `라벨 부재와 등재 밖은 다른 값이다`() {
        progressOf(null) shouldBe OpeningProgress.ABSENT
    }

    @Test
    fun `canonical 행이 있으면 마감이 뒤여도 관측값을 낸다 — 시계가 결과를 가리지 않는다`() {
        val resolved =
            view(
                present(
                    deadlineAt = Instant.parse("2026-10-25T05:00:00Z"),
                    canonical = CanonicalOpeningFacts("개찰완료", figures()),
                ),
            ).shouldBeInstanceOf<OpeningResultView.Observed>()
        resolved.progress shouldBe OpeningProgress.OPENED
        resolved.figures.participantCount shouldBe 7
    }

    @Test
    fun `잠정 축과 확정 축은 다른 칸이다 — 잠정만 있어도 확정 칸은 비어 있다`() {
        val resolved =
            view(
                present(
                    canonical =
                        CanonicalOpeningFacts(
                            progressLabel = "개찰완료",
                            figures =
                                figures(
                                    confirmedAward = null,
                                    provisionalRankOne =
                                        ProvisionalRankOne.Determined(
                                            bidAmount = ObservedBidAmount(1_000_000L, Currency.KRW),
                                            bidRate = Rate.ofFraction(BigDecimal("0.87654")),
                                            observedAt = observedAt,
                                        ),
                                ),
                        ),
                ),
            ).shouldBeInstanceOf<OpeningResultView.Observed>()
        resolved.figures.confirmedAward shouldBe null
        resolved.figures.provisionalRankOne
            .shouldBeInstanceOf<ProvisionalRankOne.Determined>()
            .bidAmount
            ?.won shouldBe 1_000_000L
    }

    @Test
    fun `확정 금액은 basis·통화·과세·provenance 와 함께 나른다`() {
        val provenance = Provenance.Published(NoticeRound("001"))
        val award = AwardAmount(980_000L, Currency.KRW, provenance)
        val resolved =
            view(present(canonical = CanonicalOpeningFacts("개찰완료", figures(confirmedAward = award))))
                .shouldBeInstanceOf<OpeningResultView.Observed>()
        val exported = requireNotNull(resolved.figures.confirmedAward).export()
        exported.won shouldBe 980_000L
        exported.currency shouldBe Currency.KRW
        exported.basis shouldBe award.basis
        exported.vatTreatment shouldBe award.vatTreatment
        exported.provenance shouldBe provenance
    }

    @Test
    fun `순위 중복은 2건 이상일 때만 성립한다`() {
        shouldThrow<IllegalArgumentException> { ProvisionalRankOne.RankDuplicated(1, observedAt) }
    }

    @Test
    fun `참가업체수는 음수일 수 없다`() {
        shouldThrow<IllegalArgumentException> { figures(participantCount = -1) }
    }

    @Test
    fun `UNRECOGNIZED·ABSENT 를 라벨에 배정한 정책은 만들 수 없다`() {
        shouldThrow<IllegalArgumentException> {
            OpeningProgressPolicyData("x", mapOf("무엇" to OpeningProgress.UNRECOGNIZED))
        }
        shouldThrow<IllegalArgumentException> {
            OpeningProgressPolicyData("x", mapOf("무엇" to OpeningProgress.ABSENT))
        }
    }

    @Test
    fun `조회 하나는 포트의 사실에 주입 시계를 더해 상태를 낸다`() {
        val facts = NoticeOpeningFacts.Present(null, null, openingAxisObserved = true)
        val port =
            OpeningResultFactsPort { requested ->
                if (requested == id) facts else NoticeOpeningFacts.NoticeAbsent
            }
        val read = ReadOpeningResult(port, Clock { now }, OPENING_PROGRESS_POLICY)
        read.read(id) shouldBe OpeningResultView.ObservedNotPromoted
        read.read(NoticeId(NoticeNumber("20269999999"), NoticeRound("001"))) shouldBe OpeningResultView.NoticeAbsent
    }

    private fun progressOf(label: String?): OpeningProgress = OPENING_PROGRESS_POLICY.progressOf(label)
}
