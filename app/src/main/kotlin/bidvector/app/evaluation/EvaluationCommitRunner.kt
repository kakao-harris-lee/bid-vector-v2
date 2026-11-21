package bidvector.app.evaluation

import bidvector.adapters.evaluation.EvaluationCommitRuns
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.relay.causeCodeOf
import bidvector.workflow.access.AccountId
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.notificationinbox.NotificationAudience
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner

/**
 * 일회성 평가 커밋 러너(D-6F10-18 ⑧, `OPEN-6A3-EVALUATION-COMMIT` 종결) —
 * `bidvector.evaluation.mode=once` 일 때만 빈으로 등록된다. HTTP 평가 진입점은 dry-run
 * 하나로 **그대로** 남는다(커밋 endpoint 를 열지 않는다 — 인증 쓰기 경로를 늘리지 않는
 * 운영자 결정 A-4).
 *
 * **M15/15A — 계정마다 한 번 돈다.** 계정 전수를 한 번 읽고([EvaluationCommitRuns.accounts]) 그
 * 목록의 계정마다 그 계정의 전략·자격으로 평가해 **그 계정의 알림함에만** 적는다. 전략을 쓸 수
 * 없는 계정은 건너뛰고 세며, 그 수가 종료 줄에 실린다.
 *
 * **계정 전수를 못 읽으면 시작하지 않는다** — 빈 목록으로 진행하면 「평가할 계정이 없다」와
 * 「누가 있는지 못 읽었다」가 같은 종료 코드 0 이 된다.
 *
 * `evaluate()` 가 `suspend` 라 [runBlocking] 으로 동기 경계 하나만 다리 놓는다(dry-run
 * 컨트롤러 선례). 그 아래의 outbox 쓰기는 **요청 하나 = 트랜잭션 하나**이므로 코루틴이
 * 어느 스레드에서 재개되든 성립한다 — run 전체의 원자성은 주장하지 않는다(D-6F10-4).
 *
 * `Failed` 를 **값으로** 받아 센다 — 그 수가 0 이 아니면 종료 코드가 비-0 이다.
 */
class EvaluationCommitRunner(
    private val runs: EvaluationCommitRuns,
    private val candidateCap: Int,
    private val log: CollectionLog,
    private val termination: CollectionTermination,
) : ApplicationRunner {
    /**
     * 예외를 Spring 에 넘기지 않고 이 자리에서 종료 코드로 옮긴다(cr L-4 — relay 러너와 같다).
     * 보조 타입을 두지 않는 이유도 같다(app 최상위 타입이 늘면 조립 장부가 함께 움직인다).
     *
     * **한 계정의 실패가 run 전체를 끊는다**(앞 판과 같은 거동). 계정별로 삼키면 그 계정의 판정이
     * 사라진 사실이 종료 코드에 남지 않는다 — 계수는 「무엇을 했는가」를 말하지 「무엇이 터졌는가」를
     * 말하지 못한다.
     */
    @Suppress("TooGenericExceptionCaught")
    override fun run(args: ApplicationArguments) {
        log.write(evaluationCommitStartLine(candidateCap))
        val accounts =
            when (val audience = runs.accounts()) {
                is NotificationAudience.Accounts -> audience.accounts
                is NotificationAudience.Unavailable -> {
                    log.write(evaluationCommitAudienceFailureLine(audience.exceptionType))
                    termination.terminate(EvaluationCommitExitCode.FAILED.value)
                    return
                }
            }
        val outcome =
            try {
                evaluateEach(accounts)
            } catch (failure: Exception) {
                log.write(evaluationCommitFailureLine(causeCodeOf(failure)))
                termination.terminate(EvaluationCommitExitCode.FAILED.value)
                return
            }
        val tally = tallyOf(outcome.results)
        val exitCode = exitCodeOf(tally)
        log.write(evaluationCommitFinishLine(tally, outcome.evaluated, outcome.skipped, exitCode))
        termination.terminate(exitCode.value)
    }

    private fun evaluateEach(accounts: List<AccountId>): AccountRunOutcome {
        val results = mutableListOf<CandidateEvaluation>()
        var evaluated = 0
        var skipped = 0
        accounts.forEach { account ->
            val run = runs.forAccount(account)
            if (run == null) {
                skipped++
            } else {
                evaluated++
                results += runBlocking { run.evaluate() }
            }
        }
        return AccountRunOutcome(results, evaluated, skipped)
    }
}

/**
 * 계정 순회의 결과 — **계수 둘을 결과와 함께** 나른다(세는 자리를 둘로 두지 않는다, 로그와 종료
 * 코드가 같은 값에서 나오는 `EvaluationCommitTally` 와 같은 규율).
 */
private class AccountRunOutcome(
    val results: List<CandidateEvaluation>,
    val evaluated: Int,
    val skipped: Int,
)
