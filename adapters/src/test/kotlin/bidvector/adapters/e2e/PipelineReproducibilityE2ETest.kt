package bidvector.adapters.e2e

import bidvector.strategy.StrategyRevision
import bidvector.workflow.evaluation.EVALUATION_LADDER_POLICY_VERSION
import contract.bidvector.ml.v1.ModelRelease
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
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
 * **등식의 입력**(6D-2 가 C-4 를 닫았다) — 입력 공고 + 정책 버전(`ladderPolicyVersion`) +
 * release 다섯(releaseId · artifactChecksum · featureSchemaVersion · codeVersion · datasetId) +
 * 전략 revision. 앞 판은 release 다섯만 덮었다(나머지 둘이 payload 에 없었다); 6F-10 이 payload
 * 를 넓혀 둘이 들어왔고 6D-2 가 등식을 거기까지 넓힌다. 단언은 **typed 형**이다 — 직렬화 문자열
 * 등식은 값이 어느 칸에 있는지를 가르지 못한다.
 *
 * **음성 대조는 둘**이다 — release(아래)와 전략 revision(P-2). 정책 버전은 `workflow` 상수라
 * 바꿀 자리가 밖에 없어 「바꾸면 깨진다」를 재지 못한다(B-3 (a), 알려진 제한). 등식이 그 축을
 * **읽고 있음**은 typed 단언이 production 상수 인스턴스와 대조하는 것으로 서고, 그 상수를
 * 바꿔치운 변이가 이 test 를 붉히는 것으로 실측한다.
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

    /**
     * P-1 — 등식의 **typed 형**. 두 run 의 되살린 payload 가 서로 같고, 그 안의 정책 버전이
     * **판정이 쓴 그 인스턴스**([EVALUATION_LADDER_POLICY_VERSION])이며 전략 revision 이
     * **시딩값**이다. 두 run 끼리만 맞대 보면 「둘 다 상수 X」인 구현도 초록이므로 두 값은 각각
     * 바깥의 출처와 대조한다. 시딩 revision 이 1 이 아닌 이유는 [E2E_STRATEGY_REVISION] 에 있다.
     */
    @Test
    fun `두 run 의 payload 는 정책 버전과 전략 revision 까지 같고 그 값이 판정이 쓴 값이다`() {
        seedPipeline()

        evaluateOnce(e2eRelease(E2E_RELEASE_ID))
        evaluateOnce(e2eRelease(E2E_RELEASE_ID))

        val decoded = decodedOutboxPayloads()
        decoded shouldHaveSize TWO_RUNS
        decoded.map { it.ladderPolicyVersion }.toSet() shouldBe setOf(EVALUATION_LADDER_POLICY_VERSION)
        decoded.map { it.strategyRevision }.toSet() shouldBe setOf(StrategyRevision(E2E_STRATEGY_REVISION))
        decoded.toSet() shouldHaveSize ONE_VALUE
    }

    /**
     * P-2 — 전략 revision 음성 대조. **DB 행**(`operator_strategy.revision`)을 바꾼다: 상수나
     * 조립 인자를 바꾸면 DB → repository → 평가 → payload → codec → DB 사슬이 비어 있어도 값이
     * 달라져 초록이 된다(설계 검토 (2) 우회 8). 시각·상관관계는 그대로라 갈린 축이 revision 임이
     * 분리되고, **되돌리면 등식이 복구된다** — 「한 번 달라지면 영원히 다르다」가 아니다.
     */
    @Test
    fun `전략 revision 을 올리면 payload 등식이 깨지고 되돌리면 복구된다`() {
        seedPipeline()
        evaluateOnce(e2eRelease(E2E_RELEASE_ID))
        val baseline = outboxPayloads().single()

        setStrategyRevision(E2E_STRATEGY_REVISION + 1)
        evaluateOnce(e2eRelease(E2E_RELEASE_ID))
        outboxPayloads() shouldHaveSize TWO_RUNS
        outboxPayloads().toSet() shouldHaveSize TWO_RUNS

        setStrategyRevision(E2E_STRATEGY_REVISION)
        evaluateOnce(e2eRelease(E2E_RELEASE_ID))

        outboxPayloads() shouldHaveSize THREE_RUNS
        outboxPayloads().toSet() shouldHaveSize TWO_RUNS
        outboxPayloads().count { it == baseline } shouldBe TWO_RUNS
        decodedOutboxPayloads().map { it.strategyRevision.value }.sorted() shouldBe
            listOf(E2E_STRATEGY_REVISION, E2E_STRATEGY_REVISION, E2E_STRATEGY_REVISION + 1)
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
        const val THREE_RUNS = 3
        const val ONE_VALUE = 1
    }
}
