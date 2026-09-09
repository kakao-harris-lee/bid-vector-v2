package bidvector.workflow.evaluation

import bidvector.decision.UnitScore
import bidvector.decision.Verdict
import bidvector.procurement.NoticeStatus
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.RequirementGroupId
import bidvector.strategy.StrategyDraft
import bidvector.strategy.WatchRuleId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * `EvaluateCandidatesUseCase`의 판정 경로(D-2·D-3~D-8·D-9·D-11·D-12) — 「판정에
 * 이르렀다」·「이르지 못했다」가 값으로 갈리는지, 기존 축(1E·1C·3A)을 그대로 싣는지를
 * 잰다. 후보 단위 격리·호출 횟수 불변식은 `EvaluateCandidatesUseCaseIsolationTest`
 * (파일 크기 한도, v2-지침서 §5).
 */
class EvaluateCandidatesUseCaseTest {
    // 결정 1 — 판정에 이른 것과 못 이른 것이 값으로 갈린다. BidNow 확정 시 알림 요청도 낳는다.
    @Test
    fun `판정에 이르면 Reached 로 남고 BidNow 면 알림 요청을 낳는다`() {
        val notice = testNotice()
        val notifications = FakeNotificationRequestPort()
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(testStrategy()),
                candidateSource = FakeCandidateSource(listOf(notice)),
                mlAnalysis = FakeMlAnalysisPort { bidNowAnalysis() },
                notifications = notifications,
            )

        val results = useCase.evaluate()

        results.size shouldBe 1
        val reached = results.single().shouldBeInstanceOf<CandidateEvaluation.Reached>()
        reached.verdict.shouldBeInstanceOf<Verdict.BidNow>()
        reached.noticeId shouldBe notice.id
        notifications.requested.size shouldBe 1
        notifications.requested.single().noticeId shouldBe notice.id
    }

    // legacy 실패 형태 ④ 뒤집기 — correlationId가 필수고 판정·알림 요청에 같은 값이 실린다.
    @Test
    fun `correlationId 는 필수 필드이고 판정과 알림 요청에 같은 값이 실린다`() {
        val notice = testNotice()
        val notifications = FakeNotificationRequestPort()
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(testStrategy()),
                candidateSource = FakeCandidateSource(listOf(notice)),
                mlAnalysis = FakeMlAnalysisPort { bidNowAnalysis() },
                notifications = notifications,
            )

        val results = useCase.evaluate()

        val reached = results.single() as CandidateEvaluation.Reached
        notifications.requested.single().correlationId shouldBe reached.correlationId
    }

    // D-2 — 공고가 입찰 가능 상태가 아니면(마감 지남) NotReached(NoticeLifecycle).
    @Test
    fun `마감 지난 공고는 NoticeLifecycle 에서 멈춘다`() {
        val notice = testNotice(status = NoticeStatus.Closed, deadlineAt = NOW.minusSeconds(60))
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(testStrategy()),
                candidateSource = FakeCandidateSource(listOf(notice)),
                mlAnalysis = FakeMlAnalysisPort { bidNowAnalysis() },
            )

        val result = useCase.evaluate().single() as CandidateEvaluation.NotReached

        result.stage shouldBe EvaluationStage.NoticeLifecycle
        result.reason.shouldBeInstanceOf<EvaluationDropReason.NoticeNotBiddable>()
    }

    // D-3~D-8 — 감시 필드 거절은 WatchVerdict.Rejected 를 그대로 싣는다(복제하지 않는다).
    @Test
    fun `감시 필드가 거절하면 WatchVerdict Rejected 를 그대로 싣는다`() {
        val notice = testNotice()
        val watchSubjects = FakeWatchSubjectPort { WatchSubjectOutcome.Found(EMPTY_SUBJECT) }
        val strategyWithFocus =
            testStrategy(
                StrategyDraft(
                    focusCategories = listOf("A001"),
                    bidNowThreshold = BigDecimal("0.7"),
                    reviewThreshold = BigDecimal("0.45"),
                ),
            )
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(strategyWithFocus),
                candidateSource = FakeCandidateSource(listOf(notice)),
                watchSubjects = watchSubjects,
                mlAnalysis = FakeMlAnalysisPort { bidNowAnalysis() },
            )

        val result = useCase.evaluate().single() as CandidateEvaluation.NotReached

        result.stage shouldBe EvaluationStage.WatchGate
        val reason = result.reason.shouldBeInstanceOf<EvaluationDropReason.WatchGateRejected>()
        reason.rejected.failed shouldBe setOf(WatchRuleId.FocusCategory)
    }

    // D-9 — 면허 미달은 LicenseVerdict.Ineligible 을 그대로 싣는다.
    @Test
    fun `면허가 미달이면 LicenseVerdict Ineligible 을 그대로 싣는다`() {
        val notice = testNotice()
        val ineligible = LicenseVerdict.Ineligible(mapOf(RequirementGroupId.Ungrouped to emptySet()))
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(testStrategy()),
                candidateSource = FakeCandidateSource(listOf(notice)),
                licenseGate = FakeLicenseGatePort { ineligible },
                mlAnalysis = FakeMlAnalysisPort { bidNowAnalysis() },
            )

        val result = useCase.evaluate().single() as CandidateEvaluation.NotReached

        result.stage shouldBe EvaluationStage.LicenseGate
        (result.reason as EvaluationDropReason.LicenseIneligible).verdict shouldBe ineligible
    }

    // 신설 — 임계 미설정 전략은 사다리를 돌릴 입력이 없다.
    @Test
    fun `사다리 임계가 미설정이면 ThresholdConfiguration 에서 멈춘다`() {
        val notice = testNotice()
        val unconfigured = testStrategy(StrategyDraft())
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(unconfigured),
                candidateSource = FakeCandidateSource(listOf(notice)),
                mlAnalysis = FakeMlAnalysisPort { bidNowAnalysis() },
            )

        val result = useCase.evaluate().single() as CandidateEvaluation.NotReached

        result.stage shouldBe EvaluationStage.ThresholdConfiguration
        result.reason shouldBe EvaluationDropReason.ActionThresholdsNotConfigured
    }

    // D-11 — 유사도 projection 미준비는 ML 부재와 다른 신호다.
    @Test
    fun `유사도 projection 미준비는 MlAvailability 에서 멈춘다`() {
        val notice = testNotice()
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(testStrategy()),
                candidateSource = FakeCandidateSource(listOf(notice)),
                mlAnalysis = FakeMlAnalysisPort { MlAnalysisOutcome.SimilarityProjectionNotReady },
            )

        val result = useCase.evaluate().single() as CandidateEvaluation.NotReached

        result.stage shouldBe EvaluationStage.MlAvailability
        result.reason shouldBe EvaluationDropReason.SimilarityProjectionNotReady
    }

    // D-12/D-13 — 적합도·확률 점수가 운영자 최소치 미만이면 사다리 전에 멈춘다.
    @Test
    fun `적합도가 운영자 최소치 미만이면 ScoreThreshold 에서 멈춘다`() {
        val notice = testNotice()
        val strategyWithMinMatch =
            testStrategy(
                StrategyDraft(
                    bidNowThreshold = BigDecimal("0.7"),
                    reviewThreshold = BigDecimal("0.45"),
                    minimumMatchScore = BigDecimal("0.5"),
                ),
            )
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(strategyWithMinMatch),
                candidateSource = FakeCandidateSource(listOf(notice)),
                mlAnalysis =
                    FakeMlAnalysisPort {
                        MlAnalysisOutcome.Analyzed(
                            priorityScore = UnitScore(BigDecimal("0.9")),
                            probabilityScore = null,
                            matchedScore = UnitScore(BigDecimal("0.2")),
                        )
                    },
            )

        val result = useCase.evaluate().single() as CandidateEvaluation.NotReached

        result.stage shouldBe EvaluationStage.ScoreThreshold
        val reason = result.reason.shouldBeInstanceOf<EvaluationDropReason.BelowMinimumMatchScore>()
        reason.threshold shouldBe BigDecimal("0.5")
        reason.actual shouldBe BigDecimal("0.2")
    }
}
