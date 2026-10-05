package bidvector.adapters.e2e

import bidvector.adapters.evaluation.JdbcCandidateSource
import bidvector.adapters.event.JdbcOutboxPort
import bidvector.adapters.ml.GrpcBidPredictionGateway
import bidvector.adapters.ml.GrpcEmbeddingGateway
import bidvector.adapters.qualification.StoredRequirementLicenseGate
import bidvector.adapters.strategy.JdbcStrategyRepository
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.EvaluateCandidatesUseCase
import bidvector.workflow.evaluation.OpportunityAnalysis
import bidvector.workflow.evaluation.OutboxNotificationRequestPort
import bidvector.workflow.notification.DispatchNotification
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * 6D-1 축 ① — mock KONEPS → 수집 → canonical → 요건·면허 gate → in-process fake ML →
 * 평가 → outbox → relay → fake sender 한 줄을 **한 프로세스 안에서** 잇는다.
 *
 * 단언의 자리는 셋이다 — **DB 상태**(canonical 행 · outbox state · inbox 행) · **sender 기록** ·
 * **use case 반환값**(`CandidateEvaluation`·`Verdict` 같은 타입 값이고 문자열이 아니다). 어느
 * 단계도 로그 줄로 「지났다」고 주장하지 않는다(설계 검토 (1)).
 */
internal class PipelineOneLineE2ETest : PipelineE2ESupport() {
    private val servers = mutableListOf<MlFakeServer>()

    @AfterEach
    fun stopServers() {
        servers.forEach { it.close() }
        servers.clear()
    }

    @Test
    fun `수집부터 발송까지 한 줄이 이어지고 각 단계가 영속 흔적을 남긴다`() {
        seedStrategy()
        seedProfile()
        collectNotices(listOf(e2eNoticeItem(PASSING_NOTICE)))
        canonicalNoticeNumbers() shouldContainExactly listOf(PASSING_NOTICE)

        val sender = runPipeline()

        outboxStates() shouldContainExactly listOf("DELIVERED")
        inboxKeys() shouldContainExactly outboxIdempotencyKeys()
        sender.sentKeys() shouldContainExactly outboxIdempotencyKeys()
        sender.sentRoutes() shouldContainExactly listOf(E2E_ROUTE.value)
        sender.sentBodies() shouldContainExactly listOf("$PASSING_NOTICE-000")
    }

    @Test
    fun `면허 gate 가 요구를 못 채운 공고를 떨어뜨린다 — 그 공고는 outbox 에 닿지 않는다`() {
        seedStrategy()
        seedProfile(licenses = listOf(E2E_LICENSE))
        collectNotices(listOf(e2eNoticeItem(PASSING_NOTICE), e2eNoticeItem(BLOCKED_NOTICE)))
        seedLicenseRequirement(noticeIdOf(BLOCKED_NOTICE), UNHELD_LICENSE)

        val evaluations = runBlocking { assembly().evaluate() }

        canonicalNoticeNumbers() shouldContainExactly listOf(BLOCKED_NOTICE, PASSING_NOTICE)
        evaluations.filterIsInstance<CandidateEvaluation.NotReached>().map { it.noticeId.number.value } shouldBe
            listOf(BLOCKED_NOTICE)
        outboxIdempotencyKeys() shouldHaveSize 1
        outboxIdempotencyKeys().single() shouldBe idempotencyKeyFor(PASSING_NOTICE)
    }

    /**
     * 설계 검토 (2) 우회 6 — use case 와 어댑터가 대역으로 바뀌어도 초록인 E2E 를 막는다.
     * 평가와 발송이 **실제로 쓰는** 두 객체에서 필드 그래프를 내려가 닿는 객체 전수를 본다.
     *
     * 수집 기준은 패키지 이름이 아니라 **출처**다 — 우리 build 출력에서 온 객체면 어느 패키지든
     * 그래프에 들어온다. 이름 술어였을 때는 관례 밖 패키지에 둔 대역이 보이지 않았다(실측).
     *
     * 양방향이다: production 출력이 아닌 객체는 **선언된 포트 경계 여섯** 중 하나를 구현해야
     * 하고, 그 여섯은 각각 그래프에 실제로 나타나야 한다. 건너뛴 가지가 있으면(깊이 상한·필드
     * 읽기 실패) 그 아래 대역을 못 보므로 둘 다 0 이어야 한다.
     */
    @Test
    fun `평가와 발송이 쥔 협력자는 production 출력에서 오고 대역은 선언된 포트 경계뿐이다`() {
        seedStrategy()
        seedProfile()

        val graph = assembly().wiredCollaborators()
        val boundaryBacked = graph.collected.filter { portBoundariesOf(it).isNotEmpty() }

        graph.depthLimitHits shouldBe 0
        graph.traversalFailures shouldBe emptyList()
        graph.skippedHolders shouldBe emptyList()
        graph.collected.filter {
            originOf(it) != ClassOrigin.MAIN && portBoundariesOf(it).isEmpty()
        } shouldBe emptyList()
        boundaryBacked.filter { originOf(it) != ClassOrigin.TEST } shouldBe emptyList()
        boundaryBacked.flatMap(::portBoundariesOf).toSet() shouldBe PORT_BOUNDARY_TYPES
        graph.collected.map { it.javaClass } shouldContainAll EXPECTED_WIRED_PRODUCTION_CLASSES
    }

    private fun runPipeline(): RecordingNotificationSender {
        val assembly = assembly()
        runBlocking { assembly.evaluate() }
        assembly.relay() shouldBe 1
        return assembly.sender
    }

    private fun assembly(): PipelineAssembly =
        MlFakeServer.start(successfulMlScript()).let { server ->
            servers += server
            PipelineAssembly(
                dataSource = dataSource(),
                mlChannel = server.channel,
                at = E2E_NOW,
                correlationPrefix = "one-line",
                mlPolicy = e2eMlCallPolicy(),
            )
        }

    private companion object {
        const val PASSING_NOTICE = "E2E-PASS-0001"
        const val BLOCKED_NOTICE = "E2E-BLOCK-0001"
        const val UNHELD_LICENSE = "전기공사업"

        /**
         * 그래프가 얕아져 조용히 비는 것을 막는 하한. **문자열이 아니라 타입**이라 이름이 바뀌면
         * 컴파일이 먼저 막는다(review PR62 P — 손으로 적은 FQN 은 조용히 낡는다).
         */
        val EXPECTED_WIRED_PRODUCTION_CLASSES: List<Class<*>> =
            listOf(
                EvaluateCandidatesUseCase::class.java,
                OpportunityAnalysis::class.java,
                OutboxNotificationRequestPort::class.java,
                DispatchNotification::class.java,
                GrpcBidPredictionGateway::class.java,
                GrpcEmbeddingGateway::class.java,
                JdbcCandidateSource::class.java,
                JdbcStrategyRepository::class.java,
                StoredRequirementLicenseGate::class.java,
                JdbcOutboxPort::class.java,
            )
    }
}
