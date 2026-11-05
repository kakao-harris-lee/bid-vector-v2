package bidvector.workflow.evaluation

import bidvector.workflow.notificationinbox.NotificationAudiencePort
import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.notificationinbox.NotificationInboxRecord
import bidvector.workflow.strategy.Clock

/**
 * **가치 게이트를 통과한 후보를 운영자 알림함에도 남긴다**(M9/ops-code · NOTI-01) — 7C③ 이 세운
 * 표와 어휘에 `NEW_CANDIDATE` 를 **낳는 자리**가 없던 것을 닫는다(생산자 0 이었다).
 *
 * **왜 `NotificationRequestPort` 의 감싸기인가**: 가치 게이트 통과는 평가 커널 안에서 단 한 자리로
 * 관측된다 — `Verdict.BidNow` 일 때만 이 포트가 불린다(그 아래 판정은 포트에 닿지 않는다). 그래서
 * 「게이트를 통과했는가」를 다시 판정하는 둘째 술어를 만들지 않고 **그 한 자리를 쓴다**. 판정을
 * 복제하면 두 술어가 갈릴 자리가 생기고, 갈려도 아무도 모른다.
 *
 * **기록이 채널보다 먼저다**(NOTI-01 「억제는 채널 억제이지 기록 억제가 아니다」). 채널 요청이
 * 실패해도 기록은 이미 났고, 반대 순서면 채널 실패가 기록까지 함께 지운다. 그래서 [channel] 의
 * 결말은 이 기록에 **아무 영향도 주지 않는다** — 그 무관함이 이 클래스의 계약이다.
 *
 * **기록 실패를 값으로 되돌리지 않는 이유**: 이 자리는 `NotificationRequestOutcome` 을 돌려주는
 * 포트이고 그 어휘는 **채널 요청**의 결말이다. 알림함 기록 실패를 거기 섞으면 러너의 종료 코드가
 * 두 축을 한 값으로 읽는다. 실패의 표지는 조립이 진다 — 쓰기 포트 빈이 `Failed` 를 로그 한 줄로
 * 적는다(D-7C3B-9, 7C③-b 가 세운 자리). **알려진 제한**: 그래서 평가 run 의 종료 코드는 알림함
 * 기록 실패를 세지 않는다.
 *
 * 멱등하다 — 같은 `(계정, 종류, 공고)` 재기록은 그 항목을 갱신하고 둘째 행을 만들지 않는다
 * (NOTI-10 의 PK). 그래서 평가를 두 번 돌려도 알림함 행이 늘지 않는다.
 */
class InboxRecordingNotifications(
    private val channel: NotificationRequestPort,
    private val inbox: NotificationInboxPort,
    private val audience: NotificationAudiencePort,
    private val clock: Clock,
) : NotificationRequestPort {
    override fun request(notification: NotificationRequest): NotificationRequestOutcome {
        // 시각을 **한 번** 읽는다 — 계정이 여럿이면 각자 다른 시각을 받게 되고, 그 차이는 같은
        // 사실의 시각이 계정마다 다르다는 뜻이 되어 아무것도 설명하지 못한다.
        val occurredAt = clock.now()
        audience.accounts().forEach { account ->
            inbox.record(
                NotificationInboxRecord(account, notification.noticeId, NotificationFact.NewCandidate, occurredAt),
            )
        }
        return channel.request(notification)
    }
}
