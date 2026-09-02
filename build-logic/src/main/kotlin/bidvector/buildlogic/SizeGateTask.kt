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
 * 파일 크기 래칫. ADR 0007 D-7 이 요구한 **도구에 의존하지 않는 자체 검사**다 —
 * detekt 2.0 이 alpha 인 동안 승인된 임계가 alpha 도구와 함께 죽지 않게 한다.
 * 함수 크기는 같은 정책 파일에서 생성한 overlay 로 detekt 이 맡는다.
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
            sources.asFileTree
                .matching { include("**/*.kt", "**/*.kts") }
                .files
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
