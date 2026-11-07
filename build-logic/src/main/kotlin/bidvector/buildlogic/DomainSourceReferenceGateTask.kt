package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

private val SOURCE_EXTENSIONS = setOf("kt", "kts")

/**
 * **소스 층 게이트 — 세 축**이고 판정은 [SourceAxisViolations]·[SourceReferencePolicy] 가 갖는다.
 * 이 층은 **이름**을 보고 바이트코드 층은 **결합**을 본다(설계 검토 §0 「두 층의 분담」).
 *
 * ⓐ domain main 소스가 허용 목록 밖 좌표를 이름으로 부르는지 — ArchUnit(바이트코드)과 의존 그래프
 * (1차) 게이트가 함께 놓치는 자리(컴파일 시 인라인되는 상수 · class literal, 알려진 제한 7·11).
 * 모듈 선택은 이 task 등록이 아니라 [SourceReferencePolicy.isDomain] 이 한다.
 *
 * ⓑ adapters 네 게이트 축(evaluation·qualification·profile·audit)의 패키지 경계
 * (`OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT`) — 그 넷이 공유하는 사각을 같은 허용 뿌리로 **이름
 * 쪽에서** 덮는다. 축 선택도 정책이 한다(`source.scoped-packages`).
 *
 * ⓒ 실행 환경 스니핑 0(OPS-13) — 전 모듈 main 소스가 대상이다.
 *
 * `sourceRoot` 는 Gradle `SourceDirectorySet` 이 아니라 **관례 디렉터리를 직접 건다** — 파생시키면
 * `exclude(...)` 한 줄로 기준과 대상이 함께 사라진다(설계 검토 S-14, `sourceSetLayoutGate` 가
 * 10차에 배운 것과 같은 이유).
 */
abstract class DomainSourceReferenceGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:Input
    abstract val moduleName: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoot: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val policy = SourceReferencePolicy.load(policyFile.get().asFile)
        val module = moduleName.get()
        val files =
            sourceRoot.files
                .filter(File::isDirectory)
                .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension in SOURCE_EXTENSIONS } }
                .sortedBy(File::getPath)
        if (files.isEmpty()) {
            writeReport(module, files, SourceAxisVerdict.EMPTY)
            throw GradleException("모듈 '$module' 의 main 소스 트리가 비어 있다 — 게이트가 아무것도 보지 못했다")
        }

        val verdict =
            parseKotlinFiles(files) { ktFile, file ->
                listOf(SourceAxisViolations.judge(policy, module, ktFile, file.path))
            }.fold(SourceAxisVerdict.EMPTY, SourceAxisVerdict::plus)

        writeReport(module, files, verdict)
        if (verdict.violations.isNotEmpty()) {
            throw GradleException(
                verdict.violations.sorted().joinToString(
                    prefix = "소스가 이름으로 경계를 넘는다:\n  ",
                    separator = "\n  ",
                ),
            )
        }
    }

    private fun writeReport(
        module: String,
        files: List<File>,
        verdict: SourceAxisVerdict,
    ) {
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (
                listOf(
                    "module=$module",
                    "files=${files.size}",
                    "references=${verdict.references}",
                    "scopedFiles=${verdict.scopedFiles}",
                    "memberCalls=${verdict.memberCalls}",
                    "violations=${verdict.violations.size}",
                ) + verdict.violations.sorted()
            ).joinToString("\n", postfix = "\n"),
        )
    }
}
