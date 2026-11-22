package bidvector.app.evaluation

import bidvector.app.wiring.RecordedExitCodes
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import java.sql.SQLException

/**
 * 커밋 러너의 **멈춤 축** — 언제 run 이 끝까지 돌지 않는가, 그리고 그때 남는 한 줄이 무엇을
 * 말하는가. `EvaluationCommitRunnerTest` 에서 이 축으로 갈라 나왔다(M15/15H, 크기 게이트).
 *
 * 멈춤 줄은 모양이 **하나**이고 `cause=` 의 값이 둘이다(`consecutive-account-failures` ·
 * `interrupted`). 그 둘의 처방이 서로 다르므로(runbook §3.4) 여기서 재는 것은 셋이다 —
 * **언제 서는가** · **어느 사유가 실리는가** · **계수 둘이 무엇을 세는가**.
 */
class EvaluationCommitStopLineTest {
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
        log.written.single { it.startsWith("evaluation-commit stopped") } shouldContain
            "cause=consecutive-account-failures"
        // `remaining` 은 **run 전체의 남은 수** = 마침 줄의 `deferred`(r3 CR L-2).
        stopLineCountsOf(log.written) shouldBe listOf(3, 2)
        accountCountsOf(log.written).sum() shouldBe 5
    }

    /**
     * **`consecutive=` 는 연속 수이지 누적 실패 수가 아니다**(M15/15H, `OPEN-M15-TEST-RESIDUE` ⓐ).
     *
     * 착수 시점에 멈춤 줄을 내는 test 다섯은 **전부 두 수가 같은 고정물**이었다(1·1 · 2·2 · 3·3 ·
     * 1·1 · 3·3) — `failed` 를 그 칸에 싣는 변이가 어디서도 붉지 않았다. 흩어진 실패를 **앞에**
     * 두면 갈린다: 1 이 터지고 2 가 지나가 계수가 0 으로 돌아간 뒤 3·4·5 가 연달아 터진다.
     * 누적 실패는 넷, 멈춘 시점의 연속은 셋이다.
     *
     * 줄이 세는 것이 **연속**이어야 하는 이유는 처방이다 — 운영자는 이 수를 설정값
     * (`max-consecutive-account-failures`)과 맞대 「상한에 닿아서 멈췄구나」를 읽는다. 누적 실패
     * 수가 거기 실리면 상한보다 큰 수가 보이고, 그 줄은 없는 줄보다 나쁘다.
     */
    @Test
    fun `멈춤 줄의 consecutive 는 연속 수다 — 흩어진 실패를 앞에 두면 누적과 갈린다`() {
        val log = RecordingCommitLog()
        val attempted = mutableListOf<Long>()

        runnerFor(
            failingRuns(
                listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L),
                // 2 만 지나간다 — 거기서 연속이 끊긴다.
                failing = setOf(1L, 3L, 4L, 5L, 6L, 7L),
                attempted = attempted,
            ),
            log,
            RecordedExitCodes(),
            maxConsecutiveAccountFailures = 3,
        ).run(DefaultApplicationArguments())

        attempted shouldBe listOf(1L, 2L, 3L, 4L, 5L)
        val stoppedLine = log.written.single { it.startsWith("evaluation-commit stopped") }
        stoppedLine shouldContain "cause=consecutive-account-failures"
        // 연속 셋 · 남은 둘. **누적 실패는 넷**이고 그 수는 이 줄에 없다.
        stopLineCountsOf(log.written) shouldBe listOf(3, 2)
        accountCountsOf(log.written) shouldBe listOf(1, 0, 4, 2)
    }

    /**
     * **인터럽트로 멈춘 줄의 `consecutive=` 도 연속 수다** — 같은 변이를 **다른 사유 갈래**에서
     * 잠근다. 사유가 둘인데 한쪽에서만 재면 그 줄을 짓는 자리가 갈라지는 날 반쪽만 막힌다.
     *
     * 여기서는 연속 상한(3)에 **닿지 않는다** — 1 이 터지고 2 가 지나간 뒤 3 이 인터럽트로 터진다.
     * 멈춘 시점의 연속은 1 이고 누적 실패는 2 다.
     */
    @Test
    fun `인터럽트로 멈춘 줄의 consecutive 도 연속 수다`() {
        val log = RecordingCommitLog()
        val attempted = mutableListOf<Long>()
        var failures = 0

        runnerFor(
            failingRuns(
                listOf(1L, 2L, 3L, 4L, 5L),
                failing = setOf(1L, 3L, 4L, 5L),
                attempted = attempted,
                failure = {
                    failures++
                    if (failures == 2) {
                        InterruptedException(DEAD_STORE_MESSAGE)
                    } else {
                        IllegalStateException(DEAD_STORE_MESSAGE)
                    }
                },
            ),
            log,
            RecordedExitCodes(),
            maxConsecutiveAccountFailures = 3,
        ).run(DefaultApplicationArguments())

        // 읽으면서 **지운다** — 인터럽트된 채로 두면 같은 worker 의 뒤 test 가 그 상태를 물려받는다.
        Thread.interrupted() shouldBe true
        attempted shouldBe listOf(1L, 2L, 3L)
        log.written.single { it.startsWith("evaluation-commit stopped") } shouldContain "cause=interrupted"
        // 연속 하나 · 남은 둘. **누적 실패는 둘**이다.
        stopLineCountsOf(log.written) shouldBe listOf(1, 2)
        accountCountsOf(log.written) shouldBe listOf(1, 0, 2, 2)
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
        stopLineCountsOf(log.written) shouldBe listOf(2, 3)
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
        log.written.single { it.startsWith("evaluation-commit stopped") } shouldContain "cause=interrupted"
        stopLineCountsOf(log.written) shouldBe listOf(1, 2)
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
        // 심는 메시지가 접속 문자열 꼴을 담으므로 러너 test 와 **같은 두 줄**로 그 부재를 잠근다.
        failureLine shouldNotContain "jdbc:"
        failureLine shouldNotContain "secret-host"
        log.written.single { it.startsWith("evaluation-commit stopped") } shouldContain "cause=interrupted"
        stopLineCountsOf(log.written) shouldBe listOf(1, 2)
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
        log.written.single { it.startsWith("evaluation-commit stopped") } shouldContain "cause=interrupted"
        // 연속 수는 **그대로 실린다** — 사유가 인터럽트여도 「셋이 연달아 터졌다」가 사라지지 않는다.
        stopLineCountsOf(log.written) shouldBe listOf(3, 2)
        accountCountsOf(log.written) shouldBe listOf(0, 0, 3, 2)
    }
}
