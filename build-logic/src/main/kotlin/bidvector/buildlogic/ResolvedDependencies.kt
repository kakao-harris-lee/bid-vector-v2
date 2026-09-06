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
 *
 * **`ProjectComponentIdentifier`는 "project 의존"과 동의어가 아니다**(M2/2A 리뷰 r3 ⓕ).
 * composite 빌드(included build) 치환 의존 — 예: `adapters`의
 * `testImplementation("bidvector:ml-contract")` — 도 해석 후에는 `ProjectComponentIdentifier`로
 * 온다. `id`만 보고 전부 `projectPaths`에 접으면 다른 빌드의 project 가 이 모듈 **자신의**
 * project 의존(같은 build, 업무 모듈 간 직접 참조 금지 대상)과 구별되지 않는다 — 그 결과
 * `ml-contract` 같은 좌표가 `ModuleDependencyPolicy.allowedProjectDependencies` 밖이라는 이유로
 * 게이트가 실패한다(현 코드 재현: `adapters:moduleDependencyGate`). **같은 build 안의
 * project 인지**(`id.build`가 이 그래프의 root 가 속한 build 와 같은지)로 갈라 다른 build 의
 * project 는 `externalModules`(`group:name`)로 분류한다 — 예외가 아니라 분류의 정정이다.
 */
internal class ResolvedDependencies(
    val externalModules: Map<String, String>,
    val projectPaths: Set<String>,
)

internal fun resolveDependencies(roots: List<ResolvedComponentResult>): ResolvedDependencies {
    val visited = mutableSetOf<ResolvedComponentResult>()
    val external = sortedMapOf<String, String>()
    val projects = sortedSetOf<String>()

    // roots 는 이 모듈 자신의 해석 가능한 configuration 들의 root component 다 — 그 id 가
    // "이 모듈이 속한 build"를 알려준다(root 자신은 project 의존이 아니라 자기 자신이다,
    // 아래에서 제거). 여러 configuration 의 root 이므로 둘 이상일 수 있지만 전부 같은 build 다.
    val ownBuilds = roots.mapNotNull { (it.id as? ProjectComponentIdentifier)?.build }.toSet()

    fun visit(node: ResolvedComponentResult) {
        if (!visited.add(node)) return
        val id = node.id
        val sameBuildProject = id is ProjectComponentIdentifier && id.build in ownBuilds
        when {
            sameBuildProject -> projects += (id as ProjectComponentIdentifier).projectPath
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
