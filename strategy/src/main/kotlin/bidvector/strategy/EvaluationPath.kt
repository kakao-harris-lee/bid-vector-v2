package bidvector.strategy

/**
 * 감시/검색 경로 태그(D-2 (a), D-11, ⑩) — predicate의 입력이 **아니라** runner 투영의
 * 축이다. 커널 함수([WatchRules.evaluate])는 이 타입을 모른다 — 「두 경로가 같은 답」은
 * 이 태그가 만드는 것이 아니라 두 경로가 같은 함수(`WatchRules.evaluate` + `compareKnownVat`)를
 * 쓴다는 구조 자체가 보장한다(STR-16 acceptance, R-BASIS-01·02).
 */
sealed interface EvaluationPath {
    data object MonitoringFilter : EvaluationPath

    data object SearchQuery : EvaluationPath
}
