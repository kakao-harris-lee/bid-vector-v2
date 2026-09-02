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
import java.io.File

/**
 * 모듈의 class output 전건이 그 모듈이 소유한 패키지 아래에 있는지 본다.
 * 판정은 [PackageOwnershipPolicy] 가 하고 이 task 는 산출물에서 패키지를 읽어 넘긴다.
 */
abstract class PackageOwnershipGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classDirectories: ConfigurableFileCollection

    @get:org.gradle.api.tasks.Input
    abstract val moduleName: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val packageRoot = readPolicy(policyFile.get().asFile).requireValue("package.root")
        val policy = PackageOwnershipPolicy(packageRoot)
        val module = moduleName.get()
        val observed = classDirectories.files.filter(File::isDirectory).flatMap(::packagesIn)

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (listOf("module=$module", "owned=${policy.ownedPackage(module)}") + observed.distinct().sorted())
                .joinToString(separator = "\n", postfix = "\n"),
        )

        val violations = policy.violations(module, observed)
        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    prefix = "모듈 '$module' 이 남의 패키지에 클래스를 낸다 (ADR 0006 §6 의 1A 결정):\n  ",
                    separator = "\n  ",
                ),
            )
        }
    }

    /** class 파일의 **위치**가 패키지다 — 소스의 `package` 선언을 믿지 않는다. */
    private fun packagesIn(root: File): List<String> =
        root
            .walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map {
                it.parentFile
                    .relativeTo(root)
                    .path
                    .replace(File.separatorChar, '.')
            }.toList()
}
