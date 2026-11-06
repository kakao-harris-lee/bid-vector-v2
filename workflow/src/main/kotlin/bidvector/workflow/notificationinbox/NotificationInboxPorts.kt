package bidvector.workflow.notificationinbox

import bidvector.procurement.NoticeId
import bidvector.workflow.access.AccountId
import java.time.Instant

/**
 * **쓰기 포트**(M7/7C③ 산출물 2) — 원천 slice 들이 **이것만** 부른다(7C② 투찰가 요청 결과 ·
 * 7C⑤ 개찰·낙찰 확정 · 7B 마감·서류 기한 · STR-08 원천 slice 새 후보).
 *
 * **HTTP 표면에 없다**(계약 (1) 구성): app production 의 어느 클래스도 이 포트의 메서드를
 * 부르지 못하고(포트 호출 쌍 허용 목록에 이 포트의 쌍이 **하나도 없다**), controller 는
 * 이 타입의 빈을 받을 수 없다(`app.http.denied-types` + 주입 게이트). 그래서 「HTTP 로 알림을
 * 위조한다」가 게이트를 바꾸지 않고는 성립하지 않는다.
 *
 * **중복 억제는 이 포트의 계약이다**(NOTI-10): 같은 `(계정, 종류, 공고)` 재기록은 **그 항목을
 * 갱신**하고 둘째 행을 만들지 않는다. 그 보증은 술어가 아니라 저장 층의 PRIMARY KEY 와 단일
 * 문장 upsert 가 진다(경합에서도 행이 늘지 않음을 실 DB 동시성 test 가 잰다).
 */
fun interface NotificationInboxPort {
    fun record(record: NotificationInboxRecord): NotificationInboxOutcome
}

/**
 * **체계의 알림이 누구의 알림함으로 가는가**(M9/ops-code · NOTI-01) — 계정 전수를 돌려준다.
 *
 * **주체를 인자로 받지 않는 둘째 질의다**(`NotificationSweepCandidatePort` 와 같은 자리). 그 포트와
 * 같은 사유로 [NotificationInboxStore] 를 넓히지 않고 **별 포트**로 둔다 — 그쪽의 「전 메서드의 첫
 * 인자가 주체」 규율은 요청 경로의 소유 경계이고, 그 자리에 계정 없는 메서드가 생기면 규율이
 * 선언으로 내려앉는다.
 *
 * **왜 이 포트가 필요한가**: 훑기는 계정을 건 행에서 실어 오지만 후보 평가에는 그런 행이 없다 —
 * 평가의 모집단은 공고이고 계정 축이 아예 없다. 그래서 「누구에게」를 어디선가 가져와야 하고,
 * 그 자리를 **설정값으로 두지 않았다**: 없는 계정 id 를 설정이 주면 저장 층의 외래키가 거부하고
 * 그 거부가 [NotificationInboxOutcome.Failed] 로 조용히 접힌다. 표를 읽으면 그 상태가 애초에
 * 표현되지 않는다.
 *
 * 반환이 **빈 청중일 수 있다**(계정 부트스트랩 전). 그것은 실패가 아니라 「받을 사람이 아직
 * 없다」는 사실이고, 소비자는 기록 0 건으로 끝낸다.
 *
 * **실패를 예외로 내보내지 않는다**(cr r1 MEDIUM · v M-2). 이 질의는 평가 커밋 run 안에서 불리고
 * 그 run 의 본론은 판정과 outbox 등록이다 — 청중을 못 읽었다는 사실이 예외로 올라가면 **채널
 * 요청 전에 run 이 멈춰** 그 판정의 흔적까지 함께 사라진다. 알림함 쓰기 포트가
 * [NotificationInboxOutcome.Failed] 로 접는 것과 같은 갈래이고, 접는 자리도 같다(어댑터).
 */
fun interface NotificationAudiencePort {
    fun accounts(): NotificationAudience
}

/**
 * 청중 질의의 답 — **「받을 사람이 없다」와 「누가 받는지 못 읽었다」를 가른다**(cr r1 MEDIUM).
 *
 * 둘을 빈 목록 하나로 접으면 장애가 「알림함이 조용히 0 건」으로 보이고 아무도 모른다. 두 값이
 * 다르므로 소비자는 소진 `when` 으로 받고, 조립이 [Unavailable] 에만 표지를 남긴다.
 */
sealed interface NotificationAudience {
    /** 받을 계정들 — **빈 목록일 수 있다**(부트스트랩 전). 그것은 사실이고 실패가 아니다. */
    data class Accounts(
        val accounts: List<AccountId>,
    ) : NotificationAudience

    /**
     * 저장 층이 답하지 못했다 — 기록 0 건이지만 그 0 은 **사실의 0 이 아니다**.
     *
     * [exceptionType] 은 **예외 클래스의 단순 이름뿐**이다(cr r2 MEDIUM). 메시지·SQLState 는 계속
     * 버린다 — 저장소 예외 메시지에는 행 값이 실릴 수 있다. 클래스 이름만 싣는 수법은 전송 축의
     * `TransportFailed(exceptionType)` 가 같은 사유로 이미 쓰는 것이고, 그 한 토큰이 「연결 고갈」과
     * 「권한 회수」와 「표 부재」를 가른다(앞 판은 셋이 같은 한 줄이었다).
     */
    data class Unavailable(
        val exceptionType: String,
    ) : NotificationAudience
}

/**
 * **읽기·읽음 처리 포트**(산출물 2) — 운영자 표면만 쓴다. 쓰기 포트와 **갈라 둔다**: 읽음
 * 전이는 운영자의 행위이고 알림 기록은 체계의 행위라, 한 포트에 두면 HTTP 층에 기록 능력이
 * 딸려 온다.
 *
 * **전 메서드의 첫 인자가 [AccountId]** 다 — 「주체를 빠뜨린 메서드」가 시그니처상 존재하지
 * 않는다. 구현의 모든 질의·갱신 술어에 `account_id` 가 들어가고, 남의 항목에 대한 읽음 처리는
 * 영향 행 0 으로 떨어진다(예외가 아니라 계수가 답한다).
 */
interface NotificationInboxStore {
    fun list(
        account: AccountId,
        request: NotificationPageRequest,
    ): NotificationInboxPage

    /** 항목 하나를 읽음으로. 이미 읽었으면 아무것도 바꾸지 않는다(처음 읽은 시각이 정본). */
    fun markRead(
        account: AccountId,
        item: NotificationItemKey,
        at: Instant,
    ): MarkReadOutcome

    /** 한 공고의 묶음 전체를 읽음으로. 안읽음 항목만 바뀐다. */
    fun markNoticeRead(
        account: AccountId,
        notice: NoticeId,
        at: Instant,
    ): MarkReadOutcome
}

/** 상한·기본값 — 매직 넘버를 호출 자리에 두지 않는다(7C① `NoticeSearchLimits` 와 같은 관례). */
object NotificationInboxLimits {
    const val MAX_PAGE_SIZE: Int = 100
    const val DEFAULT_PAGE_SIZE: Int = 20
}

/** 검증을 지난 페이지 요청 — 생성 경로는 [parseNotificationPageParameters] 하나다. */
data class NotificationPageRequest(
    val page: Int,
    val size: Int,
    val unreadOnly: Boolean,
) {
    init {
        require(page >= 0) { "page 는 음수일 수 없다: $page" }
        require(size in 1..NotificationInboxLimits.MAX_PAGE_SIZE) { "size 가 상한을 넘었다: $size" }
    }

    val offset: Long get() = page.toLong() * size.toLong()
}

/**
 * 운영자가 보낸 **원문 그대로의** 질의 값 — 전부 문자열이라 「형식이 틀렸다」가 프레임워크의
 * 조용한 강제 변환이 아니라 [NotificationPageResult.Rejected] 로 관측된다(7C① 과 같은 규율).
 */
data class NotificationPageParameters(
    val page: String? = null,
    val size: String? = null,
    val unreadOnly: String? = null,
)

/** 파싱 결과 — 거부를 삼키지 않도록 소진 `when` 으로 소비한다(예외가 아니다). */
sealed interface NotificationPageResult {
    data class Accepted(
        val request: NotificationPageRequest,
    ) : NotificationPageResult

    data class Rejected(
        val reason: NotificationPageRejection,
    ) : NotificationPageResult
}

/**
 * 거부 사유 — **응답 본문에는 실리지 않는다**(400 `INVALID_REQUEST` 하나로 합친다, 7C① ·
 * 편집 경로와 같은 관례). 진단과 test 단언의 어휘다.
 */
enum class NotificationPageRejection {
    PAGE_NOT_INTEGER,
    PAGE_NEGATIVE,
    SIZE_NOT_INTEGER,
    SIZE_OUT_OF_RANGE,
    UNREAD_ONLY_NOT_BOOLEAN,
}

/**
 * 한 페이지. [totalCount] 는 **페이지 길이와 다른 값**이고 [unreadCount] 는 `unreadOnly`
 * 와 무관하게 **그 계정의 안읽음 전수**다(화면의 배지가 읽는 값이라 필터에 흔들리면 안 된다).
 */
data class NotificationInboxPage(
    val items: List<NotificationInboxItem>,
    val totalCount: Long,
    val unreadCount: Long,
)

/**
 * 항목 하나. 저장된 사실([fact]·시각들)과 **읽을 때 합쳐 온** 공고 표시 칸([display])이 한
 * 값에 있다 — 후자는 이 표에 저장되지 않는다(D-7C3-1 ⑦).
 *
 * [display] 가 `null` 인 것은 **그 키의 공고 행이 없다**는 뜻이다(보존 기한 파기 또는 미수집 —
 * 저장소에 묘비가 없어 둘을 가르지 못한다). 「행이 없다」와 「행은 있고 제목 칸이 비었다」를
 * 구조로 가른다: 후자는 `display != null && display.title == null` 이다. 표시 칸을 전부
 * nullable 스칼라로 납작하게 접으면 그 둘이 같은 값이 된다 — HTTP 응답은 평탄해야 해서
 * 실제로 납작해지므로, 그 층에서는 `noticeKnown` boolean 이 이 구분을 들고 간다.
 */
data class NotificationInboxItem(
    val notice: NoticeId,
    val fact: NotificationFact,
    val occurredAt: Instant,
    val readAt: Instant?,
    val group: NoticeGroupCounts,
    val display: NoticeDisplay?,
) {
    /**
     * 신원은 **도출된다** — 종류를 [fact] 와 따로 들면 둘이 갈릴 수 있고, 갈려도 아무도
     * 모른다. 저장된 짝에서 사실을 복원하는 자리가 [notificationFactOf] 하나이므로 이 값은
     * 늘 그 사실과 같은 종류를 가리킨다.
     */
    val key: NotificationItemKey get() = NotificationItemKey(fact.kind, notice)

    val read: Boolean get() = readAt != null
}

/**
 * 「건별 묶음」의 계수 — 같은 공고의 항목이 몇 개이고 그 중 몇 개가 안읽음인가. **응답이
 * 평탄해야 해서**(D-6A1-20 ⓑ) 묶음을 중첩으로 실을 수 없으므로, 항목마다 자기 묶음의 계수를
 * 싣고 클라이언트가 공고 쌍으로 묶는다.
 *
 * [itemCount] 는 **페이지와 무관한 묶음 전체 크기**다 — 묶음이 페이지 경계에 걸쳐도
 * 클라이언트가 「더 있다」를 알 수 있다.
 */
data class NoticeGroupCounts(
    val itemCount: Int,
    val unreadCount: Int,
)

/** 공고 표에서 합쳐 온 표시 칸. [title] 은 그 표에서 이미 nullable 이다. */
data class NoticeDisplay(
    val title: String?,
    val status: String,
    val deadlineAt: Instant?,
)

/**
 * 읽음 처리의 결과. [markedCount] 가 0 인 것은 **이미 읽었거나 그 항목이 없거나 남의
 * 것**이라는 뜻이다 — 그 셋을 응답에서 가르지 않는다(남의 항목의 존재 여부를 알려주지 않는
 * 것이 목적이다. 인증 거부의 사유를 나누지 않는 7D 의 관례와 같은 방향).
 *
 * [unreadCount] 는 처리 뒤 그 계정의 안읽음 전수다 — 화면이 배지를 다시 묻지 않게 한다.
 */
data class MarkReadOutcome(
    val markedCount: Int,
    val unreadCount: Long,
)
