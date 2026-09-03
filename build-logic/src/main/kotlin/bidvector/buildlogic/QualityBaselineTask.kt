package bidvector.buildlogic

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class ModuleBaselineSpec {
    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val projectDependencies: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classes: ConfigurableFileCollection
}

/**
 * `OPEN-ADR-06` 의 입력을 만드는 **측정** task 다. 게이트가 아니다 — 결정 주체가 운영자이고
 * (`capability-map.md` §12) 1A 는 축을 재서 등재할 뿐 임계를 자기 승인하지 않는다.
 *
 * 재는 축은 `v2-지침서.md` §5 「크기와 결합도」의 여섯 중 **다섯**과 `OPEN-ADR-06` 이 묻는 셋
 * (클래스/타입 크기 · 상속 깊이 · mixin 수)이다. 여섯째 축(`duplicate mechanical helper`)의
 * 측정 정의는 `ADR 0007` `OPEN-ADR-16` 으로 이월했다. 타입 축은 바이트코드에서 재므로
 * Kotlin `internal` 은 public 으로 보인다 — 그 한계는 리포트 머리말이 적는다.
 */
abstract class QualityBaselineTask : DefaultTask() {
    @get:Nested
    val modules: MutableList<ModuleBaselineSpec> = mutableListOf()

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun measure() {
        val rows = modules.map(::measureModule)
        val fanIn = rows.associate { row -> row.module to rows.count { row.module in it.projectDependencies } }
        report
            .get()
            .asFile
            .apply { parentFile.mkdirs() }
            .writeText(render(rows, fanIn))
        logger.lifecycle("quality baseline → {}", report.get().asFile)
    }

    private fun measureModule(spec: ModuleBaselineSpec): BaselineRow {
        val sourceFiles =
            spec.sources.asFileTree
                .matching { include("**/*.kt") }
                .files
        val lineCounts = sourceFiles.map { it.readLines().size }
        val types = importTypes(spec)
        return BaselineRow(
            module = spec.moduleName.get(),
            projectDependencies = spec.projectDependencies.get(),
            files = sourceFiles.size,
            lines = lineCounts.sum(),
            maxFileLines = lineCounts.maxOrNull() ?: 0,
            types = types.size,
            maxTypeMembers = types.maxOfOrNull { it.methods.size + it.fields.size } ?: 0,
            maxInheritanceDepth = types.maxOfOrNull(::inheritanceDepth) ?: 0,
            maxInterfaces = types.maxOfOrNull { it.rawInterfaces.size } ?: 0,
            publicApi = types.sumOf(::publicMemberCount),
        )
    }

    private fun importTypes(spec: ModuleBaselineSpec): List<JavaClass> {
        val roots =
            spec.classes.files
                .filter { it.isDirectory }
                .map { it.toPath() }
        if (roots.isEmpty()) return emptyList()
        return ClassFileImporter().importPaths(roots).filterNot { it.isAnonymousClass }
    }

    private fun inheritanceDepth(type: JavaClass): Int =
        generateSequence(type) { it.rawSuperclass.orElse(null) }.count() - 1

    private fun publicMemberCount(type: JavaClass): Int =
        if (!type.modifiers.contains(JavaModifier.PUBLIC)) {
            0
        } else {
            1 + type.methods.count { it.modifiers.contains(JavaModifier.PUBLIC) } +
                type.fields.count { it.modifiers.contains(JavaModifier.PUBLIC) }
        }

    private fun render(
        rows: List<BaselineRow>,
        fanIn: Map<String, Int>,
    ): String {
        val preamble =
            listOf(
                "# quality baseline (OPEN-ADR-06 입력)",
                "",
                "게이트가 아니라 측정이다. 타입 축은 바이트코드 기준이라 Kotlin `internal` 이 public 으로 보인다.",
                "duplicate mechanical helper 축은 이 task 가 재지 않는다 — 측정 정의는 " +
                    "`ADR 0007` OPEN-ADR-16(운영자 결정 2026-09-03)으로 이월했다.",
                "",
                markdownRow(COLUMNS.map { it.header }),
                markdownRow(COLUMNS.mapIndexed { index, _ -> if (index == 0) "---" else "--:" }),
            )
        val body =
            rows.map { row ->
                markdownRow(COLUMNS.map { column -> column.read(row, fanIn[row.module] ?: 0).toString() })
            }
        return (preamble + body).joinToString(separator = "\n", postfix = "\n")
    }

    private fun markdownRow(cells: List<String>): String =
        cells.joinToString(separator = " | ", prefix = "| ", postfix = " |")

    /** 표의 열은 한 번만 선언한다 — 머리글·정렬행·본문이 같은 목록에서 나오므로 어긋날 수 없다. */
    private class Column(
        val header: String,
        val read: (BaselineRow, Int) -> Any,
    )

    private data class BaselineRow(
        val module: String,
        val projectDependencies: Set<String>,
        val files: Int,
        val lines: Int,
        val maxFileLines: Int,
        val types: Int,
        val maxTypeMembers: Int,
        val maxInheritanceDepth: Int,
        val maxInterfaces: Int,
        val publicApi: Int,
    )

    private companion object {
        val COLUMNS =
            listOf(
                Column("module") { row, _ -> row.module },
                Column("files") { row, _ -> row.files },
                Column("lines") { row, _ -> row.lines },
                Column("max file") { row, _ -> row.maxFileLines },
                Column("types") { row, _ -> row.types },
                Column("max type members") { row, _ -> row.maxTypeMembers },
                Column("max depth") { row, _ -> row.maxInheritanceDepth },
                Column("max interfaces") { row, _ -> row.maxInterfaces },
                Column("public api") { row, _ -> row.publicApi },
                Column("fan-out") { row, _ -> row.projectDependencies.size },
                Column("fan-in") { _, fanIn -> fanIn },
            )
    }
}
