package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.jar.JarFile

/**
 * **아카이브에 직접 넣은 클래스를 잡는다.**
 *
 * 소스 검사와 산출물 검사는 둘 다 「모듈이 컴파일한 것」을 본다. `jar { from("prebuilt") }` 는
 * 그 둘을 모두 비껴간다 — 소스가 없고 `output.classesDirs` 도 아니다. 그런데 **배포되는 것은
 * jar** 이므로 그 경로가 열려 있으면 앞의 두 검사가 지키는 것이 배포물이 아니게 된다.
 */
abstract class JarContentGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archives: ConfigurableFileCollection

    @get:org.gradle.api.tasks.Input
    abstract val moduleName: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val packageRoot = readPolicy(policyFile.get().asFile).requireValue("package.root")
        val module = moduleName.get()
        val owned = PackageOwnershipPolicy(packageRoot).ownedPackage(module).replace('.', '/')
        val entries = archives.files.filter { it.isFile }.flatMap(::classEntries)
        val offenders = entries.filterNot { it.startsWith("$owned/") }.sorted()

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (listOf("module=$module", "owned=$owned/", "entries=${entries.size}") + offenders)
                .joinToString(separator = "\n", postfix = "\n"),
        )

        if (offenders.isNotEmpty()) {
            throw GradleException(
                offenders.joinToString(
                    prefix = "아카이브에 소유 밖 클래스가 들어 있다 (모듈 '$module'):\n  ",
                    separator = "\n  ",
                ),
            )
        }
    }

    private fun classEntries(archive: java.io.File): List<String> =
        JarFile(archive).use { jar ->
            jar
                .entries()
                .asSequence()
                .filter { it.name.endsWith(".class") }
                .map { it.name }
                .toList()
        }
}
