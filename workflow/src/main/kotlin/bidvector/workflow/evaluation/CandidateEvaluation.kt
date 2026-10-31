package bidvector.workflow.evaluation

import bidvector.decision.PublishedFloorExclusion
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

    /**
     * DEC-10 계수(D-DEC10-5·D-DEC10-8) — 이 후보의 게시 하한율이 가격 경로에서 **제외됐다는
     * 사실**이다. 제외가 없었으면 `null` 이다.
     *
     * **두 가지에 다 있다**(r1 CR M-1). 앞 판은 [Reached] 에만 두고 「판정에 이르지 못한 후보에는
     * 이 축이 없다 — 분석을 돌지 않았다」고 적었는데, 그 문면은 거짓이었다: 최소 점수 드롭은
     * `MlAnalysisOutcome.Analyzed` 를 **받은 뒤** [NotReached] 로 떨어지는 자리라 분석도 제외
     * 계산도 이미 끝나 있다. 그 가지에서 계수를 버리면 「조용히 사라지는 것」을 막겠다는 이
     * slice 의 위협 모델 첫 문장이 그 자리에서만 성립하지 않는다.
     */
    val floorExclusion: PublishedFloorExclusion?

    @ConsistentCopyVisibility
    data class Reached internal constructor(
        override val noticeId: NoticeId,
        override val correlationId: CorrelationId,
        val verdict: Verdict,
        /**
         * 이 판정이 낳은 알림 요청의 처분(D-6F10-18 ⑤) — `BidNow` 가 아니면
         * [NotificationDisposition.NotApplicable] 이다. **`reach` 가 port 반환값을 버리지
         * 않는다**는 사실이 이 필드이고, 커밋 러너가 이 값으로 종료 코드를 정한다.
         *
         * dry-run 의 거동은 바뀌지 않는다 — `RecordingNotificationRequestPort` 는 늘
         * `Requested` 를 돌려주고, HTTP 응답은 이 필드를 읽지 않는다(D-6A3-6 평탄 스칼라
         * 규칙 그대로, `OPEN-6A3-EVALUATION-DETAIL`).
         */
        val disposition: NotificationDisposition,
        override val floorExclusion: PublishedFloorExclusion?,
    ) : CandidateEvaluation

    @ConsistentCopyVisibility
    data class NotReached internal constructor(
        override val noticeId: NoticeId,
        override val correlationId: CorrelationId,
        val stage: EvaluationStage,
        val reason: EvaluationDropReason,
        /** 분석을 돌지 못한 가지에서는 `null` 이고, 최소 점수 드롭에서는 계산된 사유가 실린다. */
        override val floorExclusion: PublishedFloorExclusion?,
    ) : CandidateEvaluation
}
