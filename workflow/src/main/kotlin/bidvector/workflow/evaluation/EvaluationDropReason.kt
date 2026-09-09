package bidvector.workflow.evaluation

import bidvector.procurement.NoticeStatus
import bidvector.qualification.LicenseVerdict
import bidvector.strategy.WatchVerdict
import java.math.BigDecimal

/**
 * 판정에 이르지 못한 사유(scope.md ⑥, 설계 검토 (4) 2) — **탈락 사유는 기존 축이
 * 소유한다.** `WatchVerdict`(1E)·`LicenseVerdict`(1C)·`NoticeStatus`(3A)를 그대로
 * 싣는다 — 새 어휘로 복제하지 않는다. 어느 축도 소유하지 않은 것(D-10~D-13, D-1의
 * 조사가 조사가 낸 목록에서 「어느 축도 없음」으로 판정한 넷)만 이 sealed가 최소로
 * 신설한다(조사가 낸 목록 — `_workspace/m4-4b1/01_scout_verdict_ladder.md` §3.3
 * 「`Skip`이 아닐 것 같은 것」 참고).
 */
sealed interface EvaluationDropReason {
    /** 공고가 입찰 가능 상태가 아니다(3A `NoticeStatus` — D-2). */
    data class NoticeNotBiddable(
        val status: NoticeStatus,
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
