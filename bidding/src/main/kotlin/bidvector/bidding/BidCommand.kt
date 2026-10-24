package bidvector.bidding

import java.time.Instant
import java.time.LocalDate

/**
 * 입찰 건에 거는 명령 — 전이표(`BidTransitions.kt`)의 열 축이다.
 *
 * **명령을 상태별로 쪼개지 않는다.** 「초안 만들기」와 「초안 수정」, 「제출함」과 「기록
 * 갱신」과 「소급 확인」은 사용자에게 **한 동작**이고, 무엇이 달랐는지는 전이표의 **출발
 * 상태**가 적는다(타임라인에 `from_state` 로 남는다). 명령을 쪼개면 같은 payload 를 가진
 * 어휘가 늘어나고 소급 여부를 호출부가 **고를 수 있게** 된다 — 그것이 Q-11 이 막으려는 것이다.
 */
sealed interface BidCommand {
    val tag: BidCommandTag

    /** 나중에 — 리마인드 시각이 필수다(타입이 진다). */
    data class Defer(
        val remindAt: Instant,
    ) : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.DEFER
    }

    /** 나중에 해제. */
    data object Resume : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.RESUME
    }

    /** 투찰가 메뉴 수령 — **오늘 호출자가 없다**(DEC-05 미출하, 알려진 제한 ①). */
    data class ReceivePriceMenu(
        val menu: PriceMenuRef,
    ) : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.RECEIVE_PRICE_MENU
    }

    /** 초안 만들기 · 수정 — 수정은 같은 행을 고치지 않고 새 초안을 쌓는다. */
    data class DraftBid(
        val draft: BidDraft,
    ) : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.DRAFT_BID
    }

    /**
     * 「제출함」 확인 · 기록 갱신 · 소급 확인 — 셋 다 이 하나다.
     *
     * [evidenceRetainUntil] 은 호출부가 **정책에서 풀어** 넘긴다(R-5 3년,
     * `workflow.bidding.EvidenceRetentionPolicyData`) — 도메인이 날짜 산술의 기준일을
     * 지어내지 않는다(`EffectiveDatedPolicy` 관례와 같은 축). 증빙이 없으면 `null` 이다.
     */
    data class RecordBid(
        val submission: BidSubmission,
        val evidenceRetainUntil: LocalDate?,
    ) : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.RECORD_BID
    }

    /** 개찰 대기 — 개찰일시가 필수다. */
    data class AwaitOpening(
        val openingAt: Instant,
    ) : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.AWAIT_OPENING
    }

    /** 개찰 결과 · 잠정 1위. */
    data object RecordOpeningFirst : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.RECORD_OPENING_FIRST
    }

    /** 개찰 결과 · 순위 밖. */
    data object RecordOpeningOutOfRank : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.RECORD_OPENING_OUT_OF_RANK
    }

    /** 적격심사 진입 — 서류 기한이 필수다. */
    data class EnterQualificationReview(
        val documentDueAt: Instant,
    ) : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.ENTER_QUALIFICATION_REVIEW
    }

    /** 낙찰 · 패찰 · 유찰 기록. */
    data class RecordOutcome(
        val outcome: BidOutcome,
    ) : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.RECORD_OUTCOME
    }

    /**
     * 결과 정정(§6.1 「자동(SET-01) + 사용자 정정」) — **사유가 필수**다. 원본을 덮어쓰지
     * 않는다: 이전 결과는 타임라인의 `from_state` 로 남는다
     * (`.claude/rules/kotlin-service.md` 「확정 금액 정정은 원본을 덮어쓰지 않는다」와 같은 축).
     */
    data class CorrectOutcome(
        val outcome: BidOutcome,
        val reason: TransitionReason,
    ) : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.CORRECT_OUTCOME
    }

    /** 건너뜀 — 사유가 필수다. */
    data class Skip(
        val reason: SkipReason,
    ) : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.SKIP
    }

    /** 마감 지남(미확인) — 시각 판정의 결과를 건에 적는다. */
    data object MarkDeadlinePassed : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.MARK_DEADLINE_PASSED
    }

    /**
     * 마감 판정 되돌림(계약 D-7B-4 결정 ⑤) — 시스템이 세운 「마감 지남」을 유효 마감이 다시 미래(또는 미정)가 됐을 때
     * 거둔다. 도착([restore])은 **use case 가 타임라인에서 읽어 싣고**, 전이표가 그 값이 마감 전 넷 중 하나인지
     * 판정한다(표의 꼴 — 도착 집합 — 을 깨지 않는다). 시스템 주체만 쓴다.
     */
    data class RevokeDeadlinePassed(
        val restore: BidState,
    ) : BidCommand {
        override val tag: BidCommandTag get() = BidCommandTag.REVOKE_DEADLINE_PASSED
    }
}

/** 전이표의 열 키. 어휘는 `V21` 의 `bid_case_transition.command` CHECK 와 같다. */
enum class BidCommandTag {
    DEFER,
    RESUME,
    RECEIVE_PRICE_MENU,
    DRAFT_BID,
    RECORD_BID,
    AWAIT_OPENING,
    RECORD_OPENING_FIRST,
    RECORD_OPENING_OUT_OF_RANK,
    ENTER_QUALIFICATION_REVIEW,
    RECORD_OUTCOME,
    CORRECT_OUTCOME,
    SKIP,
    MARK_DEADLINE_PASSED,
    REVOKE_DEADLINE_PASSED,
}

/**
 * 건을 **여는** 명령은 [BidCommand] 가 아니다 — 출발 상태가 없어 전이표의 행이 될 수 없다.
 * 타임라인에는 `from_state = NULL` 인 생성 전이로 남고, 그 어휘가 이것이다.
 */
const val OPEN_CASE_COMMAND: String = "ADD_TO_BID_BOX"
