package bidvector.decision.priority

import bidvector.decision.UnitScore
import java.math.BigDecimal

/**
 * [SemanticMatch.of] 결과(scope.md ④) — 판정은 sealed, `require` 가 아니다(4D-1 G-1).
 * [DimensionMismatch]·[OffsetOutOfRange] 는 `notice`·`profile`·`categoryOffset` 이
 * 각자 유효해도 그 **조합**에서 날 수 있는 정상 상태다 — 예외로 새지 않는다.
 */
sealed interface MatchOutcome {
    data class Matched(
        val score: UnitScore,
    ) : MatchOutcome

    data object DimensionMismatch : MatchOutcome

    data object OffsetOutOfRange : MatchOutcome
}

/**
 * 의미 매치 점수(scope.md ④, 조사 §1.1 `matched_score`
 * `_apply_category_priority_override(score, override*0.5)`, `round(clamp01(score+off),2)`).
 * 코사인 = 내적(L2 전제, 2E `embedding.proto`) — `notice`·`profile` 둘 다 [UnitVector] 라
 * 이미 정규화·차원>0 이 검증된 값이다.
 *
 * 이 함수는 **둘의 조합**만 판정한다 — 차원이 다르면 [MatchOutcome.DimensionMismatch],
 * `categoryOffset` 이 정책 범위([PriorityPolicyData.categoryOffsetMin]·`Max`) 밖이면
 * [MatchOutcome.OffsetOutOfRange](조용한 clamp 아님 — D-4B4 위협 모델 (e)). 둘 다
 * 통과하면 `clamp01(cosine + offset)`을 [UnitScore] 로 낸다.
 */
object SemanticMatch {
    fun of(
        notice: UnitVector,
        profile: UnitVector,
        categoryOffset: BigDecimal,
        policy: PriorityPolicyData,
    ): MatchOutcome =
        when {
            notice.values.size != profile.values.size -> MatchOutcome.DimensionMismatch
            offsetOutOfRange(categoryOffset, policy) -> MatchOutcome.OffsetOutOfRange
            else -> MatchOutcome.Matched(UnitScore(scoreOf(notice, profile, categoryOffset)))
        }

    private fun offsetOutOfRange(
        categoryOffset: BigDecimal,
        policy: PriorityPolicyData,
    ): Boolean = categoryOffset < policy.categoryOffsetMin || categoryOffset > policy.categoryOffsetMax

    private fun scoreOf(
        notice: UnitVector,
        profile: UnitVector,
        categoryOffset: BigDecimal,
    ): BigDecimal = clamp01(BigDecimal.valueOf(cosineOf(notice, profile)).add(categoryOffset))

    private fun cosineOf(
        notice: UnitVector,
        profile: UnitVector,
    ): Double = notice.doubles.indices.fold(0.0) { acc, i -> acc + notice.doubles[i] * profile.doubles[i] }
}
