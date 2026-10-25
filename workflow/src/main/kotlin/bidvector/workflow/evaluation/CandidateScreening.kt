package bidvector.workflow.evaluation

import bidvector.procurement.Notice
import bidvector.procurement.isBiddable
import bidvector.qualification.LicenseVerdict
import bidvector.strategy.OperatorStrategy
import bidvector.strategy.WatchVerdict
import bidvector.strategy.evaluate
import bidvector.workflow.strategy.Clock

/**
 * 값싼 게이트 셋(생애주기 → 감시 → 면허)을 한 후보에 돌린 결과(M7/7C①b, D-7C1B-1 Q4). 평가
 * use case 와 제안 목록 use case 가 **같은 함수**를 부른다 — 드롭 순서·사유가 두 벌이 되지 않는다.
 *
 * `Passed` 는 면허 판정 값을 버리지 않고 싣는다(`Eligible`·`Uncertain` — `Ineligible` 은 `Dropped`).
 * 평가 use case 는 그 값을 쓰지 않고, 제안 목록은 카드에 싣는다.
 */
internal sealed interface CandidateScreening {
    data class Passed(
        val license: LicenseVerdict,
    ) : CandidateScreening

    data class Dropped(
        val stage: EvaluationStage,
        val reason: EvaluationDropReason,
    ) : CandidateScreening
}

/**
 * 게이트 셋을 순서대로 돌린다 — 앞 게이트에서 멈춘 후보는 뒤 port 를 부르지 않는다. 이 함수는
 * `EvaluateCandidatesUseCase` 의 private 함수 셋(`lifecycleDrop`·`watchGateDrop`·`licenseGateDrop`)을
 * **거동 그대로** 옮긴 것이다(`EvaluateCandidatesScreeningCharacterizationTest` 가 먼저 고정했다).
 */
internal fun screenCandidate(
    notice: Notice,
    strategy: OperatorStrategy,
    clock: Clock,
    watchSubjects: WatchSubjectPort,
    licenseGate: LicenseGatePort,
): CandidateScreening =
    lifecycleDrop(notice, clock)
        ?: watchGateDrop(notice, strategy, watchSubjects)
        ?: licenseScreening(notice, licenseGate)

private fun lifecycleDrop(
    notice: Notice,
    clock: Clock,
): CandidateScreening.Dropped? {
    val deadline = notice.deadlineAt
    return if (deadline != null && isBiddable(notice.status, clock.now(), deadline)) {
        null
    } else {
        CandidateScreening.Dropped(
            EvaluationStage.NoticeLifecycle,
            EvaluationDropReason.NoticeNotBiddable(notice.status),
        )
    }
}

private fun watchGateDrop(
    notice: Notice,
    strategy: OperatorStrategy,
    watchSubjects: WatchSubjectPort,
): CandidateScreening.Dropped? =
    when (val outcome = watchSubjects.subjectFor(notice)) {
        is WatchSubjectOutcome.Unavailable -> {
            CandidateScreening.Dropped(EvaluationStage.WatchGate, EvaluationDropReason.WatchSubjectUnavailable)
        }

        is WatchSubjectOutcome.Found -> {
            watchVerdictDrop(strategy.watchRules.evaluate(outcome.subject))
        }
    }

/**
 * [WatchVerdict]가 통과가 아닌 갈래를 드롭으로 옮긴다. **`NoGate`는 통과가 아니다.**
 * legacy는 이 상태에서 스캔 자체를 하지 않았다(`_has_configured_watch_rules`
 * 게이트) — 결과(후보 0)를 그대로 두고 탈락만 값으로 남긴다.
 */
private fun watchVerdictDrop(verdict: WatchVerdict): CandidateScreening.Dropped? =
    when (verdict) {
        is WatchVerdict.Rejected -> {
            CandidateScreening.Dropped(EvaluationStage.WatchGate, EvaluationDropReason.WatchGateRejected(verdict))
        }

        is WatchVerdict.Undeterminable -> {
            CandidateScreening.Dropped(EvaluationStage.WatchGate, EvaluationDropReason.WatchGateUndeterminable(verdict))
        }

        is WatchVerdict.NoGate -> {
            CandidateScreening.Dropped(EvaluationStage.WatchGate, EvaluationDropReason.WatchGateNotConfigured(verdict))
        }

        is WatchVerdict.Passed -> {
            null
        }
    }

private fun licenseScreening(
    notice: Notice,
    licenseGate: LicenseGatePort,
): CandidateScreening =
    when (val verdict = licenseGate.verdictFor(notice)) {
        is LicenseVerdict.Ineligible -> {
            CandidateScreening.Dropped(EvaluationStage.LicenseGate, EvaluationDropReason.LicenseIneligible(verdict))
        }

        // Uncertain은 미보유가 아니다(1C U-5) — 통과한다. Eligible도 통과.
        is LicenseVerdict.Eligible, is LicenseVerdict.Uncertain -> {
            CandidateScreening.Passed(verdict)
        }
    }
