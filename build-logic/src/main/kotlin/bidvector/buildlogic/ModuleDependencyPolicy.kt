package bidvector.buildlogic

import java.io.File

/**
 * 모듈이 무엇에 의존해도 되는지의 판정. **순수 함수라 테스트가 붙는다** — Gradle 없이
 * 「업무 모듈이 서로를 참조하면 위반」을 단언할 수 있다.
 *
 * 허용 집합을 목록으로 적지 않고 층 모델에서 **유도한다**. 모듈이 늘 때 표를 손보지 않아도
 * 규칙이 그대로 적용되고, 표와 규칙이 어긋날 자리가 없다.
 * - 자기보다 **아래 층의 모든 모듈** (ADR 0006 D-3, `domain <- application <- adapters/app`)
 * - 같은 층에서는 **domain 의 공유 모듈만** (D-4, 업무 모듈 직접 참조 금지)
 */
internal class ModuleDependencyPolicy(
    private val values: Map<String, String>,
) {
    private val layerOrder: List<String> = list("layer.order")
    private val modulesByLayer: Map<String, List<String>> =
        layerOrder.associateWith { layer -> list("layer.$layer") }
    private val shareable: Set<String> = list("layer.domain.shareable").toSet()

    val forbiddenGroups: List<String> get() = list("group.forbidden")
    val allowedSubtrees: List<String> get() = list("package.allowed.subtree")
    val allowedExactPackages: List<String> get() = list("package.allowed.exact")
    val forbiddenClasses: List<String> get() = list("class.forbidden")

    fun layerOf(module: String): String? = modulesByLayer.entries.firstOrNull { (_, modules) -> module in modules }?.key

    fun isDomain(module: String): Boolean = layerOf(module) == DOMAIN_LAYER

    fun allowedProjectDependencies(module: String): Set<String> {
        val layer = layerOf(module) ?: return emptySet()
        val lowerLayers = layerOrder.takeWhile { it != layer }
        val fromBelow = lowerLayers.flatMap { modulesByLayer.getValue(it) }
        val sameLayer = if (layer == DOMAIN_LAYER) shareable - module else emptySet()
        return (fromBelow + sameLayer).toSet()
    }

    /** 위반을 사람이 읽을 수 있는 줄로 낸다. 빈 목록이 통과다. */
    fun violations(
        module: String,
        dependencies: ResolvedDependencies,
    ): List<String> = projectViolations(module, dependencies.projectPaths) + groupViolations(module, dependencies)

    private fun projectViolations(
        module: String,
        projectPaths: Set<String>,
    ): List<String> {
        val allowed = allowedProjectDependencies(module)
        return projectPaths
            .map { it.removePrefix(":") }
            .filterNot { it in allowed }
            .map { "project 의존 ':$it' — '$module' 에 허용된 것은 ${allowed.sorted()}" }
    }

    private fun groupViolations(
        module: String,
        dependencies: ResolvedDependencies,
    ): List<String> {
        if (!isDomain(module)) return emptyList()
        return dependencies.externalModules.keys
            .filter { id -> forbiddenGroups.any { group -> id.startsWith(group) } }
            .map { "금지 group '$it' — domain 모듈은 프레임워크를 볼 수 없다" }
    }

    private fun list(key: String): List<String> =
        (values[key] ?: error("아키텍처 정책에 '$key' 가 없다"))
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)

    companion object {
        private const val DOMAIN_LAYER = "domain"

        fun load(file: File): ModuleDependencyPolicy = ModuleDependencyPolicy(readPolicy(file))
    }
}
