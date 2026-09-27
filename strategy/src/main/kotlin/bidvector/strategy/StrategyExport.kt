package bidvector.strategy

/**
 * 현재 전략을 다시 편집 가능한 원시 초안으로 내보낸다(M6/6A-2b D-6A2b-2) — 필드 하나만
 * 바꾸는 편집(`EditCommand.ProvideValue`가 **전체** [StrategyDraft]를 요구한다)이 나머지
 * 필드를 지어내지 않고 현재 값 그대로 실어 보내려면 이 방향이 필요하다.
 *
 * **안전한 방향이다(획득이 아니다).** [OperatorStrategy]는 [validate]만 만들 수 있고
 * (`internal constructor`), [StrategyDraft]는 원래 누구나 자유롭게 만들 수 있는 원시
 * 입력이다 — 이 함수는 이미 정당한 값에서 원시 필드를 꺼낼 뿐이라 새 권한을 만들지
 * 않는다(`EditSession.toSnapshot()`이 같은 갈래).
 *
 * `revision`은 초안에 없다 — 시스템이 매기는 값이지 운영자 입력이 아니다([validate]의
 * 별도 인자, [StrategyDraft] KDoc).
 */
fun OperatorStrategy.toDraft(): StrategyDraft =
    StrategyDraft(
        focusCategories = watchRules.focusCategories.map(CategoryCode::value),
        focusRegionTerms = watchRules.focusRegionTerms,
        excludeRegionTerms = watchRules.excludeRegionTerms,
        requiredKeywordTerms = watchRules.requiredKeywordTerms,
        excludeKeywordTerms = watchRules.excludeKeywordTerms,
        minBudget = watchRules.budget.min,
        maxBudget = watchRules.budget.max,
        minimumMatchScore = actionThresholds.minimumMatchScore?.score?.value,
        minimumProbabilityScore = actionThresholds.minimumProbabilityScore?.score?.value,
        bidNowThreshold = actionThresholds.bidNowThreshold?.score?.value,
        reviewThreshold = actionThresholds.reviewThreshold?.score?.value,
        candidateLimit = candidateLimit?.value,
        maxActiveBids = maxActiveBids?.value,
    )
