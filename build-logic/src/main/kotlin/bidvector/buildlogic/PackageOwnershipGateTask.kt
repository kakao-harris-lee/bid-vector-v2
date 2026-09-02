package bidvector.buildlogic

import com.tngtech.archunit.core.importer.ClassFileImporter
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
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

    /**
     * source set 이름 집합. `sourceSets.create("extra")` 로 만든 출력은 `main.output` 이 아니라
     * 소유 검사를 비껴가므로, **집합 자체를 고정**해 그 경로를 닫는다.
     */
    @get:org.gradle.api.tasks.Input
    abstract val expectedSourceSets: SetProperty<String>

    @get:org.gradle.api.tasks.Input
    abstract val actualSourceSets: SetProperty<String>

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

        val unexpected = actualSourceSets.get().toSortedSet() - expectedSourceSets.get()
        val violations =
            policy.violations(module, observed) +
                unexpected.map { "예상 밖 source set '$it' — 그 출력은 소유 검사를 비껴간다" } +
                foreignOrigin(module)
        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    prefix = "모듈 '$module' 이 남의 패키지에 클래스를 낸다 (ADR 0006 §6 의 1A 결정):\n  ",
                    separator = "\n  ",
                ),
            )
        }
    }

    /**
     * **class 의 원산지 검사.** source set 을 안 거치는 경로(손으로 만든 `JavaCompile`,
     * `destinationDirectory` 리다이렉트)는 소스 쪽 검사로 잡히지 않는다. Kotlin 컴파일러는
     * 모든 산출 클래스에 `kotlin.Metadata` 를 단다 — 그것이 없는 클래스는 Kotlin 이 만든 것이
     * 아니다. (표본 실측: value class·합성 `WhenMappings` 까지 전건 보유.)
     */
    private fun foreignOrigin(module: String): List<String> {
        val roots =
            classDirectories.files.filter { dir ->
                dir.isDirectory && dir.walkTopDown().any { it.extension == "class" }
            }
        if (roots.isEmpty()) return emptyList()
        return ClassFileImporter()
            .importPaths(roots.map { it.toPath() })
            .filterNot { it.isAnnotatedWith("kotlin.Metadata") }
            .map { "'${it.name}' 에 kotlin.Metadata 가 없다 — Kotlin 이 만든 산출물이 아니다 (모듈 '$module')" }
            .sorted()
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
