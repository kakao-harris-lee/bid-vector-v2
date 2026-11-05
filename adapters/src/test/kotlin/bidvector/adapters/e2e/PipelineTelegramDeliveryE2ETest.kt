package bidvector.adapters.e2e

import bidvector.adapters.notification.FAKE_CHAT_ID
import bidvector.adapters.notification.FAKE_SITE_BASE_URL
import bidvector.adapters.notification.FakeTelegramServer
import bidvector.adapters.notification.JdbcNoticeNotificationFacts
import bidvector.adapters.notification.NoticeMessageRenderer
import bidvector.adapters.notification.NoticeMessageSettings
import bidvector.adapters.notification.TelegramScript
import bidvector.adapters.notification.channelSettings
import bidvector.adapters.relay.NotificationRelayRun
import bidvector.sharedkernel.Resolution
import bidvector.workflow.notification.Channel
import bidvector.workflow.notification.ContentRef
import bidvector.workflow.notification.NOTIFICATION_DELIVERY_POLICY
import bidvector.workflow.notification.NotificationDeliveryPolicyData
import bidvector.workflow.notification.RelayReport
import bidvector.workflow.notification.RelayTarget
import bidvector.workflow.notification.RuntimeEnvironment
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * **실 sender 조립 E2E**(6D-2 가 `OPEN-STR-12` 뒤로 넘긴 축). mock KONEPS → 수집 → canonical →
 * 면허 gate → in-process fake ML → 평가 → outbox → **production relay 조립** → **실
 * `TelegramNotificationSender`** → 가짜 Telegram 서버 한 줄.
 *
 * `PipelineOneLineE2ETest` 와 갈리는 자리는 **발송 축 셋**이다 — 그쪽은 route·renderer·sender 를
 * test 대역으로 바꿔 끼우고(그래서 「조립이 production 출력에서 온다」 그래프 단언을 지닌다), 이쪽은
 * 그 셋까지 production 이고 **가짜인 것은 Telegram 서버 하나**다. 그래서 여기서만 재는 것 둘:
 *
 *  ① 공고 표에서 읽은 표시 칸이 **wire 에 닿는다** — 본문이 수집 경로가 심은 공고명·수요기관·
 *    기초금액을 담고, 「공고 정보가 없습니다」 갈래가 아니다.
 *  ② 발송 뒤 종단이 `DELIVERED` 이고 inbox 에 그 멱등 키가 **한 줄** 남는다(at-most-once 의 장부).
 */
internal class PipelineTelegramDeliveryE2ETest : PipelineE2ESupport() {
    private val servers = mutableListOf<MlFakeServer>()

    @AfterEach
    fun stopServers() {
        servers.forEach { it.close() }
        servers.clear()
    }

    @Test
    fun `평가가 낸 행을 실 sender 조립이 보내고 공고 표시 칸이 본문에 실린다`() {
        seedStrategy()
        seedProfile()
        collectNotices(listOf(e2eNoticeItem(DELIVERY_NOTICE, demandAgency = DEMAND_AGENCY)))
        runBlocking { assembly().evaluate() }
        outboxStates() shouldContainExactly listOf("PENDING")

        FakeTelegramServer.start(listOf(TelegramScript.ok())).use { server ->
            val report =
                productionRelayRun(server.baseUrl)
                    .relay(RELAY_CLAIM_LIMIT)
                    .shouldBeInstanceOf<RelayReport.Completed>()

            report.claimed shouldBe 1
            report.delivered shouldBe 1
            report.failed shouldBe 0
            report.isolated shouldBe 0
            outboxStates() shouldContainExactly listOf("DELIVERED")
            inboxKeys() shouldContainExactly outboxIdempotencyKeys()

            assertSentBody(server)
        }
    }

    /**
     * 수신 본문의 단언은 **둘**이다. ⓐ 렌더러를 같은 공고에 직접 돌린 결과와의 등식 — relay 가 그
     * 공고를 렌더해 보냈다는 사실. ⓑ 그 결과 안의 **값들**이 수집 경로가 심은 것이라는 사실 —
     * ⓐ 만으로는 렌더러가 무엇을 넣든 통과하므로(자기 자신과의 비교) 칸의 내용을 따로 못박는다.
     */
    private fun assertSentBody(server: FakeTelegramServer) {
        val sent = server.received().single()
        sent.form.getValue("chat_id") shouldBe FAKE_CHAT_ID

        val body = sent.form.getValue("text")
        body shouldBe renderedBody()

        body shouldContain "공고명 E2E 통합 유지관리 용역 $DELIVERY_NOTICE"
        body shouldContain "수요기관 $DEMAND_AGENCY"
        body shouldContain "기초금액 1,000,000원"
        body shouldContain "$FAKE_SITE_BASE_URL/notices/$DELIVERY_NOTICE/000"
        // 마감은 KST 로 적힌다 — 수집 원문의 구역 해석은 이 slice 밖이라 **모양**만 못박는다.
        DEADLINE_LINE.containsMatchIn(body) shouldBe true
        // 공고 행을 찾았다 — 축약 갈래가 아니다(그 갈래는 이 네 칸을 아예 쓰지 않는다).
        body shouldNotContain "저장된 공고 정보가 없습니다"
    }

    /** 같은 공고를 **production 렌더러**로 직접 렌더한 결과 — wire 에 닿은 본문과 대조한다. */
    private fun renderedBody(): String =
        NoticeMessageRenderer(
            facts = JdbcNoticeNotificationFacts(dataSource()),
            settings = NoticeMessageSettings(FAKE_SITE_BASE_URL),
        ).render(ContentRef("$DELIVERY_NOTICE-000"), Channel.Telegram).body

    /**
     * **production 조립 그대로**(`NotificationRelayRun`) — 바꿔 끼우는 것은 Telegram base URL
     * 하나이고(D-9 실제 호출 0), route·renderer·sender·outbox·inbox·임대·경계는 전부 production 이다.
     */
    private fun productionRelayRun(telegramBaseUrl: String): NotificationRelayRun =
        NotificationRelayRun(
            dataSource = dataSource(),
            target = RelayTarget(E2E_OWNER, E2E_CHANNEL),
            environment = RuntimeEnvironment.Production,
            policy = deliveryPolicy(),
            channels = channelSettings(telegramBaseUrl),
            clock = Clock.fixed(E2E_NOW, ZoneOffset.UTC),
        )

    private fun deliveryPolicy(): NotificationDeliveryPolicyData {
        val resolution = NOTIFICATION_DELIVERY_POLICY.resolve(LocalDate.ofInstant(E2E_NOW, ZoneOffset.UTC))
        check(resolution is Resolution.Resolved) { "NOTIFICATION_DELIVERY_POLICY 가 해소되지 않았다: $resolution" }
        return resolution.value
    }

    private fun assembly(): PipelineAssembly =
        MlFakeServer.start(successfulMlScript()).let { server ->
            servers += server
            PipelineAssembly(
                dataSource = dataSource(),
                mlChannel = server.channel,
                at = E2E_NOW,
                correlationPrefix = "telegram-delivery",
                mlPolicy = e2eMlCallPolicy(),
            )
        }

    private companion object {
        const val DELIVERY_NOTICE = "E2E-SEND-0001"
        const val DEMAND_AGENCY = "한국테스트공사"

        /** `마감 yyyy-MM-dd HH:mm KST` — 값이 아니라 **표기 규칙**을 못박는다. */
        val DEADLINE_LINE = Regex("""^마감 \d{4}-\d{2}-\d{2} \d{2}:\d{2} KST$""", RegexOption.MULTILINE)
    }
}
