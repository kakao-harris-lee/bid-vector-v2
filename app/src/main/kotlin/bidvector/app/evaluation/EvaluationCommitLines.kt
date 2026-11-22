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
     * 전역 채널로 **보내지 않은** 건수(M15/15A) — 가장 작은 계정이 아닌 run 의 `BidNow` 다.
     * `requested` 에 접으면 종료 줄이 보내지 않은 건을 보냈다고 말하고, `requestFailed` 에
     * 접으면 계약대로 도는 run 이 매번 INCOMPLETE 로 끝난다.
     */
    val suppressed: Int,
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
        suppressed = dispositions.count { outcomeOf(it) == NotificationRequestOutcome.Suppressed },
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

/**
 * **계정 하나가 터진 것도 비-0 이다**(r1 리뷰 MEDIUM). 그 계정의 판정이 사라진 사실은 계수에만
 * 있으면 안 된다 — 종료 코드가 조용하면 cron 은 그 run 을 성공으로 적는다. 억제는 비-0 이
 * 아니다(계약대로 도는 정상 상태다, [EvaluationCommitTally.suppressed]).
 */
internal fun exitCodeOf(
    tally: EvaluationCommitTally,
    failedAccounts: Int,
): EvaluationCommitExitCode =
    if (tally.requestFailed > 0 || failedAccounts > 0) {
        EvaluationCommitExitCode.INCOMPLETE
    } else {
        EvaluationCommitExitCode.COMPLETE
    }

/**
 * 계수와 열거값만 싣는다 — 공고 ID·기관명·판정 사유는 이 줄에 없다(수집 러너와 같은 근거).
 *
 * **M15/15B — run 당 계정 상한도 싣는다**(`OPEN-M15-EVAL-COST-PER-ACCOUNT`). 그 수가 시작 줄에
 * 있어야 운영자가 「오늘 몇 계정까지 돌 수 있었나」를 끝 줄의 `deferred=` 와 맞댈 수 있다.
 */
internal fun evaluationCommitStartLine(
    candidateCap: Int,
    maxAccountsPerRun: Int,
): String = "evaluation-commit start candidateCap=$candidateCap maxAccountsPerRun=$maxAccountsPerRun"

/**
 * **M15/15A — 계정 계수 셋을 함께 싣는다.** `accounts` 는 실제로 평가한 계정 수, `skipped` 는
 * 전략을 쓸 수 없어 건너뛴 계정 수, `failed` 는 평가 중에 터진 계정 수다. 따로 세지 않으면
 * 「후보 0」이 「감시 조건이 아무도 안 걸렸다」인지 「평가한 계정이 하나도 없다」인지 구별되지
 * 않고, `accounts=0 skipped=0 failed=0` 이라는 **아무도 없는 run** 도 줄에서 보인다.
 *
 * **M15/15B — 넷째 계수 `deferred`**(`OPEN-M15-EVAL-COST-PER-ACCOUNT`). run 당 계정 상한 밖이거나
 * 연속 실패로 멈춘 뒤에 남은 계정 수다. **실패가 아니다** — 그 계정의 평가는 사라진 것이 아니라
 * **다음 KST 달력일의 창으로 미뤄졌고**(`accountsForRun` 의 회전), 그래서 종료 코드를 바꾸지 않는다
 * (`skipped` 와 같은 자격, `failed` 와 다른 자격). **run 당 계정 상한**이 청중보다 크거나 같고
 * **멈춤도 없으면** 늘 0 이다 — 원천이 둘이므로 상한 하나만 보면 틀린다(r4 CR L-1).
 *
 * **「다음 run」이 아니라 「다음 달력일」이다** — 회전 축이 날짜라 같은 날 다시 돌리면 같은 집합이
 * 돈다. 상한 밖 계정을 오늘 당장 보려면 상한을 올리는 수밖에 없다.
 *
 * 네 계수는 청중과 **닫힌다**: `accounts + skipped + failed + deferred == 청중 크기`(두 원천이
 * 서로소다 — r3 CR L-8 이 그 등식을 test 로 잠그게 했다).
 */
internal fun evaluationCommitFinishLine(
    tally: EvaluationCommitTally,
    accounts: Int,
    skipped: Int,
    failed: Int,
    deferred: Int,
    exitCode: EvaluationCommitExitCode,
): String =
    "evaluation-commit finished accounts=$accounts skipped=$skipped failed=$failed deferred=$deferred " +
        "candidates=${tally.candidates} reached=${tally.reached} " +
        "notReached=${tally.notReached} bidNow=${tally.bidNow} requested=${tally.requested} " +
        "requestFailed=${tally.requestFailed} suppressed=${tally.suppressed} " +
        "${floorExclusionFields(tally)} exit=${exitCode.value}"

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

/**
 * 계정 전수를 못 읽었다(M15/15A) — run 을 **시작하지 않는다**. 빈 목록으로 진행하면 「평가할 계정이
 * 없다」와 「누가 있는지 못 읽었다」가 같은 종료 코드 0 이 된다. 싣는 것은 예외 **클래스 이름**뿐이다
 * (`NotificationAudience.Unavailable` 이 이미 그만큼만 나른다).
 */
internal fun evaluationCommitAudienceFailureLine(exceptionType: String): String =
    evaluationCommitFailureLine("audience-unavailable:$exceptionType")

/**
 * 계정 하나가 터졌다(M15/15A) — 순회는 **계속된다**. 싣는 것은 사유 코드뿐이고 어느 계정인지는
 * 적지 않는다(이 줄은 공개 로그이고, 계정 id 는 계수로 충분하다).
 */
internal fun evaluationCommitAccountFailureLine(causeCode: String): String =
    "evaluation-commit account-failed cause=$causeCode"

/**
 * 끊기지 않는 실패로 멈췄다 — 그 실패들은 그 계정들의 일이 아니라 **체계가 내려간 것**의 신호이고,
 * 그때 남은 계정을 계속 도는 것은 K × 타임아웃을 치르는 일이다.
 */
internal const val STOP_CAUSE_CONSECUTIVE_FAILURES = "consecutive-account-failures"

/**
 * **인터럽트로 멈췄다**(M15/15B r4, CR L-4) — 누가 이 프로세스를 껐다는 신호다. 계속 돌면 신호
 * 하나가 `failed=1,2,3…` 으로 불어나 「체계가 내려갔다」와 같은 모양이 된다.
 */
internal const val STOP_CAUSE_INTERRUPTED = "interrupted"

/**
 * **끝까지 돌지 않았다**(M15/15B, `OPEN-M15-EVAL-COST-PER-ACCOUNT`). 종료 코드는 이미
 * `INCOMPLETE` 이고(실패가 0 이 아니다) 이 줄은 **왜** 멈췄는지를 말한다 — [cause] 는
 * [STOP_CAUSE_CONSECUTIVE_FAILURES] 아니면 [STOP_CAUSE_INTERRUPTED] 다.
 *
 * [consecutiveFailures] 는 **멈춘 시점의 연속 실패 계수**다(인터럽트로 멈춘 run 에서는 대개 1).
 * 줄의 모양을 사유마다 바꾸지 않는 것이 의도다 — 파서가 하나면 된다.
 *
 * [remaining] 은 **run 전체의 남은 수**이고 마침 줄의 `deferred` 와 같은 값이다(r3 CR L-2).
 * 창 안쪽만 세면 두 원천(상한 밖 · 멈춤 뒤)이 함께 설 때 두 줄의 수가 달라지는데, 그 관계가
 * 어디에도 적혀 있지 않아 읽는 사람이 둘을 더하게 된다.
 */
internal fun evaluationCommitStoppedLine(
    cause: String,
    consecutiveFailures: Int,
    remaining: Int,
): String = "evaluation-commit stopped cause=$cause consecutive=$consecutiveFailures remaining=$remaining"
