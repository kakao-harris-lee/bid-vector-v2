package bidvector.app.evaluation

import bidvector.app.adminDataSource
import bidvector.app.http.SequentialCorrelationIdFactory
import bidvector.app.http.TestStrategyRepository
import bidvector.app.wiring.EvaluationCommitProperties
import bidvector.app.wiring.EvaluationCommitWiring
import bidvector.app.wiring.EvaluationDryRunFactory
import bidvector.decision.MlUnavailableReason
import bidvector.decision.UnitScore
import bidvector.decision.Verdict
import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.qualification.LicenseVerdict
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.Resolution
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.WatchSubject
import bidvector.strategy.assembleFullScopeText
import bidvector.strategy.assembleKeywordScopeText
import bidvector.strategy.validate
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.EVALUATION_LADDER_POLICY_VERSION
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.PredictionEvidence
import bidvector.workflow.evaluation.WatchSubjectOutcome
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private const val POSTGRES_IMAGE = "postgres:16.4"
private const val MAX_ACTIVE_BIDS = 10

/** 여력이 남은 값 — 두 경로가 **같은** 값을 받는지가 이 test 의 축이다. */
private const val CURRENT_ACTIVE_BIDS = 3

/**
 * 개정 번호를 **자릿수가 긴 값**으로 둔다 — payload 대조가 부분 문자열이라, 1 이나 7 같은 값은
 * 다른 칸의 숫자와 우연히 겹쳐 「실렸다」가 거짓으로 참이 된다.
 */
private const val SEEDED_REVISION = 907

private val NOW: Instant = Instant.parse("2026-10-16T00:00:00Z")

/**
 * DEC-06 넷째 acceptance — **같은 공고를 어느 경로로 평가해도 액션과 근거가 같다.**
 *
 * 이 축에 이미 있는 측정은 **구조**다 — dry-run 조립이 커밋 조립 타입을 참조하지 않는다는
 * architecture 게이트. 구조는 두 조립이 **같은 use case 를 쓴다**는 사실까지 잠그지만, 그 둘에
 * 같은 공고를 흘려 **판정을 대조하는** 자리는 없었다. 두 조립이 use case 에 넘기는 값
 * (고정 전략·여력·분석 예산)이 갈리면 구조 게이트는 초록인 채 판정이 달라진다.
 *
 * 그래서 이 test 는 두 **진입점을 각각 부른다** — dry-run 은 컨트롤러가 부르는 요청 스코프
 * 팩토리(`EvaluationDryRunFactory.forRequest`)이고, 커밋은 일회 러너가 받는 빈을 만드는
 * 조립 메서드(`EvaluationCommitWiring.evaluationCommitRun`)다. 협력자 port 는 같은 값을
 * 내는 fake 로 **양쪽에 같게** 준다: 갈릴 수 있는 것은 조립이 스스로 고르는 값뿐이다.
 *
 * 등식은 두 층이다 — ① 두 경로가 돌려준 `CandidateEvaluation` 목록이 **값으로 같다**(액션·
 * reason code·알림 처분까지) ② 커밋 경로가 **실제로 저장한** payload 가 dry-run 이 낸 판정의
 * 사유 문자열·공고 식별자·전략 개정·사다리 정책 버전을 나른다. ② 의 기대값은 **dry-run 쪽
 * 결과에서** 뽑는다 — 두 경로가 갈리면 그 자리에서 붉어진다.
 *
 * `DataSource` 는 컨테이너 소유자다 — 이 test 의 축은 경로 등식이고 최소 권한 역할 축은 출하
 * 조립을 띄우는 test 들이 진다. 상관 ID 는 경로마다 **새 인스턴스**를 주어 같은 수열이 되게
 * 한다(값이 경로 식별자를 나르지 않는다는 것도 ① 의 일부다).
 *
 * ② 는 사유 문자열을 **쉼표로 쪼갠 조각**으로 본다 — 쉼표는 wire 구분자라 저장될 때 탈출되고,
 * 그 탈출 형식은 이 test 의 관심이 아니다(그 형식의 정본은 `adapters` 의 어휘 골든이고 디코더는
 * 그 모듈 `internal` 이라 여기서 부를 수 없다). 쪼개도 값은 하나도 버리지 않는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DryRunCommitParityTest {
    companion object {
        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_dry_run_parity_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        @JvmStatic
        @BeforeAll
        fun migrate() {
            Flyway
                .configure()
                .dataSource(adminDataSource(postgres))
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            postgres.stop()
        }
    }

    private val strategy = parityStrategy()
    private val candidate = openCandidate()
    private val candidateSource = CandidateSourcePort { listOf(candidate) }
    private val watchSubjects = WatchSubjectPort { WatchSubjectOutcome.Found(PASSING_SUBJECT) }
    private val licenseGate = LicenseGatePort { LicenseVerdict.Eligible(emptySet()) }
    private val clock = Clock { NOW }

    @Test
    fun `같은 공고를 dry-run 과 커밋 두 진입점으로 흘리면 액션과 근거가 같다`() {
        val dryRun =
            EvaluationDryRunFactory(
                strategyRepository = TestStrategyRepository(strategy),
                candidateSource = candidateSource,
                watchSubjects = watchSubjects,
                licenseGate = licenseGate,
                mlAnalysis = BidNowMlAnalysis(),
                correlationIds = SequentialCorrelationIdFactory(),
                clock = clock,
            ).forRequest(CURRENT_ACTIVE_BIDS)
        val dryRunResults = runBlocking { dryRun.useCase.evaluate() }

        val commitResults = runBlocking { commitRun().evaluate() }

        // ① 액션·근거·처분까지 값으로 같다.
        commitResults shouldBe dryRunResults

        // 등식이 공집합 대 공집합이 아니다 — 판정이 알림 축까지 닿았다.
        val reached = dryRunResults.single().shouldBeInstanceOf<CandidateEvaluation.Reached>()
        val bidNow = reached.verdict.shouldBeInstanceOf<Verdict.BidNow>()
        dryRun.wouldNotifyNoticeIds() shouldBe listOf(candidate.id)

        // ② 커밋 경로가 저장한 payload 가 dry-run 쪽 판정의 근거를 나른다.
        val payload = notificationRows(adminDataSource(postgres)).single().payload
        // 사유 목록이 **비어 있지 않다** — 비면 아래 `forEach` 가 아무것도 대조하지 않고 통과한다.
        bidNow.reasons.shouldNotBeEmpty()
        bidNow.reasons.flatMap { it.toString().split(", ") }.forEach { payload shouldContain it }
        payload shouldContain "${candidate.id.number.value}-${candidate.id.round.value}"
        // 개정 번호도 **dry-run 경로가 읽은 값**에서 뽑는다 — 두 경로가 다른 스냅샷을 보면 갈린다.
        val judgedRevision = dryRun.strategy.revision
        payload shouldContain judgedRevision.value.toString()
        payload shouldContain EVALUATION_LADDER_POLICY_VERSION.source
    }

    /** 커밋 진입점 — 일회 러너가 받는 빈을 **production 조립 메서드**가 짓는다. */
    private fun commitRun() =
        EvaluationCommitWiring().evaluationCommitRun(
            dataSource = adminDataSource(postgres),
            strategyRepository = TestStrategyRepository(strategy),
            candidateSourcePort = candidateSource,
            watchSubjectPort = watchSubjects,
            licenseGatePort = licenseGate,
            mlAnalysisPort = BidNowMlAnalysis(),
            correlationIdFactory = SequentialCorrelationIdFactory(),
            clock = clock,
            commitProperties = EvaluationCommitProperties(CURRENT_ACTIVE_BIDS),
        )
}

/**
 * 감시 게이트를 통과하는 대역 — 규칙 하나(절대 맞지 않는 제외 키워드)만 둔 전략과 짝이다
 * (`NoGate` 는 통과가 아니다). 금액·텍스트 축은 이 test 의 관심 밖이라 비운다.
 */
private val PASSING_SUBJECT =
    WatchSubject(
        categories = emptySet(),
        keywordText = assembleKeywordScopeText(null, null),
        fullText = assembleFullScopeText(null, null, null, null),
        baseAmount = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE),
    )

/** 확정 `BidNow` 를 내는 분석 — 우선도가 전략의 `bidNowThreshold` 이상이다. */
private class BidNowMlAnalysis : MlAnalysisPort {
    override suspend fun analyze(
        notice: Notice,
        correlationId: CorrelationId,
    ): MlAnalysisOutcome =
        MlAnalysisOutcome.Analyzed(
            priorityScore = UnitScore(BigDecimal("0.9")),
            probabilityScore = null,
            matchedScore = null,
            evidence = PredictionEvidence.NotPredicted(MlUnavailableReason.ScoreNotProvided),
        )
}

/**
 * 출하 정책(`STRATEGY_POLICY`)을 지나 만든 전략 — `candidateLimit` 은 비운다(두 조립이 그
 * 값을 **같게** 읽는지가 ① 의 일부다).
 */
private fun parityStrategy(): OperatorStrategy {
    val policy = STRATEGY_POLICY.resolve(LocalDate.ofInstant(NOW, java.time.ZoneOffset.UTC))
    val resolved = (policy as Resolution.Resolved<StrategyPolicyData>)
    val draft =
        StrategyDraft(
            excludeKeywordTerms = listOf("없는-문구"),
            bidNowThreshold = BigDecimal("0.7"),
            reviewThreshold = BigDecimal("0.45"),
            maxActiveBids = MAX_ACTIVE_BIDS,
        )
    return when (val result = validate(draft, StrategyRevision(SEEDED_REVISION), resolved)) {
        is StrategyValidation.Valid -> result.strategy
        is StrategyValidation.Invalid -> error("test fixture 가 유효하지 않다: ${result.violations}")
    }
}

private fun openCandidate(): Notice {
    val id = NoticeId(NoticeNumber.of("DEC06-PARITY-001"), NoticeRound.of("000"))
    val observation =
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
            SourceEndpoint.NOTICE_LIST,
            NOW,
        )
    return Notice.collected(
        NoticeCollected(
            id = id,
            businessCategory = null,
            baseAmount = null,
            estimatedAmount = null,
            allocatedBudget = null,
            floorRate = null,
            deadlineAt = NOW.plusSeconds(86_400),
            openingScheduledAt = null,
            raw = observation,
        ),
    )
}
