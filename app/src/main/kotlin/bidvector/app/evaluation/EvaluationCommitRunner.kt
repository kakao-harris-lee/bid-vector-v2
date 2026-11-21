package bidvector.app.evaluation

import bidvector.adapters.evaluation.EvaluationCommitRuns
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.relay.causeCodeOf
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
     * **한 계정의 실패는 그 계정에서 끝난다**(r1 리뷰 MEDIUM). 앞 판은 `try` 가 순회 전체를 감아
     * 계정 #2 의 예외가 #3~#N 의 평가를 **통째로** 삼켰다 — 상한 미설정은 건너뛰고 세면서 던지는
     * 계정은 뒤를 다 끊는 비대칭이었고, 15B 가 가입을 열면 아무 가입자나 운영자의 평가를 멈출 수
     * 있다. 이제 실패도 **센다**: 그 계정의 판정은 사라지되 그 사실이 계수와 종료 코드에 남는다.
     *
     * **계정 0 과 전원 건너뜀은 종료 코드를 바꾸지 않는다**(r1 리뷰 LOW, 결정). 「평가할 계정이
     * 없다」는 갓 설치한 체계의 정상 상태이고, 그것을 비-0 으로 만들면 빈 체계가 매일 운영자를
     * 깨운다. 대신 **계수가 말한다** — `accounts=0` 과 `skipped=N` 이 종료 줄에 선다. 그 둘을
     * 경보로 바꾸는 것은 cron 쪽의 일이고, 여기서 재는 것은 「계수가 사실인가」다(test 둘).
     */
    @Suppress("TooGenericExceptionCaught")
    override fun run(args: ApplicationArguments) {
        log.write(evaluationCommitStartLine(candidateCap))
        val accounts =
            when (val audience = runs.accounts()) {
                is NotificationAudience.Accounts -> {
                    audience.accounts
                }

                is NotificationAudience.Unavailable -> {
                    log.write(evaluationCommitAudienceFailureLine(audience.exceptionType))
                    termination.terminate(EvaluationCommitExitCode.FAILED.value)
                    return
                }
            }
        // **전역 발송 채널을 쥘 계정은 가장 작은 id 다**(계약 「Telegram」). 목록 순서에 기대지
        // 않고 값으로 고른다 — 청중 질의가 `ORDER BY id` 를 잃어도 이 선택은 같다. 비었으면
        // 순회 자체가 없고 계수 셋이 0 으로 선다.
        val channelHolder = accounts.minByOrNull { it.value }
        val results = mutableListOf<CandidateEvaluation>()
        var evaluated = 0
        var skipped = 0
        var failed = 0
        if (channelHolder != null) {
            accounts.forEach { account ->
                try {
                    val run = runs.forAccount(account, channelHolder)
                    if (run == null) {
                        skipped++
                    } else {
                        evaluated++
                        results += runBlocking { run.evaluate() }
                    }
                } catch (failure: Exception) {
                    failed++
                    log.write(evaluationCommitAccountFailureLine(causeCodeOf(failure)))
                }
            }
        }
        val tally = tallyOf(results)
        val exitCode = exitCodeOf(tally, failed)
        log.write(evaluationCommitFinishLine(tally, evaluated, skipped, failed, exitCode))
        termination.terminate(exitCode.value)
    }
}
