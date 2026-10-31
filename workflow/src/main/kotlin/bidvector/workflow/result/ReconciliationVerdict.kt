package bidvector.workflow.result

import bidvector.settlement.AwardMatchingPolicyData
import bidvector.settlement.AwardOutcome
import bidvector.settlement.AwardWinnerTradeName
import bidvector.settlement.OperatorNameAbsentReason
import bidvector.settlement.OperatorTradeName
import bidvector.settlement.SettledAwardOutcome
import bidvector.settlement.WinnerNameAbsentReason
import bidvector.settlement.decideAwardLookup
import bidvector.settlement.judgeAwardOutcome
import bidvector.workflow.opening.OpeningProgress
import bidvector.workflow.opening.OpeningResultView

/**
 * 대사가 판정에 이르지 못한 사유 — **열**이다(D-7C5B-7 · 레인 정정 2026-10-31).
 *
 * 개찰 축 여섯은 「판정할 거리가 아직 없다」이고, 커널 축 넷은 「거리는 있는데 견줄 상호가
 * 없다」다. 둘을 한 값으로 접지 않는 이유: 전자는 기다리면 풀리고 후자 가운데 둘
 * ([OPERATOR_NAME_NOT_DECLARED] · [OPERATOR_NAME_NORMALIZES_TO_EMPTY])은 **운영자가 프로필을
 * 고치면** 풀린다 — 화면이 「무엇을 하면 되는가」를 말하려면 그 구별이 필요하다.
 *
 * **[PROGRESS_UNRECOGNIZED] 와 [PROGRESS_ABSENT] 를 접지 않는다** — 7C⑤-a 가 가른 둘이다
 * (`OpeningProgress` KDoc: 전자는 등재 밖 라벨, 후자는 열이 빈 것). 대사에서 도로 접으면
 * 「상류 어휘가 늘었다」가 보이지 않는다.
 *
 * `enum` 인 이유는 사유가 값을 싣지 않기 때문이고, 그 덕에 [entries] 로 **전수 도달 test** 가
 * 기계로 선다(손으로 든 목록이면 사유를 더하고 test 를 안 늘려도 초록이다).
 */
enum class ReconciliationBlocker {
    /** 그 공고를 모른다 — 공고 행이 없거나 건의 공고번호가 도메인 규칙으로 서지 않는다. */
    NOTICE_ABSENT,

    /** 아직 개찰 전이다. */
    NOT_OPENED_YET,

    /** 개찰 결과가 수집되지 않았다. */
    NOT_COLLECTED,

    /** 원문은 있는데 canonical 승격이 없다. */
    OBSERVED_NOT_PROMOTED,

    /** 진행구분 라벨이 정책 등재 밖이다 — 상류가 새 어휘를 보냈다. */
    PROGRESS_UNRECOGNIZED,

    /** 진행구분 열이 비어 있다 — 개찰완료 축만 승격된 행이 그 꼴이다. */
    PROGRESS_ABSENT,

    /** 운영자가 상호를 설정하지 않았다. */
    OPERATOR_NAME_NOT_DECLARED,

    /** 운영자 상호가 정규화 뒤 빈 값이다(법인 표기 토큰만). */
    OPERATOR_NAME_NORMALIZES_TO_EMPTY,

    /**
     * 낙찰자 상호가 공개되지 않았다 — **「낙찰자가 아니다」가 아니다**(SET-01 의 3-state).
     * 오늘 production 의 답은 전건 이것이다(낙찰 목록 끝점이 출하 수집 축에 없다,
     * `OPEN-SET01-AWARD-NAME-INGEST` · D-7C5B-10 ②).
     */
    WINNER_NAME_NOT_PUBLISHED,

    /** 낙찰자 상호가 정규화 뒤 빈 값이다. */
    WINNER_NAME_NORMALIZES_TO_EMPTY,
}

/**
 * 건 하나의 대사 결말 — **낙찰·패찰·유찰·재입찰·판정 불가**다.
 *
 * 커널([judgeAwardOutcome])은 이 가운데 둘(낙찰·패찰)과 사유 넷만 안다. 유찰·재입찰은 개찰
 * 진행구분 축이고 개찰 축 여섯은 그 앞단이라, 이 타입은 **조립의 산물**이다(D-7C5B-7 「구성」).
 *
 * `isWon(): Boolean` 류의 접는 API 를 두지 않는다(`AwardOutcome` 규율 승계) — 소비자는 소진
 * `when` 만 쓴다. 판정 불가를 패찰로 접는 길이 API 에 없다.
 */
sealed interface ReconciliationVerdict {
    data object Won : ReconciliationVerdict

    data object Lost : ReconciliationVerdict

    /** 유찰 — 낙찰자가 없다. 판정 불가가 아니라 **결말**이다. */
    data object FailedBid : ReconciliationVerdict

    /** 재입찰 — 같은 이유로 결말이다(재공고는 새 공고라 새 건이 된다). */
    data object Rebid : ReconciliationVerdict

    data class Undecidable(
        val blocker: ReconciliationBlocker,
    ) : ReconciliationVerdict
}

/**
 * 개찰 결과 × 상호 둘 → 대사 결말(순수).
 *
 * **개찰 축이 커널 축보다 먼저다.** 아직 개찰 전인 건에 「상호 미설정」을 사유로 내면 운영자가
 * 상호를 채워도 답이 달라지지 않는데 채우라고 말하는 셈이 된다. 커널 안의 순서(운영자 축 →
 * 결과 축)는 커널이 그대로 지킨다.
 *
 * **금액을 받지 않는다** — [OpeningResultView] 는 받지만 판정에 쓰는 것은 그 상태와
 * 진행구분뿐이고([judgeByProgress] 의 인자가 `OpeningProgress` 하나다), 커널 시그니처에도 금액
 * 자리가 없다(SET-01: 같은 금액을 낸 다른 업체가 실존하는 도메인이라 금액 근접은 낙찰의 근거가
 * 아니다).
 */
fun reconcileBid(
    opening: OpeningResultView,
    operator: OperatorTradeName,
    winner: AwardWinnerTradeName,
    policy: AwardMatchingPolicyData,
): ReconciliationVerdict =
    when (opening) {
        OpeningResultView.NoticeAbsent -> undecidable(ReconciliationBlocker.NOTICE_ABSENT)
        OpeningResultView.NotOpenedYet -> undecidable(ReconciliationBlocker.NOT_OPENED_YET)
        OpeningResultView.NotCollected -> undecidable(ReconciliationBlocker.NOT_COLLECTED)
        OpeningResultView.ObservedNotPromoted -> undecidable(ReconciliationBlocker.OBSERVED_NOT_PROMOTED)
        is OpeningResultView.Observed -> judgeByProgress(opening.progress, operator, winner, policy)
    }

private fun undecidable(blocker: ReconciliationBlocker): ReconciliationVerdict =
    ReconciliationVerdict.Undecidable(blocker)

/** 진행구분 축 — 개찰완료만 커널로 간다. 유찰·재입찰은 상호를 보지 않는다(볼 낙찰자가 없다). */
private fun judgeByProgress(
    progress: OpeningProgress,
    operator: OperatorTradeName,
    winner: AwardWinnerTradeName,
    policy: AwardMatchingPolicyData,
): ReconciliationVerdict =
    when (progress) {
        OpeningProgress.OPENED -> fromKernel(judgeAwardOutcome(decideAwardLookup(operator, policy), winner))
        OpeningProgress.FAILED_BID -> ReconciliationVerdict.FailedBid
        OpeningProgress.REBID -> ReconciliationVerdict.Rebid
        OpeningProgress.UNRECOGNIZED -> undecidable(ReconciliationBlocker.PROGRESS_UNRECOGNIZED)
        OpeningProgress.ABSENT -> undecidable(ReconciliationBlocker.PROGRESS_ABSENT)
    }

/** 커널의 4-state 를 이 slice 의 어휘로 — **접지 않는다**(미기록·판정 안 함이 패찰이 되지 않는다). */
private fun fromKernel(outcome: AwardOutcome): ReconciliationVerdict =
    when (outcome) {
        is SettledAwardOutcome.Won -> ReconciliationVerdict.Won
        is SettledAwardOutcome.Lost -> ReconciliationVerdict.Lost
        is AwardOutcome.NotJudged -> undecidable(operatorBlocker(outcome.reason))
        is AwardOutcome.NotRecorded -> undecidable(winnerBlocker(outcome.reason))
    }

private fun operatorBlocker(reason: OperatorNameAbsentReason): ReconciliationBlocker =
    when (reason) {
        OperatorNameAbsentReason.NotDeclared -> ReconciliationBlocker.OPERATOR_NAME_NOT_DECLARED
        OperatorNameAbsentReason.NormalizesToEmpty -> ReconciliationBlocker.OPERATOR_NAME_NORMALIZES_TO_EMPTY
    }

private fun winnerBlocker(reason: WinnerNameAbsentReason): ReconciliationBlocker =
    when (reason) {
        WinnerNameAbsentReason.NotPublished -> ReconciliationBlocker.WINNER_NAME_NOT_PUBLISHED
        WinnerNameAbsentReason.NormalizesToEmpty -> ReconciliationBlocker.WINNER_NAME_NORMALIZES_TO_EMPTY
    }
