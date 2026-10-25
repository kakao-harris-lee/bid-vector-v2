package bidvector.bidding

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.FloorRate
import java.time.Instant

/**
 * 초안이 뜬 그 순간의 공고 — **사본이고 불변이다**(§6.1 「나중에 공고가 정정돼도 초안의
 * 스냅샷은 바뀌지 않는다」).
 *
 * 불변을 지키는 것은 문장이 아니라 **쓰기 경로의 부재**다: `bid_draft` 는 append-only 이고
 * (`reject_mutation()` 트리거 + UPDATE 권한 없음), 초안 수정은 같은 행을 고치는 것이 아니라
 * `draft_seq + 1` 의 **새 행**이다. 그래서 「정정 뒤에 옛 초안이 달라져 있다」가 DB 층에서
 * 불가능하다.
 *
 * [noticeRevisionNumber] 를 함께 싣는 이유: 그 값이 없으면 「어느 판의 공고를 보고 정한
 * 금액인가」를 나중에 되짚을 수 없고, 그러면 「정정됐다」는 사실 자체가 보이지 않는다 —
 * 스냅샷을 얼린 이유의 절반이 사라진다. 마감 · 하한율 · 기초금액은 이 판의 값이다.
 *
 * **`Provenance.Published.noticeRevision`(공고 **차수**, `NoticeRound`)과 다른 축이다.**
 * 이것은 `notice.revision` 카운터(정정될 때마다 트리거가 올린다)다. 차수는 이미 건의
 * [NoticeKey] 가 들고 있어 여기 다시 실으면 같은 사실이 두 자리에 있게 된다.
 */
data class NoticeSnapshot(
    val noticeRevisionNumber: Long,
    val takenAt: Instant,
    val deadlineAt: Instant?,
    val floorRate: FloorRate?,
    val baseAmount: BaseAmount?,
) {
    init {
        require(noticeRevisionNumber >= 1L) { "공고 판 번호는 1 이상이다: $noticeRevisionNumber" }
    }
}

/**
 * 투찰 예정 초안(§6.1 「투찰 예정(초안: 고른 안·시각·공고 스냅샷)」) — 고른 안과 금액,
 * 제시 시각, 그리고 그때의 공고 스냅샷.
 *
 * [presentedAt] 은 **제시 시각**이고 기록의 확인 시각([BidRecordEntry.confirmedAt])과 다른
 * 축이다 — §6.1 이 *"확인 시각과 초안 시각이 따로 남는다"* 라 적는다. 둘을 한 열로 접으면
 * 「얼마나 망설였는가」가 사라진다.
 */
data class BidDraft(
    val amount: SubmittedBidAmount,
    val presentedAt: Instant,
    val snapshot: NoticeSnapshot,
)
