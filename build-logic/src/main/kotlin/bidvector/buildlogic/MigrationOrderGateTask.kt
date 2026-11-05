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

/**
 * 마이그레이션 번호 순서의 상시 게이트 — 판정은 [MigrationOrder] 가 갖는다
 * (`OPEN-MIGRATION-ORDER-GATE`).
 *
 * 마이그레이션을 **가진** 모듈은 정책이 고른다(`migration.module`) — 모든 모듈에 같은 convention
 * plugin 이 붙으므로 `DomainSourceReferenceGateTask` 의 `isDomain` 과 같은 분담이다. 그 모듈에서
 * 디렉터리가 비거나 없으면 **실패한다**: 「잴 것이 없어 통과」는 게이트를 옮기거나 지우는 것과
 * 구별되지 않는다(`jarContentGate` 가 빈 아카이브로 배운 것과 같은 축).
 *
 * 디렉터리는 선언 입력이고 경로 민감도가 RELATIVE 다 — **이름 변경이 입력 변경이다**. `@InputDirectory`
 * 가 아니라 file collection 인 것은 마이그레이션을 갖지 않는 여덟 모듈에 그 디렉터리가 없어서다
 * (`@InputDirectory` 는 부재를 Gradle 자신이 거부해 task 가 돌기 전에 죽는다 —
 * `CpdReportPresenceGateTask` 의 `@Internal` 선택과 같은 이유).
 */
abstract class MigrationOrderGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:Input
    abstract val moduleName: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrationDirectory: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val policy = readPolicy(policyFile.get().asFile)
        val module = moduleName.get()
        if (module != policy.requireValue(MIGRATION_MODULE)) {
            writeReport(module, emptyList(), emptyList())
            return
        }

        val fileNames =
            migrationDirectory.files
                .filter(File::isDirectory)
                .flatMap { dir -> dir.walkTopDown().filter(File::isFile).map(File::getName) }
                .sorted()
        if (fileNames.isEmpty()) {
            writeReport(module, fileNames, emptyList())
            throw GradleException(
                "마이그레이션을 가진 모듈 '$module' 의 디렉터리가 비어 있다 — 게이트가 아무것도 보지 못했다 " +
                    "(경로가 옮겨졌는지 'migration.resource-path' 를 본다)",
            )
        }

        val violations = MigrationOrder.violations(fileNames, retiredVersions(policy))
        writeReport(module, fileNames, violations)
        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    prefix = "마이그레이션 번호 집합이 등식을 어긴다 (Flyway outOfOrder=false, D-6F5-8):\n  ",
                    separator = "\n  ",
                ),
            )
        }
    }

    private fun writeReport(
        module: String,
        fileNames: List<String>,
        violations: List<String>,
    ) {
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (listOf("module=$module", "files=${fileNames.size}", "violations=${violations.size}") + violations)
                .joinToString("\n", postfix = "\n"),
        )
    }

    private companion object {
        const val MIGRATION_MODULE = "migration.module"
        const val RETIRED_VERSIONS = "migration.retired-versions"

        /** 빈 값은 「은퇴 없음」이고 오류가 아니다. 정수가 아닌 항목은 정책 오류다(조용히 무시하지 않는다). */
        fun retiredVersions(policy: Map<String, String>): Set<Int> =
            policy
                .requireList(RETIRED_VERSIONS)
                .map { entry ->
                    entry.toIntOrNull() ?: error("정책 키 '$RETIRED_VERSIONS' 의 항목 '$entry' 가 정수가 아니다")
                }.toSet()
    }
}
