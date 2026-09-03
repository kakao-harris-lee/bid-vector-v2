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
 * **게이트 test 가 실제로 돌았는지 잰다.**
 *
 * 게이트를 test 로 표현하면 그 test 를 실행 집합에서 빼는 것이 곧 게이트를 끄는 것이 된다 —
 * `Test.filter { excludeTestsMatching(...) }` 한 줄이면 `check` 는 초록인데 아무것도 지키지
 * 않는다. 「통과했다」와 「돌았다」는 다른 말이므로 후자를 따로 단언한다.
 *
 * 판정 근거는 test 결과 XML 이다. 경로는 `Test` task 에서 Provider 로 받아 하드코딩하지 않는다.
 */
abstract class GateExecutionGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resultDirectories: ConfigurableFileCollection

    @get:Input
    abstract val moduleName: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val policy = readPolicy(policyFile.get().asFile)
        val required = policy["gate.tests.${moduleName.get()}"].orEmpty().toCoordinates()
        val minimum = policy.requireInt("gate.tests.minimum")
        val observed = observedSuites()

        val violations =
            required.flatMap { fqcn -> suiteViolations(fqcn, observed[fqcn], minimum) }

        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (listOf("module=${moduleName.get()}", "required=${required.size}") + violations)
                .joinToString("\n", postfix = "\n"),
        )

        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.sorted().joinToString(
                    prefix = "게이트 test 의 실행이 확인되지 않았다 — 「통과」가 「돌았다」를 뜻하지 않는다:\n  ",
                    separator = "\n  ",
                ),
            )
        }
    }

    private fun suiteViolations(
        fqcn: String,
        suite: Suite?,
        minimum: Int,
    ): List<String> {
        suite ?: return listOf("게이트 test class 가 실행되지 않았다 — $fqcn")
        return listOfNotNull(
            "게이트 test class 의 실행 건수가 $minimum 미만이다 — $fqcn".takeIf { suite.tests < minimum },
            "게이트 test 가 실패했다 — $fqcn".takeIf { suite.failed > 0 },
            "게이트 test 가 건너뛰어졌다 — $fqcn".takeIf { suite.skipped > 0 },
        )
    }

    private fun observedSuites(): Map<String, Suite> =
        resultDirectories.files
            .filter(File::isDirectory)
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "xml" } }
            .mapNotNull { it.readText().toSuite() }
            .associateBy { it.name }

    private class Suite(
        val name: String,
        val tests: Int,
        val failed: Int,
        val skipped: Int,
    )

    private companion object {
        val HEADER = Regex("<testsuite\\b[^>]*>")
        val ATTRIBUTE = Regex("(\\w+)=\"([^\"]*)\"")

        fun String.toCoordinates(): List<String> = split(',').map(String::trim).filter(String::isNotEmpty)

        fun String.toSuite(): Suite? =
            HEADER
                .find(this)
                ?.value
                ?.let { header ->
                    val attributes =
                        ATTRIBUTE
                            .findAll(header)
                            .associate { it.groupValues[1] to it.groupValues[2] }
                    attributes["name"]?.let { name ->
                        Suite(
                            name,
                            attributes["tests"].toCount(),
                            attributes["failures"].toCount() + attributes["errors"].toCount(),
                            attributes["skipped"].toCount(),
                        )
                    }
                }

        fun String?.toCount(): Int = this?.toIntOrNull() ?: 0
    }
}
