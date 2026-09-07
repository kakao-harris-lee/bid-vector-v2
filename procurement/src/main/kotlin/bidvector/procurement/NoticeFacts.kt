package bidvector.procurement

import bidvector.sharedkernel.AllocatedBudget
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.Rate
import java.time.Instant

/**
 * 공고 fact + 상태(D-3A-1 (a)) — canonical fact 셋(`Notice`·[OpeningResult]·[QualificationText])
 * 중 상태를 보유하는 유일한 자리(`NoticeId`로 묶인다). `status` 변경은 [applyEvent] 하나뿐이다 —
 * `@ConsistentCopyVisibility` + `internal constructor`(1E 관례)가 `copy(status = ...)`로 전이표를
 * 우회하는 경로를 닫는다(우회 (5), verifier r1 F-5가 「대상 부재」로 지적한 자리를 채운다).
 */
@ConsistentCopyVisibility
data class Notice internal constructor(
    val id: NoticeId,
    val status: NoticeStatus,
    val businessCategory: BusinessCategory?,
    val baseAmount: ResolvedBaseAmount?,
    val estimatedAmount: ResolvedEstimatedAmount?,
    val allocatedBudget: AllocatedBudget?,
    val floorRate: FloorRate?,
    val deadlineAt: Instant?,
) {
    /** 유일한 상태 변경 경로 — 표 밖의 전이는 [NoticeTransitionOutcome.Rejected]로 관측된다. */
    fun applyEvent(event: NoticeEvent): NoticeTransitionOutcome =
        when (val result = transition(status, event)) {
            is TransitionResult.Moved -> NoticeTransitionOutcome.Applied(copy(status = result.to))
            is TransitionResult.Rejected -> NoticeTransitionOutcome.Rejected(result)
        }

    companion object {
        /** [NoticeCollected](②)로부터 최초 진입 — 항상 `Open`이다(§2.2.1 「(없음) → NoticeCollected → Open」). */
        fun collected(command: NoticeCollected): Notice =
            Notice(
                id = command.id,
                status = NoticeStatus.Open,
                businessCategory = command.businessCategory,
                baseAmount = command.baseAmount,
                estimatedAmount = command.estimatedAmount,
                allocatedBudget = command.allocatedBudget,
                floorRate = command.floorRate,
                deadlineAt = command.deadlineAt,
            )
    }
}

/** [Notice.applyEvent]의 결과 — 소진 `when`으로만 소비한다(거부를 조용히 삼키지 않는다). */
sealed interface NoticeTransitionOutcome {
    data class Applied(
        val notice: Notice,
    ) : NoticeTransitionOutcome

    data class Rejected(
        val result: TransitionResult.Rejected,
    ) : NoticeTransitionOutcome
}

/**
 * 개찰 결과 fact(D-3A-1 (a)) — `Notice`와 별도 fact다(수집 실패·시점 단위가 다르다, COL-03
 * 「공고 1건당 별도 호출」). 예정가는 **파생 필드 + provenance**만 나른다 — 역산 계산식은
 * 여기 없다(3D 소유, 「만들지 않는 것」).
 */
data class OpeningResult(
    val noticeId: NoticeId,
    val winningRate: Rate?,
    val derivedBaseAmount: ResolvedBaseAmount.DerivedFromOpeningAmount?,
    val observedAt: Instant,
)

/** 자격 원문 fact(D-3A-1 (a)) — 원문 파싱은 3B 소관이고, 여기는 원문 + 관측 시각만 보존한다. */
data class QualificationText(
    val noticeId: NoticeId,
    val rawText: String,
    val observedAt: Instant,
)
