package bidvector.workflow.bidding

import bidvector.bidding.BidState
import bidvector.bidding.NoticeKey
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.workflow.notificationinbox.NotificationFact

/**
 * 운영자가 기록한 전이를 알림함에 **되비추는** 사상(M7/7C③-b · D-7C3B-7).
 *
 * **자동 통지가 아니다.** 체계가 개찰 결과를 대사해 알려 주는 것(SET-01 낙찰 확정 · SET-02 잠정
 * 1위)은 오늘 성립하지 않는다 — 낙찰자 상호가 canonical 에 없고(`OPEN-SET01-AWARD-NAME-INGEST`)
 * 잠정 1위 축에는 상호 칸 자체가 없다(`ProvisionalRankOne`, D-7C5-3). 그 판정을 흉내 내지 않고
 * **운영자가 실제로 기록한 전이**만 되비춘다. 그래서 NOTI-04 · SET-02 의 capability 판정은 이
 * 배선으로 올라가지 않는다(D-7C3B-7 ③).
 *
 * **명령이 아니라 도착 상태로 사상한다.** 한 사실에 이르는 명령이 둘이고(`RECORD_OUTCOME` ·
 * `CORRECT_OUTCOME`) 운영자가 보는 것은 상태다 — 명령으로 사상하면 같은 사실이 키 둘을 갖거나
 * 정정이 알림을 갱신하지 못한다. 정정으로 낙찰이 패찰로 바뀌면 **같은 키의 detail 이 바뀌고**,
 * 그때 그 항목은 안읽음으로 돌아온다(7C③ upsert 의 `read_at` 규칙).
 *
 * **유찰(`FailedOrRebid`)은 기록하지 않는다**(D-7C3B-7 ⒝). detail 어휘에 유찰이 없어
 * `NOT_AWARDED`(패찰)로 접으면 「내가 졌다」와 「아무도 못 땄다」가 한 값이 된다. 어휘를 늘리는
 * 것은 CHECK 마이그레이션이라 되돌리기 어려운 경로이고 별도 결정이다
 * (`OPEN-7C3B-FAILED-BID-DETAIL`).
 *
 * `when` 이 소진이라 **상태를 더하면 답을 주지 않고는 컴파일되지 않는다** — 새 결과 상태가
 * 조용히 「알림 없음」으로 떨어지지 않는다.
 */
internal fun reflectedNotificationFact(state: BidState): NotificationFact? =
    when (state) {
        BidState.OpenedProvisionalFirst -> NotificationFact.OpeningCompleted(provisionallyFirst = true)
        BidState.OpenedOutOfRank -> NotificationFact.OpeningCompleted(provisionallyFirst = false)
        BidState.Won -> NotificationFact.AwardDecided(awarded = true)
        BidState.Lost -> NotificationFact.AwardDecided(awarded = false)

        // 아래는 **되비출 사실이 아니다.** 유찰만 사유가 다르다(어휘 부재, 위 KDoc) — 나머지는
        // 알림함 어휘에 대응하는 종류가 없거나(초안 · 제출함 · 개찰 대기 · 적격심사) 시간 기반
        // 훑기가 자기 종류로 따로 낸다(마감 지남 계열).
        BidState.FailedOrRebid,
        BidState.Watching,
        is BidState.Deferred,
        BidState.PriceReceived,
        BidState.BidPlanned,
        BidState.BidRecorded,
        is BidState.AwaitingOpening,
        is BidState.UnderQualificationReview,
        is BidState.Skipped,
        BidState.DeadlinePassedUnrecorded,
        -> null
    }

/**
 * 건의 공고 키 → 알림함의 공고 식별자. **정규화 규칙은 `NoticeNumber.of` 하나다**(COL-05).
 *
 * 왜 원문을 그대로 쓰지 않는가: `bid_case.notice_number` 는 담을 때 받은 **원문 그대로**이고
 * (7B 의 `openCase` 가 비어 있지 않은 문자열만 본다) canonical 공고의 번호는 정규형이다. 알림
 * 항목은 눌러서 그 공고의 워크시트로 가야 하므로 canonical 쪽을 키로 쓴다 — 같은 공고를 다른
 * 표기로 담은 건 둘이 알림함에서 두 항목이 되지 않는다는 뜻이기도 하다.
 *
 * **전역(total) 함수다 — `null` 갈래를 두지 않는다.** [NoticeKey] 의 불변식이 빈 번호를 이미
 * 막고(`isNotBlank`), 정규화는 공백뿐이 아닌 문자열을 빈 값으로 만들지 않는다. 그래서 「번호가
 * 서지 않는다」는 이 타입 **위에서는 표현되지 않는 상태**이고, 그것을 여기서 다시 물으면 도달하지
 * 않는 갈래와 그 갈래를 세는 계수가 생긴다(실측으로 걷어낸 자리 — `NoticeKey(" ")` 는 애초에
 * 만들어지지 않는다).
 *
 * 그 불변식 **밖**의 행(직접 SQL 로 들어온 공백 번호)은 `NoticeKey` 를 짓기 전에 걸러야 한다 —
 * 그 방어는 행을 읽는 질의가 진다(`NotificationSweepSql`).
 */
fun NoticeKey.asNotificationNoticeId(): NoticeId = NoticeId(NoticeNumber.of(noticeNumber), noticeRound)
