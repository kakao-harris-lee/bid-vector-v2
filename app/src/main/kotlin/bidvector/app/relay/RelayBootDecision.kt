package bidvector.app.relay

import bidvector.workflow.notification.DeliveryMode
import bidvector.workflow.notification.NotificationDeliveryPolicyData
import bidvector.workflow.notification.RuntimeEnvironment

/**
 * relay 를 **띄울 수 있는가**의 답(cr R-2) — 배선이 아니라 **값**이다.
 *
 * 왜 배선에서 뽑아냈나: 이 판정은 `RelayWiring` 의 `require` 안에 인라인되어 있었고, 그
 * 상태로는 **정책표를 바꿔 넣은 입력**으로 직접 칠 수 없었다(설정 바인딩·bean 주입·Spring
 * 기동을 전부 세워야 했다). 그래서 cr G-1 이 고친 술어의 **핵심 성질**(「환경 이름이 아니라
 * 정책표가 그 환경에 붙인 모드를 본다」)이 변이로 측정되지 않았다 — 표를 `Staging -> Live`
 * 로 바꿔도 붉어지는 test 가 없었다. 순수 함수로 뽑으면 그 변이가 인자 하나다.
 */
enum class RelayBootDecision {
    Allowed,

    /**
     * 실 발송 채널이 없는 동안 **발송 가능 모드** 환경으로 뜨려 했다(`OPEN-STR-12`). 이대로
     * 뜨면 relay 가 claim 한 뒤 자리지킴 sender 에서 터지고 그 행은 다음 run 의 고아 격리가
     * 태운다 — 매 run 이 행을 영구히 잃는다. 기동 거부가 claim 을 0 으로 만든다.
     */
    RefusedLiveWithoutSender,
}

/**
 * **환경 이름을 보지 않는다** — [policy] 가 [environment] 에 붙인 모드를 본다. 오늘
 * `Production` 하나가 `Live` 지만 그 표는 날짜에 따라 달라지도록 설계된 값이므로
 * (`EffectiveDatedPolicy`), 이름으로 판정하면 표가 바뀌는 날 이 검사와 relay 자신의 억제
 * 판정이 **같은 사각을 공유한 채** 함께 뚫린다(cr G-1 이 고친 것).
 *
 * `getValue` 를 쓰는 것은 누락을 숨기지 않기 위해서다 — [NotificationDeliveryPolicyData] 가
 * 전 환경을 덮는다고 `init` 에서 보증하므로, 없으면 그것은 표의 결함이고 던지는 것이 맞다.
 */
fun relayBootDecision(
    environment: RuntimeEnvironment,
    policy: NotificationDeliveryPolicyData,
): RelayBootDecision =
    if (policy.environmentModes.getValue(environment) == DeliveryMode.Live) {
        RelayBootDecision.RefusedLiveWithoutSender
    } else {
        RelayBootDecision.Allowed
    }
