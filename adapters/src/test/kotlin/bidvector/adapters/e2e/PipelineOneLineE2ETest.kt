package bidvector.adapters.e2e

import bidvector.workflow.evaluation.CandidateEvaluation
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
 * 단언은 전부 **DB 상태**(canonical 행 · outbox state · inbox 행)와 **sender 기록**이다
 * (설계 검토 (1)) — 어느 단계도 로그 줄이나 반환값 문자열로 「지났다」고 주장하지 않는다.
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
     * 설계 검토 (2) 우회 6(verifier r1 F-1 / review G-2) — use case 와 어댑터가 대역으로
     * 바뀌어도 초록인 E2E 를 막는다. 앞 판은 **따로 지은 목록**을 검사해서 배선을 바꿔도
     * 초록이었다(실측). 지금은 평가와 발송이 **실제로 쓰는** 두 객체에서 필드 그래프를 따라
     * 내려가 닿는 `bidvector.*` 객체 전수를 본다 — 목록을 같이 고쳐 통과시키는 길이 없다.
     *
     * 양방향이다: production 출력이 아닌 객체는 **선언된 포트 경계 여섯** 중 하나를 구현해야
     * 하고, 그 여섯은 각각 그래프에 실제로 나타나야 한다.
     */
    @Test
    fun `평가와 발송이 쥔 협력자는 production 출력에서 오고 대역은 선언된 포트 경계뿐이다`() {
        seedStrategy()
        seedProfile()

        val graph = assembly().wiredCollaborators()
        val boundaryBacked = graph.filter { portBoundariesOf(it).isNotEmpty() }

        graph.filter { originOf(it) != ClassOrigin.MAIN && portBoundariesOf(it).isEmpty() } shouldBe emptyList()
        boundaryBacked.filter { originOf(it) != ClassOrigin.TEST } shouldBe emptyList()
        boundaryBacked.flatMap(::portBoundariesOf).toSet() shouldBe PORT_BOUNDARY_TYPES
        graph.map { it.javaClass.name } shouldContainAll EXPECTED_WIRED_PRODUCTION_CLASSES
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

        /** 그래프가 얕아져 조용히 비는 것을 막는 하한 — 이 줄의 클래스는 전부 배선에 실재한다. */
        val EXPECTED_WIRED_PRODUCTION_CLASSES =
            listOf(
                "bidvector.workflow.evaluation.EvaluateCandidatesUseCase",
                "bidvector.workflow.evaluation.OpportunityAnalysis",
                "bidvector.workflow.evaluation.OutboxNotificationRequestPort",
                "bidvector.workflow.notification.DispatchNotification",
                "bidvector.adapters.ml.GrpcBidPredictionGateway",
                "bidvector.adapters.ml.GrpcEmbeddingGateway",
                "bidvector.adapters.evaluation.JdbcCandidateSource",
                "bidvector.adapters.strategy.JdbcStrategyRepository",
                "bidvector.adapters.qualification.StoredRequirementLicenseGate",
                "bidvector.adapters.event.JdbcOutboxPort",
            )
    }
}

