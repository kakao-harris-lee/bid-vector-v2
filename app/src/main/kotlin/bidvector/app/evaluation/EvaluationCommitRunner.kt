package bidvector.app.evaluation

import bidvector.adapters.evaluation.EvaluationCommitRuns
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.relay.causeCodeOf
import bidvector.sharedkernel.AccountId
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.notificationinbox.NotificationAudience
import bidvector.workflow.strategy.Clock
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import java.time.LocalDate
import java.time.ZoneId

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
 * **M15/15B — run 하나가 쓰는 비용에 상한이 둘 있다**(`OPEN-M15-EVAL-COST-PER-ACCOUNT`,
 * 팀장 결정 2026-11-22). [maxAccountsPerRun] 은 한 run 이 도는 계정 수를 묶고,
 * [maxConsecutiveAccountFailures] 는 끊기지 않는 실패에서 run 을 멈춘다. 둘 다 **미룬 계정**을
 * 남기고 그 수는 마침 줄의 `deferred` 다 — 실패가 아니므로 종료 코드를 바꾸지 않는다.
 *
 * **상한 밖은 회전한다**([accountsForRun]) — 앞 판은 `take(n)` 고정 절단이라 상한 밖 계정이
 * **어느 run 에서도** 평가되지 않았고, 줄은 그것을 「미룸」이라 불렀다(r3 리뷰 M-1·F-1).
 * 이제 창이 **영업일마다 미끄러져** 모두가 `ceil((K-1)/(n-1))` 일 안에 한 번씩 돈다. 날짜는
 * 주입된 [clock] 에서 오므로 **같은 날의 재실행은 같은 집합**이다(재실행이 공평성을 흔들지
 * 않는다 — 그래서 「더 자주 돌린다」는 처방이 없다).
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
    private val maxAccountsPerRun: Int,
    private val maxConsecutiveAccountFailures: Int,
    private val clock: Clock,
    private val log: CollectionLog,
    private val termination: CollectionTermination,
) : ApplicationRunner {
    init {
        require(maxAccountsPerRun >= 1) { "run 당 계정 상한은 1 이상이어야 한다: $maxAccountsPerRun" }
        require(maxConsecutiveAccountFailures >= 1) {
            "연속 실패 상한은 1 이상이어야 한다: $maxConsecutiveAccountFailures"
        }
    }

    /**
     * 예외를 Spring 에 넘기지 않고 이 자리에서 종료 코드로 옮긴다(cr L-4 — relay 러너와 같다).
     * 보조 타입을 두지 않는 이유도 같다(app 최상위 타입이 늘면 조립 장부가 함께 움직인다) —
     * 그래서 계정 순회의 계수는 값 타입이 아니라 [evaluateAccounts] 의 지역 변수로 산다.
     */
    override fun run(args: ApplicationArguments) {
        log.write(evaluationCommitStartLine(candidateCap, maxAccountsPerRun))
        when (val audience = runs.accounts()) {
            is NotificationAudience.Accounts -> {
                evaluateAccounts(audience.accounts)
            }

            is NotificationAudience.Unavailable -> {
                log.write(evaluationCommitAudienceFailureLine(audience.exceptionType))
                termination.terminate(EvaluationCommitExitCode.FAILED.value)
            }
        }
    }

    /**
     * **한 계정의 실패는 그 계정에서 끝난다**(r1 리뷰 MEDIUM). 앞 판은 `try` 가 순회 전체를 감아
     * 계정 #2 의 예외가 #3~#N 의 평가를 **통째로** 삼켰다 — 상한 미설정은 건너뛰고 세면서 던지는
     * 계정은 뒤를 다 끊는 비대칭이었고, 15B 가 가입을 열면 아무 가입자나 운영자의 평가를 멈출 수
     * 있다. 이제 실패도 **센다**: 그 계정의 판정은 사라지되 그 사실이 계수와 종료 코드에 남는다.
     *
     * **끊기지 않는 실패는 다르다**(M15/15B) — 그것은 그 계정들의 일이 아니라 체계가 내려간
     * 신호이고, 그때 남은 계정을 계속 도는 것은 K × 타임아웃을 치르는 일이다. 멈춘 자리 뒤는
     * 상한 밖과 **같은 계수**(`deferred`)로 세고, 왜 멈췄는지는 따로 한 줄이 말한다.
     *
     * **계정 0 과 전원 건너뜀은 종료 코드를 바꾸지 않는다**(r1 리뷰 LOW, 결정). 「평가할 계정이
     * 없다」는 갓 설치한 체계의 정상 상태이고, 그것을 비-0 으로 만들면 빈 체계가 매일 운영자를
     * 깨운다. 대신 **계수가 말한다** — `accounts=0` 과 `skipped=N` 이 종료 줄에 선다.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun evaluateAccounts(accounts: List<AccountId>) {
        // 전역 발송 채널을 쥘 계정 — 비었다는 것은 **순회 자체가 없다**는 것과 같은 뜻이다.
        val channelHolder = channelHolderOf(accounts)
        if (channelHolder == null) {
            finishRun(emptyList(), evaluated = 0, skipped = 0, failed = 0, deferred = 0)
            return
        }
        // **id 순으로 세운 뒤 회전 창을 뜬다** — 정렬을 질의에 기대지 않는 이유는 보유자 선택과
        // 같다. 창의 사유와 공평성 등식은 [accountsForRun] KDoc 이 든다.
        val ordered = accounts.sortedWith(BY_ACCOUNT_VALUE)
        val taken = accountsForRun(ordered, maxAccountsPerRun, businessDay())
        val results = mutableListOf<CandidateEvaluation>()
        var evaluated = 0
        var skipped = 0
        var failed = 0
        var consecutiveFailures = 0
        var stoppedAt = -1
        for ((index, account) in taken.withIndex()) {
            try {
                if (evaluateOne(account, channelHolder, results)) {
                    evaluated++
                } else {
                    skipped++
                }
                // **연속이 끊긴다** — 한 건이라도 지나가면 계수가 0 으로 돌아간다.
                consecutiveFailures = 0
            } catch (failure: Exception) {
                failed++
                consecutiveFailures++
                noteAccountFailure(failure)
                if (consecutiveFailures >= maxConsecutiveAccountFailures) {
                    stoppedAt = index + 1
                    break
                }
            }
        }
        val stoppedRemainder = if (stoppedAt < 0) 0 else taken.size - stoppedAt
        val deferred = (ordered.size - taken.size) + stoppedRemainder
        if (stoppedAt >= 0) {
            // `remaining` 은 **run 전체의 남은 수**다(= 마침 줄의 `deferred`, r3 CR L-2) — 창
            // 안쪽만 세면 두 원천이 함께 설 때 두 줄의 수가 달라지고 그 관계가 어디에도 없다.
            log.write(evaluationCommitStoppedLine(consecutiveFailures, deferred))
        }
        finishRun(results, evaluated, skipped, failed, deferred)
    }

    /**
     * 회전 창의 축이 되는 **영업일**. 구역은 KST 다 — 창이 미끄러지는 경계가 운영자의 하루와
     * 같아야 하고, UTC 로 고르면 KST 00~09 시의 run 이 **어제 창**으로 돈다. 이 러너의 자리는
     * 외부 cron 이고 그 시각이 그 구간에 들 수 있다(수집 레인 00:41 선례).
     */
    private fun businessDay(): Long = LocalDate.ofInstant(clock.now(), EVALUATION_ROTATION_ZONE).toEpochDay()

    /** 계정 하나를 평가한다 — 돌려주는 것은 「평가가 돌았나」이고, 전략이 없으면 `false` 다. */
    private fun evaluateOne(
        account: AccountId,
        channelHolder: AccountId,
        results: MutableList<CandidateEvaluation>,
    ): Boolean {
        val run = runs.forAccount(account, channelHolder) ?: return false
        results += runBlocking { run.evaluate() }
        return true
    }

    /**
     * 실패 한 건을 줄로 남긴다. `InterruptedException` 은 **플래그를 되세우고** 흘린다
     * (15A r1 리뷰 LOW ⓒ) — 삼키면 이 스레드의 다음 blocking 호출이 인터럽트를 보지 못한다.
     */
    private fun noteAccountFailure(failure: Exception) {
        log.write(evaluationCommitAccountFailureLine(causeCodeOf(failure)))
        if (failure is InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** 마침 줄과 종료 코드가 **같은 값에서** 나오는 자리 하나 — 두 자리에 세지 않는다. */
    private fun finishRun(
        results: List<CandidateEvaluation>,
        evaluated: Int,
        skipped: Int,
        failed: Int,
        deferred: Int,
    ) {
        val tally = tallyOf(results)
        val exitCode = exitCodeOf(tally, failed)
        log.write(evaluationCommitFinishLine(tally, evaluated, skipped, failed, deferred, exitCode))
        termination.terminate(exitCode.value)
    }
}

/**
 * **전역 발송 채널을 쥘 계정**(계약 「Telegram」) — 목록 순서가 아니라 **값**으로 고른다. 청중
 * 질의가 `ORDER BY id` 를 잃어도 이 선택은 같다. 비었으면 `null` 이고 순회 자체가 없다.
 *
 * 러너 본문에서 **함수로 뽑았다**(15A r1 리뷰 LOW ⓐ — 15B 로 넘어온 축). 앞 판은 그 식이 본문
 * 안에 있어 `minByOrNull` 을 `firstOrNull` 로 바꿔도 초록이었다(청중이 늘 오름차순이었다).
 * 값 함수라 `[2, 1]` 한 건으로 그 변이가 붉는다.
 */
internal fun channelHolderOf(accounts: List<AccountId>): AccountId? = accounts.minByOrNull { it.value }

/**
 * 명시 Comparator 다 — `sortedBy` 는 stdlib 출처(`Comparisons.kt`)의 합성 비교자 클래스를 산출물에
 * 남겨 `jarContentGate`·`packageOwnershipGate`(게이트를 통과한 소스만 아카이브에 든다)가 거부한다.
 * 선례는 `bidvector.adapters.snapshot` 의 행 정렬이다.
 */
private val BY_ACCOUNT_VALUE =
    Comparator<AccountId> { left, right ->
        left.value.compareTo(right.value)
    }

/**
 * 회전 창이 미끄러지는 달력 구역 — **KST**. 축마다 제 구역 상수를 두는 것이 이 저장소의 관례다
 * (`NOTICE_MESSAGE_ZONE`·`RETENTION_CALENDAR_ZONE`) — 한 자리에만 사유를 적으면 다른 축의 근거가
 * 사라진다. 여기서의 사유는 「창이 바뀌는 경계가 운영자의 하루와 같아야 한다」다.
 */
private val EVALUATION_ROTATION_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/**
 * 이 run 이 도는 계정 — **보유자 하나 + 나머지에서 회전하는 창**이다(r3 리뷰 M-1·F-1,
 * 팀장 결정 2026-11-22).
 *
 * [ordered] 는 id 오름차순이고 그 **첫 원소가 전역 발송 채널 보유자**다([channelHolderOf] 와 같은
 * 값) — 창이 어떻게 움직여도 보유자는 빠지지 않는다. 남은 `n-1` 칸이 나머지 `K-1` 계정 위를
 * **영업일마다 `n-1` 칸씩** 미끄러지므로, 연속한 `ceil((K-1)/(n-1))` 영업일이면 모든 계정이 적어도
 * 한 번 돈다. 앞 판은 `take(n)` 고정 절단이라 `n` 번째 뒤의 계정이 **영원히** 빠졌고 — 상한이
 * 오르거나 더 작은 id 가 사라질 때까지 — 종료 코드는 0, cron 은 성공이었다.
 *
 * 상한이 청중보다 크거나 같으면 창이 서지 않는다(전원 평가, `deferred=0`). `n = 1` 이면 칸이 0 이라
 * **보유자만** 돈다 — 그 설정에서는 공평성이 성립하지 않고, 위 등식의 분모가 0 인 자리다.
 *
 * 회전은 날짜의 **순수 함수**다 — 같은 날 다시 돌리면 같은 집합이고, 영속 커서를 두지 않는다
 * (그 상태를 두면 되돌림과 재실행이 서로 다른 판을 만든다).
 */
internal fun accountsForRun(
    ordered: List<AccountId>,
    maxAccountsPerRun: Int,
    businessDay: Long,
): List<AccountId> {
    val others = ordered.drop(1)
    val slots = maxAccountsPerRun - 1
    if (others.size <= slots) return ordered
    val span = others.size
    val start = Math.floorMod(businessDay * slots.toLong(), span.toLong()).toInt()
    return listOf(ordered.first()) + List(slots) { others[(start + it) % span] }
}
