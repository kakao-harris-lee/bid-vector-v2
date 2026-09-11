package bidvector.workflow.evaluation

import bidvector.procurement.NoticeStatus
import bidvector.qualification.LicenseVerdict
import bidvector.strategy.WatchVerdict
import java.math.BigDecimal

/**
 * 판정에 이르지 못한 사유(scope.md ⑥, 설계 검토 (4) 2) — **탈락 사유는 기존 축이
 * 소유한다.** `WatchVerdict`(1E)·`LicenseVerdict`(1C)·`NoticeStatus`(3A)를 그대로
 * 싣는다 — 새 어휘로 복제하지 않는다.
 *
 * **어느 축도 소유하지 않은 것은 여섯이다(verifier r1 L-2 정정 — 이전 판은 「넷」이라
 * 적었으나 재산출하니 여섯이었다)**: [WatchSubjectUnavailable]·
 * [ActionThresholdsNotConfigured]·[AnalysisBudgetExhausted]·
 * [SimilarityProjectionNotReady]·[BelowMinimumMatchScore]·
 * [BelowMinimumProbabilityScore]. [WatchGateNotConfigured]는 여섯에 들지 않는다 —
 * `WatchVerdict.NoGate`(1E)를 그대로 싣는다(D-1, 수정 라운드 1 M-1).
 */
sealed interface EvaluationDropReason {
    /** 공고가 입찰 가능 상태가 아니다(3A `NoticeStatus` — D-2). */
    data class NoticeNotBiddable(
        val status: NoticeStatus,
    ) : EvaluationDropReason

    /**
     * 감시 규칙이 하나도 설정돼 있지 않다(1E `WatchVerdict.NoGate` 그대로 — D-1,
     * 수정 라운드 1 M-1). **운영자 결정 2026-09-10** — 이전 판은 `NoGate`를 통과로
     * 재해석했으나 되돌린다. legacy는 이 상태에서 스캔 자체를 하지 않았다
     * (`_has_configured_watch_rules` 게이트, `_workspace/m4-4b2/01_scout_composition.md`
     * §2.2 단계 8). 결과는 legacy와 같다(후보 0) — 다른 점은 탈락이 값으로
     * 남는다는 것뿐이다(이 slice ⑥의 목적).
     */
    data class WatchGateNotConfigured(
        val noGate: WatchVerdict.NoGate,
    ) : EvaluationDropReason

    /** 감시 필드 거절(1E `WatchVerdict.Rejected` 그대로 — D-3~D-8). */
    data class WatchGateRejected(
        val rejected: WatchVerdict.Rejected,
    ) : EvaluationDropReason

    /** 감시 축 판정 불가(1E `WatchVerdict.Undeterminable` 그대로 — D-8 기초금액 결측 등). */
    data class WatchGateUndeterminable(
        val undeterminable: WatchVerdict.Undeterminable,
    ) : EvaluationDropReason

    /** 감시 predicate 입력(원문 텍스트)을 조립하지 못했다(신설 — port I/O 경계, 어느 축도 다루지 않는다). */
    data object WatchSubjectUnavailable : EvaluationDropReason

    /** 보유 면허로 자격 미달(1C `LicenseVerdict.Ineligible` 그대로 — D-9). */
    data class LicenseIneligible(
        val verdict: LicenseVerdict.Ineligible,
    ) : EvaluationDropReason

    /**
     * 운영자 전략에 사다리 임계(`bidNowThreshold`·`reviewThreshold`)가 설정돼 있지
     * 않다(신설 — `ActionThresholds`는 이 값을 「미설정」으로 나를 수 있고
     * `VerdictLadderPolicyData`는 그 상태를 표현하지 못한다, 사다리를 돌릴 입력 자체가
     * 없다는 뜻이라 어느 기존 축도 이 상태를 소유하지 않는다).
     */
    data object ActionThresholdsNotConfigured : EvaluationDropReason

    /** 분석 예산 소진 — 「보류」가 아니라 「평가하지 않았다」(D-10, 신설). */
    data object AnalysisBudgetExhausted : EvaluationDropReason

    /** 유사도 projection 미준비 — 일시적 부재(D-11, 신설). */
    data object SimilarityProjectionNotReady : EvaluationDropReason

    /**
     * 적합도가 운영자 최소치 미만(D-12, 신설). 값은 `bidvector.strategy.MatchScore`를
     * 다시 싣지 않고 원시 [BigDecimal]로 나른다 — `bidvector.strategy.Score`의
     * 생성자·factory가 모듈 밖(`workflow`)에 닫혀 있어 이 모듈은 그 타입을 만들 수
     * 없다(1E 설계 검토 확인).
     */
    data class BelowMinimumMatchScore(
        val threshold: BigDecimal,
        val actual: BigDecimal,
    ) : EvaluationDropReason

    /** 가격 적합도가 운영자 최소치 미만(D-13, 신설 — 같은 이유로 원시 [BigDecimal]). */
    data class BelowMinimumProbabilityScore(
        val threshold: BigDecimal,
        val actual: BigDecimal,
    ) : EvaluationDropReason
}
