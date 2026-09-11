package bidvector.adapters.ml

import bidvector.decision.MlUnavailableReason
import bidvector.decision.ReviewReason
import bidvector.decision.Verdict
import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.qualification.LicenseVerdict
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.Resolution
import bidvector.strategy.BudgetBoundInclusivity
import bidvector.strategy.CategoryCode
import bidvector.strategy.FullScopeText
import bidvector.strategy.KeywordScopeText
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.ScoreRange
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.WatchSubject
import bidvector.strategy.validate
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CapacityPort
import bidvector.workflow.evaluation.CapacitySnapshot
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.EvaluateCandidatesUseCase
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.NotificationRequest
import bidvector.workflow.evaluation.NotificationRequestOutcome
import bidvector.workflow.evaluation.NotificationRequestPort
import bidvector.workflow.evaluation.WatchSubjectOutcome
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.strategy.AppliedStrategy
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-09-10T00:00:00Z")
private const val FOCUS_CATEGORY = "SYN-CAT-001"

private fun testStrategyPolicy(): Resolution.Resolved<StrategyPolicyData> =
    Resolution.Resolved(
        StrategyPolicyData(
            matchScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            probabilityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            priorityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            budgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
        ),
        PolicyVersion(EffectiveFrom.Initial, "test-strategy-policy"),
    )

private fun testStrategy(): OperatorStrategy {
    val draft =
        StrategyDraft(
            focusCategories = listOf(FOCUS_CATEGORY),
            bidNowThreshold = BigDecimal("0.7"),
            reviewThreshold = BigDecimal("0.45"),
        )
    val result = validate(draft, StrategyRevision(1), testStrategyPolicy())
    return (result as StrategyValidation.Valid).strategy
}

private fun testNotice(): Notice {
    val number = "20260101001"
    val observation =
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to "000"),
            SourceEndpoint.NOTICE_LIST,
            NOW,
        )
    return Notice.collected(
        NoticeCollected(
            id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000")),
            businessCategory = null,
            baseAmount = null,
            estimatedAmount = null,
            allocatedBudget = null,
            floorRate = null,
            deadlineAt = NOW.plusSeconds(86_400),
            openingScheduledAt = null,
            raw = observation,
        ),
    )
}

private val MATCHING_SUBJECT =
    WatchSubject(
        categories = setOf(CategoryCode(FOCUS_CATEGORY)),
        keywordText = KeywordScopeText(""),
        fullText = FullScopeText(""),
        baseAmount = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE),
    )

private class FixedClock : Clock {
    override fun now(): Instant = NOW
}

private class FakeStrategyRepository(
    val strategy: OperatorStrategy,
) : StrategyRepository {
    override fun load(): OperatorStrategy = strategy

    override fun save(applied: AppliedStrategy) {
        error("이 test 는 전략을 저장하지 않는다")
    }
}

private class FakeCandidateSource(
    val notices: List<Notice>,
) : CandidateSourcePort {
    override fun openCandidates(): List<Notice> = notices
}

private class FakeWatchSubjectPort : WatchSubjectPort {
    override fun subjectFor(notice: Notice): WatchSubjectOutcome = WatchSubjectOutcome.Found(MATCHING_SUBJECT)
}

private class FakeLicenseGatePort : LicenseGatePort {
    override fun verdictFor(notice: Notice): LicenseVerdict = LicenseVerdict.Eligible(emptySet())
}

private class FakeCapacityPort : CapacityPort {
    override fun snapshot(): CapacitySnapshot = CapacitySnapshot(currentActiveBids = 0, maxActiveBids = 10)
}

private class RecordingNotificationRequestPort : NotificationRequestPort {
    val requested = mutableListOf<NotificationRequest>()

    override fun request(notification: NotificationRequest): NotificationRequestOutcome {
        requested += notification
        return NotificationRequestOutcome.Requested
    }
}

private class SequentialCorrelationIdFactory : CorrelationIdFactory {
    private var counter = 0

    override fun newId(): CorrelationId {
        counter += 1
        return CorrelationId("corr-$counter")
    }
}

/**
 * scope.md ⑤, 설계 검토 (4) 우회 4 — [UnavailableMlAnalysis]가 test fake 뿐 아니라
 * **실 [EvaluateCandidatesUseCase]**를 통해 「fail-safe 실물」을 낸다는 증거다. 다른
 * port는 전부 통과시키는 fake(전략·감시·면허 게이트 정상)로 감싸 ML 분석 경로만
 * [UnavailableMlAnalysis]로 실 배선한다.
 */
class UnavailableMlAnalysisTest {
    @Test
    fun `UnavailableMlAnalysis 로 배선하면 Reached(Review(MlUnavailable(ScoreNotProvided))) 로 남고 알림은 0건이다`() {
        val notice = testNotice()
        val notifications = RecordingNotificationRequestPort()
        val useCase =
            EvaluateCandidatesUseCase(
                strategies = FakeStrategyRepository(testStrategy()),
                candidateSource = FakeCandidateSource(listOf(notice)),
                watchSubjects = FakeWatchSubjectPort(),
                licenseGate = FakeLicenseGatePort(),
                mlAnalysis = UnavailableMlAnalysis(),
                capacity = FakeCapacityPort(),
                notifications = notifications,
                correlationIds = SequentialCorrelationIdFactory(),
                clock = FixedClock(),
            )

        val result = runBlocking { useCase.evaluate() }.single() as CandidateEvaluation.Reached

        val verdict = result.verdict.shouldBeInstanceOf<Verdict.Review>()
        val reason = verdict.reasons.single().shouldBeInstanceOf<ReviewReason.MlUnavailable>()
        reason.reason shouldBe MlUnavailableReason.ScoreNotProvided
        notifications.requested.shouldBeEmpty()
    }
}
