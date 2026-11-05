package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * ADR 0007 `OPEN-ADR-07` — mutation 점수·변이 수·변이 클래스 수의 절대값 래칫.
 *
 * 판정은 전부 [mutationGateViolations] 가 하고 이 class 는 입력을 모은다. **pitest 자신의
 * `mutationThreshold` 에 기대지 않는다** — 그쪽은 기본값(0) 그대로 두고 리포트에서 다시 센다.
 */
abstract class MutationReportGateTask : DefaultTask() {
    @get:Input
    abstract val moduleName: org.gradle.api.provider.Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    /**
     * pitest 의 XML 리포트. **`@InputFile` 이 아니라 `@Internal` 이다** — `@InputFile` 이면
     * task 실행 전에 Gradle 이 파일 존재를 검증해 "Input file does not exist" 로 죽고,
     * [mutationGateViolations] 의 「존재하지 않는다」 가지가 프로덕션 배선에서 닿지 못하는
     * 죽은 가지가 된다(`cpdReportPresenceGate` 가 같은 실측으로 같은 선택을 했다). 순서는
     * 배선부의 명시적 `dependsOn(pitest)` 가 진다.
     */
    @get:Internal
    abstract val mutationXmlReport: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classDirectories: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val module = moduleName.get()
        val ratchet = MutationPolicy.load(policyFile.get().asFile).ratchet(module)
        val compiled =
            classDirectories.files
                .filter(File::isDirectory)
                .flatMap(::classNamesIn)
                .toSet()
        val violations =
            mutationGateViolations(
                moduleName = module,
                report = mutationXmlReport.get().asFile,
                ratchet = ratchet,
                compiledClasses = compiled,
            )

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (listOf("module=$module", "ratchet=$ratchet", "compiledClasses=${compiled.size}") + violations)
                .joinToString(separator = "\n", postfix = "\n"),
        )

        if (violations.isNotEmpty()) {
            throw GradleException(
                "mutation 래칫 위반 — 테스트가 약해졌거나 변이 대상이 줄었다(ADR 0007 OPEN-ADR-07):\n" +
                    violations.joinToString(separator = "\n") { "  $it" },
            )
        }
    }

    /** class 파일의 **위치**가 이름이다 — 소스 선언을 믿지 않는다(`packageOwnershipGate` 관례). */
    private fun classNamesIn(root: File): List<String> =
        root
            .walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map {
                it
                    .relativeTo(root)
                    .path
                    .removeSuffix(".class")
                    .replace(File.separatorChar, '.')
            }.toList()
}
