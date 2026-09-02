package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * 경계의 **1차 강제**(ADR 0006 D-3 — *"의존 선언이 1차 강제 수단"*). architecture test 는 실제로
 * 참조된 타입만 보므로 두 가지를 놓친다: 들어왔지만 아직 쓰지 않은 프레임워크, 그리고 **선언만
 * 되고 참조는 없는 업무 모듈 사이의 project 의존**. 이 게이트가 그 둘을 의존 그래프에서 잡는다.
 */
abstract class ModuleDependencyGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    /** main 쪽 해석 가능한 configuration 전건. `runtimeOnly`·`annotationProcessor` 도 포함된다. */
    @get:Input
    abstract val graphs: ListProperty<ResolvedComponentResult>

    /** test 쪽. 판정 기준이 다르다 — 도구 의존이 정상이므로 deny 를 쓴다. */
    @get:Input
    abstract val testGraphs: ListProperty<ResolvedComponentResult>

    @get:Input
    abstract val moduleName: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val policy = ModuleDependencyPolicy.load(policyFile.get().asFile)
        val module = moduleName.get()
        val mainResolved = resolveDependencies(graphs.get())
        val testResolved = resolveDependencies(testGraphs.get())
        val resolved =
            ResolvedDependencies(
                mainResolved.externalModules + testResolved.externalModules,
                mainResolved.projectPaths + testResolved.projectPaths,
            )
        val violations =
            policy.violations(module, resolved) + policy.mainExternalViolations(module, mainResolved)

        writeReport(module, policy, resolved)

        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    prefix = "모듈 '$module' 의 의존이 경계를 넘는다 (ADR 0006 D-3·D-4):\n  ",
                    separator = "\n  ",
                ),
            )
        }
    }

    private fun writeReport(
        module: String,
        policy: ModuleDependencyPolicy,
        resolved: ResolvedDependencies,
    ) {
        val lines =
            listOf(
                "module=$module",
                "layer=${policy.layerOf(module) ?: "(층 밖)"}",
                "allowed-projects=${policy.allowedProjectDependencies(module).sorted()}",
                "projects=${resolved.projectPaths.sorted()}",
                "external=${resolved.externalModules.size}",
            ) + resolved.externalModules.map { (id, version) -> "$id:$version" }
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            lines.joinToString(separator = "\n", postfix = "\n"),
        )
    }
}
