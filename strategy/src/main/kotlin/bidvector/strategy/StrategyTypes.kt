package bidvector.strategy

/** 전략 개정 번호(D-4 (a), ⑨) — 「전략이 바뀌었다」는 사실을 나르는 값. */
data class StrategyRevision(
    val value: Int,
) {
    init {
        require(value >= 0) { "StrategyRevision은 음수일 수 없다: $value" }
    }
}

/**
 * 운영자 전략 봉투(D-10, D-15) — 유일한 생성 경로는 [bidvector.strategy.validate]다
 * (`internal constructor`). 편집 경로마다 불변식을 재구현하던 legacy 형태(스카우트 §1.2,
 * 네 자리)를 구조적으로 폐기한다.
 */
@ConsistentCopyVisibility
data class OperatorStrategy internal constructor(
    val watchRules: WatchRules,
    val actionThresholds: ActionThresholds,
    val candidateLimit: CandidateLimit?,
    val revision: StrategyRevision,
)

/**
 * 「전략이 설정됐는가」(D-15, ⑥) — [WatchRules.isEmpty]와 다른 물음이다(STR-03 acceptance
 * 셋째 — 임계치만 바꾸고 watch 필드가 비어 있으면 이 술어는 참, `watchRules.isEmpty()`는
 * 참으로 서로 다른 값을 낸다는 것은 두 술어가 **다른 것을 본다**는 뜻이지 같은 입력에서
 * 항상 반대값이 나온다는 뜻이 아니다).
 */
fun OperatorStrategy.isConfigured(): Boolean =
    !watchRules.isEmpty() || actionThresholds.hasAnyValue() || candidateLimit != null
