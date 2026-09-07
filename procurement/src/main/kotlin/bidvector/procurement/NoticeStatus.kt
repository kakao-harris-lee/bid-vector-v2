package bidvector.procurement

import java.time.Instant

/** `data-dictionary.md` §2.2.1 — legacy는 이 여섯 값을 제약 없는 문자열 컬럼으로 갖는다. */
enum class NoticeStatus {
    Open,
    Renoticed,
    Closed,
    Awarded,
    Failed,
    Cancelled,
}

/** 상태 전이를 일으키는 사건 — 최초 진입(`NoticeCollected`)은 [transition]이 아니라 별도 진입점이다. */
sealed interface NoticeEvent {
    data object RenoticeObserved : NoticeEvent

    data object DeadlineReached : NoticeEvent

    data object AwardObserved : NoticeEvent

    data object FailureObserved : NoticeEvent

    data object CancellationObserved : NoticeEvent
}

/**
 * 전이 결과 — **표에 없는 (상태, 이벤트) 쌍은 전이가 아니라 거부이며 관측 가능해야 한다**
 * (§2.2.1). 호출부가 `Rejected`를 조용히 무시하지 않도록 소진 `when`으로 소비하게 한다.
 */
sealed interface TransitionResult {
    data class Moved(
        val to: NoticeStatus,
    ) : TransitionResult

    data class Rejected(
        val from: NoticeStatus,
        val event: NoticeEvent,
    ) : TransitionResult
}

/**
 * 전이표 데이터(§2.2.1) — 이 문서가 새로 쓴 도메인 명세다(legacy에는 전이 선언이 없다).
 * 종단 상태(`Awarded`·`Failed`·`Cancelled`)는 이 표에 나가는 전이가 없다.
 */
private val NOTICE_TRANSITION_TABLE: Map<Pair<NoticeStatus, NoticeEvent>, NoticeStatus> =
    mapOf(
        (NoticeStatus.Open to NoticeEvent.RenoticeObserved) to NoticeStatus.Renoticed,
        (NoticeStatus.Open to NoticeEvent.DeadlineReached) to NoticeStatus.Closed,
        (NoticeStatus.Renoticed to NoticeEvent.DeadlineReached) to NoticeStatus.Closed,
        (NoticeStatus.Closed to NoticeEvent.AwardObserved) to NoticeStatus.Awarded,
        (NoticeStatus.Closed to NoticeEvent.FailureObserved) to NoticeStatus.Failed,
        (NoticeStatus.Open to NoticeEvent.CancellationObserved) to NoticeStatus.Cancelled,
        (NoticeStatus.Renoticed to NoticeEvent.CancellationObserved) to NoticeStatus.Cancelled,
        (NoticeStatus.Closed to NoticeEvent.CancellationObserved) to NoticeStatus.Cancelled,
    )

/**
 * 유일한 전이 함수(⑨) — `Notice`(4B가 조립하는 상태 보유 fact)의 상태 변경은 이 함수를
 * 거쳐야 한다. 표 밖의 (상태, 이벤트) 쌍은 [TransitionResult.Rejected]를 낸다 — 조용히
 * 무시하지 않는다.
 */
fun transition(
    current: NoticeStatus,
    event: NoticeEvent,
): TransitionResult {
    val next = NOTICE_TRANSITION_TABLE[current to event]
    return if (next != null) TransitionResult.Moved(next) else TransitionResult.Rejected(current, event)
}

/**
 * "입찰 가능"은 상태 값 집합이 아니라 파생 술어다(§2.2.1) — 호출부가 상태 리터럴을 직접
 * 고르는 경로를 만들지 않는다(legacy의 단수 `"open"` 리터럴 잠재 버그와 같은 실패 모양).
 * 구간은 시작 포함·끝 제외(반개구간, §1.5) — `now`가 `deadline`과 같으면 이미 마감이다.
 */
fun isBiddable(
    status: NoticeStatus,
    now: Instant,
    deadline: Instant,
): Boolean = (status == NoticeStatus.Open || status == NoticeStatus.Renoticed) && now.isBefore(deadline)
