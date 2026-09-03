package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * **컴파일러가 먹는 소스는 source set 안에 갇혀 있어야 한다.**
 *
 * 형식·크기·언어 도구는 전부 source set 을 입력으로 삼는다. 그래서 컴파일 task 에 source set
 * 밖 파일을 직접 얹으면 그 파일만 모든 도구를 비껴간다. 도구마다 입력을 손으로 넓히는 방식은
 * **도구 수만큼 반복해야 하고 하나를 빠뜨리면 그대로 구멍**이다(ktlint 가 그랬다).
 *
 * 그래서 넓히는 대신 **좁힌다** — 두 집합의 차집합이 비어 있기를 요구하면 컴파일 대상이
 * source set 안에 갇히고, source set 을 보는 도구 전부가 자동으로 같은 집합을 본다.
 *
 * 두 입력 모두 **configuration 시점 Provider 로 선언**한다. task action 에서 task graph 를
 * 라이브로 읽으면 configuration cache 를 위반한다.
 */
abstract class CompilerSourceContainmentGateTask : DefaultTask() {
    /** 컴파일 task 가 실제로 먹는 소스. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compilerSources: ConfigurableFileCollection

    /** 형식·크기·언어 도구가 보는 집합 — source set 의 Kotlin 소스. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceSetSources: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val declared = sourceSetSources.files.map(File::getCanonicalPath).toSet()
        val escaped =
            compilerSources.files
                .filter { it.canonicalPath !in declared }
                .map(File::getPath)
                .sorted()

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (listOf("declared=${declared.size}", "compiled=${compilerSources.files.size}") + escaped)
                .joinToString(separator = "\n", postfix = "\n"),
        )

        if (escaped.isNotEmpty()) {
            throw GradleException(
                escaped.joinToString(
                    prefix = "컴파일 대상이 source set 밖에 있다 — 형식·크기·언어 도구가 그 파일을 보지 못한다:\n  ",
                    separator = "\n  ",
                    postfix = "\n소스를 source set 에 등록하라(`kotlin.srcDir(...)`) — 컴파일 task 에 직접 얹지 않는다.",
                ),
            )
        }
    }
}
