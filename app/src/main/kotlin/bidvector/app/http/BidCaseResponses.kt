package bidvector.app.http

import bidvector.bidding.NextCheckpoint
import bidvector.bidding.tag
import bidvector.workflow.bidding.BidCaseView
import bidvector.workflow.bidding.TimelineEntry

/**
 * 입찰 건 응답 — **평탄하다**(D-6A1-20 ⓑ, `StrategyReadResponse` 관례). 모든 필드가 문자열 ·
 * 숫자 · 불리언이고 중첩 object 가 없다.
 *
 * **「다음 확인 시점」이 두 필드로 나간다**: [nextCheckpointKind] 는 **항상** 값이 있고
 * (「없음」은 `NONE`), [nextCheckpointAt] 만 null 이 될 수 있다. 시각 하나로 접으면 화면이
 * 「아직 안 정했다」와 「없는 것이 맞다」를 구별할 수 없다 — §6.1 이 금지한 빈 값이 그 모양으로
 * 되살아난다.
 *
 * [overdue] 도 서버가 판정해 싣는다(계약 산출물 2 「서버가 한 시각 기준으로」) — 화면이 각자의
 * 시계로 재면 사용자마다 다른 답이 나온다.
 *
 * **공고 칸 넷은 저장된 값이 아니라 읽을 때 합쳐 온 값이다**(M14/14E) — 건은 공고를 FK 로
 * 참조하지 않으므로(V21 머리말) 그 행이 없을 수 있다. 응답이 평탄해야 해서 표시 칸이
 * 납작해지는데, 그러면 「행이 없다」와 「행은 있고 제목 칸이 비었다」가 같은 `null` 셋이 된다 —
 * [noticeKnown] 이 그 구분을 들고 간다(알림함 `NotificationItem` 과 같은 꼴).
 */
data class BidCaseResponse(
    val caseId: Long,
    val noticeNumber: String,
    val noticeRound: String,
    val bidKind: String,
    val state: String,
    val caseVersion: Int,
    val nextCheckpointKind: String,
    val nextCheckpointAt: String?,
    val overdue: Boolean,
    val reconciliationCandidate: Boolean,
    val noticeKnown: Boolean,
    val noticeTitle: String?,
    val demandAgencyName: String?,
    val noticeAgencyName: String?,
) {
    companion object {
        /** 「없음」의 어휘 — 빈 문자열이 아니라 **이름**이다. */
        const val NO_CHECKPOINT = "NONE"

        fun from(view: BidCaseView): BidCaseResponse {
            val checkpoint = view.nextCheckpoint
            return BidCaseResponse(
                caseId = view.case.id.value,
                noticeNumber = view.case.notice.noticeNumber,
                noticeRound = view.case.notice.noticeRound.value,
                bidKind = view.case.kind.name,
                state = view.case.state.tag.name,
                caseVersion = view.case.caseVersion,
                nextCheckpointKind =
                    when (checkpoint) {
                        is NextCheckpoint.At -> checkpoint.kind.name
                        NextCheckpoint.None -> NO_CHECKPOINT
                    },
                nextCheckpointAt =
                    when (checkpoint) {
                        is NextCheckpoint.At -> checkpoint.at.toString()
                        NextCheckpoint.None -> null
                    },
                overdue = view.overdue,
                reconciliationCandidate = view.reconciliationCandidate,
                // `notice` 가 null 인 것은 **그 키의 공고 행이 없다**는 뜻이다 — 칸이 빈 것과 다르다.
                noticeKnown = view.notice != null,
                noticeTitle = view.notice?.title,
                demandAgencyName = view.notice?.demandAgencyName,
                noticeAgencyName = view.notice?.noticeAgencyName,
            )
        }
    }
}

/**
 * 타임라인 한 줄 — **본문이 없다.** 메모 · 증빙 참조는 이 응답에 자리가 없다(계약 산출물 8:
 * audit 는 본문을 남기지 않는다). [reason] 은 건너뜀 · 정정의 사유다.
 */
data class BidTimelineEntryResponse(
    val caseVersion: Int,
    val fromState: String?,
    val toState: String,
    val command: String,
    val actorKind: String,
    val actorDetail: String?,
    val reason: String?,
    val retroactive: Boolean,
    val occurredAt: String,
) {
    companion object {
        fun from(entry: TimelineEntry): BidTimelineEntryResponse =
            BidTimelineEntryResponse(
                caseVersion = entry.caseVersion,
                fromState = entry.fromState?.name,
                toState = entry.toState.name,
                command = entry.command,
                actorKind = entry.actorKind.name,
                actorDetail = entry.actorDetail,
                reason = entry.reason,
                retroactive = entry.retroactive,
                occurredAt = entry.occurredAt.toString(),
            )
    }
}
