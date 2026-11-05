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
 * 쓰이지 않는 import 의 상시 게이트 — 판정은 [UnusedImports] 가 갖는다
 * (`OPEN-KTLINT-UNUSED-IMPORTS-SILENT`).
 *
 * `sourceLanguageGate` 와 같은 배선이다: **관례 트리를 직접 걸고** source set 에서 파생시키지 않는다.
 * 파생시키면 `exclude(...)` 한 줄로 기준과 대상이 함께 사라진다(설계 검토 S-14).
 *
 * 등록 자리가 셋인 것은 `sizeGate`/`buildLogicSizeGate`/`scriptSizeGate` 와 같은 이유다 — 모듈 트리,
 * included build 트리, 그리고 어느 모듈에도 속하지 않는 느슨한 빌드 스크립트. 한 자리만 걸면 나머지
 * 트리로 옮기는 것이 우회가 된다.
 */
abstract class UnusedImportGateTask : DefaultTask() {
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
        val syntaxNames = readPolicy(policyFile.get().asFile).requireList(SYNTAX_NAMES).toSet()
        val files = kotlinSources()
        val violations =
            parseKotlinFiles(files) { ktFile, file ->
                UnusedImports.detectIn(ktFile, file.path, syntaxNames)
            }.sortedWith(compareBy({ it.fileName }, { it.line }))

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (
                listOf("files=${files.size}", "unused=${violations.size}") +
                    violations.map { "${it.fileName}:${it.line} ${it.fqn}" }
            ).joinToString("\n", postfix = "\n"),
        )

        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    prefix = "쓰이지 않는 import 가 있다 (들여온 이름이 본문에 없다):\n  ",
                    separator = "\n  ",
                ) { "${it.fileName}:${it.line} ${it.fqn} — '${it.name}' 이 본문에서 쓰이지 않는다" },
            )
        }
    }

    /** 디렉터리는 훑고 개별 파일은 그대로 받는다 — 느슨한 스크립트는 파일로 걸린다. */
    private fun kotlinSources(): List<File> =
        sources.files
            .flatMap { entry ->
                if (entry.isDirectory) entry.walkTopDown().filter(File::isFile).toList() else listOf(entry)
            }.filter { it.extension in SOURCE_EXTENSIONS }
            .distinct()
            .sortedBy(File::getPath)

    private companion object {
        const val SYNTAX_NAMES = "source.import.syntax-names"
        val SOURCE_EXTENSIONS = setOf("kt", "kts")
    }
}
