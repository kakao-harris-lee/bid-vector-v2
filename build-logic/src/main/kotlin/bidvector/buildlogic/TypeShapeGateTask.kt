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
        val imported =
            if (roots.isEmpty()) {
                emptyList()
            } else {
                ClassFileImporter().importPaths(roots).filterNot { it.isAnonymousClass }
            }
        // D-7 — 상속 깊이는 이 배선이 스캔한 집합(= 이 모듈이 소유한 타입) 안에서만 잰다.
        val ownedTypeNames = imported.map { it.name }.toSet()
        val shapes = imported.map { it.toTypeShape(ownedTypeNames) }

        writeReport(policy, shapes)
        failOnViolations(policy, shapes)
    }

    private fun failOnViolations(
        policy: TypeShapeRatchetPolicy,
        shapes: List<TypeShape>,
    ) {
        val violations = policy.violations(shapes)
        if (violations.isEmpty()) return
        // verifier r1 L-1 — `sizeGate`의 파일·함수 축은 위반 메시지가 `v2-지침서.md §5`를
        // 문면으로 가리킨다. 이 축도 같은 형태로 상향 경로를 명시한다 — 빌드 로그만 보는
        // 개발자가 결정 ID(D-3)만으로는 그 문면에 닿지 못한다.
        throw GradleException(
            violations.joinToString(
                prefix =
                    "상속 깊이·인터페이스 수 래칫 위반 ${violations.size}건 — 증가는 금지다(D-3). " +
                        "올리려면 `docs/adr/0007-test-pyramid-and-ratchet.md` §5 OPEN-ADR-06 해소 절을 " +
                        "먼저 개정한다:\n  ",
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
