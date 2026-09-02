package bidvector.buildlogic

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

/**
 * 해석된 의존 그래프를 두 갈래로 편다.
 *
 * **project 의존을 함께 걷는 것이 요점이다.** `moduleVersion` 만 보면 Gradle project 의존이
 * 통째로 빠지고, 그러면 업무 모듈 사이의 직접 참조 선언이 1 차 게이트를 그냥 지나간다
 * (ADR 0006 D-3·D-4 가 그 선언을 1 차 강제로 둔다).
 */
internal class ResolvedDependencies(
    val externalModules: Map<String, String>,
    val projectPaths: Set<String>,
)

internal fun resolveDependencies(roots: List<ResolvedComponentResult>): ResolvedDependencies {
    val visited = mutableSetOf<ResolvedComponentResult>()
    val external = sortedMapOf<String, String>()
    val projects = sortedSetOf<String>()

    fun visit(node: ResolvedComponentResult) {
        if (!visited.add(node)) return
        when (val id = node.id) {
            is ProjectComponentIdentifier -> projects += id.projectPath
            else -> node.moduleVersion?.let { external["${it.group}:${it.name}"] = it.version }
        }
        node.dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .forEach { visit(it.selected) }
    }

    roots.forEach(::visit)
    // root 자신은 그 모듈이다 — 자기 자신은 의존이 아니다.
    roots.mapNotNull { (it.id as? ProjectComponentIdentifier)?.projectPath }.forEach(projects::remove)
    return ResolvedDependencies(external, projects)
}
