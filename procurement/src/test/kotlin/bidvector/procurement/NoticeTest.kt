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
        deadlineAt = null,
        openingScheduledAt = null,
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
    fun `같은 모듈 안에서는 copy(status=X) 가 열려 있다 — 경계는 모듈 밖뿐이다(우회 5, N-5 이름 정정)`() {
        val notice = Notice.collected(COMMAND)

        // 이 test 이름이 이전 판(verifier r2 N-5)과 반대로 읽힌다 — 실제로 이 호출은
        // *성공*한다. `Notice`(internal constructor + @ConsistentCopyVisibility)가 닫는
        // 것은 **모듈 경계**이지 파일 경계가 아니다(위협 모델 (0) — 같은 모듈 안의 저자는
        // 방어 대상 밖이다, scope.md 「방어하지 않는 것」). 우회 (5)의 실제 폐쇄(다른 모듈,
        // 예: adapters 에서 `notice.copy(status = …)` 호출)는 격리 worktree 에서 컴파일
        // 실패로 실측했다(evidence `commands.md`/`checklist.md` N-1·5 실측 기록) — 그 증거는
        // 컴파일 실패 자체라 이 gate 안의 test 로 상주하지 않는다. 이 test 가 남기는 것은
        // 대조군(같은 모듈에서는 검사 없이 값이 바뀐다)과, 표 자체는 여전히 그 전이를
        // 거부로 판정한다는 사실(`transition` 직접 호출)이다.
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
