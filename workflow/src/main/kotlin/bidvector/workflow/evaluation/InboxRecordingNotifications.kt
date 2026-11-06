package bidvector.workflow.evaluation

import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.NotificationAudience
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
    audience: NotificationAudiencePort,
    private val clock: Clock,
) : NotificationRequestPort {
    /**
     * **청중은 run 당 한 번 읽는다**(cr r1 low). 앞 판은 후보마다 같은 질의를 냈다 — 계정 전수는
     * 후보에 따라 달라지지 않으므로 그 반복에 새 정보가 없고, 「One query, not N」이 이 저장소의
     * 선례다(OPS-08 (a)).
     *
     * 이 인스턴스의 수명이 **한 run** 이라서 성립한다: 생산자는 커밋 run 조립 안에서 만들어지고 그
     * 조립은 일회 러너(`bidvector.evaluation.mode=once`)의 것이다. 상시 빈으로 옮기는 날에는 이
     * 지연 계산도 함께 옮겨야 한다(새 계정이 영영 안 보인다).
     *
     * 첫 알림에서 읽는다 — `BidNow` 가 하나도 없는 실행은 이 질의를 **아예 내지 않는다**.
     */
    private val resolvedAudience: NotificationAudience by lazy { audience.accounts() }

    override fun request(notification: NotificationRequest): NotificationRequestOutcome {
        recordFor(notification)
        return channel.request(notification)
    }

    private fun recordFor(notification: NotificationRequest) =
        when (val resolved = resolvedAudience) {
            is NotificationAudience.Accounts -> recordFor(notification, resolved.accounts)

            // 청중을 못 읽었다 — **기록 0 건으로 끝내고 채널 요청은 그대로 간다**(cr r1 MEDIUM ·
            // v M-2). 앞 판은 이 자리에서 예외가 올라가 평가 run 이 멈췄고, 그 판정의 outbox
            // 흔적까지 함께 사라졌다. 표지는 조립이 진다(청중 빈의 로그 한 줄) — workflow 층은
            // 로거를 들지 않고, 기록 실패의 표지가 이미 그 자리에 있다(D-7C3B-9).
            NotificationAudience.Unavailable -> Unit
        }

    private fun recordFor(
        notification: NotificationRequest,
        accounts: List<AccountId>,
    ) {
        // 시각을 **한 번** 읽는다 — 계정이 여럿이면 각자 다른 시각을 받게 되고, 그 차이는 같은
        // 사실의 시각이 계정마다 다르다는 뜻이 되어 아무것도 설명하지 못한다.
        val occurredAt = clock.now()
        accounts.forEach { account ->
            // `NotificationInboxRecord` 를 **만드는 단 한 자리**다 — paper-bid(V25)가 그 타입에
            // 투찰 종류 필수 인자를 더하는 중이라 병합 때 한 줄로 그 값을 싣는다.
            inbox.record(
                NotificationInboxRecord(account, notification.noticeId, NotificationFact.NewCandidate, occurredAt),
            )
        }
    }
}
