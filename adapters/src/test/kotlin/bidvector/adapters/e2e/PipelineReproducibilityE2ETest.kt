package bidvector.adapters.e2e

import contract.bidvector.ml.v1.ModelRelease
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * 6D-1 축 ⑤ — 같은 입력·같은 release·같은 고정 시각·같은 순번 상관관계 식별자로 **두 번**
 * 돌리면 outbox payload 가 같다. 비교는 **DB 에서 되읽은 직렬화 문자열**로 한다(같은 객체
 * 참조를 맞대보지 않는다, 설계 검토 (2) 우회 4). 두 run 은 서로 다른 조립이고 각자
 * 자기 트랜잭션에서 쓴다.
 *
 * **제외 필드를 손으로 적지 않는다** — `NotificationRequestedPayload` 에는 시각도 상관관계
 * 식별자도 없다(둘은 outbox 의 별도 열이다). 그래서 payload 전체가 그대로 등식의 대상이고,
 * 시각·상관관계 축은 그 열로 따로 잰다.
 *
 * **알려진 제한(C-4)** — 등식에 드는 release 축은 payload 의 다섯 필드(releaseId ·
 * artifactChecksum · featureSchemaVersion · codeVersion · datasetId)다. 정책 버전과 전략
 * revision 은 payload 에 없어 이 등식이 덮지 못한다 — payload 확장은 6F-10, 등식 확대는 6D-2.
 */
internal class PipelineReproducibilityE2ETest : PipelineE2ESupport() {
    private val servers = mutableListOf<MlFakeServer>()

    @AfterEach
    fun stopServers() {
        servers.forEach { it.close() }
        servers.clear()
    }

    @Test
    fun `같은 입력과 같은 release 로 두 번 돌리면 payload 와 시각과 상관관계가 모두 같다`() {
        seedPipeline()

        evaluateOnce(e2eRelease(E2E_RELEASE_ID))
        evaluateOnce(e2eRelease(E2E_RELEASE_ID))

        outboxPayloads() shouldHaveSize TWO_RUNS
        outboxPayloads().toSet() shouldHaveSize ONE_VALUE
        outboxCorrelationIds() shouldHaveSize TWO_RUNS
        outboxCorrelationIds().toSet() shouldHaveSize ONE_VALUE
        outboxOccurredAt().toSet() shouldHaveSize ONE_VALUE
    }

    /**
     * 음성 대조 — 등식이 「무엇을 넣어도 같다」가 아님을 보인다. release 하나만 바꾸면 payload
     * 가 갈린다(시각·상관관계는 그대로라 갈린 축이 release 임이 분리된다).
     */
    @Test
    fun `release 를 바꾸면 payload 등식이 깨진다`() {
        seedPipeline()

        evaluateOnce(e2eRelease(E2E_RELEASE_ID))
        evaluateOnce(e2eRelease(E2E_ROLLBACK_RELEASE_ID))

        outboxPayloads() shouldHaveSize TWO_RUNS
        outboxPayloads().toSet() shouldHaveSize TWO_RUNS
        outboxCorrelationIds().toSet() shouldHaveSize ONE_VALUE
        outboxOccurredAt().toSet() shouldHaveSize ONE_VALUE
    }

    private fun seedPipeline() {
        seedStrategy()
        seedProfile()
        collectNotices(listOf(e2eNoticeItem(NOTICE)))
    }

    /** 매 run 이 새 조립·새 서버다 — 상관관계 순번은 접두가 같아 0 부터 다시 센다(결정성 축). */
    private fun evaluateOnce(release: ModelRelease) {
        val server = MlFakeServer.start(successfulMlScript(release = release))
        servers += server
        val assembly =
            PipelineAssembly(
                dataSource = dataSource(),
                mlChannel = server.channel,
                at = E2E_NOW,
                correlationPrefix = CORRELATION_PREFIX,
                mlPolicy = e2eMlCallPolicy(),
            )
        runBlocking { assembly.evaluate() }
    }

    private companion object {
        const val NOTICE = "E2E-REPRO-0001"
        const val CORRELATION_PREFIX = "repro"
        const val TWO_RUNS = 2
        const val ONE_VALUE = 1
    }
}
