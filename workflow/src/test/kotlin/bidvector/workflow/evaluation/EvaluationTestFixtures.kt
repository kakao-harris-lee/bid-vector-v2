package bidvector.workflow.evaluation

import bidvector.decision.LadderInput
import bidvector.decision.UnitScore
import bidvector.decision.Verdict
import bidvector.decision.VerdictLadder
import bidvector.decision.VerdictLadderPolicyData
import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeEvent
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeStatus
import bidvector.procurement.NoticeTransitionOutcome
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
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.strategy.AppliedStrategy
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * `EvaluateCandidatesUseCaseTest`·`EvaluateCandidatesUseCaseIsolationTest`(파일 크기
 * 한도 500줄, v2-지침서 §5)가 함께 쓰는 fake port·조립 헬퍼 — `internal`(같은 모듈 test
 * 소스셋, 이 패키지 파일 둘만 실제로 쓴다).
 */
internal val TEST_STRATEGY_POLICY_VERSION = PolicyVersion(EffectiveFrom.Initial, "test-strategy-policy")
internal val NOW: Instant = Instant.parse("2026-09-09T00:00:00Z")
internal val FUTURE_DEADLINE: Instant = NOW.plusSeconds(86_400)

internal fun strategyPolicy(): Resolution.Resolved<StrategyPolicyData> =
    Resolution.Resolved(
        StrategyPolicyData(
            matchScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            probabilityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            priorityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            budgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
        ),
        TEST_STRATEGY_POLICY_VERSION,
    )

/**
 * 감시 카테고리 하나 + 사다리 임계만 설정된 기본 전략 — 개별 test가 draft를 바꿔
 * 재구성한다. **감시 규칙을 반드시 하나 둔다**(수정 라운드 1 M-1 — `NoGate`가 이제
 * 통과가 아니므로, 다른 단계를 재는 test는 감시 게이트를 먼저 통과해야 한다).
 * `MATCHING_SUBJECT`가 이 카테고리와 일치해 `Passed`를 낸다.
 */
internal fun testStrategy(
    draft: StrategyDraft =
        StrategyDraft(
            focusCategories = listOf(DEFAULT_FOCUS_CATEGORY),
            bidNowThreshold = BigDecimal("0.7"),
            reviewThreshold = BigDecimal("0.45"),
        ),
): OperatorStrategy {
    val result = validate(draft, StrategyRevision(1), strategyPolicy())
    return (result as StrategyValidation.Valid).strategy
}

internal fun testNotice(
    number: String = "20260101001",
    status: NoticeStatus = NoticeStatus.Open,
    deadlineAt: Instant? = FUTURE_DEADLINE,
): Notice {
    val observation =
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to "000"),
            SourceEndpoint.NOTICE_LIST,
            NOW,
        )
    val notice =
        Notice.collected(
            NoticeCollected(
                id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000")),
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = deadlineAt,
                openingScheduledAt = null,
                raw = observation,
            ),
        )
    return when (status) {
        NoticeStatus.Open -> {
            notice
        }

        NoticeStatus.Closed -> {
            val outcome = notice.applyEvent(NoticeEvent.DeadlineReached)
            (outcome as NoticeTransitionOutcome.Applied).notice
        }

        else -> {
            error("test helper는 Open·Closed만 지원한다: $status")
        }
    }
}

internal class FixedClock(
    val instant: Instant = NOW,
) : Clock {
    override fun now(): Instant = instant
}

internal class FakeStrategyRepository(
    var strategy: OperatorStrategy,
) : StrategyRepository {
    var loadCount = 0

    override fun load(): OperatorStrategy {
        loadCount += 1
        return strategy
    }

    override fun save(applied: AppliedStrategy) {
        error("이 use case는 전략을 저장하지 않는다")
    }
}

internal class FakeCandidateSource(
    val notices: List<Notice>,
) : CandidateSourcePort {
    override fun openCandidates(): List<Notice> = notices
}

/** 규칙이 하나도 없는 감시 subject — `testStrategy()`의 기본 draft가 아닌, 감시 미설정 자체를 재는 test용. */
internal val EMPTY_SUBJECT =
    WatchSubject(
        categories = emptySet(),
        keywordText = KeywordScopeText(""),
        fullText = FullScopeText(""),
        baseAmount = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE),
    )

internal const val DEFAULT_FOCUS_CATEGORY = "SYN-CAT-001"

/** `testStrategy()`의 기본 `focusCategories`와 일치해 감시 게이트를 `Passed`로 통과하는 subject. */
internal val MATCHING_SUBJECT =
    WatchSubject(
        categories = setOf(bidvector.strategy.CategoryCode(DEFAULT_FOCUS_CATEGORY)),
        keywordText = KeywordScopeText(""),
        fullText = FullScopeText(""),
        baseAmount = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE),
    )

internal class FakeWatchSubjectPort(
    val outcomeFor: (Notice) -> WatchSubjectOutcome = { WatchSubjectOutcome.Found(MATCHING_SUBJECT) },
) : WatchSubjectPort {
    val calledFor = mutableListOf<NoticeId>()

    override fun subjectFor(notice: Notice): WatchSubjectOutcome {
        calledFor += notice.id
        return outcomeFor(notice)
    }
}

internal class FakeLicenseGatePort(
    val verdictFor: (Notice) -> LicenseVerdict = { LicenseVerdict.Eligible(emptySet()) },
) : LicenseGatePort {
    val calledFor = mutableListOf<NoticeId>()

    override fun verdictFor(notice: Notice): LicenseVerdict {
        calledFor += notice.id
        return verdictFor.invoke(notice)
    }
}

internal class FakeMlAnalysisPort(
    val outcomeFor: (Notice) -> MlAnalysisOutcome,
) : MlAnalysisPort {
    val callCountFor = mutableMapOf<NoticeId, AtomicInteger>()
    val correlationIdSeenFor = mutableMapOf<NoticeId, CorrelationId>()

    override fun analyze(
        notice: Notice,
        correlationId: CorrelationId,
    ): MlAnalysisOutcome {
        callCountFor.getOrPut(notice.id) { AtomicInteger(0) }.incrementAndGet()
        correlationIdSeenFor[notice.id] = correlationId
        return outcomeFor(notice)
    }
}

internal class FakeCapacityPort(
    val result: CapacitySnapshot,
) : CapacityPort {
    var callCount = 0

    override fun snapshot(): CapacitySnapshot {
        callCount += 1
        return result
    }
}

internal class FakeNotificationRequestPort : NotificationRequestPort {
    val requested = mutableListOf<NotificationRequest>()

    override fun request(notification: NotificationRequest): NotificationRequestOutcome {
        requested += notification
        return NotificationRequestOutcome.Requested
    }
}

internal class SequentialCorrelationIdFactory : CorrelationIdFactory {
    private var counter = 0

    override fun newId(): CorrelationId {
        counter += 1
        return CorrelationId("corr-$counter")
    }
}

/** 항상 확정 BidNow를 내는 ML 분석 — priority가 bidNowThreshold(0.7) 이상. */
internal fun bidNowAnalysis(): MlAnalysisOutcome =
    MlAnalysisOutcome.Analyzed(
        priorityScore = UnitScore(BigDecimal("0.9")),
        probabilityScore = null,
        matchedScore = null,
    )

/**
 * [EvaluateCandidatesUseCase]의 `judge` 위임을 감싸 호출 횟수를 센다(verifier r1 L-1
 * — `VerdictLadder`는 `object`라 fake로 대체할 수 없어 얇은 위임 뒤에서 센다).
 */
internal class CountingJudge {
    val callCountFor = mutableMapOf<NoticeId, AtomicInteger>()

    fun wrap(
        notice: Notice,
        delegate: (LadderInput, Resolution.Resolved<VerdictLadderPolicyData>) -> Verdict,
    ): (LadderInput, Resolution.Resolved<VerdictLadderPolicyData>) -> Verdict =
        { input, policy ->
            callCountFor.getOrPut(notice.id) { AtomicInteger(0) }.incrementAndGet()
            delegate(input, policy)
        }
}

internal fun useCase(
    strategyRepository: FakeStrategyRepository,
    candidateSource: FakeCandidateSource,
    watchSubjects: FakeWatchSubjectPort = FakeWatchSubjectPort(),
    licenseGate: FakeLicenseGatePort = FakeLicenseGatePort(),
    mlAnalysis: FakeMlAnalysisPort,
    capacity: FakeCapacityPort = FakeCapacityPort(CapacitySnapshot(currentActiveBids = 0, maxActiveBids = 10)),
    notifications: FakeNotificationRequestPort = FakeNotificationRequestPort(),
    correlationIds: SequentialCorrelationIdFactory = SequentialCorrelationIdFactory(),
    analysisBudget: Int? = null,
    judge: (LadderInput, Resolution.Resolved<VerdictLadderPolicyData>) -> Verdict = VerdictLadder::judge,
): EvaluateCandidatesUseCase =
    EvaluateCandidatesUseCase(
        strategies = strategyRepository,
        candidateSource = candidateSource,
        watchSubjects = watchSubjects,
        licenseGate = licenseGate,
        mlAnalysis = mlAnalysis,
        capacity = capacity,
        notifications = notifications,
        correlationIds = correlationIds,
        clock = FixedClock(),
        analysisBudget = analysisBudget,
        judge = judge,
    )
