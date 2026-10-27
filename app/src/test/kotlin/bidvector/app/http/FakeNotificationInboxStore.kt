package bidvector.app.http

import bidvector.procurement.NoticeId
import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.MarkReadOutcome
import bidvector.workflow.notificationinbox.NotificationInboxPage
import bidvector.workflow.notificationinbox.NotificationInboxStore
import bidvector.workflow.notificationinbox.NotificationItemKey
import bidvector.workflow.notificationinbox.NotificationPageRequest
import java.time.Instant

/**
 * 저장소 없는 알림함(M7/7C③) — **빈 목록과 0 건 처리**를 낸다. 꽂는 자리는
 * [HttpTestApplication] 의 `@Bean` 하나다.
 *
 * **`@Configuration` 으로 두지 않는다**(실측으로 걷어낸 길). 이 패키지에 둘째 `@Configuration`
 * 을 두면 출하 조립을 띄우는 E2E 의 스캔에도 걸려 같은 이름의 빈이 두 번 정의되고
 * `BeanDefinitionOverrideException` 으로 **E2E 여럿이 기동 실패**한다. 그래서 이 파일은 fake
 * **타입만** 들고, 빈 등록은 조립 자신이 한다.
 *
 * **r1 검토(verifier F1)가 이 자리의 앞 판 논증을 반증했다.** 앞 판은 「항목을 지어내지 않는
 * 것이 정직하다 — 여기서 재는 것은 표면(상태 코드·헤더·**키 집합**·인증)이다」라고 적었는데,
 * 목록이 **항상 비어 있으면** `NotificationItemResponse.from` 이 test 에서 한 번도 돌지 않아
 * 그 「키 집합」이 **측정되지 않는다**. 실제로 응답 DTO 에 getter 전용 속성을 더하는 변이가
 * 어떤 test 도 붉히지 못했다(Jackson 은 그 키를 내보낸다). 그래서 이 fake 는 이제 호출자가
 * **한 페이지를 꽂을 수 있다** — 지어낸 값이 거동의 근거가 되는 것이 아니라, 직렬화가 실제로
 * 돌아 **문서와 wire 의 키 등식**을 잴 수 있게 하는 것이 목적이다(질의 거동의 근거는 여전히
 * 어댑터의 실 DB test 다).
 */
class FakeNotificationInboxStore : NotificationInboxStore {
    /** 기본은 빈 페이지다 — 꽂지 않은 test 의 거동은 앞 판과 같다. */
    @Volatile
    var page: NotificationInboxPage = NotificationInboxPage(items = emptyList(), totalCount = 0L, unreadCount = 0L)

    @Volatile
    var outcome: MarkReadOutcome = MarkReadOutcome(markedCount = 0, unreadCount = 0L)

    /** 마지막 목록 요청 — 판독이 서버 상한·기본값을 적용했는지 표면에서 확인하는 자리. */
    @Volatile
    var lastRequest: NotificationPageRequest? = null

    @Volatile
    var lastItem: NotificationItemKey? = null

    @Volatile
    var lastNotice: NoticeId? = null

    override fun list(
        account: AccountId,
        request: NotificationPageRequest,
    ): NotificationInboxPage {
        lastRequest = request
        return page
    }

    override fun markRead(
        account: AccountId,
        item: NotificationItemKey,
        at: Instant,
    ): MarkReadOutcome {
        lastItem = item
        return outcome
    }

    override fun markNoticeRead(
        account: AccountId,
        notice: NoticeId,
        at: Instant,
    ): MarkReadOutcome {
        lastNotice = notice
        return outcome
    }
}
