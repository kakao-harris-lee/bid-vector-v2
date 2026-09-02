package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * **게이트가 적용되지 않은 모듈을 잡는다.**
 *
 * 지금까지의 게이트는 전부 `bidvector.kotlin-conventions` 안에 있다. 새 모듈이 그 plugin 대신
 * `kotlin("jvm")` 을 직접 적용하면 크기·소유·의존·언어·detekt·ktlint 가 **통째로 사라지고**
 * 아무것도 실패하지 않는다 — 게이트의 부재는 조용하다.
 *
 * 각 모듈이 이미 `qualityBaseline` 에 자기를 등록하므로 **그 등록 집합이 곧 「plugin 이 적용된
 * 모듈」**이다. 그것을 `subprojects` 와 대조한다.
 */
abstract class ConventionCoverageGateTask : DefaultTask() {
    @get:Input
    abstract val expectedModules: SetProperty<String>

    @get:Input
    abstract val registeredModules: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val expected = expectedModules.get().toSortedSet()
        val registered = registeredModules.get().toSortedSet()
        val missing = expected - registered
        val unexpected = registered - expected

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            listOf("expected=$expected", "registered=$registered", "missing=$missing")
                .joinToString(separator = "\n", postfix = "\n"),
        )

        if (missing.isNotEmpty() || unexpected.isNotEmpty()) {
            throw GradleException(
                "convention plugin 이 적용되지 않은 모듈이 있다 — 그 모듈에는 게이트가 없다.\n" +
                    "  적용 안 됨: $missing\n  등록됐으나 subproject 가 아님: $unexpected",
            )
        }
    }
}
