package bidvector.app.relay

import bidvector.workflow.notification.Channel
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
 * **자격을 stderr 로 내보내는 JVM 속성**(v N-2) — 어휘가 닫혀 있다.
 *
 * JDK `HttpClient` 는 이 속성이 켜지면 요청을 자기 로그로 찍고, 그 줄은 **요청 URL 전체**를 담는다.
 * 이 slice 의 URL 은 경로 조각에 봇 자격을 싣기 때문에 그 한 줄이 자격을 내보낸다 — 「이 패키지는
 * 로깅 API 좌표를 하나도 쥐지 않는다」는 바이트코드 게이트는 **JDK 자신의 로그**를 덮지 못한다.
 *
 * 속성 **이름**은 비밀이 아니므로 거부가 그것을 나르는 것은 D-3 과 어긋나지 않는다. `JAVA_TOOL_OPTIONS`
 * 로 켜도 JVM 시스템 속성이 되므로 같은 한 자리에서 보인다.
 */
enum class HttpClientLogProperty(
    val propertyName: String,
) {
    HttpClientLog("jdk.httpclient.HttpClient.log"),
    InternalDebug("jdk.internal.httpclient.debug"),
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

    /**
     * 설정은 다 찼는데 **relay 가 쓰는 채널에 활성 route 가 없다**(cr M-2). 그대로 뜨면 relay 가
     * 행을 집고 dispatch 가 전부 `Suppressed` 를 내서 **그 run 의 모든 행이 `FAILED` 로 탄다** —
     * 종단 전이는 단방향이라 되돌릴 간선이 없다. 오늘 이 상태가 되는 길은
     * `bidvector.relay.channel` 을 `email` 로 두는 것이다(Email route 는 `enabled=false`, D-2).
     *
     * 채널 **이름만** 나른다 — 어느 설정을 고쳐야 하는지 말하기에 충분하고 비밀이 아니다.
     */
    data class RefusedChannelWithoutActiveRoute(
        val channel: Channel,
    ) : RelayBootDecision

    /**
     * **JDK HttpClient 의 내부 로그가 켜져 있다**(v N-2). 그대로 뜨면 발송마다 요청 URL 이 stderr 로
     * 나가고 그 URL 은 경로에 **봇 자격**을 싣는다. 발송 어댑터가 로거를 쥐지 않는다는 보증은 그 축을
     * 덮지 못한다 — 찍는 주체가 JDK 다.
     *
     * runbook 이 「운영에서 금지」라고 적는 것만으로는 **아무것도 막지 못한다**(안 돌린 게이트와 같다).
     * 그래서 전제를 **기동 판정**에 올렸다. [enabled] 는 켜진 속성의 **이름**이고 그 값이 아니다 —
     * 값에는 로그 수준·대상이 들어오고 그것은 거부를 읽는 데 필요하지 않다.
     */
    data class RefusedHttpClientLogging(
        val enabled: Set<HttpClientLogProperty>,
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
 * 자격을 받아야 한다. 같은 이유로 [httpClientLogging] 도 Live 에서만 거부 사유다 — 비-Live 는
 * 보내지 않으므로 찍힐 자격이 없고, 거기서 거부하면 개발자가 HTTP 를 들여다보는 일을 막는다.
 *
 * 사유 **순서는 첫 위반 승**이고 그 순서에 뜻이 있다: ① 설정 결손 — 없으면 route 를 물을 대상
 * 자체가 없다 ② 활성 route 부재 ③ 로그 속성. ③ 을 뒤에 두는 것은 ①② 상태에서는 **발송이 0 이라
 * 찍힐 것도 없기** 때문이다. 운영자는 고칠 것을 하나씩 받는다.
 */
fun relayBootDecision(
    environment: RuntimeEnvironment,
    policy: NotificationDeliveryPolicyData,
    channels: ChannelConfigPresence,
    deliveryChannel: Channel,
    activeRoute: Boolean,
    httpClientLogging: Set<HttpClientLogProperty>,
): RelayBootDecision {
    if (!relayDeliveryIsLive(environment, policy)) return RelayBootDecision.Allowed
    val missing = channels.missing()
    return when {
        missing.isNotEmpty() -> RelayBootDecision.RefusedMissingChannelConfig(missing)
        !activeRoute -> RelayBootDecision.RefusedChannelWithoutActiveRoute(deliveryChannel)
        httpClientLogging.isNotEmpty() -> RelayBootDecision.RefusedHttpClientLogging(httpClientLogging)
        else -> RelayBootDecision.Allowed
    }
}

/**
 * 그 환경이 **실제로 보내는가** — [relayBootDecision] 이 쓰는 같은 술어이고, 배선도 이것을 쓴다.
 *
 * 왜 밖으로 뺐나(cr L-6): 배선이 설정 값 객체를 **짓는 시점**을 이 답 뒤로 미루기 위해서다. 자격
 * 모양 검사는 값 객체 생성 시점에 있으므로, 비-Live 에서도 설정 객체를 지으면 **모양 위반이 환경을
 * 가리지 않고 기동 실패**가 된다 — D-7 의 문면(「Live 가 아니면 설정 없이 뜬다」)은 **부재**를 말하고
 * 모양 위반은 말하지 않았다. 배선이 이 술어를 먼저 묻고 Live 일 때만 설정을 짓는다.
 *
 * 술어를 복제하지 않는 것이 요점이다 — 배선이 자기 사본을 쓰면 「환경 이름이 아니라 정책표를 본다」는
 * 성질(cr G-1)이 그 사본에서 조용히 갈릴 수 있다.
 */
fun relayDeliveryIsLive(
    environment: RuntimeEnvironment,
    policy: NotificationDeliveryPolicyData,
): Boolean = policy.environmentModes.getValue(environment) == DeliveryMode.Live
