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
 * 크기 래칫. **파일 축·함수 축·타입 멤버 축 셋을 든다.**
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

        val functionLimit = policy.requireInt("limit.function.lines")
        val longFunctions =
            measureFunctions(measured.map { it.first })
                .filter { it.lines > functionLimit }
                .sortedByDescending { it.lines }

        val typeLimit = policy.requireInt("limit.type.members")
        val types = measureTypes(measured.map { it.first })
        val oversizedTypes = types.filter { it.memberCount > typeLimit }.sortedByDescending { it.memberCount }

        writeReport(policy.requireValue("policy.version"), limit, functionLimit, typeLimit, measured, types)

        failOnFileOffenders(measured, limit)
        failOnLongFunctions(longFunctions, functionLimit)
        failOnOversizedTypes(oversizedTypes, typeLimit)
    }

    /** 세 축의 실패를 각자 한 자리에서 던진다 — `gate()`에 셋을 모으면 `ThrowsCount`에 걸린다. */
    private fun failOnFileOffenders(
        measured: List<Pair<File, Int>>,
        limit: Int,
    ) {
        val offenders = measured.filter { (_, lines) -> lines > limit }
        if (offenders.isEmpty()) return
        throw GradleException(
            offenders.joinToString(
                prefix = "파일 $limit 줄 한도 초과 ${offenders.size}건 (v2-지침서.md §5):\n  ",
                separator = "\n  ",
            ) { (file, lines) -> "$lines 줄 — ${file.path}" },
        )
    }

    private fun failOnLongFunctions(
        longFunctions: List<MeasuredFunction>,
        functionLimit: Int,
    ) {
        if (longFunctions.isEmpty()) return
        throw GradleException(
            longFunctions.joinToString(
                prefix = "함수 $functionLimit 줄 한도 초과 ${longFunctions.size}건 (v2-지침서.md §5):\n  ",
                separator = "\n  ",
            ) { "${it.lines} 줄 — ${it.file.path}:${it.startLine} ${it.name}" },
        )
    }

    private fun failOnOversizedTypes(
        oversizedTypes: List<MeasuredType>,
        typeLimit: Int,
    ) {
        if (oversizedTypes.isEmpty()) return
        throw GradleException(
            oversizedTypes.joinToString(
                prefix = "타입 멤버 $typeLimit 개 한도 초과 ${oversizedTypes.size}건 (OPEN-ADR-06 (a)):\n  ",
                separator = "\n  ",
            ) { "${it.memberCount} 개 — ${it.file.path}:${it.startLine} ${it.name}" },
        )
    }

    private fun writeReport(
        policyVersion: String,
        fileLimit: Int,
        functionLimit: Int,
        typeLimit: Int,
        measured: List<Pair<File, Int>>,
        types: List<MeasuredType>,
    ) {
        val body =
            measured.joinToString(separator = "\n") { (file, lines) -> "$lines\t${file.name}" }
        val maxType = types.maxByOrNull { it.memberCount }
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "policy.version=$policyVersion\n" +
                "limit.file.lines=$fileLimit\n" +
                "limit.function.lines=$functionLimit\n" +
                "limit.type.members=$typeLimit\n" +
                "files=${measured.size}\n" +
                "max.type.members=${maxType?.memberCount ?: 0}\t${maxType?.name ?: ""}\n" +
                "$body\n",
        )
    }
}
