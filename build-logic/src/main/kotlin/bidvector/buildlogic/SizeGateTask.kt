package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * 크기 래칫. **파일 축과 함수 축을 둘 다 든다.**
 *
 * 함수 축은 Kotlin PSI 로 직접 잰다 — detekt 을 경유하지 않으므로 `@Suppress` 가 어떤 표기든
 * (한 줄·다중 행·`@file:`·`@kotlin.`) 이 임계에 영향이 없다. 억제 표기를 텍스트로 막으려
 * 들면 표기를 열거하는 게임이 된다.
 *
 * 파일 축은 **확장자를 가리지 않는다** — 손으로 쓴 소스가 무엇이든 승인된 한도를 받는다.
 * 어떤 언어가 허용되는지는 `sourceLanguageGate` 가 따로 판정한다.
 * ADR 0007 D-7 이 요구한 **도구에 의존하지 않는 자체 검사**다 —
 * detekt 2.0 이 alpha 인 동안 승인된 임계가 alpha 도구와 함께 죽지 않게 한다.
 */
abstract class SizeGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val policy = readPolicy(policyFile.get().asFile)
        val limit = policy.requireInt("limit.file.lines")
        val measured =
            sources.asFileTree.files
                .map { it to it.readLines().size }
                .sortedByDescending { (_, lines) -> lines }

        writeReport(policy.requireValue("policy.version"), limit, measured)

        val offenders = measured.filter { (_, lines) -> lines > limit }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                offenders.joinToString(
                    prefix = "파일 $limit 줄 한도 초과 ${offenders.size}건 (v2-지침서.md §5):\n  ",
                    separator = "\n  ",
                ) { (file, lines) -> "$lines 줄 — ${file.path}" },
            )
        }

        val functionLimit = policy.requireInt("limit.function.lines")
        val longFunctions =
            measureFunctions(measured.map { it.first })
                .filter { it.lines > functionLimit }
                .sortedByDescending { it.lines }
        if (longFunctions.isNotEmpty()) {
            throw GradleException(
                longFunctions.joinToString(
                    prefix = "함수 $functionLimit 줄 한도 초과 ${longFunctions.size}건 (v2-지침서.md §5):\n  ",
                    separator = "\n  ",
                ) { "${it.lines} 줄 — ${it.file.path}:${it.startLine} ${it.name}" },
            )
        }
    }

    private fun writeReport(
        policyVersion: String,
        limit: Int,
        measured: List<Pair<File, Int>>,
    ) {
        val body =
            measured.joinToString(separator = "\n") { (file, lines) -> "$lines\t${file.name}" }
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "policy.version=$policyVersion\nlimit.file.lines=$limit\nfiles=${measured.size}\n$body\n",
        )
    }
}
