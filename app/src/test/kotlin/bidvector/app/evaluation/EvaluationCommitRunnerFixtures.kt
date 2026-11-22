package bidvector.app.evaluation

import bidvector.adapters.evaluation.EvaluationCommitRuns
import bidvector.adapters.persistence.ReleaseIdentifier
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.http.TestStrategyRepository
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
import org.postgresql.ds.PGSimpleDataSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val CANDIDATE_CAP = 5

// M15/15A — 여력 상한 상수는 사라졌다. 공장이 **그 계정의 전략에서** 상한을 읽으므로 이 test 가
// 상한 값을 들고 있을 자리가 없다(전략 읽기에서 던지는 조립이라 거기까지 가지도 않는다).
internal const val DEAD_STORE_MESSAGE = "전략 저장소가 응답하지 않는다 jdbc:postgresql://secret-host/db"

/** 두 상한의 test 기본값 — 그 축을 재지 않는 test 는 둘 다 넉넉히 받는다. */
private const val ROOMY_ACCOUNT_CAP = 100
private const val ROOMY_CONSECUTIVE_CAP = 100

/** 회전 창의 기준일 — 날짜 축을 재지 않는 test 는 이 하루에 고정한다. */
internal val FIXED_DATE: LocalDate = LocalDate.of(2026, 11, 22)

/** 회전 창이 미끄러지는 구역 — production 과 같은 KST 다(UTC 면 하루가 밀린다). */
private val ROTATION_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/** cron 이 실제로 도는 시각대(04:10 KST)를 흉내 낸다. */
private fun clockAt(date: LocalDate): Clock = Clock { date.atTime(4, 10).atZone(ROTATION_ZONE).toInstant() }

@Suppress("LongParameterList")
internal fun runnerFor(
    runs: EvaluationCommitRuns,
    log: CollectionLog,
    termination: CollectionTermination,
    maxAccountsPerRun: Int = ROOMY_ACCOUNT_CAP,
    maxConsecutiveAccountFailures: Int = ROOMY_CONSECUTIVE_CAP,
    date: LocalDate = FIXED_DATE,
    clock: Clock = clockAt(date),
): EvaluationCommitRunner =
    EvaluationCommitRunner(
        runs = runs,
        candidateCap = CANDIDATE_CAP,
        maxAccountsPerRun = maxAccountsPerRun,
        maxConsecutiveAccountFailures = maxConsecutiveAccountFailures,
        clock = clock,
        log = log,
        termination = termination,
    )

/**
 * 줄을 모으는 로그 이중체 — 러너를 돌리는 test 가 **같은 것을** 쓴다(앞 판은 class 마다 제 사본을
 * 중첩 class 로 들고 있었다).
 */
internal class RecordingCommitLog : CollectionLog {
    val written: MutableList<String> = mutableListOf()

    override fun write(line: String) {
        written += line
    }
}

/** id 오름차순 청중 — 회전 표 test 가 쓰는 한 줄짜리 조립. */
internal fun accountIds(count: Int): List<AccountId> = (1L..count).map { AccountId(it) }

/** 선택 결과를 **값**으로 본다(`AccountId` 는 값 비교를 제공하지 않는다, 7D 규율). */
internal fun List<AccountId>.values(): List<Long> = map { it.value }

/**
 * 마침 줄의 **계정 계수 넷**(r3 CR L-8). 네 수가 청중과 닫히는지를 시나리오마다 한 줄로 본다 —
 * 개별 값만 단언하면 `deferred` 산술이 한 칸 밀리는 변경이 한 test 에서만 붉는다.
 */
internal fun accountCountsOf(written: List<String>): List<Int> {
    val line = written.single { it.startsWith("evaluation-commit finished") }
    return listOf("accounts", "skipped", "failed", "deferred").map { field ->
        requireNotNull(Regex("""\b$field=(\d+)""").find(line)) { "$field 가 마침 줄에 없다: $line" }
            .groupValues[1]
            .toInt()
    }
}

/**
 * 전략 읽기에서 던지는 조립 — 그 뒤 협력자는 불리지 않는다.
 *
 * **M15/15A — 계정 전수는 읽힌다.** 러너가 먼저 청중을 묻고 그 계정으로 run 을 짓기 때문이다.
 * 던지는 자리는 여전히 전략 읽기이고, 그 아래 협력자는 그대로 「불리면 터지는」 구현이다.
 */
internal fun deadCommitRuns(): EvaluationCommitRuns =
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
internal fun audienceUnavailableRuns(): EvaluationCommitRuns =
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
internal fun partiallyDeadCommitRuns(): EvaluationCommitRuns =
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
internal fun healthyRuns(accounts: Int): EvaluationCommitRuns = recordingRuns((1L..accounts).toList(), mutableListOf())

/** 평가된 계정 id 를 순서대로 적는 조립 — 「누가 먼저인가」를 그 목록이 말한다. */
internal fun recordingRuns(
    ids: List<Long>,
    evaluated: MutableList<Long>,
): EvaluationCommitRuns =
    emptyCandidateRuns(ids) { account ->
        evaluated += account.value
        strategyWithCap()
    }

/**
 * 지정한 계정에서 전략 읽기가 던지는 조립 — 시도 순서를 함께 적는다. [skipping] 의 계정은 상한
 * 없는 전략을 받아 **건너뜀**이 된다(`forAccount` 가 `null` 을 낸다).
 */
internal fun failingRuns(
    ids: List<Long>,
    failing: Set<Long>,
    attempted: MutableList<Long>,
    skipping: Set<Long> = emptySet(),
    failure: () -> Exception = { IllegalStateException(DEAD_STORE_MESSAGE) },
): EvaluationCommitRuns =
    emptyCandidateRuns(ids) { account ->
        attempted += account.value
        when (account.value) {
            in failing -> throw failure()
            in skipping -> strategyWithoutCap()
            else -> strategyWithCap()
        }
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
internal fun emptyAudienceRuns(): EvaluationCommitRuns =
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
private fun strategyWithCap(): OperatorStrategy = strategyOf(maxActiveBids = 3)

/** 여력 상한이 **없는** 전략 — 공장이 `null` 을 내므로 그 계정은 건너뜀으로 센다. */
private fun strategyWithoutCap(): OperatorStrategy = strategyOf(maxActiveBids = null)

private fun strategyOf(maxActiveBids: Int?): OperatorStrategy =
    when (
        val result =
            validate(
                StrategyDraft(focusCategories = listOf("CAT-1"), maxActiveBids = maxActiveBids),
                StrategyRevision(1),
                STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>,
            )
    ) {
        is StrategyValidation.Valid -> result.strategy
        is StrategyValidation.Invalid -> error("test 전략이 유효하지 않다: ${result.violations}")
    }
