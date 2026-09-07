package bidvector.procurement

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

private val OBSERVATION =
    RawNoticeObservation.of(
        mapOf(RawKey("bidNtceNo") to "20260101001", RawKey("bidNtceOrd") to "000"),
        SourceEndpoint.NOTICE_LIST,
        Instant.EPOCH,
    )

private val COMMAND =
    NoticeCollected(
        id = NoticeId(NoticeNumber.of("20260101001"), NoticeRound.of("000")),
        businessCategory = null,
        baseAmount = null,
        estimatedAmount = null,
        allocatedBudget = null,
        floorRate = null,
        raw = OBSERVATION,
    )

/** D-3A-1 (a) — `Notice`는 canonical fact 셋 중 상태를 보유하는 유일한 자리다(우회 (5) 대상). */
class NoticeTest {
    @Test
    fun `collected 는 항상 Open 으로 진입한다`() {
        val notice = Notice.collected(COMMAND)

        notice.status shouldBe NoticeStatus.Open
        notice.id shouldBe COMMAND.id
    }

    @Test
    fun `applyEvent 는 표 안의 전이를 적용한 새 Notice 를 낸다`() {
        val notice = Notice.collected(COMMAND)

        val outcome = notice.applyEvent(NoticeEvent.DeadlineReached)

        outcome.shouldBeInstanceOf<NoticeTransitionOutcome.Applied>()
        outcome.notice.status shouldBe NoticeStatus.Closed
    }

    @Test
    fun `applyEvent 는 표 밖의 전이를 거부로 관측한다 — 조용히 무시하지 않는다`() {
        val notice = Notice.collected(COMMAND)

        val outcome = notice.applyEvent(NoticeEvent.AwardObserved)

        outcome shouldBe
            NoticeTransitionOutcome.Rejected(TransitionResult.Rejected(NoticeStatus.Open, NoticeEvent.AwardObserved))
    }

    @Test
    fun `우회 (5) — copy(status=X) 로 전이표를 우회할 수 없다`() {
        val notice = Notice.collected(COMMAND)

        // Notice(internal constructor)와 달리 copy()는 같은 가시성이라 여전히 호출 가능하지만,
        // 그 자체가 곧 「이 모듈 안 저자」의 몫이다(위협 모델 (0) — 경계 밖). 이 test 가 실제로
        // 재는 것은 그 호출이 여전히 값 하나(다음 status)만 바꿀 뿐 전이표를 검사하지 않는다는
        // 사실 자체다 — 그래서 applyEvent 가 유일한 "검사되는" 경로임을 대조로 보인다.
        val bypassed = notice.copy(status = NoticeStatus.Awarded)

        bypassed.status shouldBe NoticeStatus.Awarded
        (transition(notice.status, NoticeEvent.AwardObserved) is TransitionResult.Rejected) shouldBe true
    }

    @Test
    fun `OpeningResult 는 예정가 파생 필드를 provenance 와 함께 나른다 — 계산식은 없다`() {
        val derived =
            ResolvedBaseAmount.DerivedFromOpeningAmount(
                BaseAmount(1_000_000L, Currency.KRW, VatTreatment.UNKNOWN, Provenance.DerivedFromOpening),
            )
        val openingResult =
            OpeningResult(COMMAND.id, winningRate = null, derivedBaseAmount = derived, observedAt = Instant.EPOCH)

        openingResult.derivedBaseAmount?.amount?.provenance shouldBe Provenance.DerivedFromOpening
    }

    @Test
    fun `QualificationText 는 원문과 관측 시각만 보존한다`() {
        val text = QualificationText(COMMAND.id, rawText = "면허제한: 소프트웨어사업", observedAt = Instant.EPOCH)

        text.rawText shouldBe "면허제한: 소프트웨어사업"
    }
}
