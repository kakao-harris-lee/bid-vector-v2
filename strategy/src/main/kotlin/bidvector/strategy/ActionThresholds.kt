package bidvector.strategy

/**
 * 액션 임계치 넷(STR-03, ⑦) — 소비하지 않는다(사다리는 M4 4B). 전부 nullable이다 —
 * 「미설정」과 「값 0」은 다른 상태다(D-15 「설정됐는가」 술어가 이 nullable 위에 선다).
 * 교차 불변식(`review <= bidNow`)은 둘 다 설정됐을 때만 [bidvector.strategy.validate]가
 * 검사한다.
 */
data class ActionThresholds(
    val minimumMatchScore: MatchScore?,
    val minimumProbabilityScore: ProbabilityScore?,
    val bidNowThreshold: PriorityScore?,
    val reviewThreshold: PriorityScore?,
) {
    /** D-15 — 「전략이 설정됐는가」 술어가 참조하는 부분 술어. */
    fun hasAnyValue(): Boolean =
        minimumMatchScore != null ||
            minimumProbabilityScore != null ||
            bidNowThreshold != null ||
            reviewThreshold != null

    companion object {
        fun empty(): ActionThresholds = ActionThresholds(null, null, null, null)
    }
}

/** 추천 후보 수 표현 상한(⑦) — 양의 정수. 분석 예산과 결합하지 않는다(STR-06 「폐기」). */
data class CandidateLimit(
    val value: Int,
) {
    init {
        require(value > 0) { "CandidateLimit은 양수여야 한다: $value" }
    }
}
