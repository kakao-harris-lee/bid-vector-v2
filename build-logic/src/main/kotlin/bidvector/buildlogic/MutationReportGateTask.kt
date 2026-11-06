package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
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
     * pitest 의 XML 리포트 — **판정의 유일한 근거**이고 그래서 **task 지문에 들어간다.** 이
     * 선언 하나가 메시지에 쓰이는 경로와 up-to-date 지문을 **함께** 낸다.
     *
     * `@InputFiles` 인 것이 두 가지를 한 자리에서 지킨다.
     *
     * 1. **부재를 허용한다.** `@InputFile` 이면 task 실행 전에 Gradle 이 파일 존재를 검증해
     *    "Input file does not exist" 로 죽고, [mutationGateViolations] 의 「존재하지 않는다」
     *    가지가 프로덕션 배선에서 닿지 못하는 죽은 가지가 된다(`cpdReportPresenceGate` 가 같은
     *    실측으로 같은 선택을 했다). 순서는 배선부의 명시적 `dependsOn(pitest)` 가 진다.
     * 2. **지문이 배선의 둘째 줄에 기대지 않는다.** 앞 판은 지문용 property 를 따로 두고
     *    convention 이 한 값에서 둘을 채웠다 — 그 **한 줄을 지우면** TestKit fixture 는 두
     *    property 를 스스로 묶으므로 test 가 초록인데, 프로덕션 증분 빌드는 「main 산출물·정책은
     *    그대로이고 test 만 약해진」 경우, 즉 래칫이 존재하는 이유 그 자체에 **UP-TO-DATE 로
     *    건너뛰고 exit 0** 이 됐다(리포트 15/73 으로 재현). 지금은 경로를 주는 선언과 지문을
     *    만드는 선언이 **같은 것**이라 반쪽만 배선된 상태가 성립하지 않는다.
     *
     * `upToDateWhen { false }` 로도 같은 거짓 초록을 막을 수 있지만 그쪽은 **근거가 그대로일
     * 때도** 매 빌드 다시 돌린다 — 래칫이 원하는 것은 「항상 재실행」이 아니라 「근거가 바뀌면
     * 재실행」이고, 그 차이를 `MutationReportGateUpToDateTest` 의 음성 대조가 잠근다.
     *
     * `@SkipWhenEmpty` 를 붙이지 않는다: 붙이면 리포트 부재가 `NO-SOURCE` 가 되어 「존재하지
     * 않는다」 위반이 다시 침묵한다.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mutationXmlReport: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classDirectories: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val module = moduleName.get()
        val policy = MutationPolicy.load(policyFile.get().asFile)
        val ratchet = policy.ratchet(module)
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
            (
                listOf(
                    "policyVersion=${policy.version}",
                    "module=$module",
                    "ratchet=$ratchet",
                    "compiledClasses=${compiled.size}",
                ) + violations
            ).joinToString(separator = "\n", postfix = "\n"),
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
