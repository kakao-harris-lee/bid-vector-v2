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
 * domain main 의 **public(+protected) API** 가 raw 부동소수 타입을 표면에 드러내는지 잰다 —
 * `milestone-1.md` 「완료 조건」의 *"raw `Double` 금액/rate가 public domain API에 없음"*을
 * 강제하는 장치다(설계 검토 부록 「요구의 귀속」 — 내용은 1B, 장치는 1A).
 *
 * `domainSourceReferenceGate` 와 같은 두 판정 축(이름 참조 대 타입 표면)이라 별도 task 다 —
 * 정책·사유·리포트가 다르다(설계 검토 부록 §3 「task 하나인가 둘인가」). domain 판정은
 * [SourceReferencePolicy.isDomain] 을 재사용한다.
 */
abstract class DomainApiTypeGateTask : DefaultTask() {
    /** `architecture-policy.properties` — `layer.domain` 으로 모듈이 domain 인지만 읽는다. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    /** `api-type-policy.properties` — 금지 타입 목록. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apiPolicyFile: RegularFileProperty

    @get:Input
    abstract val moduleName: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoot: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val domainPolicy = SourceReferencePolicy.load(policyFile.get().asFile)
        val module = moduleName.get()
        if (!domainPolicy.isDomain(module)) {
            writeReport(module, files = emptyList(), surfaces = emptyList(), violations = 0)
            return
        }

        val files = domainSourceFiles()
        if (files.isEmpty()) {
            writeReport(module, files, emptyList(), violations = 0)
            throw GradleException("도메인 모듈 '$module' 의 소스 트리가 비어 있다 — 게이트가 아무것도 보지 못했다")
        }

        val apiPolicy = ApiTypePolicy.load(apiPolicyFile.get().asFile)
        val surfaces = PublicApiTypes.extractAll(files)
        val violations = surfaces.flatMap { it.uses }.filter { apiPolicy.forbids(it.name) != null }
        writeReport(module, files, surfaces, violations.size)
        if (violations.isNotEmpty()) failOnForbiddenTypes(violations, apiPolicy)
        val untyped = surfaces.flatMap { it.untyped }
        if (untyped.isNotEmpty()) failOnUntyped(untyped)
    }

    private fun domainSourceFiles(): List<File> =
        sourceRoot.files
            .filter(File::isDirectory)
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension in SOURCE_EXTENSIONS } }
            .sortedBy(File::getPath)

    private fun failOnForbiddenTypes(
        violations: List<ApiTypeUse>,
        apiPolicy: ApiTypePolicy,
    ) {
        val message =
            "public domain API 에 원시 부동소수 타입이 있다 (milestone-1.md 「완료 조건」 " +
                "\"raw Double 금액/rate가 public domain API에 없음\", v2-지침서.md §4.1 " +
                "\"확정 원화 금액은 Long 원 단위 또는 명시적 BigDecimal로 표현한다\"):\n  "
        throw GradleException(
            violations
                .sortedWith(
                    compareBy({ it.fileName }, { it.line }),
                ).joinToString(prefix = message, separator = "\n  ") {
                    val fileName = File(it.fileName).name
                    "$fileName:${it.line} ${it.declaration} — '${it.written}' (${apiPolicy.forbids(it.name)})"
                },
        )
    }

    private fun failOnUntyped(untyped: List<UntypedDeclaration>): Unit =
        throw GradleException(
            untyped.sortedWith(compareBy({ it.fileName }, { it.line })).joinToString(
                prefix = "public 선언에 타입이 없어 이 축을 잴 수 없다 — 타입을 명시한다:\n  ",
                separator = "\n  ",
            ) { "${File(it.fileName).name}:${it.line} ${it.declaration}" },
        )

    private fun writeReport(
        module: String,
        files: List<File>,
        surfaces: List<PublicApiSurface>,
        violations: Int,
    ) {
        val publicDeclarations = surfaces.sumOf { it.publicDeclarations }
        val uses = surfaces.sumOf { it.uses.size }
        val untyped = surfaces.sumOf { it.untyped.size }
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "module=$module\nfiles=${files.size}\npublicDeclarations=$publicDeclarations\n" +
                "typeUses=$uses\nviolations=$violations\nuntyped=$untyped\n",
        )
    }
}
