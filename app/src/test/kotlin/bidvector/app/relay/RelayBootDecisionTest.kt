package bidvector.app.relay

import bidvector.sharedkernel.Resolution
import bidvector.workflow.notification.DeliveryMode
import bidvector.workflow.notification.NOTIFICATION_DELIVERY_POLICY
import bidvector.workflow.notification.NotificationDeliveryPolicyData
import bidvector.workflow.notification.RuntimeEnvironment
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

private const val MASKED_SUFFIX_LENGTH = 4

/**
 * cr R-2 — 기동 거부 판정을 **표와 설정 유무를 바꿔 넣어** 직접 친다. 배선을 세우지 않고 bean 을
 * 주입하지 않는다: 입력이 셋(환경, 정책표, 설정 존재 여부)뿐인 순수 함수이기 때문이다.
 *
 * **M9/STR12 로 판정 내용이 바뀌었다**(D-7). 실 sender 가 들어왔으므로 「Live 면 거부」가 아니라
 * **「Live 인데 채널 설정이 비면 거부」**다. cr G-1 이 고친 **성질**(환경 이름이 아니라 정책표가 그
 * 환경에 붙인 모드를 본다)은 그대로이고, 아래 변이 셋이 그것을 계속 잠근다.
 *
 * 판정 함수는 설정의 **값**을 받지 않는다 — `Boolean` 셋만 받는다. 그래서 거부 메시지가 자격·chat
 * id 원문을 담을 경로가 타입에 없다(설계 검토 (1)).
 *
 * 기대값은 전부 리터럴이다. 표에서 끌어오면 표가 바뀌는 날 기대값도 함께 바뀌어 아무것도 고정하지
 * 않는다.
 */
class RelayBootDecisionTest {
    /**
     * **이 slice 의 핵심 변이**(cr G-1 이 고친 성질): 표가 `Staging -> Live` 를 갖는 날이면
     * `Staging` 에도 설정이 요구된다. 이름 술어(`environment != Production`)는 여기서 허용을 낸다.
     */
    @Test
    fun `표가 Staging 에 Live 를 붙이면 Staging 도 설정을 요구한다`() {
        val mutated = tableWith(RuntimeEnvironment.Staging to DeliveryMode.Live)

        relayBootDecision(RuntimeEnvironment.Staging, mutated, nothingConfigured()) shouldBe
            RelayBootDecision.RefusedMissingChannelConfig(ChannelConfigField.entries.toSet())
        relayBootDecision(RuntimeEnvironment.Staging, mutated, fullyConfigured()) shouldBe
            RelayBootDecision.Allowed
    }

    /**
     * 같은 변이표의 **다른 환경**은 설정 없이도 허용이다 — 판정이 「표 전체에 Live 가 있는가」가
     * 아니라 「이 환경의 모드가 Live 인가」임을 가른다.
     */
    @Test
    fun `표에 Live 가 있어도 그 환경이 아니면 설정 없이 허용이다`() {
        val mutated = tableWith(RuntimeEnvironment.Staging to DeliveryMode.Live)

        relayBootDecision(RuntimeEnvironment.Development, mutated, nothingConfigured()) shouldBe
            RelayBootDecision.Allowed
    }

    /** 이름 축의 **음성 대조** — 표가 `Production` 에서 Live 를 떼면 설정이 비어도 허용이다. */
    @Test
    fun `표가 Production 에서 Live 를 떼면 설정이 비어도 허용이다`() {
        val mutated = tableWith(RuntimeEnvironment.Production to DeliveryMode.DryRun)

        relayBootDecision(RuntimeEnvironment.Production, mutated, nothingConfigured()) shouldBe
            RelayBootDecision.Allowed
    }

    /**
     * **승인된 실제 표**에서의 세 칸 — 오늘 `Production` 만 설정을 요구하고 `Staging` 은 설정 없이
     * 뜬다(D-7 「Live 가 아니면 지금처럼 설정 없이 뜬다」).
     */
    @Test
    fun `승인된 표에서는 Production 만 설정을 요구한다`() {
        val approved = approvedPolicy()

        relayBootDecision(RuntimeEnvironment.Production, approved, fullyConfigured()) shouldBe
            RelayBootDecision.Allowed
        relayBootDecision(RuntimeEnvironment.Production, approved, nothingConfigured()) shouldBe
            RelayBootDecision.RefusedMissingChannelConfig(ChannelConfigField.entries.toSet())
        relayBootDecision(RuntimeEnvironment.Staging, approved, nothingConfigured()) shouldBe
            RelayBootDecision.Allowed
    }

    /**
     * **빠진 칸이 무엇인지 거부가 나른다** — 운영자가 받는 기동 실패가 「무언가 비었다」가 아니라
     * 「이 칸이 비었다」여야 한다. 칸 하나만 비는 조합 셋을 각각 잰다(칸을 통째로 접으면 그 셋이
     * 같은 값을 낸다).
     */
    @Test
    fun `거부는 빠진 칸만 나른다`() {
        val approved = approvedPolicy()

        refusalFor(approved, fullyConfigured().copy(botCredential = false)) shouldBe
            setOf(ChannelConfigField.BotCredential)
        refusalFor(approved, fullyConfigured().copy(chatId = false)) shouldBe
            setOf(ChannelConfigField.ChatId)
        refusalFor(approved, fullyConfigured().copy(siteBaseUrl = false)) shouldBe
            setOf(ChannelConfigField.SiteBaseUrl)
        refusalFor(approved, fullyConfigured().copy(botCredential = false, chatId = false)) shouldBe
            setOf(ChannelConfigField.BotCredential, ChannelConfigField.ChatId)
    }

    /** 칸 어휘가 셋이다 — 새 설정 칸이 생기면 이 수가 움직여 그 칸의 기동 검사를 함께 보게 된다. */
    @Test
    fun `설정 칸 어휘는 셋이다`() {
        ChannelConfigField.entries.map { it.name } shouldBe listOf("BotCredential", "ChatId", "SiteBaseUrl")
    }

    private fun refusalFor(
        policy: NotificationDeliveryPolicyData,
        presence: ChannelConfigPresence,
    ): Set<ChannelConfigField> {
        val decision = relayBootDecision(RuntimeEnvironment.Production, policy, presence)
        return (decision as RelayBootDecision.RefusedMissingChannelConfig).missing
    }
}

private fun fullyConfigured() = ChannelConfigPresence(botCredential = true, chatId = true, siteBaseUrl = true)

private fun nothingConfigured() = ChannelConfigPresence(botCredential = false, chatId = false, siteBaseUrl = false)

/**
 * 전 환경을 `DryRun` 으로 덮은 표에 [overrides] 만 바꿔치운다 — [NotificationDeliveryPolicyData]
 * 가 전 환경을 요구하므로(`init`) 부분표를 만들 수 없다.
 */
private fun tableWith(vararg overrides: Pair<RuntimeEnvironment, DeliveryMode>): NotificationDeliveryPolicyData =
    NotificationDeliveryPolicyData(
        environmentModes = RuntimeEnvironment.entries.associateWith { DeliveryMode.DryRun } + overrides,
        maskedSuffixLength = MASKED_SUFFIX_LENGTH,
    )

private fun approvedPolicy(): NotificationDeliveryPolicyData {
    val resolution = NOTIFICATION_DELIVERY_POLICY.resolve(LocalDate.of(2026, 10, 6))
    check(resolution is Resolution.Resolved) { "NOTIFICATION_DELIVERY_POLICY 가 해소되지 않았다: $resolution" }
    return resolution.value
}
