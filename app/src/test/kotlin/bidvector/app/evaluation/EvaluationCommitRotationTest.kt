package bidvector.app.evaluation

import bidvector.app.wiring.RecordedExitCodes
import bidvector.sharedkernel.AccountId
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * 커밋 러너의 **창 선택 축** — 오늘 run 이 어느 계정을 도는가. 회전 창 자체를 **직접** 재는 자리로
 * 섰고(M15/15B r4, CR L-2 · verifier F-2), M15/15H 에서 `EvaluationCommitRunnerTest` 의 창·상한
 * 단언을 이 축으로 모았다(크기 게이트). 앞 판의 회전 단언은 전부 러너를 통했고 `(K, n)` 한 쌍·날짜
 * 한 위상뿐이라, **법(modulus)이 한 칸 어긋나는 변이**와 경계 둘(`K == n` · `K == n+1`)이 조용했다.
 *
 * 재는 것은 둘이다 — 순수 함수 [accountsForRun](표·주기·요일 패턴)과, 그 선택이 **러너까지 닿아**
 * 마침 줄의 `deferred=` 가 되는 축(KST 경계·같은 날 재실행·상한 경계).
 */
class EvaluationCommitRotationTest {
    /**
     * `(K, n, 날짜) → 뽑힌 계정` 표. 기대값을 식으로 쓰지 않고 **적는다** — 식으로 쓰면 구현과 같은
     * 실수를 두 번 한다.
     *
     * - `K=1 · n=1` 은 **나머지가 없는** 자리다. 회전 가지로 빠지면 법이 0 이라 터진다 —
     *   `others.size <= slots` 의 `<=` 를 `<` 로 바꾸는 변이가 여기서 붉는다.
     * - `K=10 · n=4 · day=9` 는 법이 `span` 인지 `span-1` 인지 **가르는** 자리다(9 mod 9 = 0,
     *   9 mod 8 = 1). 보폭이 1 이 된 뒤로는 `day=3` 이 그 둘을 가르지 못한다(3 mod 9 = 3 mod 8).
     * - 날짜가 하루 늘면 창이 **한 칸** 미끄러진다 — 보폭을 `n-1` 로 되돌리는 변이가 여기서
     *   붉는다(`K=4 · n=3` 의 둘째·셋째 줄이 서로 바뀐다).
     */
    @Test
    fun `회전 창 표 — 경계와 여러 시작일`() {
        // K == n · K < n : 창이 서지 않는다(전원 평가).
        accountsForRun(accountIds(3), maxAccountsPerRun = 3, businessDay = 5).values() shouldBe listOf(1L, 2L, 3L)
        accountsForRun(accountIds(3), maxAccountsPerRun = 10, businessDay = 5).values() shouldBe listOf(1L, 2L, 3L)

        // 나머지가 없다 — 보유자 하나뿐이다.
        accountsForRun(accountIds(1), maxAccountsPerRun = 1, businessDay = 0).values() shouldBe listOf(1L)
        // n = 1 : 칸이 0 이라 보유자만 돈다(날짜가 아무리 커도).
        accountsForRun(accountIds(5), maxAccountsPerRun = 1, businessDay = 12345).values() shouldBe listOf(1L)

        // K == n + 1 : 가장 작은 회전 판. 하루에 한 칸씩 미끄러져 사흘이면 나머지 셋을 다 덮는다.
        accountsForRun(accountIds(4), maxAccountsPerRun = 3, businessDay = 0).values() shouldBe listOf(1L, 2L, 3L)
        accountsForRun(accountIds(4), maxAccountsPerRun = 3, businessDay = 1).values() shouldBe listOf(1L, 3L, 4L)
        accountsForRun(accountIds(4), maxAccountsPerRun = 3, businessDay = 2).values() shouldBe listOf(1L, 4L, 2L)

        // 창은 날짜만큼 미끄러진다 — 보폭이 1 이라 `day` 가 곧 시작 칸이다.
        accountsForRun(accountIds(10), maxAccountsPerRun = 4, businessDay = 3).values() shouldBe
            listOf(1L, 5L, 6L, 7L)
        // 법이 `span` 임을 가르는 자리.
        accountsForRun(accountIds(10), maxAccountsPerRun = 4, businessDay = 9).values() shouldBe
            listOf(1L, 2L, 3L, 4L)
        // epoch 이전(음수 날짜)에서도 같은 규칙이다(`floorMod`).
        accountsForRun(accountIds(10), maxAccountsPerRun = 4, businessDay = -1).values() shouldBe
            listOf(1L, 10L, 2L, 3L)
    }

    /**
     * 보유자는 **언제나 첫 원소**이고 창에 중복이 없으며 크기는 `min(n, K)` 다 — 날짜가 어디에
     * 있든 같다. 보유자 고정을 빼는 변이와, 창 안에서 칸을 건너뛰어 나머지를 벗어나는 변이가
     * 이 단언들에서 붉는다(법·보폭 축은 위 표가 든다).
     */
    @Test
    fun `보유자는 늘 첫 원소이고 창에 중복이 없다`() {
        for (day in -40L..40L) {
            val window = accountsForRun(accountIds(AUDIENCE), maxAccountsPerRun = CAP, businessDay = day).values()
            window.first() shouldBe 1L
            window.size shouldBe CAP
            window.toSet().size shouldBe CAP
        }
    }

    /**
     * **연속한 `K-n+1` 달력일이면 전원이 한 번씩 돈다** — 그 보증을 한 위상이 아니라 **모든 시작
     * 위상**에서 잰다(verifier F-2: 앞 판은 기준일 하나에 묶여 있었다). 보폭이 1 이 되면서 한
     * 바퀴가 `ceil((K-1)/(n-1))` 에서 이 수로 길어졌다(M15/15H 의 교환).
     */
    @Test
    fun `어느 날 시작해도 한 주기면 전원이 돈다`() {
        for (start in -40L..40L) {
            val covered =
                (0 until PERIOD).flatMap { offset ->
                    accountsForRun(accountIds(AUDIENCE), maxAccountsPerRun = CAP, businessDay = start + offset)
                        .values()
                }
            // 순서는 위상마다 다르다 — **집합으로** 비교한다(15B r5 CR L-6, 저장소 관례).
            covered.distinct() shouldContainExactlyInAnyOrder (1L..AUDIENCE.toLong()).toList()
        }
    }

    /**
     * **축은 KST 달력일이다** — 구역을 UTC 로 바꾸는 변이가 여기서 붉는다. 23:59 KST 와 01분 뒤의
     * 00:01 KST 는 **다른 날**이고(UTC 로는 같은 날이다), 같은 KST 날짜의 두 시각은 같은 집합이다.
     */
    @Test
    fun `회전 경계는 KST 자정이다`() {
        val lateOnTheTwentySecond = selectionAt("2026-11-22T14:59:00Z")
        val earlyOnTheTwentyThird = selectionAt("2026-11-22T15:01:00Z")
        val lateOnTheTwentyThird = selectionAt("2026-11-23T14:59:00Z")

        earlyOnTheTwentyThird shouldNotBe lateOnTheTwentySecond
        earlyOnTheTwentyThird shouldBe lateOnTheTwentyThird
    }

    /**
     * **평일만 도는 timer 에서도 전원이 돈다**(`OPEN-M15-EVAL-COST-PER-ACCOUNT` ⓐ).
     *
     * 앞 판의 보폭은 `n-1` 이라 시작 위치가 `날짜 mod P`(`P = span / gcd(n-1, span)`)에만 달려
     * 있었고, `K=22 · n=4` 에서 그 `P` 가 **정확히 7** 이었다 — 실행일이 평일뿐이면 잔여류 다섯만
     * 돌아 **나머지 둘에 든 계정 여섯이 영원히** 평가되지 않았다. 종료 코드는 0, cron 은 성공이다.
     *
     * 보폭이 1 이면 그 거칢이 `gcd(c, span)`(cron 자신의 공차에만 달린 값)으로 줄고, 창이 하루에
     * 한 칸 미끄러지므로 **간격이 창 칸 수 이하인 어떤 실행일 집합도** 끊기지 않고 이어 덮는다.
     * 평일 timer 의 최대 간격은 3(금→월)이고 여기 칸은 셋이다.
     *
     * 시작 요일 일곱 **전부**에서 잰다 — 한 요일에 묶으면 위상 하나만 보는 앞 판의 실수를 되풀이한다.
     */
    @Test
    fun `평일만 도는 timer 에서도 전원이 돈다`() {
        for (offset in 0L until DAYS_IN_WEEK) {
            val covered =
                weekdaysFrom(WEEKDAY_PROBE_START.plusDays(offset), WEEKDAY_RUNS).flatMap { day ->
                    accountsForRun(
                        accountIds(STARVED_AUDIENCE),
                        maxAccountsPerRun = STARVED_CAP,
                        businessDay = day.toEpochDay(),
                    ).values()
                }

            covered.distinct() shouldContainExactlyInAnyOrder (1L..STARVED_AUDIENCE.toLong()).toList()
        }
    }

    /**
     * **선택이 러너까지 닿는다 — 연속한 달력일이면 전원이 한 번씩 돌고 보유자는 매일 든다.**
     * 위 주기 test 가 순수 함수에서 재는 것을 여기서는 **평가가 실제로 돈 계정**으로 잰다.
     * 축이 「영업일」이 **아닌** 것이 요점이다 — 주말·공휴일을 포함한다(15B r5 CR L-1).
     *
     * 오프셋을 0 으로 고정하는 변이는 날들의 집합을 같게 만들어 **두 단언 모두** 붉는다.
     */
    @Test
    fun `연속한 KST 달력일이면 모든 계정이 한 번씩 돈다 — 보유자는 매일 든다`() {
        val coverage = mutableSetOf<Long>()
        val perDay = mutableListOf<List<Long>>()

        repeat(PERIOD) { offset ->
            val evaluated = mutableListOf<Long>()
            runnerFor(
                recordingRuns(ROTATION_AUDIENCE, evaluated),
                RecordingCommitLog(),
                RecordedExitCodes(),
                maxAccountsPerRun = CAP,
                clock = clockAt(FIXED_DATE.plusDays(offset.toLong())),
            ).run(DefaultApplicationArguments())

            evaluated.size shouldBe CAP
            evaluated.first() shouldBe ROTATION_AUDIENCE.first()
            perDay += evaluated
            coverage += evaluated
        }

        coverage shouldBe ROTATION_AUDIENCE.toSet()
        perDay.toSet().size shouldBe PERIOD
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
            maxAccountsPerRun = CAP,
        ).run(DefaultApplicationArguments())
        runnerFor(
            recordingRuns(ROTATION_AUDIENCE, second),
            RecordingCommitLog(),
            RecordedExitCodes(),
            maxAccountsPerRun = CAP,
        ).run(DefaultApplicationArguments())

        second shouldBe first
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

    /** 러너를 그 시각에 한 번 돌려 **실제로 평가된 계정**을 받는다(선택이 러너까지 닿는 축이다). */
    private fun selectionAt(instant: String): List<Long> {
        val evaluated = mutableListOf<Long>()
        runnerFor(
            recordingRuns(ROTATION_AUDIENCE, evaluated),
            RecordingCommitLog(),
            RecordedExitCodes(),
            maxAccountsPerRun = CAP,
            clock = Clock { Instant.parse(instant) },
        ).run(DefaultApplicationArguments())
        return evaluated
    }

    /** [start] 부터의 평일 [count] 개 — 토·일을 건너뛴다(평일만 거는 timer 의 실행일 집합이다). */
    private fun weekdaysFrom(
        start: LocalDate,
        count: Int,
    ): List<LocalDate> {
        val weekdays = mutableListOf<LocalDate>()
        var day = start
        while (weekdays.size < count) {
            if (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY) {
                weekdays += day
            }
            day = day.plusDays(1)
        }
        return weekdays
    }

    private companion object {
        /** 청중 10 · 상한 4 → 칸 셋 · 나머지 아홉 · 한 바퀴 `K - n + 1 = 7` 달력일. */
        const val AUDIENCE = 10
        const val CAP = 4
        const val PERIOD = 7

        /** 위 세 수가 함께 움직여야 하므로 청중 목록도 같은 자리에서 낸다. */
        val ROTATION_AUDIENCE: List<Long> = (1L..AUDIENCE.toLong()).toList()

        /** `OPEN-M15-EVAL-COST-PER-ACCOUNT` ⓐ 가 든 판 그대로 — 그 행이 센 기아 계정이 여섯이다. */
        const val STARVED_AUDIENCE = 22
        const val STARVED_CAP = 4

        /** 시작 요일 일곱 전부를 훑는다. */
        const val DAYS_IN_WEEK = 7L

        /** 평일 run 의 상한 — 이 수 안에 전원이 돈다(실측 최대 15, 시작 요일 월요일). */
        const val WEEKDAY_RUNS = 15
        val WEEKDAY_PROBE_START: LocalDate = LocalDate.of(2026, 11, 23)
    }
}
