package bidvector.app.http

import bidvector.adapters.evaluation.NoticeWatchSubjectPort
import bidvector.app.wiring.BidPriceRequestFactory
import bidvector.app.wiring.BidPriceRequestOutcome
import bidvector.app.wiring.EvaluationDryRunFactory
import bidvector.app.wiring.MaxActiveBidsNotConfiguredException
import bidvector.decision.MlUnavailableReason
import bidvector.procurement.Notice
import bidvector.procurement.NoticeId
import bidvector.strategy.OperatorStrategy
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.EvaluationDropReason
import bidvector.workflow.evaluation.EvaluationStage
import bidvector.workflow.evaluation.MlAnalysisOutcome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * **차분 test** — 공고 단건 평가가 `evaluateDryRun`(전건 평가)과 **같은 판정 커널**을 돌아 같은
 * 입력에서 같은 결과를 낸다. 이것이 7C ② 가 판정 경로를 복제하지 않았다는 증거다: 단건 조립이
 * 어딘가에서 자기 판정을 하기 시작하면 이 등식이 깨진다.
 *
 * 비교에서 **상관 ID 는 뺀다** — 호출마다 새로 발급되는 값이라 두 실행에서 같을 수 없고, 같다면
 * 오히려 발급이 고정됐다는 뜻이다(그 축은 이 test 의 관심이 아니다).
 *
 * 표본 **둘**은 서로 다른 탈락 단계를 덮는다(`verifier r1 L-4` — 앞 문면의 「셋」은 틀렸다).
 * 한 단계만 재면 그 단계 밖의 차분이 측정되지 않는다. 판정 갈래 쪽은 ML 이 늘 미가용이라
 * `Reached` 에서 재는 갈래가 하나다 — 그 사실은 도달 단언이 따로 잰다.
 */
class BidPriceRequestDifferentialTest {
    private val clock = FixedClock(Instant.parse("2026-10-23T00:00:00Z"))

    /** 마감 전·마감 후 두 공고 — 뒤는 커널의 수명 단계 드롭으로 내려온다. */
    private val biddable = bidPriceNotice(bidPriceNoticeId("BP-OPEN"))
    private val expired =
        bidPriceNotice(bidPriceNoticeId("BP-EXPIRED"), deadlineAt = Instant.parse("2026-01-01T00:00:00Z"))
    private val allNotices = listOf(biddable, expired)

    private fun strategy(): OperatorStrategy = bidPriceStrategy()

    private fun fullRunResults(
        strategy: OperatorStrategy,
        currentActiveBids: Int,
        analysisBudgetNotices: List<Notice> = allNotices,
    ): List<CandidateEvaluation> {
        val factory =
            EvaluationDryRunFactory(
                strategyRepository = TestStrategyRepository(strategy),
                candidateSource = CandidateSourcePort { analysisBudgetNotices },
                watchSubjects = NoticeWatchSubjectPort(),
                licenseGate = { licenseJudgementFixture().verdict },
                mlAnalysis = CountingMlAnalysisPort(unavailable()),
                correlationIds = SequentialCorrelationIdFactory(),
                clock = clock,
            )
        return runBlocking { factory.forRequest(currentActiveBids).useCase.evaluate() }
    }

    private fun singleRun(
        id: NoticeId,
        strategy: OperatorStrategy,
        currentActiveBids: Int,
    ): BidPriceRequestOutcome =
        BidPriceRequestFactory(
            notices = FakeNoticeByIdPort(allNotices.associateBy { it.id }),
            strategyRepository = TestStrategyRepository(strategy),
            licenseJudgements = FakeLicenseJudgementPort(),
            watchSubjects = NoticeWatchSubjectPort(),
            mlAnalysis = CountingMlAnalysisPort(unavailable()),
            correlationIds = SequentialCorrelationIdFactory(),
            clock = clock,
        ).forRequest(id, currentActiveBids)

    private fun unavailable(): MlAnalysisOutcome = MlAnalysisOutcome.Unavailable(MlUnavailableReason.ScoreNotProvided)

    @Test
    fun `공고별로 전건 dry-run 과 같은 결과를 낸다 — 판정 경로를 복제하지 않았다`() {
        val strategy = strategy()
        val full = fullRunResults(strategy, currentActiveBids = 0).associateBy { it.noticeId }

        allNotices.forEach { notice ->
            val outcome = singleRun(notice.id, strategy, currentActiveBids = 0)
            val single =
                (outcome as? BidPriceRequestOutcome.Prepared)
                    ?: error("준비되지 않았다: $outcome")
            val singleResult = runBlocking { single.run.evaluate() }

            withClue("${notice.id.number.value} 의 단건 결과가 전건과 달라졌다") {
                comparable(singleResult) shouldBe comparable(requireNotNull(full[notice.id]))
            }
        }
    }

    /**
     * **차분이 갈래마다 실제로 닿는지 고정한다**(`D-7C2-5` (d)) — 앞 판은 fixture 전략이 관심
     * 업종으로 감시 규칙을 세워 모든 후보가 **감시 게이트에서 거절**됐고, 그래서 차분이 덮은
     * 것은 드롭 경로뿐이었다(그 과장을 evidence 에 정정으로 적었다). 이 test 가 없으면 같은
     * 침묵이 다시 선다 — fixture 가 조용히 한 갈래로 쏠려도 차분 등식은 초록이다.
     */
    @Test
    fun `차분 표본이 드롭·사다리 도달·ML 단계 도달 셋에 실제로 닿는다`() {
        val strategy = strategy()
        val results = fullRunResults(strategy, currentActiveBids = 0).associateBy { it.noticeId }

        withClue("마감이 지난 공고는 드롭 갈래에 닿아야 한다") {
            (results.getValue(expired.id) is CandidateEvaluation.NotReached) shouldBe true
        }
        withClue("마감 전 공고는 사다리에 닿아 판정을 받아야 한다 — 닿지 않으면 판정 축이 미측정이다") {
            (results.getValue(biddable.id) is CandidateEvaluation.Reached) shouldBe true
        }

        val prepared = singleRun(biddable.id, strategy, currentActiveBids = 0) as BidPriceRequestOutcome.Prepared
        runBlocking { prepared.run.evaluate() }

        withClue("ML 단계까지 가야 한다 — null 이면 그 앞에서 멈췄다는 뜻이다") {
            (prepared.run.mlOutcome() != null) shouldBe true
        }
    }

    @Test
    fun `마감이 지난 공고는 404 가 아니라 수명 단계 드롭으로 내려온다`() {
        val outcome = singleRun(expired.id, strategy(), currentActiveBids = 0)
        val prepared = outcome as BidPriceRequestOutcome.Prepared
        val result = runBlocking { prepared.run.evaluate() }

        val notReached = result as CandidateEvaluation.NotReached
        notReached.stage shouldBe EvaluationStage.NoticeLifecycle
        (notReached.reason is EvaluationDropReason.NoticeNotBiddable) shouldBe true
    }

    @Test
    fun `없는 공고는 평가를 시작하지 않고 NoticeNotFound 로 답한다`() {
        singleRun(bidPriceNoticeId("BP-MISSING"), strategy(), currentActiveBids = 0) shouldBe
            BidPriceRequestOutcome.NoticeNotFound
    }

    /**
     * **의도된 어긋남 하나**(`D-7C2-1` Q2) — 분석 예산은 전건 스캔의 비용 통제이고 운영자가 지목한
     * 공고의 허가 조건이 아니다. 전건에서 예산에 밀려 평가되지 않는 공고도 단건 요청에서는
     * 평가된다. 이 차이를 test 가 **고정**한다(조용한 차이로 두지 않는다).
     */
    @Test
    fun `분석 예산은 단건 경로에 적용되지 않는다 — 전건과 어긋나는 유일한 축`() {
        val limited = bidPriceStrategy(candidateLimit = 1)
        val full = fullRunResults(limited, currentActiveBids = 0).associateBy { it.noticeId }

        withClue("전건에서는 둘째 공고가 예산에 밀려야 한다") {
            val second = full[expired.id] as CandidateEvaluation.NotReached
            second.stage shouldBe EvaluationStage.AnalysisBudget
            second.reason shouldBe EvaluationDropReason.AnalysisBudgetExhausted
        }

        val prepared = singleRun(expired.id, limited, currentActiveBids = 0) as BidPriceRequestOutcome.Prepared
        val single = runBlocking { prepared.run.evaluate() } as CandidateEvaluation.NotReached

        withClue("단건에서는 예산이 발화하지 않고 실제 사유(수명)로 내려와야 한다") {
            single.stage shouldBe EvaluationStage.NoticeLifecycle
        }
    }

    @Test
    fun `자격 판정은 요청당 정확히 한 번 돈다 — 응답 근거와 커널 판정이 같은 값이다`() {
        val judgements = FakeLicenseJudgementPort()
        val factory =
            BidPriceRequestFactory(
                notices = FakeNoticeByIdPort(allNotices.associateBy { it.id }),
                strategyRepository = TestStrategyRepository(strategy()),
                licenseJudgements = judgements,
                watchSubjects = NoticeWatchSubjectPort(),
                mlAnalysis = CountingMlAnalysisPort(unavailable()),
                correlationIds = SequentialCorrelationIdFactory(),
                clock = clock,
            )

        val prepared = factory.forRequest(biddable.id, 0) as BidPriceRequestOutcome.Prepared
        runBlocking { prepared.run.evaluate() }

        judgements.callCount shouldBe 1
        prepared.run.licenseJudgement shouldBe licenseJudgementFixture()
    }

    /**
     * **요청당 비용 상한** — 공고 읽기 1 · 감시 입력 ≤1 · ML ≤1. 후보 전건 스캔은 이 경로에
     * 아예 없다(팩토리가 `CandidateSourcePort` 를 받지 않는다 — 생성자 시그니처가 그 사실이다).
     */
    @Test
    fun `요청당 포트 호출 상한을 지킨다`() {
        val notices = FakeNoticeByIdPort(allNotices.associateBy { it.id })
        val watch = CountingWatchSubjectPort(NoticeWatchSubjectPort())
        val ml = CountingMlAnalysisPort(unavailable())
        val factory =
            BidPriceRequestFactory(
                notices = notices,
                strategyRepository = TestStrategyRepository(strategy()),
                licenseJudgements = FakeLicenseJudgementPort(),
                watchSubjects = watch,
                mlAnalysis = ml,
                correlationIds = SequentialCorrelationIdFactory(),
                clock = clock,
            )

        val prepared = factory.forRequest(biddable.id, 0) as BidPriceRequestOutcome.Prepared
        runBlocking { prepared.run.evaluate() }

        notices.callCount shouldBe 1
        withClue("감시 입력 조립은 공고 하나당 한 번이다") { watch.callCount shouldBe 1 }
        withClue("ML 호출은 요청당 한 번을 넘지 않는다") { (ml.callCount <= 1) shouldBe true }
    }

    @Test
    fun `여력 상한이 설정되지 않은 전략은 평가를 거부한다 — dry-run 과 같은 fail-closed`() {
        shouldThrow<MaxActiveBidsNotConfiguredException> {
            singleRun(biddable.id, bidPriceStrategy(maxActiveBids = null), currentActiveBids = 0)
        }
    }

    /**
     * 비교 대상 — 상관 ID 를 뺀 나머지 전부다. 필드를 빼면서 비교가 약해지지 않도록 **갈래별로
     * 전 필드를 적는다**(`copy` 로 한 필드만 지우는 방법은 생성자가 `internal` 이라 쓸 수 없다).
     */
    private fun comparable(evaluation: CandidateEvaluation): List<Any?> =
        when (evaluation) {
            is CandidateEvaluation.Reached -> {
                listOf("REACHED", evaluation.noticeId, evaluation.verdict, evaluation.disposition)
            }

            is CandidateEvaluation.NotReached -> {
                listOf("NOT_REACHED", evaluation.noticeId, evaluation.stage, evaluation.reason)
            }
        }
}
