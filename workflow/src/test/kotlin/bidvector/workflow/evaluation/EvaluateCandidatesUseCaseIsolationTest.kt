package bidvector.workflow.evaluation

import bidvector.decision.UnitScore
import bidvector.decision.Verdict
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * 후보 단위 격리·분석 예산·호출 횟수 불변식(결정 4·5·6·8·10, D-10) — 코어 판정 경로는
 * `EvaluateCandidatesUseCaseTest`(파일 크기 한도, v2-지침서 §5).
 */
class EvaluateCandidatesUseCaseIsolationTest {
    // D-10 — 분석 예산을 넘긴 후보는 흔적을 남기고, 그 뒤 port를 아예 부르지 않는다.
    @Test
    fun `분석 예산을 넘긴 후보는 AnalysisBudgetExhausted 로 남고 다른 port 를 부르지 않는다`() {
        val within = testNotice(number = "20260101001")
        val beyond = testNotice(number = "20260101002")
        val watchSubjects = FakeWatchSubjectPort()
        val mlAnalysis = FakeMlAnalysisPort { bidNowAnalysis() }
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(testStrategy()),
                candidateSource = FakeCandidateSource(listOf(within, beyond)),
                watchSubjects = watchSubjects,
                mlAnalysis = mlAnalysis,
                analysisBudget = 1,
            )

        val results = useCase.evaluate()

        results shouldHaveSize 2
        results[0].shouldBeInstanceOf<CandidateEvaluation.Reached>()
        val second = results[1] as CandidateEvaluation.NotReached
        second.stage shouldBe EvaluationStage.AnalysisBudget
        second.reason shouldBe EvaluationDropReason.AnalysisBudgetExhausted
        watchSubjects.calledFor shouldBe listOf(within.id)
        mlAnalysis.callCountFor.keys shouldBe setOf(within.id)
    }

    // 결정 6 — 후보 단위 격리: 한 후보의 port 실패가 다른 후보 결과를 지우지 않는다(전역 rollback 없음).
    @Test
    fun `한 후보의 실패가 다른 후보의 성공을 지우지 않는다`() {
        val failing = testNotice(number = "20260101001")
        val succeeding = testNotice(number = "20260101002")
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(testStrategy()),
                candidateSource = FakeCandidateSource(listOf(failing, succeeding)),
                watchSubjects =
                    FakeWatchSubjectPort { notice ->
                        if (notice.id == failing.id) {
                            WatchSubjectOutcome.Unavailable
                        } else {
                            WatchSubjectOutcome.Found(EMPTY_SUBJECT)
                        }
                    },
                mlAnalysis = FakeMlAnalysisPort { bidNowAnalysis() },
            )

        val results = useCase.evaluate()

        results shouldHaveSize 2
        val first = results[0] as CandidateEvaluation.NotReached
        first.reason shouldBe EvaluationDropReason.WatchSubjectUnavailable
        results[1].shouldBeInstanceOf<CandidateEvaluation.Reached>()
    }

    // 결정 5 — 판정은 공고당 정확히 한 번(사다리 호출 횟수를 fake 계수로 단언).
    @Test
    fun `판정은 공고당 정확히 한 번 돈다`() {
        val notice = testNotice()
        val mlAnalysis = FakeMlAnalysisPort { bidNowAnalysis() }
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(testStrategy()),
                candidateSource = FakeCandidateSource(listOf(notice)),
                mlAnalysis = mlAnalysis,
            )

        useCase.evaluate()

        mlAnalysis.callCountFor.getValue(notice.id).get() shouldBe 1
    }

    // 결정 4 — 전략은 use case 진입에서 정확히 한 번만 읽는다(후보 수와 무관).
    @Test
    fun `전략은 후보가 여럿이어도 진입에서 한 번만 읽는다`() {
        val notices = listOf(testNotice("20260101001"), testNotice("20260101002"), testNotice("20260101003"))
        val strategyRepository = FakeStrategyRepository(testStrategy())
        val useCase =
            useCase(
                strategyRepository = strategyRepository,
                candidateSource = FakeCandidateSource(notices),
                mlAnalysis = FakeMlAnalysisPort { bidNowAnalysis() },
            )

        useCase.evaluate()

        strategyRepository.loadCount shouldBe 1
    }

    // 결정 10 / 형태 ⑧ — 용량은 run당 한 번만 읽어 모든 후보가 같은 스냅샷을 공유한다(중복 계수 불가능).
    @Test
    fun `용량은 후보가 여럿이어도 run당 한 번만 읽는다`() {
        val notices = listOf(testNotice("20260101001"), testNotice("20260101002"))
        val capacity = FakeCapacityPort(CapacitySnapshot(currentActiveBids = 2, maxActiveBids = 2))
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(testStrategy()),
                candidateSource = FakeCandidateSource(notices),
                mlAnalysis = FakeMlAnalysisPort { bidNowAnalysis() },
                capacity = capacity,
            )

        val results = useCase.evaluate()

        capacity.callCount shouldBe 1
        // capacity 가 가득 찼고 priority(0.9) < capacityHoldPriorityThreshold(0.8, policy slot) 는 거짓이라
        // capacity-hold 는 아니지만, 두 후보 다 같은 스냅샷(currentActiveBids=2)을 봤다는 것이 핵심 —
        // 사다리 자체의 정확성은 4B-1이 이미 검증했으므로 여기서는 호출 횟수만 재확인한다.
        results shouldHaveSize 2
    }

    // 결정 8 — 요청과 배달을 타입으로 가른다: Review/Skip 은 알림 요청을 낳지 않는다.
    @Test
    fun `BidNow 가 아니면 알림 요청을 낳지 않는다`() {
        val notice = testNotice()
        val notifications = FakeNotificationRequestPort()
        // priority 낮고 probability·matched 도 낮아 Skip(LowPriority)로 확정.
        val useCase =
            useCase(
                strategyRepository = FakeStrategyRepository(testStrategy()),
                candidateSource = FakeCandidateSource(listOf(notice)),
                mlAnalysis =
                    FakeMlAnalysisPort {
                        MlAnalysisOutcome.Analyzed(
                            priorityScore = UnitScore(BigDecimal("0.1")),
                            probabilityScore = UnitScore(BigDecimal("0.1")),
                            matchedScore = UnitScore(BigDecimal("0.1")),
                        )
                    },
                notifications = notifications,
            )

        val result = useCase.evaluate().single() as CandidateEvaluation.Reached

        result.verdict.shouldBeInstanceOf<Verdict.Skip>()
        notifications.requested.shouldBeEmpty()
    }
}
