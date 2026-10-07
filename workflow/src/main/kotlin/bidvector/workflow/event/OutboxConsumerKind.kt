package bidvector.workflow.event

/**
 * outbox payload 종류의 닫힌 어휘(D-6F10-13) — **소비자가 자기 종류만 집도록** `claim`·
 * `claimedEntries`·lease 키가 이 값을 받는다.
 *
 * 왜 필요한가: 좁히지 않은 `claim`은 `state = 'PENDING'` 전부를 집는다. outbox 에는
 * `StrategyUpdated`(전략 편집이 실제로 쓴다, `JdbcStrategyEditTransaction`)도 있고 **그
 * 소비자는 아직 없다**. 알림 relay 가 그 행을 집으면 전이표에 `Claimed -> Pending` 간선이
 * 없어 격리밖에 못 한다 — 남의 행을 종단으로 태운다. 종류로 좁히면 그 길이 **질의 자체에서**
 * 닫힌다(relay 가 조심하는 것이 아니라 집히지 않는다).
 *
 * `payload_type` **문자열은 이 값이 아니다** — 저장 형식은 어댑터(`OutboxPayloadCodec`)의
 * 것이고, 이 enum 은 `workflow`의 어휘다. 둘을 잇는 매핑이 어댑터에 있고, 그 매핑이
 * payload 클래스 기준 매핑과 어긋나지 않는지는 어댑터 test 가 등식으로 잰다.
 *
 * `StrategyUpdated`의 소비자 부재는 `OPEN-6F10-STRATEGY-EVENT-CONSUMER`다 — 이 enum 이
 * 그 종류를 **어휘에 들고 있는 것**이 그 OPEN 을 보이게 하는 자리다(열거에서 빼면 「없는
 * 종류」가 되어 소비자 부재가 안 보인다).
 */
enum class OutboxConsumerKind {
    NotificationRequested,
    StrategyUpdated,
}
