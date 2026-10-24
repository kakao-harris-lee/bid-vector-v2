package bidvector.workflow.evaluation

import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeStatus
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.RequirementGroupId
import bidvector.qualification.UncertainReason
import bidvector.strategy.StrategyDraft
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * 값싼 게이트 셋(생애주기 → 감시 → 면허)의 **현재 거동을 고정하는** 특성화 test(M7/7C①b, 팀장 Q4).
 *
 * 그 세 게이트를 `EvaluateCandidatesUseCase` 의 private 함수에서 모듈 공용 함수로 옮기는 refactor 가
 * **거동 불변**이라는 증거가 이 파일이다 — refactor 커밋보다 **먼저** 들어온다. 고정하는 것은 셋이다:
 * ① 후보마다의 (멈춘 단계, 사유) 전수 ② 각 port 가 **어느 후보에 대해** 불렸는가(앞 게이트에서 멈춘
 * 후보는 뒤 port 를 부르지 않는다) ③ 분석 예산은 값싼 게이트보다 **앞**에서 깎는다(F-1 — 이 순서는
 * 결함으로 보고됐지만 이 slice 는 바꾸지 않는다, 별 slice).
 */
class EvaluateCandidatesScreeningCharacterizationTest {
    private val open = testNotice(number = "20260101001")
    private val closed = testNotice(number = "20260101002", status = NoticeStatus.Closed)
    private val watchUnavailable = testNotice(number = "20260101003")
    private val watchRejected = testNotice(number = "20260101004")
    private val ineligible = testNotice(number = "20260101005")
    private val uncertain = testNotice(number = "20260101006")
    private val ineligibleVerdict = LicenseVerdict.Ineligible(mapOf(RequirementGroupId.Ungrouped to emptySet()))

    private val watchSubjects =
        FakeWatchSubjectPort { notice ->
            when (notice.id) {
                watchUnavailable.id -> WatchSubjectOutcome.Unavailable
                watchRejected.id -> WatchSubjectOutcome.Found(EMPTY_SUBJECT)
                else -> WatchSubjectOutcome.Found(MATCHING_SUBJECT)
            }
        }
    private val licenseGate =
        FakeLicenseGatePort { notice ->
            when (notice.id) {
                ineligible.id -> ineligibleVerdict
                uncertain.id -> LicenseVerdict.Uncertain(UncertainReason.OperatorLicensesNotDeclared)
                else -> LicenseVerdict.Eligible(emptySet())
            }
        }

    private fun run(
        strategyDraft: StrategyDraft? = null,
        analysisBudget: Int? = null,
    ): List<CandidateEvaluation> {
        val strategy = strategyDraft?.let { testStrategy(it) } ?: testStrategy()
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(strategy),
                candidateSource =
                    FakeCandidateSource(listOf(open, closed, watchUnavailable, watchRejected, ineligible, uncertain)),
                watchSubjects = watchSubjects,
                licenseGate = licenseGate,
                mlAnalysis = FakeMlAnalysisPort { unavailableAnalysis() },
                analysisBudget = analysisBudget,
            )
        return runBlocking { useCase.evaluate() }
    }

    private fun List<CandidateEvaluation>.dropsById(): Map<NoticeId, Pair<EvaluationStage, EvaluationDropReason>> =
        filterIsInstance<CandidateEvaluation.NotReached>().associate { it.noticeId to (it.stage to it.reason) }

    @Test
    fun `후보마다 멈춘 단계와 사유가 지금 그대로다`() {
        val results = run()

        val all = listOf(open, closed, watchUnavailable, watchRejected, ineligible, uncertain)
        results.map { it.noticeId } shouldBe all.map { it.id }
        val drops = results.dropsById()
        drops.keys shouldBe setOf(closed.id, watchUnavailable.id, watchRejected.id, ineligible.id)
        drops.getValue(closed.id) shouldBe
            (EvaluationStage.NoticeLifecycle to EvaluationDropReason.NoticeNotBiddable(NoticeStatus.Closed))
        drops.getValue(watchUnavailable.id) shouldBe
            (EvaluationStage.WatchGate to EvaluationDropReason.WatchSubjectUnavailable)
        drops.getValue(watchRejected.id).first shouldBe EvaluationStage.WatchGate
        (drops.getValue(watchRejected.id).second is EvaluationDropReason.WatchGateRejected) shouldBe true
        drops.getValue(ineligible.id) shouldBe
            (EvaluationStage.LicenseGate to EvaluationDropReason.LicenseIneligible(ineligibleVerdict))
        // Uncertain 은 미보유가 아니다(U-5) — 면허 게이트를 지나 사다리에 이른다.
        val reached = results.filterIsInstance<CandidateEvaluation.Reached>()
        reached.map { it.noticeId } shouldBe listOf(open.id, uncertain.id)
    }

    @Test
    fun `앞 게이트에서 멈춘 후보는 뒤 port 를 부르지 않는다`() {
        run()

        val watched = listOf(open, watchUnavailable, watchRejected, ineligible, uncertain)
        watchSubjects.calledFor shouldBe watched.map { it.id }
        licenseGate.calledFor shouldBe listOf(open, ineligible, uncertain).map { it.id }
    }

    @Test
    fun `감시 규칙이 없으면 생애주기를 지난 후보 전부가 감시 게이트에서 NoGate 로 멈춘다`() {
        val results = run(strategyDraft = StrategyDraft())

        val drops = results.dropsById()
        drops.getValue(closed.id).first shouldBe EvaluationStage.NoticeLifecycle
        val watchDrops = drops.filterKeys { it != closed.id && it != watchUnavailable.id }
        watchDrops.values.map { it.second }.toSet() shouldBe
            setOf(EvaluationDropReason.WatchGateNotConfigured(bidvector.strategy.WatchVerdict.NoGate))
        licenseGate.calledFor shouldBe emptyList()
    }

    @Test
    fun `분석 예산은 값싼 게이트보다 앞에서 원시 후보 순번으로 깎는다 — F-1 거동 고정`() {
        val results = run(analysisBudget = 2)

        val drops = results.dropsById()
        listOf(watchUnavailable, watchRejected, ineligible, uncertain).forEach { notice ->
            drops.getValue(notice.id) shouldBe
                (EvaluationStage.AnalysisBudget to EvaluationDropReason.AnalysisBudgetExhausted)
        }
        watchSubjects.calledFor shouldBe listOf(open.id)
        licenseGate.calledFor shouldBe listOf(open.id)
    }
}
