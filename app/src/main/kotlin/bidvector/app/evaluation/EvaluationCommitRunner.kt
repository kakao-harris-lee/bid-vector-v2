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
 * 이제 창이 **KST 달력일마다 한 칸씩 미끄러져**, run 사이의 최대 간격이 `g` 일이고 `g ≤ n-1` 인
 * cron 이면 모두가 **`K-n+g` 달력일** 안에 한 번씩 돈다(매 달력일 cron 은 `g=1` 이라 `K-n+1`).
 * 날짜는 주입된 [clock] 에서 오므로 **같은 날의 재실행은 같은 집합**이다(재실행이 공평성을
 * 흔들지 않는다 — 그래서 「더 자주 돌린다」는 처방이 없다).
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
    // **가드의 정본은 `EvaluationProperties.init` 이다**(r4 OQ ③) — 출하 경로의 값은 거기서
    // 바인딩 시점에 걸러지고 `EvaluationCommitWiringTest` 가 그 거부를 잰다. 여기 둘은 **test 가
    // 러너를 직접 조립하는 경로의 방어**다(그 조립은 설정 타입을 지나지 않는다). 한쪽만 완화되면
    // 조용해지므로 어느 쪽이 정본인지 적어 둔다.
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
        // 멈춤은 **한 값**이다(r5 CR L-2 — **첫째 = 멈춘 자리(1-based) · 둘째 = 사유**, 15F r1
        // CR L-9). 둘을 따로 두면 한쪽만 세우는 변경이 줄에 틀린 사유를 찍고, 그 줄은 없는 줄보다 나쁘다.
        var stopped: Pair<Int, String>? = null
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
                // **플래그를 되세우는 것이 먼저다** — 멈춤 판정이 그 플래그를 읽는다.
                noteAccountFailure(failure)
                val cause = stopCauseFor(consecutiveFailures)
                if (cause != null) {
                    stopped = (index + 1) to cause
                    break
                }
            }
        }
        val stoppedRemainder = stopped?.let { (at, _) -> taken.size - at } ?: 0
        val deferred = (ordered.size - taken.size) + stoppedRemainder
        stopped?.let { (_, cause) ->
            // `remaining` 은 **run 전체의 남은 수**다(= 마침 줄의 `deferred`, r3 CR L-2) — 창
            // 안쪽만 세면 두 원천이 함께 설 때 두 줄의 수가 달라지고 그 관계가 어디에도 없다.
            log.write(evaluationCommitStoppedLine(cause, consecutiveFailures, deferred))
        }
        finishRun(results, evaluated, skipped, failed, deferred)
    }

    /**
     * 이 실패에서 run 을 멈춰야 하는가 — 멈추지 않으면 `null` 이다.
     *
     * **인터럽트는 그 계정의 일이 아니다**(r4 CR L-4). 누가 이 프로세스를 껐다는 신호이고,
     * 인터럽트된 스레드에서는 다음 계정의 blocking 호출이 대개 **즉시** 다시 던진다 — 계속 돌면
     * 신호 **하나**가 `failed=1,2,3…` 으로 불어나 마침 줄이 「계정 셋이 터졌다」로 읽힌다.
     * 「체계가 내려갔다」와 「누가 나를 껐다」를 같은 모양으로 만들지 않는다.
     *
     * **그 신호는 예외 타입이 아니라 스레드의 인터럽트 플래그다**(15B r5 v F-1). 출하 DB 경로의
     * 인터럽트는 `InterruptedException` 으로 오지 않는다 — HikariCP 는 연결 획득 중 인터럽트를
     * 받으면 플래그를 되세운 뒤 `SQLException(…, InterruptedException)` 으로 **감싸** 던진다.
     * 타입을 열거하는 술어는 그 모양 집합을 따라잡지 못한다(`ClosedByInterruptException` ·
     * `InterruptedIOException` · 그것을 감싼 Spring `DataAccessException` · 드라이버마다 다른 사본).
     * 이 술어가 잡는 것은 **플래그를 되세우는 번역 전부**다(HikariCP 가 그렇다) — 감싼 예외도
     * **첫 건에서** 잡힌다(앞 판은 다음 계정의 raw 인터럽트까지 가서 `failed` 가 둘이었다 —
     * r5 CR L-3 의 상한 2 가 1 이 됐다).
     *
     * **되세우지 않는 계층은 여전히 사각이다**(15F r1 cr L-1). `InterruptedException` 을 던지는
     * 순간 인터럽트 상태는 **지워지므로**, 그것을 잡아 되세우지 **않고** 감싸는 계층(상류
     * 라이브러리 · 미래의 드라이버 · `Future.get` 류를 감싼 자리)이 끼면 플래그는 꺼진 채 오고 이
     * 술어는 앞 판과 똑같이 눈이 먼다 — 그때는 연속 상한이 다시 유일한 상한이다.
     * [noteAccountFailure] 는 **정확히 `InterruptedException` 일 때만** 되세우므로 감싼 예외를
     * 구제하지 못한다. 그 사각은 `reports/evidence/m15/15f/scope.md` 알려진 제한이 든다.
     *
     * **인터럽트가 연속 상한보다 앞이다** — 둘이 같은 실패에서 함께 참일 수 있고(상한에 닿은 그
     * 건이 마침 인터럽트), 그때 사유를 연속 상한으로 적으면 「누가 껐다」가 어느 줄에도 남지 않는다.
     * 연속 수는 멈춤 줄의 `consecutive=` 칸에 그대로 실리므로 이 순서는 아무 정보도 잃지 않는다.
     *
     * **플래그의 범위는 run 이 아니라 스레드다 — 좁히지 않는 것이 결정이다**(15F r1 cr L-2).
     * 술어는 「이 run 중에 인터럽트가 왔는가」가 아니라 「지금 이 스레드에 플래그가 서 있는가」를
     * 묻는다. 진입 전에 남은 플래그가 있으면 무관한 첫 실패가 `interrupted` 로 적히지만, 머리에서
     * `Thread.interrupted()` 로 지워 범위를 좁히면 **run 시작 직전에 온 진짜 종료 신호를 삼킨다** —
     * 일회성 `ApplicationRunner` 라 그 창이 기동 직후에 실제로 있다. 교환이 이익이 아니다.
     *
     * 호출 순서가 전제다: [noteAccountFailure] 가 raw `InterruptedException` 의 플래그를 **먼저**
     * 되세운다(`runBlocking` 의 `joinBlocking` 이 읽으면서 지우기 때문이다).
     */
    private fun stopCauseFor(consecutiveFailures: Int): String? =
        when {
            Thread.currentThread().isInterrupted -> STOP_CAUSE_INTERRUPTED
            consecutiveFailures >= maxConsecutiveAccountFailures -> STOP_CAUSE_CONSECUTIVE_FAILURES
            else -> null
        }

    /**
     * 회전 창의 축이 되는 **KST 달력일**(주말·공휴일을 포함한다 — 「영업일」이 아니다). 구역이
     * KST 인 것은 창이 미끄러지는 경계가 운영자의 하루와 같아야 해서다. UTC 로 고르면 KST
     * 00~09 시의 run 이 **어제 창**으로 돈다 — 이 러너의 자리는 외부 cron 이고 그 시각이 그 구간에
     * 들 수 있다(수집 레인 00:41 선례).
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
     * (15A r1 리뷰 LOW ⓒ) — `runBlocking` 의 `joinBlocking` 이 그 플래그를 **읽으면서 지우므로**
     * 되세우지 않으면 신호가 여기서 끝난다.
     *
     * 되세운 플래그가 닿는 자리는 이제 **둘**이다 — 바로 뒤의 [stopCauseFor] 판정과, 멈춘 뒤의
     * **종료 경로**(`SpringApplication.exit` 의 context close). 멈춘 자리 뒤로 `runBlocking` 재진입이
     * 없으므로 「다음 계정의 blocking 호출」은 더 이상 이 문장의 대상이 아니다(15F A-4f).
     * 종료 직전에 지우지 않는 것은 결정이다: `SpringApplication.exit` 의 context close 는 **이
     * 스레드에서** 돌고, 지우면 그 경로가 인터럽트를 보지 못해 15A r1 이 닫은 결함을 종료
     * 경로에서 다시 연다. **JVM 종료 훅은 이 축이 아니다**(15F r1 v F-3) — 훅은 별 스레드에서
     * 돌아 이 플래그와 무관하다.
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
 * **KST 달력일마다 한 칸씩** 미끄러진다. 앞 판은 `take(n)` 고정 절단이라 `n` 번째 뒤의 계정이
 * **영원히** 빠졌고 — 상한이 오르거나 더 작은 id 가 사라질 때까지 — 종료 코드는 0, cron 은
 * 성공이었다.
 *
 * **보폭이 1 인 것이 결정이다**(M15/15H, `OPEN-M15-EVAL-COST-PER-ACCOUNT` ⓐ). 보폭이 `n-1` 이던
 * 앞 판에서는 시작 위치가 `날짜 mod P`(`P = (K-1)/gcd(n-1, K-1)`)에만 달려 있어, 실행일이
 * **주기적** 부분집합이면(평일만 도는 timer) 그 잔여류가 `Z_P` 를 덮지 못해 **어느 창은 영원히
 * 오지 않았다** — `n=4`·`K=22` 에서 그 `P` 가 정확히 7 이라 평일 timer 가 계정 여섯을 영구히
 * 뺐고, 종료 코드는 0 이었다. 일반화하면 실행일이 **공차 `c` 의 등차수열**일 때 시작 위치가 도는
 * 집합의 거칢은 `gcd(보폭 × c, K-1)` 이고, 보폭 1 은 그 값을 **cron 자신의 공차에만** 달리게 하는
 * 최선의 선택이다 — 요일 패턴을 열거해 막는 것이 아니다.
 *
 * **그 식이 평일 timer 를 그대로 설명하지는 않는다** — 평일 실행일은 등차수열 하나가 아니라 `7k`
 * 평행이동 다섯의 합집합이라 등차수열보다 **쉬운** 경우다. 그러니 보증은 그 식이 아니라 **간격**으로
 * 적는다. **run 사이의 최대 달력일 간격을 `g` 라 할 때, `g ≤ n-1` 이면 `K-n+g` 달력일 안에 전원이
 * 한 번씩 돈다.** 창이 하루에 한 칸만 미끄러지므로 간격이 칸 수(`n-1`)를 넘지 않는 한 덮개가
 * 끊기지 않고(그 조건이 `g ≤ n-1` 이다), 창 하나가 `n-1` 칸을 덮으므로 마지막 run 이 창 끝에서
 * 최대 `g-1` 일 앞설 수 있다 — 그 `g-1` 이 `+g` 의 출처다. **`g` 가 1 이 아니면 경계가 는다**:
 * 매 달력일 cron 은 `g=1` 이라 `K-n+1` 이고, 평일만 도는 timer 는 금→월이 있어 `g=3` 이므로
 * `K-n+3` 이다(`n=4`·`K=22` → 21 일 · `n=50`·`K=200` → 153 일 — 둘 다 실측으로 **딱 맞는**
 * 경계다). 대가는 **한 바퀴가 길어지는 것**이다: 매 달력일 cron 에서 `ceil((K-1)/(n-1))` 이던 한
 * 바퀴가 `K-n+1` 달력일이 된다. 그 교환은 위 OPEN 행이 들고 있고, 한 바퀴를 되찾는 축
 * (실행 순번·영속 커서)은 ML 배선 slice 소관이다.
 *
 * **날짜의 순수 함수로는 어떤 선택도 전칭이 될 수 없다** — 실행일 집합이 그 함수의 주기와 같은
 * 주기를 가지면 창 하나가 영원히 오지 않는다. 보폭 1 이 남기는 사각은 `n` 이 2·3 이면서 나머지
 * 수가 7 의 배수인 조합이고(출하 기본값 `n=50` 은 그 밖이다), 처방된 timer 는 여전히 매 달력일
 * 돈다(`docs/runbook/m7-7f-production.md` §13.3 `OnCalendar=*-*-*`).
 *
 * 상한이 청중보다 크거나 같으면 창이 서지 않는다(전원 평가). `n = 1` 이면 칸이 0 이라 **보유자만**
 * 돈다 — 그 설정에서는 공평성이 성립하지 않는다.
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
    val start = Math.floorMod(businessDay, span.toLong()).toInt()
    return listOf(ordered.first()) + List(slots) { others[(start + it) % span] }
}
