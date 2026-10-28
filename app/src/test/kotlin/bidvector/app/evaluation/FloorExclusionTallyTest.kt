package bidvector.app.evaluation

import bidvector.app.http.SequentialCorrelationIdFactory
import bidvector.app.http.TestStrategyRepository
import bidvector.app.wiring.EvaluationDryRunFactory
import bidvector.decision.MlUnavailableReason
import bidvector.decision.PublishedFloorExclusion
import bidvector.decision.UnitScore
import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.qualification.LicenseVerdict
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.WatchSubject
import bidvector.strategy.assembleFullScopeText
import bidvector.strategy.assembleKeywordScopeText
import bidvector.strategy.validate
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.PredictionEvidence
import bidvector.workflow.evaluation.WatchSubjectOutcome
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-10-28T00:00:00Z")
private const val MAX_ACTIVE_BIDS = 10

/**
 * DEC-10 계수의 **집계 고리**(r2 CR H-1 ①) — `tallyOf` 가 **결과 전체**에서 사유를 센다
 * (D-DEC10-14). 앞 판은 이 자리를 지나는 test 가 없어 `results.count` → `reached.count` 로
 * 되돌려도 전건이 초록이었다: 러너를 지나는 유일한 test 의 후보가 `Reached` 한 건이었기 때문이다.
 *
 * 여기서는 후보 **둘**을 흘린다 — 하나는 판정에 이르고(`Reached`), 하나는 최소 점수로 드롭돼
 * `NotReached` 가 된다. 둘 다 제외 사유를 들고 있으므로 `reached` 만 세는 구현에서는 수가 모자란다.
 *
 * DB 를 쓰지 않는다 — `EvaluationDryRunFactory` 가 production 조립이고 port 가 전부 fake 다
 * (`CandidateEvaluation` 의 생성자가 `internal` 이라 값을 손으로 지을 수는 없고, use case 를
 * 지나야 한다).
 */
class FloorExclusionTallyTest {
    private val results: List<CandidateEvaluation> by lazy { evaluateTwoCandidates() }

    @Test
    fun `판정에 이른 후보와 드롭된 후보가 둘 다 사유별 계수에 든다`() {
        val tally = tallyOf(results)

        results shouldHaveSize 2
        tally.reached shouldBe 1
        tally.notReached shouldBe 1
        tally.floorExclusions[PublishedFloorExclusion.IMPLAUSIBLE] shouldBe 1
        tally.floorExclusions[PublishedFloorExclusion.ORIGIN_NOT_JUDGED] shouldBe 1
    }

    /** 집계가 **줄에 나온다** — 사슬의 마지막 고리까지 같은 수다. */
    @Test
    fun `마침 줄에 두 사유가 각각 한 건으로 나온다`() {
        val tally = tallyOf(results)

        evaluationCommitFinishLine(tally, exitCodeOf(tally)) shouldBe
            "evaluation-commit finished candidates=2 reached=1 notReached=1 bidNow=1 " +
            "requested=1 requestFailed=0 floorImplausible=1 floorOriginNotJudged=1 exit=0"
    }

    private fun evaluateTwoCandidates(): List<CandidateEvaluation> {
        val reaching = candidate("DEC10-TALLY-001")
        val dropped = candidate("DEC10-TALLY-002")
        val analyses =
            mapOf(
                reaching.id to analyzed("0.9", matched = null, PublishedFloorExclusion.IMPLAUSIBLE),
                dropped.id to analyzed("0.9", matched = "0.1", PublishedFloorExclusion.ORIGIN_NOT_JUDGED),
            )
        val factory =
            EvaluationDryRunFactory(
                strategyRepository = TestStrategyRepository(tallyStrategy()),
                candidateSource = CandidateSourcePort { listOf(reaching, dropped) },
                watchSubjects = WatchSubjectPort { WatchSubjectOutcome.Found(PASSING_SUBJECT) },
                licenseGate = LicenseGatePort { LicenseVerdict.Eligible(emptySet()) },
                mlAnalysis = MlAnalysisPort { notice, _ -> analyses.getValue(notice.id) },
                correlationIds = SequentialCorrelationIdFactory(),
                clock = Clock { NOW },
            )
        return runBlocking { factory.forRequest(currentActiveBids = 0).useCase.evaluate() }
    }
}

private fun analyzed(
    priority: String,
    matched: String?,
    exclusion: PublishedFloorExclusion,
): MlAnalysisOutcome =
    MlAnalysisOutcome.Analyzed(
        priorityScore = UnitScore(BigDecimal(priority)),
        probabilityScore = null,
        matchedScore = matched?.let { UnitScore(BigDecimal(it)) },
        evidence = PredictionEvidence.NotPredicted(MlUnavailableReason.ScoreNotProvided),
        floorExclusion = exclusion,
    )

private val PASSING_SUBJECT =
    WatchSubject(
        categories = emptySet(),
        keywordText = assembleKeywordScopeText(null, null),
        fullText = assembleFullScopeText(null, null, null, null),
        baseAmount = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE),
    )

/** `minimumMatchScore` 가 있어야 **분석 뒤** 드롭 가지(그 제외가 사라질 수 있던 자리)가 돈다. */
private fun tallyStrategy(): bidvector.strategy.OperatorStrategy {
    val resolved =
        STRATEGY_POLICY.resolve(
            LocalDate.ofInstant(NOW, ZoneOffset.UTC),
        ) as Resolution.Resolved<StrategyPolicyData>
    val draft =
        StrategyDraft(
            // 감시 규칙이 하나도 없으면 `WatchGate` 에서 **분석 전에** 떨어진다(`NoGate`) —
            // 그러면 계수 축이 애초에 계산되지 않아 이 test 가 재려는 자리를 지나지 않는다.
            excludeKeywordTerms = listOf("없는-문구"),
            bidNowThreshold = BigDecimal("0.7"),
            reviewThreshold = BigDecimal("0.45"),
            minimumMatchScore = BigDecimal("0.5"),
            maxActiveBids = MAX_ACTIVE_BIDS,
        )
    return when (val result = validate(draft, StrategyRevision(1), resolved)) {
        is StrategyValidation.Valid -> result.strategy
        is StrategyValidation.Invalid -> error("test fixture 가 유효하지 않다: ${result.violations}")
    }
}

private fun candidate(number: String): Notice {
    val id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))
    val observation =
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
            SourceEndpoint.NOTICE_LIST,
            NOW,
        )
    return Notice.collected(
        NoticeCollected(
            id = id,
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
