package bidvector.app.evaluation

import bidvector.adapters.evaluation.EvaluationCommitRun
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.workflow.evaluation.CandidateEvaluation
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import java.sql.SQLException

/** 실행 실패의 정제된 표현 — 원인 코드만 싣고 원 예외를 잇지 않는다(수집 러너와 같은 근거). */
class EvaluationCommitRunFailedException(
    val causeCode: String,
) : RuntimeException("evaluation commit run failed cause=$causeCode")

private fun causeCodeOf(failure: Exception): String =
    when (failure) {
        is SQLException -> "${failure.javaClass.name}:sqlState=${failure.sqlState}"
        else -> failure.javaClass.name
    }

/**
 * 일회성 평가 커밋 러너(D-6F10-18 ⑧, `OPEN-6A3-EVALUATION-COMMIT` 종결) —
 * `bidvector.evaluation.mode=once` 일 때만 빈으로 등록된다. HTTP 평가 진입점은 dry-run
 * 하나로 **그대로** 남는다(커밋 endpoint 를 열지 않는다 — 인증 쓰기 경로를 늘리지 않는
 * 운영자 결정 A-4).
 *
 * `evaluate()` 가 `suspend` 라 [runBlocking] 으로 동기 경계 하나만 다리 놓는다(dry-run
 * 컨트롤러 선례). 그 아래의 outbox 쓰기는 **요청 하나 = 트랜잭션 하나**이므로 코루틴이
 * 어느 스레드에서 재개되든 성립한다 — run 전체의 원자성은 주장하지 않는다(D-6F10-4).
 *
 * `Failed` 를 **값으로** 받아 센다 — 그 수가 0 이 아니면 종료 코드가 비-0 이다.
 */
class EvaluationCommitRunner(
    private val run: EvaluationCommitRun,
    private val candidateCap: Int,
    private val log: CollectionLog,
    private val termination: CollectionTermination,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        log.write(evaluationCommitStartLine(candidateCap))
        val tally = tallyOf(evaluateOrFail())
        val exitCode = exitCodeOf(tally)
        log.write(evaluationCommitFinishLine(tally, exitCode))
        termination.terminate(exitCode.value)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun evaluateOrFail(): List<CandidateEvaluation> =
        try {
            runBlocking { run.evaluate() }
        } catch (failure: Exception) {
            val causeCode = causeCodeOf(failure)
            log.write(evaluationCommitFailureLine(causeCode))
            throw EvaluationCommitRunFailedException(causeCode)
        }
}
