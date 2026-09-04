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
 * domain main Kotlin 소스가 **이름으로** 허용 목록 밖 좌표를 부르는지 잰다 — ArchUnit(바이트코드)
 * 과 의존 그래프(1차) 게이트가 함께 놓치는 자리(컴파일 시 인라인되는 상수 · class literal,
 * 알려진 제한 7·11)를 덮는다. 설계 검토 §0 「두 층의 분담」 — 이 층은 **이름**을, 바이트코드
 * 층은 **결합**을 본다.
 *
 * `sourceRoot` 는 Gradle `SourceDirectorySet` 이 아니라 **관례 디렉터리를 직접 건다** — 파생시키면
 * `exclude(...)` 한 줄로 기준과 대상이 함께 사라진다(설계 검토 S-14, `sourceSetLayoutGate` 가
 * 10차에 배운 것과 같은 이유).
 *
 * domain 이 아닌 모듈에서는 판정 없이 통과한다 — 모든 모듈에 같은 convention plugin 이 적용되고
 * 모듈 선택은 이 task 등록이 아니라 [SourceReferencePolicy.isDomain] 이 한다
 * (`ModuleDependencyPolicy.mainExternalViolations` 와 같은 분담).
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
        if (!policy.isDomain(module)) {
            writeReport(module, emptyList(), emptyList(), emptyList())
            return
        }

        val files =
            sourceRoot.files
                .filter(File::isDirectory)
                .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension in SOURCE_EXTENSIONS } }
                .sortedBy(File::getPath)
        if (files.isEmpty()) {
            writeReport(module, files, emptyList(), emptyList())
            throw GradleException("도메인 모듈 '$module' 의 소스 트리가 비어 있다 — 게이트가 아무것도 보지 못했다")
        }

        val references = files.flatMap { file -> SourceReferences.extract(file.path, file.readText()) }
        val violations = references.filterNot { it.isAdmittedBy(policy) }
        writeReport(module, files, references, violations)
        failOn(violations)
    }

    private fun failOn(violations: List<SourceReference>) {
        if (violations.isEmpty()) return
        throw GradleException(
            violations.sortedWith(compareBy({ it.fileName }, { it.line })).joinToString(
                prefix =
                    "도메인 소스가 허용 목록 밖을 이름으로 부른다 (v2-지침서.md §3.1 " +
                        "「domain은 Spring, JPA, JSON, HTTP, broker를 import하지 않는다」):\n  ",
                separator = "\n  ",
            ) { "${it.fileName}:${it.line} ${it.fqn} — 허용 목록에 없다" },
        )
    }

    private fun writeReport(
        module: String,
        files: List<File>,
        references: List<SourceReference>,
        violations: List<SourceReference>,
    ) {
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "module=$module\nfiles=${files.size}\nreferences=${references.size}\nviolations=${violations.size}\n",
        )
    }

    private fun SourceReference.isAdmittedBy(policy: SourceReferencePolicy): Boolean =
        if (wildcard) policy.admitsWildcard(fqn) else policy.admits(fqn)
}
