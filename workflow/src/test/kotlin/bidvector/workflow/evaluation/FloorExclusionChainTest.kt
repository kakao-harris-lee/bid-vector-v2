package bidvector.workflow.evaluation

import bidvector.decision.MlUnavailableReason
import bidvector.decision.PublishedFloorExclusion
import bidvector.decision.UnitScore
import bidvector.strategy.StrategyDraft
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private fun analyzed(
    exclusion: PublishedFloorExclusion?,
    priority: String = "0.9",
    matched: String? = null,
): MlAnalysisOutcome =
    MlAnalysisOutcome.Analyzed(
        priorityScore = UnitScore(BigDecimal(priority)),
        probabilityScore = null,
        matchedScore = matched?.let { UnitScore(BigDecimal(it)) },
        evidence = PredictionEvidence.NotPredicted(MlUnavailableReason.ScoreNotProvided),
        floorExclusion = exclusion,
    )

/**
 * DEC-10 계수 사슬(r1 CR H-1) — **값이 지나가는지**를 잰다. 컴파일 필수 인자는 「칸이 있다」만
 * 세고 「값이 지나간다」를 세지 않는다: 앞 판은 `Analyzed` → `Reached` 를 지나는 test 인자가
 * 전부 `null` 이어서, 그 고리가 끊겨도 전건이 초록이었다.
 *
 * 위협 모델 (0) 의 첫 문장이 「그 제외가 조용히 사라지는 것」이라, 사라질 수 있는 자리마다
 * non-null 을 흘려 보내는 것이 이 파일의 일이다.
 */
class FloorExclusionChainTest {
    private fun evaluate(outcome: MlAnalysisOutcome): List<CandidateEvaluation> =
        runBlocking {
            useCase(
                strategyRepository = FakeStrategyRepository(testStrategy()),
                candidateSource = FakeCandidateSource(listOf(testNotice())),
                mlAnalysis = FakeMlAnalysisPort { outcome },
            ).evaluate()
        }

    @Test
    fun `분석이 실은 제외 사유가 판정 결과에 그대로 올라온다`() {
        val result = evaluate(analyzed(PublishedFloorExclusion.IMPLAUSIBLE)).single()

        result.shouldBeInstanceOf<CandidateEvaluation.Reached>()
        result.floorExclusion shouldBe PublishedFloorExclusion.IMPLAUSIBLE
    }

    @Test
    fun `심판 아님 사유도 같은 자리로 올라온다 — 두 사유가 한 값으로 접히지 않는다`() {
        val result = evaluate(analyzed(PublishedFloorExclusion.ORIGIN_NOT_JUDGED)).single()

        result.shouldBeInstanceOf<CandidateEvaluation.Reached>()
        result.floorExclusion shouldBe PublishedFloorExclusion.ORIGIN_NOT_JUDGED
    }

    @Test
    fun `제외가 없으면 null 이 올라온다 — 없는 것을 지어내지 않는다`() {
        val result = evaluate(analyzed(null)).single()

        result.shouldBeInstanceOf<CandidateEvaluation.Reached>()
        result.floorExclusion shouldBe null
    }

    /**
     * r1 CR M-1 — 최소 점수 드롭은 `Analyzed` 를 **받은 뒤** `NotReached` 로 떨어지는 자리다.
     * 분석도 제외 계산도 이미 끝나 있으므로 그 사유를 버리면 계수가 조용히 사라진다.
     */
    @Test
    fun `최소 점수로 드롭돼도 제외 사유는 버려지지 않는다`() {
        val strategy =
            testStrategy(
                draft =
                    StrategyDraft(
                        focusCategories = listOf(DEFAULT_FOCUS_CATEGORY),
                        bidNowThreshold = BigDecimal("0.7"),
                        reviewThreshold = BigDecimal("0.45"),
                        minimumMatchScore = BigDecimal("0.9"),
                    ),
            )
        val dropped =
            runBlocking {
                useCase(
                    strategyRepository = FakeStrategyRepository(strategy),
                    candidateSource = FakeCandidateSource(listOf(testNotice())),
                    mlAnalysis =
                        FakeMlAnalysisPort { analyzed(PublishedFloorExclusion.IMPLAUSIBLE, matched = "0.1") },
                ).evaluate()
            }.single()

        dropped.shouldBeInstanceOf<CandidateEvaluation.NotReached>()
        dropped.stage shouldBe EvaluationStage.ScoreThreshold
        dropped.floorExclusion shouldBe PublishedFloorExclusion.IMPLAUSIBLE
    }

    /** 분석을 돌지 못한 가지에는 그 축이 없다 — 「계산하지 않았다」와 「제외했다」를 가른다. */
    @Test
    fun `ML 미가용 가지는 제외 사유 없이 판정에 이른다`() {
        val result = evaluate(MlAnalysisOutcome.Unavailable(MlUnavailableReason.CircuitOpen)).single()

        result.shouldBeInstanceOf<CandidateEvaluation.Reached>()
        result.floorExclusion shouldBe null
    }
}
