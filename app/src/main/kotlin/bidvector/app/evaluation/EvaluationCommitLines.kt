package bidvector.app.evaluation

import bidvector.decision.Verdict
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.NotificationDisposition
import bidvector.workflow.evaluation.NotificationRequestOutcome

/**
 * 평가 커밋 러너의 종료 코드 — **0 은 `BidNow` 판정 전부가 outbox 행으로 남았을 때만**이다.
 *
 * 사유 토큰은 `name`, 종료 코드는 [value] — 한 쌍으로 간다.
 */
enum class EvaluationCommitExitCode(
    val value: Int,
) {
    COMPLETE(0),

    /** 실행 자체가 실패했다. */
    FAILED(1),

    /**
     * 판정은 났는데 **흔적이 없는 것**이 있다(`NotificationRequestOutcome.Failed`) — 오늘
     * 평가에는 판정 기록 표가 없어 outbox 행이 판정의 유일한 영속 흔적이므로(D-6F7-2) 그것은
     * 판정이 사라진 것과 같다. 앞 판의 `reach` 는 이 값을 버렸고 run 은 성공으로 끝났다.
     */
    INCOMPLETE(2),
}

/** run 하나의 집계 — 로그와 종료 코드가 같은 값에서 나온다(두 자리에 세지 않는다). */
internal data class EvaluationCommitTally(
    val candidates: Int,
    val reached: Int,
    val notReached: Int,
    val bidNow: Int,
    val requested: Int,
    val requestFailed: Int,
)

internal fun tallyOf(results: List<CandidateEvaluation>): EvaluationCommitTally {
    val reached = results.filterIsInstance<CandidateEvaluation.Reached>()
    val dispositions = reached.map { it.disposition }
    return EvaluationCommitTally(
        candidates = results.size,
        reached = reached.size,
        notReached = results.size - reached.size,
        bidNow = reached.count { it.verdict is Verdict.BidNow },
        requested = dispositions.count { outcomeOf(it) == NotificationRequestOutcome.Requested },
        requestFailed = dispositions.count { outcomeOf(it) == NotificationRequestOutcome.Failed },
    )
}

/**
 * 소진 `when` — 「판정이 승격이 아니어서 요청이 없었다」와 「요청했는데 실패했다」를 가른다.
 * `null` 이었다면 호출부가 그 둘을 한 분기로 접었을 자리다.
 */
private fun outcomeOf(disposition: NotificationDisposition): NotificationRequestOutcome? =
    when (disposition) {
        NotificationDisposition.NotApplicable -> null
        is NotificationDisposition.Requested -> disposition.outcome
    }

internal fun exitCodeOf(tally: EvaluationCommitTally): EvaluationCommitExitCode =
    if (tally.requestFailed > 0) EvaluationCommitExitCode.INCOMPLETE else EvaluationCommitExitCode.COMPLETE

/** 계수와 열거값만 싣는다 — 공고 ID·기관명·판정 사유는 이 줄에 없다(수집 러너와 같은 근거). */
internal fun evaluationCommitStartLine(candidateCap: Int): String = "evaluation-commit start candidateCap=$candidateCap"

internal fun evaluationCommitFinishLine(
    tally: EvaluationCommitTally,
    exitCode: EvaluationCommitExitCode,
): String =
    "evaluation-commit finished candidates=${tally.candidates} reached=${tally.reached} " +
        "notReached=${tally.notReached} bidNow=${tally.bidNow} requested=${tally.requested} " +
        "requestFailed=${tally.requestFailed} exit=${exitCode.value}"

internal fun evaluationCommitFailureLine(causeCode: String): String = "evaluation-commit failed cause=$causeCode"
