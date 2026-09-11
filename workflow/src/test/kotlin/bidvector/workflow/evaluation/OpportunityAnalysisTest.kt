package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.decision.VerdictLadder
import bidvector.decision.priority.PRIORITY_POLICY
import bidvector.decision.priority.derive.DERIVATION_POLICY
import bidvector.procurement.Notice
import bidvector.qualification.OperatorLicenses
import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Rate
import bidvector.workflow.embedding.EmbeddingOutcome
import bidvector.workflow.embedding.EmbeddingUnavailableReason
import bidvector.workflow.embedding.TextKind
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.prediction.BidPredictionOutcome
import bidvector.workflow.prediction.UnmeasurableReason
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration

/**
 * `OpportunityAnalysis`(scope.md ①~④) 경로 전수(⑤) — 정상, guard 체인 각 단계 실패,
 * 임베딩 미가용 10 사유, release 불일치, 차원 불일치, offset 정책 밖, 예측 부재·미가용·
 * fitness/floorRate 이상 시 점수 성분 drop, 예산·호출 순서·무상태·use case 통합.
 */
class OpportunityAnalysisTest {
    private fun matchingEmbed(): FakeEmbedTextPort =
        FakeEmbedTextPort { request ->
            when (request.kind) {
                TextKind.NOTICE -> embedded(vector = normalizedEmbeddingVector(1.0f, 0.0f))
                TextKind.OPERATOR_PROFILE -> embedded(vector = normalizedEmbeddingVector(1.0f, 0.0f))
            }
        }

    private fun analysis(
        embed: FakeEmbedTextPort = matchingEmbed(),
        prediction: FakeBidPredictionPort = FakeBidPredictionPort { predicted() },
        profile: ConfigurableProfilePort = ConfigurableProfilePort(testProfile()),
        workload: FakeWorkloadPort = FakeWorkloadPort(),
        watchSubjects: FakeWatchSubjectPort = FakeWatchSubjectPort(),
        capacity: FakeCapacityPort = FakeCapacityPort(CapacitySnapshot(currentActiveBids = 2, maxActiveBids = 10)),
        clock: FixedClock = FixedClock(),
    ): OpportunityAnalysis = OpportunityAnalysis(embed, prediction, profile, workload, watchSubjects, capacity, clock)

    private fun analyzeNotice(
        instance: OpportunityAnalysis,
        notice: Notice = testNoticeWithMoney(),
    ): MlAnalysisOutcome = runBlocking { instance.analyze(notice, CorrelationId("corr-1")) }

    private fun MlAnalysisOutcome.shouldBeAnalyzed(): MlAnalysisOutcome.Analyzed {
        check(this is MlAnalysisOutcome.Analyzed) { "Analyzed 가 아니다: $this" }
        return this
    }

    @Test
    fun `정상 — Analyzed, probability null, matched = SemanticMatch 값`() {
        val analyzed = analyzeNotice(analysis()).shouldBeAnalyzed()

        analyzed.probabilityScore shouldBe null
        analyzed.matchedScore?.value?.compareTo(BigDecimal.ONE) shouldBe 0
    }

    @Test
    fun `subject Unavailable 이면 Unavailable ScoreNotProvided`() {
        val watchSubjects = FakeWatchSubjectPort { WatchSubjectOutcome.Unavailable }

        val outcome = analyzeNotice(analysis(watchSubjects = watchSubjects))

        outcome shouldBe MlAnalysisOutcome.Unavailable(MlUnavailableReason.ScoreNotProvided)
    }

    @Test
    fun `profile null 이면 Unavailable ScoreNotProvided`() {
        val outcome = analyzeNotice(analysis(profile = ConfigurableProfilePort(null)))

        outcome shouldBe MlAnalysisOutcome.Unavailable(MlUnavailableReason.ScoreNotProvided)
    }

    @Test
    fun `NOTICE 합성 Empty 면 Unavailable InvalidRequest`() {
        val watchSubjects = FakeWatchSubjectPort { WatchSubjectOutcome.Found(EMPTY_SUBJECT) }

        val outcome = analyzeNotice(analysis(watchSubjects = watchSubjects))

        outcome shouldBe MlAnalysisOutcome.Unavailable(MlUnavailableReason.InvalidRequest)
    }

    @Test
    fun `PROFILE 합성 Empty 면 Unavailable InvalidRequest`() {
        val emptyProfile =
            testProfile(businessTypes = emptySet(), licenses = OperatorLicenses.NotDeclared, regionTerms = emptyList())

        val outcome = analyzeNotice(analysis(profile = ConfigurableProfilePort(emptyProfile)))

        outcome shouldBe MlAnalysisOutcome.Unavailable(MlUnavailableReason.InvalidRequest)
    }

    @Test
    fun `embed Unavailable 10 사유가 같은 이름으로 옮겨진다`() {
        val reasons =
            listOf(
                EmbeddingUnavailableReason.CircuitOpen to MlUnavailableReason.CircuitOpen,
                EmbeddingUnavailableReason.DeadlineExceeded to MlUnavailableReason.DeadlineExceeded,
                EmbeddingUnavailableReason.RetryBudgetExhausted to MlUnavailableReason.RetryBudgetExhausted,
                EmbeddingUnavailableReason.TransportFailed to MlUnavailableReason.TransportFailed,
                EmbeddingUnavailableReason.ReleaseMismatch to MlUnavailableReason.ReleaseMismatch,
                EmbeddingUnavailableReason.ContractViolation to MlUnavailableReason.ContractViolation,
                EmbeddingUnavailableReason.UnsupportedSchema to MlUnavailableReason.UnsupportedSchema,
                EmbeddingUnavailableReason.UnsupportedRelease to MlUnavailableReason.UnsupportedRelease,
                EmbeddingUnavailableReason.InvalidRequest to MlUnavailableReason.InvalidRequest,
                EmbeddingUnavailableReason.ModelNotReady to MlUnavailableReason.ModelNotReady,
            )
        reasons.forEach { (embeddingReason, expected) ->
            val embed = FakeEmbedTextPort { EmbeddingOutcome.Unavailable(embeddingReason) }

            val outcome = analyzeNotice(analysis(embed = embed))

            outcome shouldBe MlAnalysisOutcome.Unavailable(expected)
        }
    }

    @Test
    fun `notice profile release 불일치는 ReleaseMismatch`() {
        val embed =
            FakeEmbedTextPort { request ->
                when (request.kind) {
                    TextKind.NOTICE -> embedded(release = TEST_RELEASE)
                    TextKind.OPERATOR_PROFILE -> embedded(release = OTHER_RELEASE)
                }
            }

        val outcome = analyzeNotice(analysis(embed = embed))

        outcome shouldBe MlAnalysisOutcome.Unavailable(MlUnavailableReason.ReleaseMismatch)
    }

    @Test
    fun `차원 불일치는 ContractViolation`() {
        val embed =
            FakeEmbedTextPort { request ->
                when (request.kind) {
                    TextKind.NOTICE -> embedded(vector = normalizedEmbeddingVector(1.0f))
                    TextKind.OPERATOR_PROFILE -> embedded(vector = normalizedEmbeddingVector(1.0f, 0.0f))
                }
            }

        val outcome = analyzeNotice(analysis(embed = embed))

        outcome shouldBe MlAnalysisOutcome.Unavailable(MlUnavailableReason.ContractViolation)
    }

    @Test
    fun `categoryOffset 정책 범위 밖이면 InvalidRequest`() {
        // 출하 PriorityPolicyData.categoryOffsetMin..Max 는 [-0.20, 0.20] — 0.5는 밖.
        val opportunity =
            OPPORTUNITY_POLICY.entries
                .single()
                .second
                .copy(categoryOffset = BigDecimal("0.5"))
        val opportunityPolicyTable =
            EffectiveDatedPolicy(source = "test", entries = listOf(EffectiveFrom.Initial to opportunity))
        val instance =
            OpportunityAnalysis(
                embed = matchingEmbed(),
                prediction = FakeBidPredictionPort { predicted() },
                profile = ConfigurableProfilePort(testProfile()),
                workload = FakeWorkloadPort(),
                watchSubjects = FakeWatchSubjectPort(),
                capacity = FakeCapacityPort(CapacitySnapshot(2, 10)),
                clock = FixedClock(),
                opportunityPolicyTable = opportunityPolicyTable,
                derivationPolicyTable = DERIVATION_POLICY,
                priorityPolicyTable = PRIORITY_POLICY,
            )

        val outcome = analyzeNotice(instance)

        outcome shouldBe MlAnalysisOutcome.Unavailable(MlUnavailableReason.InvalidRequest)
    }

    @Test
    fun `baseAmount null 이면 두 성분 Absent 이지만 Analyzed 유지`() {
        val notice = testNotice(number = "20260101003")

        analyzeNotice(analysis(), notice).shouldBeAnalyzed()
    }

    @Test
    fun `예측 Unavailable(DeadlineExceeded) 여도 Analyzed 유지 — 두 성분만 drop`() {
        val prediction =
            FakeBidPredictionPort { BidPredictionOutcome.Unavailable(MlUnavailableReason.DeadlineExceeded) }

        analyzeNotice(analysis(prediction = prediction)).shouldBeAnalyzed()
    }

    @Test
    fun `예측 Unmeasurable 이어도 Analyzed 유지`() {
        val prediction =
            FakeBidPredictionPort { BidPredictionOutcome.Unmeasurable(UnmeasurableReason.InsufficientSamples) }

        analyzeNotice(analysis(prediction = prediction)).shouldBeAnalyzed()
    }

    @Test
    fun `floorRate 1 초과면 margin 만 drop 되고 Analyzed 유지`() {
        val floorRate =
            FloorRate(Rate.ofFraction(BigDecimal("1.2")), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))
        val notice = testNoticeWithMoney(floorRate = floorRate)

        analyzeNotice(analysis(), notice).shouldBeAnalyzed()
    }

    @Test
    fun `fitness 범위 밖이면 budgetCapture·expectedMargin 둘 다 drop 되지만 Analyzed 유지`() {
        val prediction = FakeBidPredictionPort { predicted(fitness = BigDecimal("1.5")) }

        analyzeNotice(analysis(prediction = prediction)).shouldBeAnalyzed()
    }

    @Test
    fun `deadlineAt 과거(음수 remaining) 여도 예외 없이 Analyzed`() {
        val clock = FixedClock()
        val notice = testNoticeWithMoney(deadlineAt = clock.instant.minusSeconds(3600))

        analyzeNotice(analysis(clock = clock), notice).shouldBeAnalyzed()
    }

    @Test
    fun `CapacitySnapshot max 0 이어도 0 나눗셈 없이 Analyzed`() {
        val capacity = FakeCapacityPort(CapacitySnapshot(currentActiveBids = 0, maxActiveBids = 0))

        analyzeNotice(analysis(capacity = capacity)).shouldBeAnalyzed()
    }

    @Test
    fun `예산 — fake 가 받은 CallBudget remaining 이 정책 값과 같다`() {
        val embed = matchingEmbed()
        val prediction = FakeBidPredictionPort { predicted() }

        analyzeNotice(analysis(embed = embed, prediction = prediction))

        embed.budgetsSeen.forEach { it.remaining shouldBe Duration.ofSeconds(2) }
        prediction.budgetsSeen.forEach { it.remaining shouldBe Duration.ofSeconds(3) }
    }

    @Test
    fun `호출 순서 횟수 — embed 는 notice profile 순으로 2회, predict 는 최대 1회`() {
        val embed = matchingEmbed()
        val prediction = FakeBidPredictionPort { predicted() }

        analyzeNotice(analysis(embed = embed, prediction = prediction))

        embed.requestsSeen.size shouldBe 2
        embed.requestsSeen[0].kind shouldBe TextKind.NOTICE
        embed.requestsSeen[1].kind shouldBe TextKind.OPERATOR_PROFILE
        prediction.callCount shouldBe 1
    }

    @Test
    fun `두 번째 embed 호출도 새 예산을 받는다 — 합산 예산이 아니다`() {
        val embed = matchingEmbed()

        analyzeNotice(analysis(embed = embed))

        embed.budgetsSeen.size shouldBe 2
        embed.budgetsSeen[0] shouldBe embed.budgetsSeen[1]
    }

    @Test
    fun `같은 입력 두 번은 같은 결과 — 무상태`() {
        val instance = analysis()
        val notice = testNoticeWithMoney(number = "20260101004")

        val first = analyzeNotice(instance, notice)
        val second = analyzeNotice(instance, notice)

        first shouldBe second
    }

    @Test
    fun `use case 통합 — EvaluateCandidatesUseCase + 조합기 + fake 로 사다리 판정까지`() {
        val strategy = testStrategy()
        val strategyRepository = FakeStrategyRepository(strategy)
        val notice = testNoticeWithMoney(number = "20260101005")
        val candidateSource = FakeCandidateSource(listOf(notice))
        val watchSubjects = FakeWatchSubjectPort { WatchSubjectOutcome.Found(MATCHING_SUBJECT) }
        val licenseGate = FakeLicenseGatePort()
        val mlAnalysis = analysis(watchSubjects = watchSubjects)
        val capacity = FakeCapacityPort(CapacitySnapshot(currentActiveBids = 2, maxActiveBids = 10))
        val notifications = FakeNotificationRequestPort()
        val correlationIds = SequentialCorrelationIdFactory()

        val useCase =
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
                judge = VerdictLadder::judge,
            )

        val evaluations = runBlocking { useCase.evaluate() }

        val reached = evaluations.single() as CandidateEvaluation.Reached
        reached.noticeId shouldBe notice.id
    }
}
