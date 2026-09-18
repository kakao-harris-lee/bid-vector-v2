package bidvector.adapters.persistence

import bidvector.adapters.strategy.InvalidStoredStrategyException
import bidvector.adapters.strategy.JdbcStrategyRepository
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.strategy.BudgetBoundInclusivity
import bidvector.strategy.CategoryCode
import bidvector.strategy.ScoreRange
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyViolation
import bidvector.strategy.ThresholdField
import bidvector.strategy.isConfigured
import bidvector.workflow.strategy.Actor
import bidvector.workflow.strategy.BeginOutcome
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.CommandId
import bidvector.workflow.strategy.CommandResult
import bidvector.workflow.strategy.EditCommand
import bidvector.workflow.strategy.EditSession
import bidvector.workflow.strategy.EditSessionId
import bidvector.workflow.strategy.EditSessionPolicyData
import bidvector.workflow.strategy.EditSessionRepository
import bidvector.workflow.strategy.EditSessionSnapshot
import bidvector.workflow.strategy.EditStrategyWorkflow
import bidvector.workflow.strategy.EditableField
import bidvector.workflow.strategy.EventSink
import bidvector.workflow.strategy.OperatorId
import bidvector.workflow.strategy.StrategyRepository
import bidvector.workflow.strategy.TransitionOutcome
import bidvector.workflow.strategy.toSnapshot
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

private val OPERATOR = OperatorId("op-6f1")
private val CLOCK_INSTANT: Instant = Instant.parse("2026-09-17T00:00:00Z")

/** [StrategyPolicyData.matchScoreRange]만 좁혀 「정책이 바뀌었다」를 흉내낸다 — 나머지는 항상 `[0,1]`. */
private fun testPolicy(matchScoreMax: BigDecimal = BigDecimal.ONE): Resolution.Resolved<StrategyPolicyData> =
    Resolution.Resolved(
        StrategyPolicyData(
            matchScoreRange = ScoreRange(BigDecimal.ZERO, matchScoreMax),
            probabilityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            priorityScoreRange = ScoreRange(BigDecimal.ZERO, BigDecimal.ONE),
            budgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
        ),
        PolicyVersion(EffectiveFrom.Initial, "test-6f1-strategy-policy"),
    )

private class InMemorySessionRepository : EditSessionRepository {
    private val sessions = mutableMapOf<EditSessionId, EditSession>()

    override fun load(id: EditSessionId): EditSessionSnapshot? = sessions[id]?.toSnapshot()

    override fun save(session: EditSession) {
        sessions[session.id] = session
    }
}

private class RecordingEventSink : EventSink {
    override fun publish(
        event: StrategyEvent,
        actor: Actor,
    ) = Unit
}

private class StrategyFixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}

/**
 * [JdbcStrategyRepository]의 왕복·개정·정책 불일치·부재 test(M6/6F-1 S-50). `AppliedStrategy`
 * 는 `workflow` 모듈 밖에서 만들 수 없다(D-6F1-2와 같은 폐쇄) — 이 test는 실제
 * [EditStrategyWorkflow]를 `strategies`에 이 repository 를 꽂아 구동한다. 이것이 정당한
 * `AppliedStrategy`를 얻는 **유일한** 방법이다(workflow가 `strategies.save`를 내부에서 부른다,
 * `EditStrategyWorkflow.process` 참고) — repository의 `save`를 직접 부르는 test가 아니다.
 */
class JdbcStrategyRepositoryTest : PersistenceTestSupport() {
    private fun workflowWith(
        strategies: StrategyRepository,
        policy: Resolution.Resolved<StrategyPolicyData> = testPolicy(),
    ): EditStrategyWorkflow =
        EditStrategyWorkflow(
            sessions = InMemorySessionRepository(),
            strategies = strategies,
            clock = StrategyFixedClock(CLOCK_INSTANT),
            events = RecordingEventSink(),
            strategyPolicy = policy,
            sessionPolicy = EditSessionPolicyData(Duration.ofMinutes(15)),
        )

    /** `begin → provideValue → confirm`을 몰아 `strategies.save`를 발생시킨다(유일한 정당 경로). */
    private fun applyDraft(
        workflow: EditStrategyWorkflow,
        sessionId: EditSessionId,
        draft: StrategyDraft,
        seenRevision: StrategyRevision,
    ) {
        val begun = workflow.begin(sessionId, OPERATOR, EditableField.CandidateLimit)
        check(begun is BeginOutcome.Started) { "begin 이 Started 를 기대했으나 $begun" }

        val provided =
            workflow.provideValue(
                EditCommand.ProvideValue(
                    CommandId("$sessionId-provide"),
                    sessionId,
                    Actor.Operator(OPERATOR),
                    EditableField.CandidateLimit,
                    draft,
                ),
            )
        check(provided is CommandResult.Processed && provided.outcome is TransitionOutcome.Accepted) {
            "provideValue 가 Accepted 를 기대했으나 $provided"
        }

        val confirmed =
            workflow.confirm(
                EditCommand.Confirm(CommandId("$sessionId-confirm"), sessionId, Actor.Operator(OPERATOR), seenRevision),
            )
        check(confirmed is CommandResult.Processed && confirmed.outcome is TransitionOutcome.Applied) {
            "confirm 이 Applied 를 기대했으나 $confirmed"
        }
    }

    private fun fullDraft(): StrategyDraft =
        StrategyDraft(
            focusCategories = listOf("BC01", "BC02"),
            focusRegionTerms = listOf("서울", "경기"),
            excludeRegionTerms = listOf("제주"),
            requiredKeywordTerms = listOf("소프트웨어"),
            excludeKeywordTerms = listOf("건설"),
            minBudget = BaseAmount(1_000_000L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared),
            maxBudget = BaseAmount(50_000_000L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared),
            minimumMatchScore = BigDecimal("0.60"),
            minimumProbabilityScore = BigDecimal("0.55"),
            bidNowThreshold = BigDecimal("0.70"),
            reviewThreshold = BigDecimal("0.45"),
            candidateLimit = 10,
        )

    // ⓐ — 왕복: 저장한 전략을 읽으면 같다.
    @Test
    fun `왕복 — 저장한 전략을 읽으면 같다`() {
        val repository = JdbcStrategyRepository(dataSource(), testPolicy())
        val workflow = workflowWith(repository)
        val draft = fullDraft()

        applyDraft(workflow, EditSessionId("s-round-trip"), draft, StrategyRevision(0))

        // 새 인스턴스로 다시 읽어 프로세스 내 캐시가 없음을 함께 확인한다.
        val reloaded = JdbcStrategyRepository(dataSource(), testPolicy()).load()

        reloaded.watchRules.focusCategories shouldBe setOf(CategoryCode("BC01"), CategoryCode("BC02"))
        reloaded.watchRules.focusRegionTerms shouldBe listOf("서울", "경기")
        reloaded.watchRules.excludeRegionTerms shouldBe listOf("제주")
        reloaded.watchRules.requiredKeywordTerms shouldBe listOf("소프트웨어")
        reloaded.watchRules.excludeKeywordTerms shouldBe listOf("건설")
        reloaded.watchRules.budget.min shouldBe draft.minBudget
        reloaded.watchRules.budget.max shouldBe draft.maxBudget
        val thresholds = reloaded.actionThresholds
        thresholds.minimumMatchScore
            ?.score
            ?.value shouldBe BigDecimal("0.60")
        thresholds.minimumProbabilityScore
            ?.score
            ?.value shouldBe BigDecimal("0.55")
        thresholds.bidNowThreshold
            ?.score
            ?.value shouldBe BigDecimal("0.70")
        thresholds.reviewThreshold
            ?.score
            ?.value shouldBe BigDecimal("0.45")
        reloaded.candidateLimit?.value shouldBe 10
        reloaded.revision shouldBe StrategyRevision(1)
    }

    // ⓑ — 개정 증가가 도메인 값 그대로 저장된다.
    @Test
    fun `개정 증가 — 두 번째 저장 뒤 최신 값과 revision 2가 반영된다`() {
        val repository = JdbcStrategyRepository(dataSource(), testPolicy())
        val workflow = workflowWith(repository)

        applyDraft(workflow, EditSessionId("s-rev-1"), fullDraft(), StrategyRevision(0))
        val afterFirst = repository.load()
        afterFirst.revision shouldBe StrategyRevision(1)

        val secondDraft = fullDraft().copy(candidateLimit = 25, bidNowThreshold = BigDecimal("0.80"))
        applyDraft(workflow, EditSessionId("s-rev-2"), secondDraft, StrategyRevision(1))

        val afterSecond = repository.load()
        afterSecond.revision shouldBe StrategyRevision(2)
        afterSecond.candidateLimit?.value shouldBe 25
        afterSecond.actionThresholds.bidNowThreshold
            ?.score
            ?.value shouldBe BigDecimal("0.80")

        // 이력 두 줄이 각각 다른 revision·값으로 실제로 쌓였다(D-6F1-1 ① 「개정 이력」).
        val historyRevisions =
            dataSource().connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT revision FROM operator_strategy_revision ORDER BY revision").use {
                        generateSequence { if (it.next()) it.getInt("revision") else null }.toList()
                    }
                }
            }
        historyRevisions shouldBe listOf(1, 2)

        // verifier r2 MEDIUM-2 — `operator_strategy_revision`의 payload 컬럼은 위 `revision`
        // 하나 말고는 아무 test 도 되읽지 않았다(컬럼 순서를 바꿔 심어도 전건이 초록이었다).
        // `min_budget_currency`·`min_budget_vat`을 나란히 되읽어 그 둘이 서로 바뀌면 잡히게
        // 한다 — `INSERT_STRATEGY_REVISION`의 바인딩 순서(`bindStrategyRow`)가 실제로
        // 지켜지는지를 이력 표 자신에서 확인한다(현재 값 표가 아니라).
        val secondRevisionPayload =
            dataSource().connection.use { connection ->
                connection
                    .prepareStatement(
                        "SELECT min_budget_currency, min_budget_vat, candidate_limit, bid_now_threshold " +
                            "FROM operator_strategy_revision WHERE revision = 2",
                    ).use { statement ->
                        statement.executeQuery().use { rs ->
                            check(rs.next()) { "operator_strategy_revision 에 revision=2 행이 없다" }
                            listOf(
                                rs.getString("min_budget_currency"),
                                rs.getString("min_budget_vat"),
                                rs.getInt("candidate_limit"),
                                rs.getBigDecimal("bid_now_threshold"),
                            )
                        }
                    }
            }
        secondRevisionPayload shouldBe listOf("KRW", "INCLUSIVE", 25, BigDecimal("0.80"))
    }

    // ⓒ — 저장된 값이 현재 정책으로 무효면 실패한다(전략을 지어내지 않는다, D-6F1-3).
    @Test
    fun `정책 불일치 — 저장된 값이 현재 정책으로 무효면 load 가 실패한다`() {
        val saveRepository = JdbcStrategyRepository(dataSource(), testPolicy(matchScoreMax = BigDecimal.ONE))
        val workflow = workflowWith(saveRepository, testPolicy(matchScoreMax = BigDecimal.ONE))
        val draft = fullDraft().copy(minimumMatchScore = BigDecimal("0.90"))
        applyDraft(workflow, EditSessionId("s-invalid"), draft, StrategyRevision(0))

        // 정책이 바뀌어 이제 matchScore 상한이 0.5 다 — 저장된 0.90은 이제 무효다.
        val stricterRepository = JdbcStrategyRepository(dataSource(), testPolicy(matchScoreMax = BigDecimal("0.5")))
        val thrown = shouldThrow<InvalidStoredStrategyException> { stricterRepository.load() }
        thrown.violations shouldBe listOf(StrategyViolation.ScoreOutOfRange(ThresholdField.MinimumMatchScore))
    }

    // ⓓ — 전략 없음(첫 기동)은 실패가 아니라 빈 전략이고, 「설정됐는가」 술어가 거짓이다.
    @Test
    fun `전략 없음 — 첫 기동은 예외 없이 빈 전략을 내고 isConfigured 는 거짓이다`() {
        val repository = JdbcStrategyRepository(dataSource(), testPolicy())

        val strategy = repository.load()

        strategy.isConfigured() shouldBe false
        strategy.revision shouldBe StrategyRevision(0)
        strategy.watchRules.budget.min shouldBe null
        strategy.watchRules.budget.max shouldBe null
        strategy.candidateLimit shouldBe null
    }

    // ⓔ — 저장 전후 필드 동일성: 경계값(스코어 0/1, 빈 목록 혼재)도 save 가 지어내지 않는다.
    @Test
    fun `저장 전후 필드 동일성 — 경계값도 save 가 값을 지어내지 않고 그대로 왕복한다`() {
        val repository = JdbcStrategyRepository(dataSource(), testPolicy())
        val workflow = workflowWith(repository)
        val edgeDraft =
            StrategyDraft(
                focusCategories = emptyList(),
                focusRegionTerms = emptyList(),
                excludeRegionTerms = listOf("단독_제외"),
                requiredKeywordTerms = emptyList(),
                excludeKeywordTerms = emptyList(),
                minBudget = null,
                maxBudget = BaseAmount(1L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared),
                minimumMatchScore = BigDecimal.ZERO,
                minimumProbabilityScore = BigDecimal.ONE,
                bidNowThreshold = null,
                reviewThreshold = null,
                candidateLimit = 1,
            )

        applyDraft(workflow, EditSessionId("s-edge"), edgeDraft, StrategyRevision(0))

        val reloaded = JdbcStrategyRepository(dataSource(), testPolicy()).load()
        reloaded.watchRules.focusCategories shouldBe emptySet()
        reloaded.watchRules.excludeRegionTerms shouldBe listOf("단독_제외")
        reloaded.watchRules.budget.min shouldBe null
        reloaded.watchRules.budget.max shouldBe edgeDraft.maxBudget
        reloaded.actionThresholds.minimumMatchScore
            ?.score
            ?.value shouldBe BigDecimal.ZERO
        reloaded.actionThresholds.minimumProbabilityScore
            ?.score
            ?.value shouldBe BigDecimal.ONE
        reloaded.actionThresholds.bidNowThreshold shouldBe null
        reloaded.actionThresholds.reviewThreshold shouldBe null
        reloaded.candidateLimit?.value shouldBe 1
    }

    // D-6F1-5 우회 (3) — 개정 번호를 DB 기본값·시퀀스·트리거로 정하면 이 test 가 붉어진다.
    @Test
    fun `카탈로그 확인 — revision 컬럼은 DB 기본값이 없고 두 표에 트리거가 없다`() {
        val defaults =
            dataSource().connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement
                        .executeQuery(
                            "SELECT table_name, column_default FROM information_schema.columns " +
                                "WHERE table_schema = 'public' " +
                                "AND table_name IN ('operator_strategy', 'operator_strategy_revision') " +
                                "AND column_name = 'revision'",
                        ).use { rs ->
                            generateSequence {
                                if (rs.next()) rs.getString("table_name") to rs.getString("column_default") else null
                            }.toMap()
                        }
                }
            }
        defaults shouldBe mapOf("operator_strategy" to null, "operator_strategy_revision" to null)

        val triggers =
            dataSource().connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement
                        .executeQuery(
                            "SELECT trigger_name FROM information_schema.triggers WHERE trigger_schema = 'public' " +
                                "AND event_object_table IN ('operator_strategy', 'operator_strategy_revision')",
                        ).use { rs ->
                            generateSequence { if (rs.next()) rs.getString("trigger_name") else null }.toList()
                        }
                }
            }
        triggers shouldBe emptyList()
    }
}
