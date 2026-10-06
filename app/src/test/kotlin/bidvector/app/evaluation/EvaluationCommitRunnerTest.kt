package bidvector.app.evaluation

import bidvector.adapters.evaluation.EvaluationCommitRun
import bidvector.adapters.evaluation.RequestCapacityPort
import bidvector.app.collection.CollectionLog
import bidvector.app.http.TestStrategyRepository
import bidvector.app.wiring.RecordedExitCodes
import bidvector.procurement.Notice
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.DefaultApplicationArguments
import java.time.Instant

private const val CANDIDATE_CAP = 5
private const val MAX_ACTIVE_BIDS = 10
private const val DEAD_STORE_MESSAGE = "전략 저장소가 응답하지 않는다 jdbc:postgresql://secret-host/db"

/**
 * 커밋 러너의 **예외 경로**(R2-L-2, cr R-6) — 협력자가 던지면 러너가 Spring 에 넘기지 않고
 * 정제된 사유 토큰과 `FAILED`(1) 를 낸다.
 *
 * 앞 판이 재지 않은 것: `EvaluationCommitRunE2ETest` 는 성공과 **outbox 쓰기 실패**(값으로
 * 돌아오는 `Failed`)만 돌렸다 — `catch` 블록은 어느 test 도 지나지 않았고, 종료 코드
 * `FAILED` 와 실패 줄은 relay 러너 쪽 대응물만 측정돼 있었다.
 *
 * 던지는 자리를 전략 저장소로 잡는 이유: 평가 use case 가 **가장 먼저** 부르는 협력자라
 * 나머지 port 가 불리지 않는다(자리지킴 람다들이 거짓 신호를 만들지 않는다). 실 DB 도
 * 필요 없다 — `EvaluationCommitRun` 은 `DataSource` 로 질의를 돌리지 않고 조립만 한다.
 */
class EvaluationCommitRunnerTest {
    private class RecordingLog : CollectionLog {
        val written = mutableListOf<String>()

        override fun write(line: String) {
            written += line
        }
    }

    @Test
    fun `협력자가 던지면 사유 토큰을 남기고 FAILED 1 로 종료한다`() {
        val log = RecordingLog()
        val termination = RecordedExitCodes()

        EvaluationCommitRunner(deadCommitRun(), CANDIDATE_CAP, log, termination)
            .run(DefaultApplicationArguments())

        termination.recorded() shouldBe listOf(EvaluationCommitExitCode.FAILED.value)
        val lines = log.written.joinToString("\n")
        lines shouldContain "evaluation-commit start"
        lines shouldContain "evaluation-commit failed cause="
    }

    /**
     * 실패 줄은 **예외 메시지를 싣지 않는다** — 클래스 이름까지다. 메시지에 접속 문자열을
     * 심어 두고 그것이 로그에 없음을 잰다(relay 러너와 같은 규율, 그쪽은 SQLSTATE 축).
     */
    @Test
    fun `실패 줄은 예외 메시지를 싣지 않는다`() {
        val log = RecordingLog()

        EvaluationCommitRunner(deadCommitRun(), CANDIDATE_CAP, log, RecordedExitCodes())
            .run(DefaultApplicationArguments())

        val failureLine = log.written.single { it.startsWith("evaluation-commit failed") }
        failureLine shouldContain IllegalStateException::class.java.name
        failureLine shouldNotContain "jdbc:"
        failureLine shouldNotContain "secret-host"
    }
}

/** 전략 읽기에서 던지는 조립 — 그 뒤 협력자는 불리지 않는다. */
private fun deadCommitRun(): EvaluationCommitRun =
    EvaluationCommitRun(
        dataSource = PGSimpleDataSource(),
        strategies = TestStrategyRepository().also { it.loadFailure = { IllegalStateException(DEAD_STORE_MESSAGE) } },
        candidateSource = CandidateSourcePort { error("후보 원천이 불렸다 — 전략 읽기가 먼저 던져야 한다") },
        watchSubjects = WatchSubjectPort { error("감시 port 가 불렸다") },
        licenseGate = LicenseGatePort { error("면허 게이트가 불렸다") },
        mlAnalysis = MlAnalysisPort { _: Notice, _ -> error("ML port 가 불렸다") },
        capacity = RequestCapacityPort(0, MAX_ACTIVE_BIDS),
        correlationIds = CorrelationIdFactory { CorrelationId("commit-runner-test") },
        clock = Clock { Instant.parse("2026-10-07T00:00:00Z") },
        analysisBudget = null,
    )
