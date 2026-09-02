package bidvector.buildlogic

import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

/**
 * 해석된 의존 그래프를 `group:name` → `version` 으로 편다. 게이트(무엇이 들어왔는가)와
 * 스모크(무슨 버전으로 해석됐는가)가 같은 그래프를 다르게 읽으므로 순회는 한 자리에 둔다.
 */
internal fun resolvedModules(roots: List<ResolvedComponentResult>): Map<String, String> {
    val visited = mutableSetOf<ResolvedComponentResult>()
    val modules = sortedMapOf<String, String>()

    fun visit(node: ResolvedComponentResult) {
        if (!visited.add(node)) return
        node.moduleVersion?.let { modules["${it.group}:${it.name}"] = it.version }
        node.dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .forEach { visit(it.selected) }
    }

    roots.forEach(::visit)
    return modules
}
