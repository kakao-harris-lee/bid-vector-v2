package bidvector.adapters.e2e

import bidvector.workflow.evaluation.CandidateEvaluation
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
        outboxIdempotencyKeys().single() shouldContain PASSING_NOTICE
    }

    /**
     * 설계 검토 (2) 우회 6 — use case 와 어댑터가 fake 로 바뀌어도 초록인 E2E 를 막는다. 손으로
     * 적은 이름 목록이 아니라 조립이 실제로 쥔 객체의 `CodeSource` 를 본다: production 출력
     * (`classes/kotlin/main`)에서 왔는가. fake 넷은 그 반대편(test 출력)에 있어야 한다 —
     * 양방향이라 「전부 main」도 「전부 test」도 통과하지 못한다.
     */
    @Test
    fun `use case 와 어댑터는 production 출력에서 오고 fake 는 포트 경계 넷뿐이다`() {
        seedStrategy()
        seedProfile()
        val assembly = assembly()

        val production = assembly.productionCollaborators().map { it to originOf(it) }
        val fakes = assembly.portBoundaryFakes().map { it to originOf(it) }

        production.filterNot { it.second == ClassOrigin.MAIN } shouldBe emptyList()
        fakes.filterNot { it.second == ClassOrigin.TEST } shouldBe emptyList()
        production shouldHaveSize PRODUCTION_COLLABORATOR_COUNT
        fakes shouldHaveSize PORT_BOUNDARY_FAKE_COUNT
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
        const val PRODUCTION_COLLABORATOR_COUNT = 12
        const val PORT_BOUNDARY_FAKE_COUNT = 4
    }
}

internal enum class ClassOrigin { MAIN, TEST, UNKNOWN }

/**
 * 객체가 **어느 컴파일 출력**에서 왔는지 — 소스 문자열 grep 이 아니라 클래스로더가 아는
 * 사실이다(설계 검토 「게이트 술어는 문자열이 아니라 구조로」). 스타일을 바꿔도 이 값은
 * 바뀌지 않고, production 을 fake 로 바꾸면 반드시 바뀐다.
 */
internal fun originOf(instance: Any): ClassOrigin {
    val location =
        instance.javaClass.protectionDomain
            ?.codeSource
            ?.location
            ?.path ?: return ClassOrigin.UNKNOWN
    return when {
        location.contains("/classes/kotlin/main/") ||
            location.contains("/classes/java/main/") ||
            location.contains("/build/libs/") -> ClassOrigin.MAIN

        location.contains("/classes/kotlin/test/") || location.contains("/classes/java/test/") -> ClassOrigin.TEST

        else -> ClassOrigin.UNKNOWN
    }
}
