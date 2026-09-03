package bidvector.buildlogic

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

    /** 컴파일러가 실제로 먹은 소스. 그 **이름 집합**이 원산지 판정의 기준이다. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compilerSources: ConfigurableFileCollection

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
     * `destinationDirectory` 리다이렉트, `doLast` 복사)는 소스 쪽 검사로 잡히지 않는다.
     *
     * 판정은 class 의 **`SourceFile` 이 게이트를 통과한 소스 이름 집합에 드는가**다. 앞선 판은
     * `kotlin.Metadata` 보유를 앵커로 썼는데 **소스에 한 줄로 위조된다** — Codex 8차가
     * `@kotlin.Metadata` 를 단 Java 클래스로 그 층을 통과시켰다. `SourceFile` 은 컴파일러가 쓰고,
     * 무엇보다 **컴파일러가 먹은 파일 목록과 대조**되므로 위조만으로는 부족하다.
     */
    private fun foreignOrigin(module: String): List<String> {
        val verified = verifiedSourceNames(compilerSources.files)
        return classDirectories.files
            .filter(File::isDirectory)
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "class" } }
            .mapNotNull { file -> originViolation(file, verified, module) }
            .sorted()
    }

    private fun originViolation(
        file: File,
        verified: Set<String>,
        module: String,
    ): String? {
        val source = file.readBytes().sourceFileName()
        if (source != null && source in verified) return null
        val what = source?.let { "SourceFile '$it' 이 게이트를 통과한 소스가 아니다" } ?: "SourceFile 이 없다"
        return "'${file.name}' — $what (모듈 '$module')"
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
