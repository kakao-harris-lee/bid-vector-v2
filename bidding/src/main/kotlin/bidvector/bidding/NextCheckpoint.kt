package bidvector.bidding

import java.time.Instant

/** 다음 확인 시점이 **무엇인가** — 「없음」을 포함해 다섯 갈래다. */
enum class CheckpointKind {
    DEADLINE,
    OPENING,
    DOCUMENT_DUE,
    NOTICE_EXPECTED,
    REMINDER,
}

/**
 * 「다음 확인 시점」 — §6.1 *"어느 상태에서도 「다음 확인 시점」이 비어 있지 않다(없으면
 * 「없음」이 명시된다 — 빈 값 금지)"*.
 *
 * **nullable 을 쓰지 않는 것이 그 요구다.** `Instant?` 로 두면 「아직 안 정했다」와 「없는 것이
 * 맞다」가 같은 값이 되고, 화면은 그 둘을 빈 칸으로 똑같이 그린다. [None] 은 **답**이지 공백이
 * 아니다.
 */
sealed interface NextCheckpoint {
    data class At(
        val at: Instant,
        val kind: CheckpointKind,
    ) : NextCheckpoint

    data object None : NextCheckpoint
}

/**
 * 상태에서 다음 확인 시점을 낸다 — **순수 함수이고 전역(total)이다.**
 *
 * 저장하지 않는다. 파생값을 영속화하면 공고 정정 · 시각 경과와 갈라지고, 「서버가 한 시각
 * 기준으로 판정한다」(계약 산출물 2)는 성질이 적재 시점에 굳어 버린다.
 *
 * 전역성이 성립하는 방식은 셋이 맞물린다 — ⓐ 출력이 sealed 라 빈 값이 없고, ⓑ `when` 이
 * 상태 전수라 상태를 더하면 답 없이는 컴파일되지 않으며, ⓒ **필수 입력은 상태가 싣는다**
 * ([BidState.Deferred.remindAt] 등). ⓒ 덕분에 이 함수에는 「있어야 하는데 없는」 경우가 없다.
 *
 * [noticeDeadlineAt] 은 **라이브 공고**의 마감이다(초안 스냅샷의 사본이 아니다) — 투찰 예정
 * 이전 상태에서는 공고가 정정되면 다음 확인 시점도 따라 움직이는 것이 맞다. 초안이 얼린
 * 것은 「그때 그 금액을 정한 근거」이지 「앞으로 언제 확인할 것인가」가 아니다.
 */
fun nextCheckpoint(
    state: BidState,
    noticeDeadlineAt: Instant?,
    outcomeNoticeExpectedAt: Instant?,
): NextCheckpoint =
    when (state) {
        BidState.Watching,
        BidState.PriceReceived,
        BidState.BidPlanned,
        -> {
            at(noticeDeadlineAt, CheckpointKind.DEADLINE)
        }

        is BidState.Deferred -> {
            NextCheckpoint.At(state.remindAt, CheckpointKind.REMINDER)
        }

        // 개찰일시를 아직 모른다 — 운영자가 `AWAIT_OPENING` 으로 적으면 그때 생긴다.
        BidState.BidRecorded -> {
            NextCheckpoint.None
        }

        is BidState.AwaitingOpening -> {
            NextCheckpoint.At(state.openingAt, CheckpointKind.OPENING)
        }

        BidState.OpenedProvisionalFirst,
        BidState.OpenedOutOfRank,
        -> {
            at(outcomeNoticeExpectedAt, CheckpointKind.NOTICE_EXPECTED)
        }

        is BidState.UnderQualificationReview -> {
            NextCheckpoint.At(state.documentDueAt, CheckpointKind.DOCUMENT_DUE)
        }

        BidState.Won,
        BidState.Lost,
        BidState.FailedOrRebid,
        is BidState.Skipped,
        BidState.DeadlinePassedUnrecorded,
        -> {
            NextCheckpoint.None
        }
    }

private fun at(
    instant: Instant?,
    kind: CheckpointKind,
): NextCheckpoint = instant?.let { NextCheckpoint.At(it, kind) } ?: NextCheckpoint.None

/**
 * 지난 일정 경고 — **서버가 한 시각 기준으로 판정한다**(계약 산출물 2, 화면 D 의 「지난 일정
 * 경고」). 화면이 각자의 시계로 재면 사용자마다 다른 답이 나온다.
 */
fun isOverdue(
    checkpoint: NextCheckpoint,
    now: Instant,
): Boolean =
    when (checkpoint) {
        is NextCheckpoint.At -> checkpoint.at.isBefore(now)
        NextCheckpoint.None -> false
    }
