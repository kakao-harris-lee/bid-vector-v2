package bidvector.app.evaluation

import bidvector.adapters.evaluation.EvaluationCommitRuns
import bidvector.adapters.persistence.ReleaseIdentifier
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.http.TestStrategyRepository
import bidvector.app.wiring.RecordedExitCodes
import bidvector.procurement.Notice
import bidvector.sharedkernel.AccountId
import bidvector.sharedkernel.Resolution
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.notificationinbox.NotificationAudience
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyReader
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.DefaultApplicationArguments
import java.time.Instant
import java.time.LocalDate

private const val CANDIDATE_CAP = 5

// M15/15A — 여력 상한 상수는 사라졌다. 공장이 **그 계정의 전략에서** 상한을 읽으므로 이 test 가
// 상한 값을 들고 있을 자리가 없다(전략 읽기에서 던지는 조립이라 거기까지 가지도 않는다).
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
        val log = RecordingLog()
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
        val log = RecordingLog()
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
        val log = RecordingLog()
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
        val log = RecordingLog()

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
        val log = RecordingLog()
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
        val log = RecordingLog()
        val termination = RecordedExitCodes()

        runnerFor(healthyRuns(accounts = 5), log, termination, maxAccountsPerRun = 2)
            .run(DefaultApplicationArguments())

        termination.recorded() shouldBe listOf(EvaluationCommitExitCode.COMPLETE.value)
        val finishLine = log.written.single { it.startsWith("evaluation-commit finished") }
        finishLine shouldContain "accounts=2"
        finishLine shouldContain "deferred=3"
        finishLine shouldContain "failed=0"
        log.written.single { it.startsWith("evaluation-commit start") } shouldContain "maxAccountsPerRun=2"
    }

    /** 상한이 계정 수보다 크면 미룬 계정이 0 이다 — 상한이 평소에 아무것도 바꾸지 않는다. */
    @Test
    fun `상한이 넉넉하면 미룬 계정이 0 이다 — 대조`() {
        val log = RecordingLog()

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
        val log = RecordingLog()
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
        val log = RecordingLog()
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
        stoppedLine shouldContain "remaining=2"
    }

    /**
     * **흩어진 실패는 멈추지 않는다** — 성공 하나가 연속을 끊는다. 실패 넷이 상한(3)보다 많아도
     * 연속이 아니면 run 은 끝까지 간다(그 실패들은 그 계정들의 일이다).
     */
    @Test
    fun `연속이 아닌 실패는 run 을 멈추지 않는다`() {
        val log = RecordingLog()
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

/** 두 상한의 test 기본값 — 그 축을 재지 않는 test 는 둘 다 넉넉히 받는다. */
private const val ROOMY_ACCOUNT_CAP = 100
private const val ROOMY_CONSECUTIVE_CAP = 100

private fun runnerFor(
    runs: EvaluationCommitRuns,
    log: CollectionLog,
    termination: CollectionTermination,
    maxAccountsPerRun: Int = ROOMY_ACCOUNT_CAP,
    maxConsecutiveAccountFailures: Int = ROOMY_CONSECUTIVE_CAP,
): EvaluationCommitRunner =
    EvaluationCommitRunner(
        runs = runs,
        candidateCap = CANDIDATE_CAP,
        maxAccountsPerRun = maxAccountsPerRun,
        maxConsecutiveAccountFailures = maxConsecutiveAccountFailures,
        log = log,
        termination = termination,
    )

/**
 * 전략 읽기에서 던지는 조립 — 그 뒤 협력자는 불리지 않는다.
 *
 * **M15/15A — 계정 전수는 읽힌다.** 러너가 먼저 청중을 묻고 그 계정으로 run 을 짓기 때문이다.
 * 던지는 자리는 여전히 전략 읽기이고, 그 아래 협력자는 그대로 「불리면 터지는」 구현이다.
 */
private fun deadCommitRuns(): EvaluationCommitRuns =
    EvaluationCommitRuns(
        dataSource = PGSimpleDataSource(),
        strategies = TestStrategyRepository().also { it.loadFailure = { IllegalStateException(DEAD_STORE_MESSAGE) } },
        candidateSource = CandidateSourcePort { error("후보 원천이 불렸다 — 전략 읽기가 먼저 던져야 한다") },
        watchSubjects = WatchSubjectPort { error("감시 port 가 불렸다") },
        licenseGate = LicenseGatePort { _, _ -> error("면허 게이트가 불렸다") },
        mlAnalysis = MlAnalysisPort { _, _: Notice, _ -> error("ML port 가 불렸다") },
        correlationIds = CorrelationIdFactory { CorrelationId("commit-runner-test") },
        clock = Clock { Instant.parse("2026-10-07T00:00:00Z") },
        notificationInbox = { error("알림함 쓰기가 불렸다 — 전략 읽기가 먼저 던져야 한다") },
        audience = { NotificationAudience.Accounts(listOf(AccountId(1))) },
        currentActiveBids = 0,
        release = ReleaseIdentifier("commit-runner-test"),
    )

/**
 * 계정 전수를 못 읽는 조립(M15/15A) — 러너는 **빈 목록으로 진행하지 않는다**. 진행하면
 * 「평가할 계정이 없다」와 「누가 있는지 못 읽었다」가 같은 종료 코드 0 이 된다.
 */
private fun audienceUnavailableRuns(): EvaluationCommitRuns =
    EvaluationCommitRuns(
        dataSource = PGSimpleDataSource(),
        strategies =
            object : StrategyReader {
                override fun load(account: AccountId): OperatorStrategy = error("청중을 못 읽었는데 전략을 읽었다")
            },
        candidateSource = CandidateSourcePort { error("후보 원천이 불렸다") },
        watchSubjects = WatchSubjectPort { error("감시 port 가 불렸다") },
        licenseGate = LicenseGatePort { _, _ -> error("면허 게이트가 불렸다") },
        mlAnalysis = MlAnalysisPort { _, _: Notice, _ -> error("ML port 가 불렸다") },
        correlationIds = CorrelationIdFactory { CorrelationId("commit-runner-test") },
        clock = Clock { Instant.parse("2026-10-07T00:00:00Z") },
        notificationInbox = { error("알림함 쓰기가 불렸다") },
        audience = { NotificationAudience.Unavailable("SQLException") },
        currentActiveBids = 0,
        release = ReleaseIdentifier("commit-runner-test"),
    )

/**
 * 계정 둘 — 작은 쪽은 전략 읽기에서 던지고 큰 쪽은 상한이 있는 전략을 받는다. 후보 원천이 비어
 * 있어 큰 쪽의 평가는 **효과 없이** 끝난다(DB 에 닿지 않는다).
 */
private fun partiallyDeadCommitRuns(): EvaluationCommitRuns =
    EvaluationCommitRuns(
        dataSource = PGSimpleDataSource(),
        strategies =
            object : StrategyReader {
                override fun load(account: AccountId): OperatorStrategy =
                    if (account.value == 1L) throw IllegalStateException(DEAD_STORE_MESSAGE) else strategyWithCap()
            },
        candidateSource = CandidateSourcePort { emptyList() },
        watchSubjects = WatchSubjectPort { error("후보가 없으므로 감시 port 는 불리지 않는다") },
        licenseGate = LicenseGatePort { _, _ -> error("후보가 없으므로 면허 게이트는 불리지 않는다") },
        mlAnalysis = MlAnalysisPort { _, _: Notice, _ -> error("후보가 없으므로 ML port 는 불리지 않는다") },
        correlationIds = CorrelationIdFactory { CorrelationId("commit-runner-test") },
        clock = Clock { Instant.parse("2026-10-07T00:00:00Z") },
        notificationInbox = { error("후보가 없으므로 알림함 쓰기는 불리지 않는다") },
        audience = { NotificationAudience.Accounts(listOf(AccountId(1), AccountId(2))) },
        currentActiveBids = 0,
        release = ReleaseIdentifier("commit-runner-test"),
    )

/**
 * 계정 N 개가 전부 정상으로 도는 조립(M15/15B) — 후보 원천이 비어 평가는 **효과 없이** 끝난다.
 * 재는 것은 판정이 아니라 **몇 계정이 돌았고 몇이 미뤄졌는가**다.
 */
private fun healthyRuns(accounts: Int): EvaluationCommitRuns = recordingRuns((1L..accounts).toList(), mutableListOf())

/** 평가된 계정 id 를 순서대로 적는 조립 — 「누가 먼저인가」를 그 목록이 말한다. */
private fun recordingRuns(
    ids: List<Long>,
    evaluated: MutableList<Long>,
): EvaluationCommitRuns =
    emptyCandidateRuns(ids) { account ->
        evaluated += account.value
        strategyWithCap()
    }

/** 지정한 계정에서 전략 읽기가 던지는 조립 — 시도 순서를 함께 적는다. */
private fun failingRuns(
    ids: List<Long>,
    failing: Set<Long>,
    attempted: MutableList<Long>,
): EvaluationCommitRuns =
    emptyCandidateRuns(ids) { account ->
        attempted += account.value
        if (account.value in failing) throw IllegalStateException(DEAD_STORE_MESSAGE) else strategyWithCap()
    }

/**
 * 후보가 **비어 있는** 조립의 공통 틀 — 평가가 DB 에 닿지 않으므로 계수 축만 남는다.
 * 전략 읽기 자리에 test 가 주는 람다를 꽂아 「누가 불렸나」와 「누가 던지나」를 그 자리에서 정한다.
 */
private fun emptyCandidateRuns(
    ids: List<Long>,
    strategyOf: (AccountId) -> OperatorStrategy,
): EvaluationCommitRuns =
    EvaluationCommitRuns(
        dataSource = PGSimpleDataSource(),
        strategies =
            object : StrategyReader {
                override fun load(account: AccountId): OperatorStrategy = strategyOf(account)
            },
        candidateSource = CandidateSourcePort { emptyList() },
        watchSubjects = WatchSubjectPort { error("후보가 없으므로 감시 port 는 불리지 않는다") },
        licenseGate = LicenseGatePort { _, _ -> error("후보가 없으므로 면허 게이트는 불리지 않는다") },
        mlAnalysis = MlAnalysisPort { _, _: Notice, _ -> error("후보가 없으므로 ML port 는 불리지 않는다") },
        correlationIds = CorrelationIdFactory { CorrelationId("commit-runner-test") },
        clock = Clock { Instant.parse("2026-10-07T00:00:00Z") },
        notificationInbox = { error("후보가 없으므로 알림함 쓰기는 불리지 않는다") },
        audience = { NotificationAudience.Accounts(ids.map { AccountId(it) }) },
        currentActiveBids = 0,
        release = ReleaseIdentifier("commit-runner-test"),
    )

/** 청중이 **읽혔고 비었다** — 「못 읽었다」와 다른 상태다(그쪽은 `Unavailable`). */
private fun emptyAudienceRuns(): EvaluationCommitRuns =
    EvaluationCommitRuns(
        dataSource = PGSimpleDataSource(),
        strategies =
            object : StrategyReader {
                override fun load(account: AccountId): OperatorStrategy = error("계정이 없는데 전략을 읽었다")
            },
        candidateSource = CandidateSourcePort { error("후보 원천이 불렸다") },
        watchSubjects = WatchSubjectPort { error("감시 port 가 불렸다") },
        licenseGate = LicenseGatePort { _, _ -> error("면허 게이트가 불렸다") },
        mlAnalysis = MlAnalysisPort { _, _: Notice, _ -> error("ML port 가 불렸다") },
        correlationIds = CorrelationIdFactory { CorrelationId("commit-runner-test") },
        clock = Clock { Instant.parse("2026-10-07T00:00:00Z") },
        notificationInbox = { error("알림함 쓰기가 불렸다") },
        audience = { NotificationAudience.Accounts(emptyList()) },
        currentActiveBids = 0,
        release = ReleaseIdentifier("commit-runner-test"),
    )

/** 여력 상한이 있는 전략 — 이 값이 있어야 공장이 run 을 세운다. */
private fun strategyWithCap(): OperatorStrategy =
    when (
        val result =
            validate(
                StrategyDraft(focusCategories = listOf("CAT-1"), maxActiveBids = 3),
                StrategyRevision(1),
                STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>,
            )
    ) {
        is StrategyValidation.Valid -> result.strategy
        is StrategyValidation.Invalid -> error("test 전략이 유효하지 않다: ${result.violations}")
    }
