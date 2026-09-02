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
 * domain 모듈의 **1차 강제**(ADR 0006 D-3). architecture test 는 실제로 참조된 타입만 보므로
 * "의존은 들어왔는데 아직 안 쓴" 상태를 잡지 못한다. 이 게이트는 해석된 의존 그래프를 보고
 * 금지 group 이 classpath 에 오르는 순간 실패한다 — 도메인이 프레임워크를 **볼 수조차 없게** 한다.
 */
abstract class DomainDependencyGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:Input
    abstract val graphs: ListProperty<ResolvedComponentResult>

    @get:Input
    abstract val moduleName: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val policy = readPolicy(policyFile.get().asFile)
        val forbidden = policy.requireList("group.forbidden")
        val resolved = resolvedModules(graphs.get()).keys
        val offenders = resolved.filter { id -> forbidden.any { group -> id.startsWith(group) } }

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (listOf("module=${moduleName.get()}", "components=${resolved.size}") + resolved)
                .joinToString(separator = "\n", postfix = "\n"),
        )

        if (offenders.isNotEmpty()) {
            throw GradleException(
                offenders.joinToString(
                    prefix = "domain 모듈 '${moduleName.get()}' 의 classpath 에 금지 의존이 있다 (ADR 0006 D-3):\n  ",
                    separator = "\n  ",
                ),
            )
        }
    }
}
