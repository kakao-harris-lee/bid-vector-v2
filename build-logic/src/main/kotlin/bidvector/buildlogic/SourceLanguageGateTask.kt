package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * 소스 트리에 Kotlin 아닌 소스가 있으면 실패한다.
 *
 * convention plugin 이 `java.setSrcDirs(emptyList())` 로 이미 **구조적으로 봉쇄**했지만 그 봉쇄는
 * **조용하다** — `src/main/java/Leak.java` 를 넣어도 산출물이 생기지 않을 뿐 아무도 알려주지
 * 않는다. 이 게이트가 그것을 소리 나게 한다.
 *
 * **그래서 등록된 srcDir 만 보아서는 안 된다.** 봉쇄가 성공하면 그 파일은 어느 source set 에도
 * 속하지 않게 되고, srcDir 만 훑는 검사에는 **보이지 않는다**(실측으로 그 상태를 만났다).
 * 관례 트리(`src/`)와 등록된 srcDir 을 **함께** 훑는 이유다. Java 산출물이 `classes/java/main` 으로 새면
 * 소유·크기·경계 게이트가 통째로 비껴간다는 것이 그 봉쇄의 이유다.
 */
abstract class SourceLanguageGateTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectories: ConfigurableFileCollection

    /** 리소스 트리는 소스가 아니다 — 여기서 빼지 않으면 `.yml`·`.properties` 가 전부 걸린다. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val excludedDirectories: ConfigurableFileCollection

    @get:Input
    abstract val allowedExtensions: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val allowed = allowedExtensions.get()
        val excluded = excludedDirectories.files.map(File::getCanonicalPath)
        val offenders =
            sourceDirectories.files
                .filter(File::isDirectory)
                .flatMap { root -> root.walkTopDown().filter { it.isFile }.map { root to it } }
                .filterNot { (_, file) -> file.extension in allowed }
                .filterNot { (_, file) -> excluded.any { file.canonicalPath.startsWith("$it${File.separator}") } }
                .map { (root, file) -> file.relativeTo(root).path }
                .distinct()
                .sorted()

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (listOf("allowed=${allowed.sorted()}", "offenders=${offenders.size}") + offenders)
                .joinToString(separator = "\n", postfix = "\n"),
        )

        if (offenders.isNotEmpty()) {
            throw GradleException(
                offenders.joinToString(
                    prefix = "소스 트리에 Kotlin 이 아닌 소스가 있다 (게이트가 비껴간다):\n  ",
                    separator = "\n  ",
                ),
            )
        }
    }
}
