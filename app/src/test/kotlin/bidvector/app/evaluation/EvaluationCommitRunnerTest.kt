package bidvector.app.evaluation

import bidvector.app.wiring.RecordedExitCodes
import bidvector.sharedkernel.AccountId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import java.sql.SQLException

// 회전 축을 재는 판 — 청중 K=10 · 상한 n=4 라 칸이 셋이고, ceil((K-1)/(n-1)) = ceil(9/3) = 3 일이면
// 전원이 한 번씩 돈다. 세 수가 함께 움직여야 하므로 한자리에 둔다.
private val ROTATION_AUDIENCE: List<Long> = (1L..10L).toList()
private const val ROTATION_CAP = 4
private const val ROTATION_DAYS = 3

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
    /**
     * **한 계정이 던져도 run 은 끝까지 간다**(r1 리뷰 MEDIUM — 주장을 뒤집었다). 앞 판은 이
     * 자리에서 `FAILED`(1) 로 **즉시 끝났고**, 그것은 계정이 하나일 때만 옳은 거동이었다 —
     * 계정 #2 가 던지면 #3~#N 이 평가되지 않는데 그 사실이 어디에도 남지 않았다.
     *
     * 이제 그 계정만 실패로 **세고** 종료 코드는 `INCOMPLETE`(2) 다. 「판정이 사라졌다」는
     * 여전히 비-0 이고, 사라진 범위가 **계수**로 보인다.
     */
    @Test
    fun `협력자가 던지면 그 계정만 실패로 세고 INCOMPLETE 2 로 끝낸다`() {
        val log = RecordingCommitLog()
        val termination = RecordedExitCodes()

        runnerFor(deadCommitRuns(), log, termination)
            .run(DefaultApplicationArguments())

        termination.recorded() shouldBe listOf(EvaluationCommitExitCode.INCOMPLETE.value)
        val lines = log.written.joinToString("\n")
        lines shouldContain "evaluation-commit start"
        lines shouldContain "evaluation-commit account-failed cause="
        val finishLine = log.written.single { it.startsWith("evaluation-commit finished") }
        finishLine shouldContain "accounts=0"
        finishLine shouldContain "failed=1"
    }

    /**
     * **던진 계정이 뒤 계정을 끊지 않는다** — r1 리뷰가 지목한 실제 결함의 측정이다. 계정 둘 중
     * 작은 쪽이 전략 읽기에서 던지고, 큰 쪽은 정상 평가된다(후보가 0 이라 효과는 없다).
     * 앞 판에서는 이 단언의 `accounts=1` 이 **0** 이었다.
     */
    @Test
    fun `한 계정이 던져도 나머지 계정은 평가된다`() {
        val log = RecordingCommitLog()
        val termination = RecordedExitCodes()

        runnerFor(partiallyDeadCommitRuns(), log, termination)
            .run(DefaultApplicationArguments())

        termination.recorded() shouldBe listOf(EvaluationCommitExitCode.INCOMPLETE.value)
        val finishLine = log.written.single { it.startsWith("evaluation-commit finished") }
        finishLine shouldContain "accounts=1"
        finishLine shouldContain "failed=1"
        finishLine shouldContain "skipped=0"
    }

    /**
     * **청중이 비면 아무도 평가하지 않고 그 사실이 계수로 선다**(r1 리뷰 LOW — 결정과 문서화).
     * 종료 코드는 0 이다: 「평가할 계정이 없다」는 갓 설치한 체계의 정상 상태이고, 비-0 으로
     * 만들면 빈 체계가 매일 운영자를 깨운다. 「못 읽었다」와 가르는 것은 **종료 코드가 아니라
     * 계수**이고(그쪽은 FAILED 1), 그 둘이 구별된다는 것이 여기서 재는 것이다.
     */
    @Test
    fun `청중이 비면 계수가 0 이고 종료 코드는 0 이다`() {
        val log = RecordingCommitLog()
        val termination = RecordedExitCodes()

        runnerFor(emptyAudienceRuns(), log, termination)
            .run(DefaultApplicationArguments())

        termination.recorded() shouldBe listOf(EvaluationCommitExitCode.COMPLETE.value)
        val finishLine = log.written.single { it.startsWith("evaluation-commit finished") }
        finishLine shouldContain "accounts=0"
        finishLine shouldContain "skipped=0"
        finishLine shouldContain "failed=0"
        log.written.none { it.startsWith("evaluation-commit failed") } shouldBe true
    }

    /**
     * 실패 줄은 **예외 메시지를 싣지 않는다** — 클래스 이름까지다. 메시지에 접속 문자열을
     * 심어 두고 그것이 로그에 없음을 잰다(relay 러너와 같은 규율, 그쪽은 SQLSTATE 축).
     */
    @Test
    fun `계정 실패 줄은 예외 메시지를 싣지 않는다`() {
        val log = RecordingCommitLog()

        runnerFor(deadCommitRuns(), log, RecordedExitCodes())
            .run(DefaultApplicationArguments())

        val failureLine = log.written.single { it.startsWith("evaluation-commit account-failed") }
        failureLine shouldContain IllegalStateException::class.java.name
        failureLine shouldNotContain "jdbc:"
        failureLine shouldNotContain "secret-host"
    }

    /**
     * **M15/15A — 계정 전수를 못 읽으면 시작하지 않는다.** 그 0 은 사실의 0 이 아니다.
     * 사유 토큰에는 예외 **클래스 이름**만 실린다(청중 포트가 그만큼만 나른다).
     */
    @Test
    fun `계정 전수를 못 읽으면 평가를 시작하지 않고 FAILED 1 로 끝낸다`() {
        val log = RecordingCommitLog()
        val termination = RecordedExitCodes()

        runnerFor(audienceUnavailableRuns(), log, termination)
            .run(DefaultApplicationArguments())

        termination.recorded() shouldBe listOf(EvaluationCommitExitCode.FAILED.value)
        val failureLine = log.written.single { it.startsWith("evaluation-commit failed") }
        failureLine shouldContain "audience-unavailable:SQLException"
        log.written.none { it.startsWith("evaluation-commit finished") } shouldBe true
    }

    /**
     * **M15/15B — run 당 계정 상한**(`OPEN-M15-EVAL-COST-PER-ACCOUNT`, 팀장 결정 2026-11-22).
     * 상한 밖 계정은 **미룬 것**이지 실패가 아니다 — 종료 코드가 0 으로 남는 것이 그 사실이다.
     */
    @Test
    fun `상한을 넘는 계정은 미루고 종료 코드를 바꾸지 않는다`() {
        val log = RecordingCommitLog()
        val termination = RecordedExitCodes()

        runnerFor(healthyRuns(accounts = 5), log, termination, maxAccountsPerRun = 2)
            .run(DefaultApplicationArguments())

        termination.recorded() shouldBe listOf(EvaluationCommitExitCode.COMPLETE.value)
        val finishLine = log.written.single { it.startsWith("evaluation-commit finished") }
        finishLine shouldContain "accounts=2"
        finishLine shouldContain "deferred=3"
        finishLine shouldContain "failed=0"
        log.written.single { it.startsWith("evaluation-commit start") } shouldContain "maxAccountsPerRun=2"
        // 네 계수가 청중과 닫힌다(r3 CR L-8) — `deferred` 산술이 한 칸 밀리면 이 줄이 붉는다.
        accountCountsOf(log.written).sum() shouldBe 5
    }

    /**
     * **상한 밖은 회전한다**(r3 리뷰 M-1·F-1, 팀장 결정 2026-11-22). 앞 판은 `take(n)` 고정
     * 절단이라 `n` 번째 뒤의 계정이 **어느 run 에서도** 평가되지 않았다 — 줄은 그것을 「미룸」이라
     * 불렀고 cron 은 성공을 적었다. 이제 창이 **KST 달력일**마다 `n-1` 칸씩 미끄러져
     * `ceil((K-1)/(n-1))` 달력일이면 전원이 한 번씩 돈다(K=10 · n=4 → 3 일). 축이 「영업일」이
     * **아닌** 것이 요점이다 — 주말·공휴일을 포함한다(15B r5 CR L-1 · `accountsForRun` KDoc).
     *
     * 오프셋을 0 으로 고정하는 변이는 세 날의 집합을 같게 만들어 **두 단언 모두** 붉는다.
     */
    @Test
    fun `연속한 KST 달력일이면 모든 계정이 한 번씩 돈다 — 보유자는 매일 든다`() {
        val coverage = mutableSetOf<Long>()
        val perDay = mutableListOf<List<Long>>()

        repeat(ROTATION_DAYS) { offset ->
            val evaluated = mutableListOf<Long>()
            runnerFor(
                recordingRuns(ROTATION_AUDIENCE, evaluated),
                RecordingCommitLog(),
                RecordedExitCodes(),
                maxAccountsPerRun = ROTATION_CAP,
                clock = clockAt(FIXED_DATE.plusDays(offset.toLong())),
            ).run(DefaultApplicationArguments())

            evaluated.size shouldBe ROTATION_CAP
            evaluated.first() shouldBe ROTATION_AUDIENCE.first()
            perDay += evaluated
            coverage += evaluated
        }

        coverage shouldBe ROTATION_AUDIENCE.toSet()
        perDay.toSet().size shouldBe ROTATION_DAYS
    }

    /**
     * **회전 축은 날짜다** — 같은 날 다시 돌리면 같은 집합이 돈다. 그래서 상한 밖 계정을 오늘
     * 보려면 「더 자주 돌린다」가 아니라 상한을 올려야 한다(runbook §3.4 의 처방이 그 문장이다).
     */
    @Test
    fun `같은 날 다시 돌리면 같은 집합이다 — 더 자주 돌려도 바뀌지 않는다`() {
        val first = mutableListOf<Long>()
        val second = mutableListOf<Long>()

        runnerFor(
            recordingRuns(ROTATION_AUDIENCE, first),
            RecordingCommitLog(),
            RecordedExitCodes(),
            maxAccountsPerRun = ROTATION_CAP,
        ).run(DefaultApplicationArguments())
        runnerFor(
            recordingRuns(ROTATION_AUDIENCE, second),
            RecordingCommitLog(),
            RecordedExitCodes(),
            maxAccountsPerRun = ROTATION_CAP,
        ).run(DefaultApplicationArguments())

        second shouldBe first
    }

    /** 상한이 계정 수보다 크면 미룬 계정이 0 이다 — 상한이 평소에 아무것도 바꾸지 않는다. */
    @Test
    fun `상한이 넉넉하면 미룬 계정이 0 이다 — 대조`() {
        val log = RecordingCommitLog()

        runnerFor(healthyRuns(accounts = 3), log, RecordedExitCodes(), maxAccountsPerRun = 50)
            .run(DefaultApplicationArguments())

        val finishLine = log.written.single { it.startsWith("evaluation-commit finished") }
        finishLine shouldContain "accounts=3"
        finishLine shouldContain "deferred=0"
    }

    /**
     * **운영 계정이 언제나 먼저다.** 청중이 순서를 잃어도(`[3, 1, 2]`) 상한 1 이 고르는 것은
     * 가장 작은 id 다 — 정렬을 질의에 기대지 않는다. 순회가 목록 순서였다면 3 이 평가된다.
     */
    @Test
    fun `상한이 1 이면 가장 작은 id 가 평가된다 — 청중 순서가 뒤섞여도`() {
        val log = RecordingCommitLog()
        val evaluatedAccounts = mutableListOf<Long>()

        runnerFor(
            recordingRuns(listOf(3L, 1L, 2L), evaluatedAccounts),
            log,
            RecordedExitCodes(),
            maxAccountsPerRun = 1,
        ).run(DefaultApplicationArguments())

        evaluatedAccounts shouldBe listOf(1L)
        log.written.single { it.startsWith("evaluation-commit finished") } shouldContain "deferred=2"
    }

    /**
     * **채널 보유자를 값으로 고른다**(15A r1 리뷰 LOW ⓐ — 15B 로 넘어온 축). 청중이 `[2, 1]` 이면
     * 보유자는 1 이다. `minByOrNull` 을 `firstOrNull` 로 바꾸면 2 가 되어 이 단언이 붉는다
     * (앞 판은 청중이 늘 오름차순이라 그 변이가 초록이었다).
     */
    @Test
    fun `채널 보유자는 가장 작은 id 다 — 청중이 내림차순이어도`() {
        // `AccountId` 는 값 비교를 제공하지 않는다(7D 규율) — **값**으로 센다.
        channelHolderOf(listOf(AccountId(2), AccountId(1)))?.value shouldBe 1L
        channelHolderOf(listOf(AccountId(3), AccountId(1), AccountId(2)))?.value shouldBe 1L
        channelHolderOf(emptyList()) shouldBe null
    }

    /**
     * **끊기지 않는 실패는 run 을 멈춘다**(팀장 결정 2026-11-22). 체계가 내려간 상태에서 남은
     * 계정을 계속 도는 것은 K × 타임아웃을 치르는 일이다. 멈춘 뒤의 나머지는 `deferred` 로 세고,
     * 종료 코드는 실패가 있으므로 `INCOMPLETE` 다.
     */
    @Test
    fun `연속 실패가 상한에 닿으면 남은 계정을 멈추고 미룬다`() {
        val log = RecordingCommitLog()
        val termination = RecordedExitCodes()
        val attempted = mutableListOf<Long>()

        runnerFor(
            failingRuns(listOf(1L, 2L, 3L, 4L, 5L), failing = setOf(1L, 2L, 3L, 4L, 5L), attempted = attempted),
            log,
            termination,
            maxConsecutiveAccountFailures = 3,
        ).run(DefaultApplicationArguments())

        termination.recorded() shouldBe listOf(EvaluationCommitExitCode.INCOMPLETE.value)
        attempted shouldBe listOf(1L, 2L, 3L)
        val finishLine = log.written.single { it.startsWith("evaluation-commit finished") }
        finishLine shouldContain "failed=3"
        finishLine shouldContain "deferred=2"
        val stoppedLine = log.written.single { it.startsWith("evaluation-commit stopped") }
        stoppedLine shouldContain "cause=consecutive-account-failures"
        // `remaining` 은 **run 전체의 남은 수** = 마침 줄의 `deferred`(r3 CR L-2).
        stoppedLine shouldContain "remaining=2"
        accountCountsOf(log.written).sum() shouldBe 5
    }

    /**
     * 두 원천이 **함께** 설 때의 수 — 청중 5 · 상한 3 · 연속 2 에서 멈춘다. 멈춤 줄의
     * `remaining` 과 마침 줄의 `deferred` 가 **같은 값**(상한 밖 2 + 창 안 나머지 1 = 3)이다.
     * 창 안쪽만 세던 앞 판은 여기서 `remaining=1` 을 적었고 그 관계가 어디에도 없었다.
     */
    @Test
    fun `상한 밖과 멈춤이 함께 서면 두 줄의 수가 같다`() {
        val log = RecordingCommitLog()
        val attempted = mutableListOf<Long>()

        runnerFor(
            failingRuns(listOf(1L, 2L, 3L, 4L, 5L), failing = setOf(1L, 2L, 3L, 4L, 5L), attempted = attempted),
            log,
            RecordedExitCodes(),
            maxAccountsPerRun = 3,
            maxConsecutiveAccountFailures = 2,
        ).run(DefaultApplicationArguments())

        attempted.size shouldBe 2
        log.written.single { it.startsWith("evaluation-commit stopped") } shouldContain "remaining=3"
        log.written.single { it.startsWith("evaluation-commit finished") } shouldContain "deferred=3"
        accountCountsOf(log.written).sum() shouldBe 5
    }

    /**
     * **건너뜀도 연속을 끊는다 — 그것이 결정이고 이 test 가 그 결정을 잠근다**(r3 verifier L-1).
     * 「전략이 없다」는 전략 **읽기가 성공했다**는 뜻이라 체계가 살아 있다는 증거다. 그래서 F S F S F
     * 는 연속 상한 2 에 닿지 않는다 — 비용은 run 당 계정 상한이 여전히 묶는다.
     *
     * 반대 의미(건너뜀은 연속을 안 끊는다)를 고르면 이 test 가 붉는다.
     */
    @Test
    fun `건너뜀이 연속 실패를 끊는다 — 흩어진 실패로 멈추지 않는다`() {
        val log = RecordingCommitLog()
        val attempted = mutableListOf<Long>()

        runnerFor(
            failingRuns(
                listOf(1L, 2L, 3L, 4L, 5L),
                failing = setOf(1L, 3L, 5L),
                attempted = attempted,
                skipping = setOf(2L, 4L),
            ),
            log,
            RecordedExitCodes(),
            maxConsecutiveAccountFailures = 2,
        ).run(DefaultApplicationArguments())

        attempted shouldBe listOf(1L, 2L, 3L, 4L, 5L)
        log.written.none { it.startsWith("evaluation-commit stopped") } shouldBe true
        accountCountsOf(log.written) shouldBe listOf(0, 2, 3, 0)
    }

    /**
     * **인터럽트는 삼키지도, 계속 돌지도 않는다.** 플래그를 되세우지 않으면 이 스레드의 다음
     * blocking 호출이 인터럽트를 보지 못하고(15A r1 리뷰 LOW ⓒ), 순회를 계속하면 종료 신호
     * **하나**가 `failed=1,2,3…` 으로 불어나 「체계가 내려갔다」와 같은 모양이 된다(r4 CR L-4).
     *
     * 그래서 첫 계정에서 멈추고, 남은 둘은 **미룬 것**으로 센다 — 실패는 하나다.
     */
    @Test
    fun `인터럽트 예외는 플래그를 되세우고 run 을 멈춘다`() {
        val log = RecordingCommitLog()
        val termination = RecordedExitCodes()
        val attempted = mutableListOf<Long>()

        runnerFor(
            failingRuns(
                listOf(1L, 2L, 3L),
                failing = setOf(1L, 2L, 3L),
                attempted = attempted,
                failure = { InterruptedException(DEAD_STORE_MESSAGE) },
            ),
            log,
            termination,
        ).run(DefaultApplicationArguments())

        // 읽으면서 **지운다** — 인터럽트된 채로 두면 같은 worker 의 뒤 test 가 그 상태를 물려받는다.
        Thread.interrupted() shouldBe true
        attempted shouldBe listOf(1L)
        termination.recorded() shouldBe listOf(EvaluationCommitExitCode.INCOMPLETE.value)
        log.written.count { it.startsWith("evaluation-commit account-failed") } shouldBe 1
        val stoppedLine = log.written.single { it.startsWith("evaluation-commit stopped") }
        stoppedLine shouldContain "cause=interrupted"
        stoppedLine shouldContain "remaining=2"
        accountCountsOf(log.written) shouldBe listOf(0, 0, 1, 2)
    }

    /**
     * **출하 DB 경로의 인터럽트는 이 모양으로 온다**(15B r5 v F-1). HikariCP 는 연결 획득 중
     * 인터럽트를 받으면 **플래그를 되세운 뒤** `SQLException(…, InterruptedException)` 으로 감싸
     * 던진다 — 예외 **타입**만 보는 술어는 그 예외에 `null`(계속 돈다)을 내고, 종료 신호 하나가
     * 남은 계정마다 타임아웃을 치르며 `failed` 를 불린다.
     *
     * 그래서 재는 것은 타입이 아니라 **플래그**다. 실패 줄은 여전히 `SQLException` 이라고 말하고
     * (그것이 사실이다) 멈춤 줄만 `interrupted` 라고 말한다 — 두 줄이 다른 것을 보고한다.
     */
    @Test
    fun `감싼 인터럽트도 플래그가 서 있으면 첫 건에서 멈춘다`() {
        val log = RecordingCommitLog()
        val termination = RecordedExitCodes()
        val attempted = mutableListOf<Long>()

        runnerFor(
            failingRuns(
                listOf(1L, 2L, 3L),
                failing = setOf(1L, 2L, 3L),
                attempted = attempted,
                // HikariCP 의 모양 그대로 — **플래그를 먼저 세우고** 감싼다.
                failure = {
                    Thread.currentThread().interrupt()
                    SQLException(DEAD_STORE_MESSAGE, InterruptedException())
                },
            ),
            log,
            termination,
        ).run(DefaultApplicationArguments())

        // 읽으면서 **지운다** — 인터럽트된 채로 두면 같은 worker 의 뒤 test 가 그 상태를 물려받는다.
        Thread.interrupted() shouldBe true
        attempted shouldBe listOf(1L)
        termination.recorded() shouldBe listOf(EvaluationCommitExitCode.INCOMPLETE.value)
        val failureLine = log.written.single { it.startsWith("evaluation-commit account-failed") }
        failureLine shouldContain SQLException::class.java.name
        // **SQL 갈래의 누출 축도 여기서 선다**(15F r1 cr L-8). `causeCodeOf` 는 갈래가 둘이고
        // (SQL 은 클래스 이름 + SQLSTATE, 그 밖은 클래스 이름) 출하에서 실제로 도는 갈래가 이쪽이다.
        // 심는 메시지가 접속 문자열 꼴을 담으므로 앞 test 와 **같은 두 줄**로 그 부재를 잠근다.
        failureLine shouldNotContain "jdbc:"
        failureLine shouldNotContain "secret-host"
        val stoppedLine = log.written.single { it.startsWith("evaluation-commit stopped") }
        stoppedLine shouldContain "cause=interrupted"
        stoppedLine shouldContain "remaining=2"
        accountCountsOf(log.written) shouldBe listOf(0, 0, 1, 2)
    }

    /**
     * **사유에는 우선순위가 있다 — 인터럽트가 연속 상한보다 앞이다**(15F r1 v F-1).
     *
     * 두 술어가 **같은 실패에서 함께 참**인 자리가 있다: 연속 실패가 상한에 닿은 바로 그 건이
     * 마침 인터럽트일 때다(배포가 내려가는 DB 를 만난 run 의 모양이다). 그때 줄이 무엇이라고
     * 말하는가는 **처방을 가른다** — `consecutive-account-failures` 는 「계정 쪽을 고치고 다시
     * 돌려라」로, `interrupted` 는 「고칠 자리가 계정이 아니다」로 읽힌다(runbook §3.4).
     *
     * 인터럽트가 앞인 것이 맞다: 연속 수는 **멈춤 줄의 `consecutive=` 칸에 그대로 실리므로**
     * 정보를 잃지 않지만, 사유를 연속 상한으로 적으면 「누가 껐다」는 사실이 어느 줄에도 남지 않는다.
     *
     * 이 test 가 잠그는 것은 `when` 두 가지의 **순서**다 — 그 둘을 바꾸면 여기서 붉는다.
     */
    @Test
    fun `인터럽트와 연속 상한이 함께 서면 사유는 인터럽트다`() {
        val log = RecordingCommitLog()
        val termination = RecordedExitCodes()
        val attempted = mutableListOf<Long>()
        var failures = 0

        runnerFor(
            failingRuns(
                listOf(1L, 2L, 3L, 4L, 5L),
                failing = setOf(1L, 2L, 3L, 4L, 5L),
                attempted = attempted,
                // 셋째 실패에서만 플래그가 선다 — 그 한 건에서 두 술어가 함께 참이다.
                failure = {
                    failures++
                    if (failures == 3) {
                        InterruptedException(DEAD_STORE_MESSAGE)
                    } else {
                        IllegalStateException(DEAD_STORE_MESSAGE)
                    }
                },
            ),
            log,
            termination,
            maxConsecutiveAccountFailures = 3,
        ).run(DefaultApplicationArguments())

        // 읽으면서 **지운다** — 인터럽트된 채로 두면 같은 worker 의 뒤 test 가 그 상태를 물려받는다.
        Thread.interrupted() shouldBe true
        attempted shouldBe listOf(1L, 2L, 3L)
        termination.recorded() shouldBe listOf(EvaluationCommitExitCode.INCOMPLETE.value)
        val stoppedLine = log.written.single { it.startsWith("evaluation-commit stopped") }
        stoppedLine shouldContain "cause=interrupted"
        // 연속 수는 **그대로 실린다** — 사유가 인터럽트여도 「셋이 연달아 터졌다」가 사라지지 않는다.
        stoppedLine shouldContain "consecutive=3"
        stoppedLine shouldContain "remaining=2"
        accountCountsOf(log.written) shouldBe listOf(0, 0, 3, 2)
    }

    /**
     * **흩어진 실패는 멈추지 않는다** — 성공 하나가 연속을 끊는다. 실패 넷이 상한(3)보다 많아도
     * 연속이 아니면 run 은 끝까지 간다(그 실패들은 그 계정들의 일이다).
     */
    @Test
    fun `연속이 아닌 실패는 run 을 멈추지 않는다`() {
        val log = RecordingCommitLog()
        val attempted = mutableListOf<Long>()

        runnerFor(
            failingRuns(listOf(1L, 2L, 3L, 4L, 5L), failing = setOf(1L, 3L, 5L), attempted = attempted),
            log,
            RecordedExitCodes(),
            maxConsecutiveAccountFailures = 3,
        ).run(DefaultApplicationArguments())

        attempted shouldBe listOf(1L, 2L, 3L, 4L, 5L)
        val finishLine = log.written.single { it.startsWith("evaluation-commit finished") }
        finishLine shouldContain "failed=3"
        finishLine shouldContain "deferred=0"
        log.written.none { it.startsWith("evaluation-commit stopped") } shouldBe true
    }
}
