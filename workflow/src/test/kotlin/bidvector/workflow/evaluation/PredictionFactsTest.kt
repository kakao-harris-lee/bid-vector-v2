package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.decision.ProvenanceRuleId
import bidvector.decision.UnitScore
import bidvector.decision.priority.PRIORITY_POLICY
import bidvector.decision.priority.ScoreFact
import bidvector.decision.priority.derive.DERIVATION_POLICY
import bidvector.procurement.OpeningResult
import bidvector.sharedkernel.AwardAmount
import bidvector.sharedkernel.BaseAmountProvenance
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.prediction.PredictionDiagnostics
import bidvector.workflow.prediction.SegmentSupport
import bidvector.workflow.prediction.Weight
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * verifier r1 F-1 — [predictedFacts]가 `MarginInputs.init`의 세 술어(recommendedRate·
 * predictedRate·floorRate 각 `fraction ≤ 1`)를 **호출 전에** 전부 판정하는지 직접 잰다.
 * `analyze()` 전체를 거치면 `MlAnalysisOutcome.Analyzed`가 budgetCapture·expectedMargin
 * 성분을 노출하지 않아 이 관문의 개별 성패를 볼 수 없다 — `internal` 함수를 같은 패키지
 * test 가 직접 부른다(init ≠ gate: 값 타입의 `require`는 마지막 안전판이지 이 관문이 아니다).
 */
class PredictionFactsTest {
    private val testCapacity = UnitScore(BigDecimal("0.5"))

    private val testPolicies: ResolvedPolicies by lazy {
        val version = PolicyVersion(EffectiveFrom.Initial, "test")
        ResolvedPolicies(
            opportunity = OPPORTUNITY_POLICY.entries.single().second,
            opportunityVersion = version,
            derivation = Resolution.Resolved(DERIVATION_POLICY.entries.single().second, version),
            priority = PRIORITY_POLICY.entries.single().second,
            // M4/4B-8(D-4B8-5) — 표본과 같은 SAMPLE_PROVENANCE_POLICY singleton.
            provenancePolicy = Resolution.Resolved(SAMPLE_PROVENANCE_POLICY.entries.single().second, version),
        )
    }

    @Test
    fun `candidates base 1 0 은 통과하고 1 0000001 은 두 성분 Absent(ContractViolation) 이며 예외가 없다`() {
        val notice = testNoticeWithMoney()
        val baseAmount = requireNotNull(notice.baseAmount).amount

        val atBoundary =
            predicted(
                conservative = Rate.ofFraction(BigDecimal("0.95")),
                base = Rate.ofFraction(BigDecimal("1.0")),
                aggressive = Rate.ofFraction(BigDecimal("1.05")),
            )
        val overBoundary =
            predicted(
                conservative = Rate.ofFraction(BigDecimal("1.0")),
                base = Rate.ofFraction(BigDecimal("1.0000001")),
                aggressive = Rate.ofFraction(BigDecimal("1.1")),
            )

        val at =
            shouldNotThrowAny { predictedFacts(atBoundary, baseAmount, notice, testPolicies, testCapacity, emptyMap()) }
        at.budgetCapture.shouldBePresent()
        at.expectedMargin.shouldBePresent()

        val over =
            shouldNotThrowAny {
                predictedFacts(overBoundary, baseAmount, notice, testPolicies, testCapacity, emptyMap())
            }
        over.budgetCapture shouldBe ScoreFact.Absent(MlUnavailableReason.ContractViolation)
        over.expectedMargin shouldBe ScoreFact.Absent(MlUnavailableReason.ContractViolation)
        // M4/4D-4(D-4D4-1) — 신뢰 못 할 응답은 NotPredicted다(진단이 있어도 Diagnosed가 아니다).
        over.evidence shouldBe PredictionEvidence.NotPredicted(MlUnavailableReason.ContractViolation)
    }

    @Test
    fun `floorRate 1 0 은 margin 통과, 1 0000001 은 margin 만 Absent(InvalidRequest) — budgetCapture 는 무관`() {
        val floorAtBoundary =
            FloorRate(Rate.ofFraction(BigDecimal("1.0")), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))
        val floorOverBoundary =
            FloorRate(Rate.ofFraction(BigDecimal("1.0000001")), FloorRateOrigin.NoticeValue(NoticeRound.of("000")))
        val noticeAtBoundary = testNoticeWithMoney(floorRate = floorAtBoundary)
        val noticeOverBoundary = testNoticeWithMoney(floorRate = floorOverBoundary)
        val baseAmount = requireNotNull(noticeAtBoundary.baseAmount).amount
        val validPrediction = predicted()

        val at =
            shouldNotThrowAny {
                predictedFacts(validPrediction, baseAmount, noticeAtBoundary, testPolicies, testCapacity, emptyMap())
            }
        at.budgetCapture.shouldBePresent()
        at.expectedMargin.shouldBePresent()

        val over =
            shouldNotThrowAny {
                predictedFacts(validPrediction, baseAmount, noticeOverBoundary, testPolicies, testCapacity, emptyMap())
            }
        over.budgetCapture.shouldBePresent()
        over.expectedMargin shouldBe ScoreFact.Absent(MlUnavailableReason.InvalidRequest)
    }

    // ---- M4/4D-3(scope.md D-4D3-3, 위협 모델 우회 (8)) — 진단은 사다리 점수를 바꾸지
    // 않는다. 진단만 다른 두 Predicted 가 같은 ScoreFact 쌍을 낸다. ----

    @Test
    fun `진단만 다른 두 Predicted 는 같은 ScoreFact 쌍을 낸다(우회 8)`() {
        val notice = testNoticeWithMoney()
        val baseAmount = requireNotNull(notice.baseAmount).amount
        val lowShrinkage =
            predicted(
                diagnostics =
                    PredictionDiagnostics(
                        trainingRowCount = 4000,
                        segmentSupport = SegmentSupport.Direct,
                        shrinkageWeight = Weight(BigDecimal("0.0500")),
                        excludedObservations = 1,
                        agencySampleCount = 40,
                        agencySampleBelowThreshold = false,
                    ),
            )
        val highShrinkage =
            predicted(
                diagnostics =
                    PredictionDiagnostics(
                        trainingRowCount = 0,
                        segmentSupport = SegmentSupport.Global,
                        shrinkageWeight = Weight(BigDecimal("0.9500")),
                        excludedObservations = 30,
                        agencySampleCount = 2,
                        agencySampleBelowThreshold = true,
                    ),
            )

        val fromLowShrinkage =
            predictedFacts(lowShrinkage, baseAmount, notice, testPolicies, testCapacity, emptyMap())
        val fromHighShrinkage =
            predictedFacts(highShrinkage, baseAmount, notice, testPolicies, testCapacity, emptyMap())

        // M4/4D-4(D-4D4-2) — 진단은 사다리 점수(ScoreFact 쌍)를 바꾸지 않는다. evidence는
        // 그 정의상 진단을 그대로 옮기므로 둘은 여기서 갈린다(아래가 그 사실을 직접 잰다).
        fromLowShrinkage.budgetCapture shouldBe fromHighShrinkage.budgetCapture
        fromLowShrinkage.expectedMargin shouldBe fromHighShrinkage.expectedMargin
        (fromLowShrinkage.evidence == fromHighShrinkage.evidence) shouldBe false
    }

    // ---- M4/4D-4(D-4D4-1·7) — evidence 캐리어가 진단·release·표본 제외 계수를 그대로 옮긴다. ----

    @Test
    fun `predictedFacts 는 Predicted 의 diagnostics release 와 호출자의 excludedSamples 를 Diagnosed 로 옮긴다(D-4D4-1·7)`() {
        val notice = testNoticeWithMoney()
        val baseAmount = requireNotNull(notice.baseAmount).amount
        val excludedSamples = mapOf(SampleExclusionReason.BASE_AMOUNT_MISSING to 3)
        val prediction = predicted()

        val components = predictedFacts(prediction, baseAmount, notice, testPolicies, testCapacity, excludedSamples)

        val evidence = components.evidence.shouldBeInstanceOfDiagnosed()
        evidence.diagnostics shouldBe prediction.diagnostics
        evidence.release shouldBe prediction.release
        evidence.excludedSamples shouldBe excludedSamples
    }

    private fun PredictionEvidence.shouldBeInstanceOfDiagnosed(): PredictionEvidence.Diagnosed {
        check(this is PredictionEvidence.Diagnosed) { "Diagnosed 가 아니다: $this" }
        return this
    }

    private fun ScoreFact<UnitScore>.shouldBePresent() {
        check(this is ScoreFact.Present) { "Present 가 아니다: $this" }
    }

    /**
     * verifier r2 N-2 — D-4B7-9는 표본 공급 실패 사유를 `ScoreNotProvided`로 뭉개지 않고
     * `supply.reason`을 그대로 옮긴다고 못 박는다. `absentPairForUnavailableSupply`가
     * 그 배선의 유일한 지점이다.
     */
    @Test
    fun `absentPairForUnavailableSupply 는 supply 의 사유를 그대로 옮긴다(D-4B7-9)`() {
        val supply = CompetitionSampleSupply.Unavailable(MlUnavailableReason.TransportFailed)

        val components = absentPairForUnavailableSupply(supply)

        components.budgetCapture shouldBe ScoreFact.Absent(MlUnavailableReason.TransportFailed)
        components.expectedMargin shouldBe ScoreFact.Absent(MlUnavailableReason.TransportFailed)
        // M4/4D-4(D-4D4-1) — 예측을 시도하지 않은 경로는 NotPredicted다.
        components.evidence shouldBe PredictionEvidence.NotPredicted(MlUnavailableReason.TransportFailed)
    }

    @Test
    fun `absentPairForUnavailableSupply 는 ScoreNotProvided 로 뭉개지 않는다 — 다른 사유와 구별된다`() {
        val supply = CompetitionSampleSupply.Unavailable(MlUnavailableReason.CircuitOpen)

        val components = absentPairForUnavailableSupply(supply)

        components.budgetCapture shouldBe ScoreFact.Absent(MlUnavailableReason.CircuitOpen)
        components.expectedMargin shouldBe ScoreFact.Absent(MlUnavailableReason.CircuitOpen)
        (components.budgetCapture == ScoreFact.Absent(MlUnavailableReason.ScoreNotProvided)) shouldBe false
        (components.evidence == PredictionEvidence.NotPredicted(MlUnavailableReason.ScoreNotProvided)) shouldBe false
    }

    // ---- M4/4B-8(D-4B8-1·2, OPEN-4B7-TARGET-LABEL 닫힘) — 대상 공고 라벨 규칙표.
    // `provenanceLabelFor`(SampleConversion.kt)를 `opening = null`로 직접 불러 잰다 — 4B-7
    // `SampleEligibilityTest`의 라벨 test와 같은 관례(같은 분류기, 다른 opening 인자). ----

    @Test
    fun `대상 라벨 — 추정가격이 없어 SuspectRatio 는 불가하고 기초금액이 정수라 Clean`() {
        val notice = testNoticeWithMoney(number = "20260101020", won = 1_000_000_000L)
        val baseAmount = requireNotNull(notice.baseAmount).amount

        val label = provenanceLabelFor(notice, null, baseAmount, testPolicies.provenancePolicy)

        label shouldBe BaseAmountProvenance.Clean
    }

    @Test
    fun `대상 라벨 — 기초금액이 추정가격의 신뢰 상한(1점15배)을 넘으면 SuspectRatio`() {
        val notice =
            testNoticeWithMoney(number = "20260101021", won = 1_200_000_001L, estimatedAmountWon = 1_000_000_000L)
        val baseAmount = requireNotNull(notice.baseAmount).amount

        val label = provenanceLabelFor(notice, null, baseAmount, testPolicies.provenancePolicy)

        label shouldBe BaseAmountProvenance.SuspectRatio
    }

    @Test
    fun `대상 라벨 — 기초금액이 0이면 네 규칙 모두 불성립해 Unknown`() {
        val notice = testNoticeWithMoney(number = "20260101022", won = 0L)
        val baseAmount = requireNotNull(notice.baseAmount).amount

        val label = provenanceLabelFor(notice, null, baseAmount, testPolicies.provenancePolicy)

        label shouldBe BaseAmountProvenance.Unknown
    }

    /**
     * D-4B8-1 「DerivedYega는 구조적으로 불가」의 직접 증거다. **알려진 제한** — 원화는
     * `Long`이라 `rawBaseAmount`가 항상 정수이고, production `ruleOrder`(SuspectRatio →
     * CleanInteger → DerivedYega → DerivedVat)에서는 CleanInteger가 SuspectRatio 다음
     * 자리에서 그 정수성만으로 항상 먼저 매치한다 — 그래서 production 정책으로는
     * DerivedYega·DerivedVat 자체가 (대상이든 표본이든, opening 유무와 무관하게) 관측되지
     * 않는다(4B-7 `SampleEligibilityTest`에도 두 라벨의 test가 없다 — 같은 이유). 그래서 이
     * test는 `ruleOrder`만 DerivedYega 우선으로 바꾼 test 전용 정책으로 CleanInteger의
     * 선매치를 비활성화하고, opening 유무만 바꿔 「대상 조립부가 낙찰 입력을 null로
     * 강제한다」는 D-4B8-1의 주장을 직접 잰다(변이).
     */
    @Test
    fun `대상 라벨 — DerivedYega 는 opening 이 있어야만 매치한다(변이 — opening=null 이면 불가)`() {
        val notice = testNoticeWithMoney(number = "20260101023", won = 1_000_000_000L)
        val baseAmount = requireNotNull(notice.baseAmount).amount
        val winningRate = Rate.ofFraction(BigDecimal("0.5"))
        val matchingOpening =
            OpeningResult(
                noticeId = notice.id,
                winningRate = winningRate,
                derivedBaseAmount = null,
                observedAt = Instant.parse("2026-09-16T00:00:00Z"),
                finalAwardAmount = AwardAmount(500_000_000L, Currency.KRW, Provenance.DerivedFromOpening),
            )
        val yegaFirstPolicy =
            Resolution.Resolved(
                testPolicies.provenancePolicy.value.copy(
                    ruleOrder =
                        listOf(
                            ProvenanceRuleId.DerivedYega,
                            ProvenanceRuleId.SuspectRatio,
                            ProvenanceRuleId.CleanInteger,
                            ProvenanceRuleId.DerivedVat,
                        ),
                ),
                PolicyVersion(EffectiveFrom.Initial, "test-yega-first"),
            )

        // 대조군 — 같은 데이터에 opening 이 있으면 DerivedYega 가 실제로 매치할 수 있다.
        provenanceLabelFor(notice, matchingOpening, baseAmount, yegaFirstPolicy) shouldBe
            BaseAmountProvenance.DerivedYega
        // predictionRequestFor 가 실제로 쓰는 형태 — opening = null 이면 같은 정책·같은 base 라도
        // DerivedYega 가 나올 수 없고, 다음 매치인 CleanInteger(Clean)로 접힌다.
        provenanceLabelFor(notice, null, baseAmount, yegaFirstPolicy) shouldBe BaseAmountProvenance.Clean
    }

    @Test
    fun `predictionRequestFor 라벨은 Unknown 상수가 아니다 — 입력에 따라 갈린다(OPEN-4B7-TARGET-LABEL 닫힘)`() {
        val cleanNotice = testNoticeWithMoney(number = "20260101024", won = 1_000_000_000L)
        val suspectNotice =
            testNoticeWithMoney(number = "20260101025", won = 1_200_000_001L, estimatedAmountWon = 1_000_000_000L)
        val cleanResolved = requireNotNull(cleanNotice.baseAmount)
        val suspectResolved = requireNotNull(suspectNotice.baseAmount)

        val cleanRequest =
            predictionRequestFor(cleanResolved, cleanNotice, testPolicies, CorrelationId("corr-clean"), emptyList())
        val suspectRequest =
            predictionRequestFor(
                suspectResolved,
                suspectNotice,
                testPolicies,
                CorrelationId("corr-suspect"),
                emptyList(),
            )

        cleanRequest.baseAmountProvenanceLabel shouldBe BaseAmountProvenance.Clean
        suspectRequest.baseAmountProvenanceLabel shouldBe BaseAmountProvenance.SuspectRatio
        (cleanRequest.baseAmountProvenanceLabel == BaseAmountProvenance.Unknown) shouldBe false
    }
}
