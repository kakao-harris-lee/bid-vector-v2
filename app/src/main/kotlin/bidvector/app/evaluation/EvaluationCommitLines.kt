package bidvector.app.evaluation

import bidvector.decision.PublishedFloorExclusion
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
    /**
     * DEC-10 계수(D-DEC10-5) — 게시 하한율이 가격 경로에서 제외된 건수를 **사유별로** 센다.
     * 표는 사유 전수를 키로 갖는다(0 건도 줄에 나온다) — 「그 사유가 0 이었다」와 「그 사유를
     * 세지 않았다」가 구별되지 않으면 계수가 없는 것과 같다.
     */
    val floorExclusions: Map<PublishedFloorExclusion, Int>,
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
        // **결과 전체**에서 센다(r1 CR M-1) — 최소 점수 드롭은 분석을 받은 뒤 `NotReached` 로
        // 떨어지는 자리라, `reached` 만 세면 그 가지의 제외가 줄에서 사라진다.
        floorExclusions =
            PublishedFloorExclusion.entries.associateWith { exclusion ->
                results.count { it.floorExclusion == exclusion }
            },
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
        "requestFailed=${tally.requestFailed} ${floorExclusionFields(tally)} exit=${exitCode.value}"

/**
 * 사유별 계수 필드 — 사유 전수를 돌아 **0 건도 적는다**. 키 이름은 소진 `when` 이 정한다
 * (Kotlin 상수 이름에서 파생시키지 않는다 — 상수를 개명해도 계수 이름이 조용히 따라 바뀌면 안 된다).
 *
 * **이름의 층이 둘이다**(r1 CR M-2). `capability-map.md` DEC-10 acceptance 셋째는 그 계수를
 * `floor_implausible` 로 부르고, 그것은 운영자 승인 문서의 어휘다. 이 줄이 방출하는 키는
 * `floorImplausible` 이다 — 같은 계수의 **이 줄 표기**이고, 줄의 다른 필드 전부가 camelCase 라
 * 한 줄 안에서 표기가 갈리지 않게 한다. 둘의 대응은 evidence 가 한 줄로 적는다.
 */
private fun floorExclusionFields(tally: EvaluationCommitTally): String =
    PublishedFloorExclusion.entries
        .joinToString(" ") { exclusion -> "${logKeyOf(exclusion)}=${tally.floorExclusions[exclusion] ?: 0}" }

private fun logKeyOf(exclusion: PublishedFloorExclusion): String =
    when (exclusion) {
        PublishedFloorExclusion.IMPLAUSIBLE -> "floorImplausible"
        PublishedFloorExclusion.ORIGIN_NOT_JUDGED -> "floorOriginNotJudged"
    }

internal fun evaluationCommitFailureLine(causeCode: String): String = "evaluation-commit failed cause=$causeCode"
