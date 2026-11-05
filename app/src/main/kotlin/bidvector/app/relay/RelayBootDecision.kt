package bidvector.app.relay

import bidvector.workflow.notification.DeliveryMode
import bidvector.workflow.notification.NotificationDeliveryPolicyData
import bidvector.workflow.notification.RuntimeEnvironment

/** 발송에 필요한 설정 칸 — 어휘가 닫혀 있어 새 칸이 생기면 기동 검사도 함께 움직인다. */
enum class ChannelConfigField {
    BotCredential,
    ChatId,
    SiteBaseUrl,
}

/**
 * 설정이 **있는가**만 나른다(D-3) — **값을 나르지 않는다.** 그래서 [relayBootDecision] 도,
 * 그 거부를 기동 실패로 옮기는 메시지도 자격·chat id 원문을 담을 경로가 없다.
 */
data class ChannelConfigPresence(
    val botCredential: Boolean,
    val chatId: Boolean,
    val siteBaseUrl: Boolean,
) {
    /** 빠진 칸 — **칸마다 따로** 센다. 하나로 접으면 「무언가 비었다」밖에 말할 수 없다. */
    fun missing(): Set<ChannelConfigField> =
        buildSet {
            if (!botCredential) add(ChannelConfigField.BotCredential)
            if (!chatId) add(ChannelConfigField.ChatId)
            if (!siteBaseUrl) add(ChannelConfigField.SiteBaseUrl)
        }
}

/**
 * relay 를 **띄울 수 있는가**의 답(cr R-2) — 배선이 아니라 **값**이다.
 *
 * 왜 배선에서 뽑아냈나: 이 판정은 `RelayWiring` 의 `require` 안에 인라인되어 있었고, 그
 * 상태로는 **정책표를 바꿔 넣은 입력**으로 직접 칠 수 없었다(설정 바인딩·bean 주입·Spring
 * 기동을 전부 세워야 했다). 그래서 cr G-1 이 고친 술어의 **핵심 성질**(「환경 이름이 아니라
 * 정책표가 그 환경에 붙인 모드를 본다」)이 변이로 측정되지 않았다 — 표를 `Staging -> Live`
 * 로 바꿔도 붉어지는 test 가 없었다. 순수 함수로 뽑으면 그 변이가 인자 하나다.
 */
sealed interface RelayBootDecision {
    data object Allowed : RelayBootDecision

    /**
     * 발송 가능(`Live`) 모드 환경인데 알림 채널 설정이 비었다(M9/STR12 D-7). 이대로 뜨면 relay 가
     * claim 한 뒤 보낼 곳이 없어 그 행들이 어휘 밖 종단으로 타 버린다 — 종단 전이는 단방향이라
     * 되돌릴 간선이 없다. 기동 거부가 claim 을 0 으로 만든다.
     *
     * **앞 판은 `RefusedLiveWithoutSender` 였다** — 실 sender 가 없어 `Live` 자체가 거부 사유였다
     * (`OPEN-STR-12`). 그 자리지킴이 사라져 사유가 「설정 결손」으로 좁아졌다.
     *
     * [missing] 은 **칸 이름**이다(값이 아니다) — 운영자가 받는 실패 메시지가 무엇을 채워야
     * 하는지 말할 수 있어야 하고, 그러면서 원문은 담지 않아야 한다.
     */
    data class RefusedMissingChannelConfig(
        val missing: Set<ChannelConfigField>,
    ) : RelayBootDecision
}

/**
 * **환경 이름을 보지 않는다** — [policy] 가 [environment] 에 붙인 모드를 본다. 오늘
 * `Production` 하나가 `Live` 지만 그 표는 날짜에 따라 달라지도록 설계된 값이므로
 * (`EffectiveDatedPolicy`), 이름으로 판정하면 표가 바뀌는 날 이 검사와 relay 자신의 억제
 * 판정이 **같은 사각을 공유한 채** 함께 뚫린다(cr G-1 이 고친 것).
 *
 * `getValue` 를 쓰는 것은 누락을 숨기지 않기 위해서다 — [NotificationDeliveryPolicyData] 가
 * 전 환경을 덮는다고 `init` 에서 보증하므로, 없으면 그것은 표의 결함이고 던지는 것이 맞다.
 *
 * **Live 가 아니면 설정을 보지 않는다**(D-7) — 그 환경의 relay 는 claim 조차 하지 않으므로
 * (환경 억제) 발송 설정이 없어도 거짓된 상태가 아니다. 요구하면 스테이징 러너가 쓰지도 않을
 * 자격을 받아야 한다.
 */
fun relayBootDecision(
    environment: RuntimeEnvironment,
    policy: NotificationDeliveryPolicyData,
    channels: ChannelConfigPresence,
): RelayBootDecision {
    if (policy.environmentModes.getValue(environment) != DeliveryMode.Live) return RelayBootDecision.Allowed
    val missing = channels.missing()
    return if (missing.isEmpty()) {
        RelayBootDecision.Allowed
    } else {
        RelayBootDecision.RefusedMissingChannelConfig(missing)
    }
}
