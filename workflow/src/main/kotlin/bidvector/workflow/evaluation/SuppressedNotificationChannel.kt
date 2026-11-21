package bidvector.workflow.evaluation

/**
 * **전역 발송 채널을 받지 않는 run 의 채널**(M15/15A, 계약 「Telegram」).
 *
 * 전역 경로(outbox → relay → Telegram)는 **하나**이고 그 수신자는 운영자 한 사람이다. 계정이
 * 여럿이 된 뒤 모든 계정의 run 이 그 채널을 쥐면 가입자의 후보가 운영자 전화기로 가고, 반대로
 * 가입자는 자기 후보를 남의 채널로 보낸다 — 이 slice 가 전략·프로필에서 닫은 누출과 **같은
 * 부류**다. 그래서 채널을 쥐는 run 은 **가장 작은 계정**(운영 계정) 하나이고, 나머지 계정의
 * run 은 이 채널을 받아 알림함에만 남는다.
 *
 * **왜 `null` 채널이 아니라 타입인가.** 채널이 nullable 이면 호출부마다 「없으면 어떻게 하는가」를
 * 다시 정해야 하고, 그 분기가 늘어나는 만큼 「보내지 않았다」와 「보낼 자리를 잊었다」가 같은
 * 모양이 된다. 값을 하나 두면 결과가 [NotificationRequestOutcome.Suppressed] 로 **집계에 실려**
 * 종료 줄에서 보인다 — 억제가 꺼져도 켜져도 계수가 말한다.
 *
 * 기록(알림함)은 억제하지 않는다 — 억제는 **채널 억제이지 기록 억제가 아니다**(NOTI-01). 그
 * 순서를 지키는 것은 이 타입이 아니라 [InboxRecordingNotifications] 이고, 이 채널은 그 안쪽에
 * 꽂혀 「채널 쪽만」 비운다.
 */
object SuppressedNotificationChannel : NotificationRequestPort {
    override fun request(notification: NotificationRequest): NotificationRequestOutcome =
        NotificationRequestOutcome.Suppressed
}
