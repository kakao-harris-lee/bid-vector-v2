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
        samples: FakeCompetitionSamplePort = FakeCompetitionSamplePort(),
        clock: FixedClock = FixedClock(),
    ): OpportunityAnalysis =
        OpportunityAnalysis(embed, prediction, profile, workload, watchSubjects, capacity, samples, clock)

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
    fun `notice 임베딩이 이미 Unavailable 이면 profile 은 호출하지 않는다(verifier r1 F-4)`() {
        val embed =
            FakeEmbedTextPort { request ->
                when (request.kind) {
                    TextKind.NOTICE -> EmbeddingOutcome.Unavailable(EmbeddingUnavailableReason.DeadlineExceeded)
                    TextKind.OPERATOR_PROFILE -> embedded()
                }
            }

        val outcome = analyzeNotice(analysis(embed = embed))

        outcome shouldBe MlAnalysisOutcome.Unavailable(MlUnavailableReason.DeadlineExceeded)
        embed.requestsSeen.size shouldBe 1
        embed.requestsSeen.single().kind shouldBe TextKind.NOTICE
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
                samples = FakeCompetitionSamplePort(),
                clock = FixedClock(),
                opportunityPolicyTable = opportunityPolicyTable,
                derivationPolicyTable = DERIVATION_POLICY,
                priorityPolicyTable = PRIORITY_POLICY,
            )

        val outcome = analyzeNotice(instance)

        outcome shouldBe MlAnalysisOutcome.Unavailable(MlUnavailableReason.InvalidRequest)
    }

    @Test
    fun `baseAmount null 이면 두 성분 Absent 이지만 Analyzed 유지 — evidence 는 NotPredicted(D-4D4-7)`() {
        val notice = testNotice(number = "20260101003")

        val analyzed = analyzeNotice(analysis(), notice).shouldBeAnalyzed()

        analyzed.evidence shouldBe PredictionEvidence.NotPredicted(MlUnavailableReason.ScoreNotProvided)
    }

    // verifier r1 V-2 — D-4D4-1 의 다섯 사유 매핑 중 Unavailable 가지: evidence 도 outcome.reason 을 그대로 옮긴다.
    @Test
    fun `예측 Unavailable(DeadlineExceeded) 여도 Analyzed 유지 — 두 성분만 drop, evidence 는 그 사유의 NotPredicted`() {
        val prediction =
            FakeBidPredictionPort { BidPredictionOutcome.Unavailable(MlUnavailableReason.DeadlineExceeded) }

        val analyzed = analyzeNotice(analysis(prediction = prediction)).shouldBeAnalyzed()

        analyzed.evidence shouldBe PredictionEvidence.NotPredicted(MlUnavailableReason.DeadlineExceeded)
    }

    // verifier r1 V-2 — Unmeasurable 가지: outcome 자체에 재사용할 MlUnavailableReason 이 없어
    // ScoreNotProvided 로 접는다(predictionFacts 의 absentPair(MlUnavailableReason.ScoreNotProvided) 배선).
    @Test
    fun `예측 Unmeasurable 이어도 Analyzed 유지 — evidence 는 NotPredicted(ScoreNotProvided)`() {
        val prediction =
            FakeBidPredictionPort { BidPredictionOutcome.Unmeasurable(UnmeasurableReason.InsufficientSamples) }

        val analyzed = analyzeNotice(analysis(prediction = prediction)).shouldBeAnalyzed()

        analyzed.evidence shouldBe PredictionEvidence.NotPredicted(MlUnavailableReason.ScoreNotProvided)
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

    // ---- M4/4B-7(D-4B7-9) — 경쟁 표본 조회·요청 축 ----

    @Test
    fun `businessCategory 있으면 categoryCode 를 실은 query 로 port 를 부른다`() {
        val category =
            bidvector.procurement.BusinessCategory(
                bidvector.procurement.CategoryCode.of("A01"),
                null,
            )
        val notice = testNoticeWithMoney(number = "20260101006", businessCategory = category)
        val samples = FakeCompetitionSamplePort()

        analyzeNotice(analysis(samples = samples), notice).shouldBeAnalyzed()

        val query = samples.queriesSeen.single()
        query.categoryCode shouldBe category.code
        query.excludeNoticeId shouldBe notice.id
    }

    @Test
    fun `businessCategory 없으면 port 를 부르지 않고 표본 0건으로 접는다`() {
        val notice = testNoticeWithMoney(number = "20260101007")
        val samples = FakeCompetitionSamplePort()

        val prediction = FakeBidPredictionPort { predicted() }
        analyzeNotice(analysis(prediction = prediction, samples = samples), notice).shouldBeAnalyzed()

        samples.queriesSeen shouldBe emptyList()
        prediction.requestsSeen.single().competitionSamples shouldBe emptyList()
    }

    @Test
    fun `Supplied 표본이 예측 요청에 그대로 실린다`() {
        val category =
            bidvector.procurement.BusinessCategory(
                bidvector.procurement.CategoryCode.of("A01"),
                null,
            )
        val notice = testNoticeWithMoney(number = "20260101008", businessCategory = category)
        val sample =
            bidvector.workflow.prediction.CompetitionSample(
                observedBidRate = Rate.ofFraction(BigDecimal("0.9")),
                baseAmount =
                    bidvector.sharedkernel.BaseAmount(
                        1_000_000L,
                        bidvector.sharedkernel.Currency.KRW,
                        bidvector.sharedkernel.VatTreatment.UNKNOWN,
                        bidvector.sharedkernel.Provenance.Undeclared,
                    ),
                baseAmountProvenanceLabel = bidvector.sharedkernel.BaseAmountProvenance.Clean,
                openedOn = java.time.LocalDate.of(2026, 1, 1),
            )
        val samples =
            FakeCompetitionSamplePort {
                CompetitionSampleSupply.Supplied(samples = listOf(sample), excluded = emptyMap())
            }
        val prediction = FakeBidPredictionPort { predicted() }

        analyzeNotice(analysis(prediction = prediction, samples = samples), notice).shouldBeAnalyzed()

        prediction.requestsSeen.single().competitionSamples shouldBe listOf(sample)
    }

    /**
     * M3/3H-2(D-3H2-1) — `predictionRequestFor`가 `analyze()` 전체 경로를 거쳐도
     * `notice.demandAgency`의 코드를 요청 `agencyId`에 그대로 싣는지 잰다(단위 test는
     * `PredictionFactsTest`가 같은 값을 직접 잰다 — 이 test는 배선 자체를 확인한다).
     * 표본 축(`CompetitionSample.agencyId`)은 이 test 층에서 관측 불가 — `FakeCompetitionSamplePort`가
     * `sampleOf`를 거치지 않고 fake 가 직접 `CompetitionSample`을 주므로(우회 (5) 관련),
     * 표본 축 조립은 `SampleEligibilityTest`(단위)가 잰다.
     */
    @Test
    fun `notice 의 수요기관 코드가 analyze() 경유로도 요청 agencyId 에 그대로 실린다(D-3H2-1)`() {
        val demandAgency =
            bidvector.procurement.Agency(
                bidvector.procurement.AgencyCode.of("1234567"),
                bidvector.procurement.AgencyName.of("수요기관"),
            )
        val notice = testNoticeWithMoney(number = "20260101011", demandAgency = demandAgency)
        val prediction = FakeBidPredictionPort { predicted() }

        analyzeNotice(analysis(prediction = prediction), notice).shouldBeAnalyzed()

        prediction.requestsSeen.single().agencyId shouldBe bidvector.workflow.prediction.AgencyId("1234567")
    }

    @Test
    fun `Unavailable 표본 공급은 Analyzed 를 유지하되 예측 성분만 Absent — evidence 는 supply reason 의 NotPredicted(D-4D4-7)`() {
        val category =
            bidvector.procurement.BusinessCategory(
                bidvector.procurement.CategoryCode.of("A01"),
                null,
            )
        val notice = testNoticeWithMoney(number = "20260101009", businessCategory = category)
        val samples =
            FakeCompetitionSamplePort { CompetitionSampleSupply.Unavailable(MlUnavailableReason.TransportFailed) }
        val prediction = FakeBidPredictionPort { predicted() }

        val analyzed = analyzeNotice(analysis(prediction = prediction, samples = samples), notice).shouldBeAnalyzed()

        // 표본 공급 Unavailable 은 predictionFacts 를 halt 시킨다 — prediction.predict 는 불리지 않는다
        // (absentPair 로 두 성분만 drop, analyze() 자체는 Analyzed 유지).
        prediction.callCount shouldBe 0
        analyzed.evidence shouldBe PredictionEvidence.NotPredicted(MlUnavailableReason.TransportFailed)
    }

    // ---- M4/4D-4(D-4D4-7) — Analyzed.evidence 가 Predicted.diagnostics·release·Supplied.excluded 와 등가 ----

    @Test
    fun `Supplied 예측 성공 경로 Analyzed evidence 는 Predicted diagnostics release Supplied excluded 를 옮긴다(D-4D4-7)`() {
        val category =
            bidvector.procurement.BusinessCategory(
                bidvector.procurement.CategoryCode.of("A01"),
                null,
            )
        val notice = testNoticeWithMoney(number = "20260101010", businessCategory = category)
        val excludedSamples = mapOf(SampleExclusionReason.BASE_AMOUNT_MISSING to 2)
        val samples =
            FakeCompetitionSamplePort {
                CompetitionSampleSupply.Supplied(samples = emptyList(), excluded = excludedSamples)
            }
        val predictedOutcome = predicted()
        val prediction = FakeBidPredictionPort { predictedOutcome }

        val analyzed = analyzeNotice(analysis(prediction = prediction, samples = samples), notice).shouldBeAnalyzed()

        val evidence = analyzed.evidence
        check(evidence is PredictionEvidence.Diagnosed) { "Diagnosed 가 아니다: $evidence" }
        evidence.diagnostics shouldBe predictedOutcome.diagnostics
        evidence.release shouldBe predictedOutcome.release
        evidence.excludedSamples shouldBe excludedSamples
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
