package bidvector.workflow.retention

/**
 * 파기 순서 도출의 결과 — 순환이면 **순서가 없다**는 것을 값으로 말한다(예외가 아니라 값:
 * 호출부가 「실행하지 않는다」로 옮긴다).
 */
sealed interface DeletionOrderOutcome {
    data class Ordered(
        val tables: List<String>,
    ) : DeletionOrderOutcome

    /** 순환에 걸린 표들 — 위상 정렬이 끝내지 못하고 남은 집합이다. */
    data class Cyclic(
        val tables: Set<String>,
    ) : DeletionOrderOutcome
}

/**
 * 자식 → 부모 순서(설계 검토 우회 6) — **FK 간선에서 위상 정렬로 도출한다.** 손 순서를 두면
 * V19 가 FK 를 더할 때 조용히 틀린다: 부모를 먼저 지우면 FK 위반으로 run 이 중단되고 그
 * 중단은 「파기가 안 됐다」로 보이지 않는다(부분 파기 + 실패 종료).
 *
 * 입력 [tables] 밖의 간선은 무시한다 — 파기 대상이 아닌 부모(`raw_observation`)는 순서에
 * 영향이 없다. 정렬은 **입력 순서를 보존한다**(같은 깊이의 표가 실행마다 다른 순서로 돌지
 * 않게 — 보고와 로그가 안정적이어야 비교가 선다).
 */
internal fun deletionOrderOf(
    tables: List<String>,
    dependencies: Set<TableDependency>,
): DeletionOrderOutcome {
    val remaining = tables.toMutableList()
    val relevant = dependencies.filter { it.child in tables && it.parent in tables }
    val ordered = mutableListOf<String>()
    while (remaining.isNotEmpty()) {
        // 남은 표 가운데 「아직 남은 자식이 없는」 것부터 — 자식이 먼저 사라져야 부모를 지울 수 있다.
        val ready = remaining.filter { parent -> relevant.none { it.parent == parent && it.child in remaining } }
        if (ready.isEmpty()) return DeletionOrderOutcome.Cyclic(remaining.toSet())
        ordered += ready
        remaining -= ready.toSet()
    }
    return DeletionOrderOutcome.Ordered(ordered)
}
