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
 * cr R-2 — 기동 거부 판정을 **표를 바꿔 넣어** 직접 친다. 배선을 세우지 않고 bean 을 주입하지
 * 않는다: 입력이 둘(환경, 정책표)뿐인 순수 함수이기 때문이다.
 *
 * 기대값을 표에서 끌어오지 않는다 — 전부 리터럴이다. 표에서 끌어오면 표가 바뀌는 날 기대값도
 * 함께 바뀌어 test 가 **아무것도 고정하지 않는다**(「더하기만 한 변이는 초록」의 사촌).
 */
class RelayBootDecisionTest {
    /**
     * **이 slice 의 핵심 변이**(cr G-1 이 고친 성질): 표가 `Staging -> Live` 를 갖는 날이면
     * `Staging` 기동도 거부된다. 앞 판의 술어(`environment != Production`)는 여기서 `Allowed`
     * 를 냈고, 그 뒤 relay 가 claim 한 행이 자리지킴 sender 에서 터져 영구 격리됐다.
     */
    @Test
    fun `표가 Staging 에 Live 를 붙이면 Staging 기동도 거부된다`() {
        val mutated = tableWith(RuntimeEnvironment.Staging to DeliveryMode.Live)

        relayBootDecision(RuntimeEnvironment.Staging, mutated) shouldBe RelayBootDecision.RefusedLiveWithoutSender
    }

    /**
     * 같은 변이표의 **다른 환경**은 거부되지 않는다 — 판정이 「표 전체에 Live 가 있는가」가
     * 아니라 「이 환경의 모드가 Live 인가」임을 가른다. 이 칸이 없으면 「표에 Live 가 하나라도
     * 있으면 거부」하는 구현도 위 test 에서 초록이다.
     */
    @Test
    fun `표에 Live 가 있어도 그 환경이 아니면 허용이다`() {
        val mutated = tableWith(RuntimeEnvironment.Staging to DeliveryMode.Live)

        relayBootDecision(RuntimeEnvironment.Development, mutated) shouldBe RelayBootDecision.Allowed
    }

    /** 이름 축의 **음성 대조** — 표가 `Production` 에서 Live 를 떼면 `Production` 도 허용된다. */
    @Test
    fun `표가 Production 에서 Live 를 떼면 Production 도 허용된다`() {
        val mutated = tableWith(RuntimeEnvironment.Production to DeliveryMode.DryRun)

        relayBootDecision(RuntimeEnvironment.Production, mutated) shouldBe RelayBootDecision.Allowed
    }

    /**
     * **승인된 실제 표**에서의 두 칸 — 오늘 `Production` 이 거부되고 `Staging` 이 허용된다.
     * 위 변이 셋이 「표를 본다」를 잠그고, 이 둘이 「오늘의 답」을 잠근다.
     */
    @Test
    fun `승인된 표에서는 Production 만 거부된다`() {
        val approved = approvedPolicy()

        relayBootDecision(RuntimeEnvironment.Production, approved) shouldBe RelayBootDecision.RefusedLiveWithoutSender
        relayBootDecision(RuntimeEnvironment.Staging, approved) shouldBe RelayBootDecision.Allowed
    }
}

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
