package bidvector.workflow.evaluation

import bidvector.decision.Verdict
import bidvector.procurement.NoticeId
import bidvector.workflow.event.CorrelationId

/**
 * 공고 하나의 평가 결과(scope.md ①, 설계 검토 (4) 1) — 「판정에 이르렀다」([Reached],
 * 4B-1 [Verdict]를 싣는다)와 「이르지 못했다」([NotReached], 단계 + 사유)로 가른다.
 * 둘 다 값이고 `null`이 아니다 — 소비자는 소진 `when`으로만 마주친다(legacy 실패
 * 형태 ⑥ 「조용한 드롭」의 뒤집기).
 *
 * 생성자는 전부 `internal` + `@ConsistentCopyVisibility`(4A `AppliedStrategy`·4B-1
 * `Verdict` 관례, 설계 검토 (2) 1행 — 「생성자는 닫는다: 밖에서 지으면 '판정했다'를
 * 위조」). 유일한 생성 경로는 [EvaluateCandidatesUseCase]다.
 */
sealed interface CandidateEvaluation {
    val noticeId: NoticeId
    val correlationId: CorrelationId

    @ConsistentCopyVisibility
    data class Reached internal constructor(
        override val noticeId: NoticeId,
        override val correlationId: CorrelationId,
        val verdict: Verdict,
    ) : CandidateEvaluation

    @ConsistentCopyVisibility
    data class NotReached internal constructor(
        override val noticeId: NoticeId,
        override val correlationId: CorrelationId,
        val stage: EvaluationStage,
        val reason: EvaluationDropReason,
    ) : CandidateEvaluation
}
