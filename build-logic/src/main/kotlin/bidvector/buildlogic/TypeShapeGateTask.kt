package bidvector.buildlogic

import com.tngtech.archunit.core.importer.ClassFileImporter
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

/**
 * 상속 깊이·구현 인터페이스 수 래칫(D-3) — `qualityBaseline`이 재는 축과 같은 값을
 * [TypeShapeRatchetPolicy]로 판정한다. `sizeGate`(PSI)와 축이 달라 별도 task 다 —
 * 이 축은 바이트코드 없이는 잴 수 없다(상위 타입 해석).
 */
abstract class TypeShapeGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classes: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val policy = TypeShapeRatchetPolicy.load(policyFile.get().asFile)
        val roots = classes.files.filter { it.isDirectory }.map { it.toPath() }
        val shapes =
            if (roots.isEmpty()) {
                emptyList()
            } else {
                ClassFileImporter()
                    .importPaths(roots)
                    .filterNot { it.isAnonymousClass }
                    .map { it.toTypeShape() }
            }

        writeReport(policy, shapes)
        failOnViolations(policy, shapes)
    }

    private fun failOnViolations(
        policy: TypeShapeRatchetPolicy,
        shapes: List<TypeShape>,
    ) {
        val violations = policy.violations(shapes)
        if (violations.isEmpty()) return
        throw GradleException(
            violations.joinToString(
                prefix = "상속 깊이·인터페이스 수 래칫 위반 ${violations.size}건 (D-3, OPEN-ADR-06 (a)):\n  ",
                separator = "\n  ",
            ),
        )
    }

    private fun writeReport(
        policy: TypeShapeRatchetPolicy,
        shapes: List<TypeShape>,
    ) {
        val maxDepth = shapes.maxOfOrNull { it.inheritanceDepth } ?: 0
        val maxInterfaces = shapes.maxOfOrNull { it.interfaceCount } ?: 0
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            "ratchet.type.inheritance-depth.max=${policy.maxInheritanceDepth}\n" +
                "ratchet.type.interfaces.max=${policy.maxInterfaces}\n" +
                "types=${shapes.size}\n" +
                "max.inheritance-depth=$maxDepth\n" +
                "max.interfaces=$maxInterfaces\n",
        )
    }
}
