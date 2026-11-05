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
     * pitest 의 XML 리포트 — **판정의 유일한 근거**다. 여기 담긴 경로가 메시지에 쓰이고,
     * 지문에는 아래 [mutationXmlReportFingerprint] 가 같은 값을 넣는다.
     *
     * **`@InputFile` 로 선언하지 않는다** — `@InputFile` 이면 task 실행 전에 Gradle 이 파일
     * 존재를 검증해 "Input file does not exist" 로 죽고, [mutationGateViolations] 의
     * 「존재하지 않는다」 가지가 프로덕션 배선에서 닿지 못하는 죽은 가지가 된다
     * (`cpdReportPresenceGate` 가 같은 실측으로 같은 선택을 했다). 순서는 배선부의 명시적
     * `dependsOn(pitest)` 가 진다.
     */
    @get:Internal
    abstract val mutationXmlReport: RegularFileProperty

    /**
     * 같은 리포트를 **task 지문에 넣는다.** 앞 판은 이 선언이 없어, 래칫이 존재하는 이유 그
     * 자체인 「main 산출물·정책은 그대로이고 test 만 약해진」 경우에 입력 지문이 같아
     * **UP-TO-DATE 로 건너뛰고 exit 0** 이 됐다(리포트 15/73 로 재현).
     *
     * **파일 컬렉션인 것이 핵심이다** — 컬렉션은 부재를 허용하므로 위 KDoc 이 `@InputFile` 을
     * 피한 이유가 그대로 지켜지면서 리포트가 지문에 들어온다. `upToDateWhen { false }` 로
     * 같은 거짓 초록을 막을 수도 있지만 그쪽은 **근거가 그대로일 때도** 매 빌드 다시 돌린다 —
     * 래칫이 원하는 것은 「항상 재실행」이 아니라 「근거가 바뀌면 재실행」이고, 그 차이를
     * `MutationReportGateUpToDateTest` 의 음성 대조가 잠근다.
     *
     * `@SkipWhenEmpty` 를 붙이지 않는다: 붙이면 리포트 부재가 `NO-SOURCE` 가 되어 「존재하지
     * 않는다」 위반이 다시 침묵한다.
     *
     * **배선은 둘을 한 값에서 채운다**(`mutation-conventions` 의 `pitestXmlReport` 지역 값) —
     * 두 property 지만 출처는 하나다. task 안에서 파생시키지 않는 이유는 `Task.project` 접근이
     * configuration cache 에서 금지돼 있기 때문이다.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mutationXmlReportFingerprint: ConfigurableFileCollection

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
