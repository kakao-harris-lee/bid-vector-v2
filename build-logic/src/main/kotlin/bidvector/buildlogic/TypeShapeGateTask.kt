package bidvector.buildlogic

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
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

    /** D-7·verifier r2 H-1 — 소유 판정을 넓히는 루트 패키지 접두. `architecture-policy.properties`
     * 의 `package.root`(ADR 0006 D-3)에서 호출부가 읽어 넘긴다 — 하드코딩하지 않는다. */
    @get:Input
    abstract val rootPackagePrefix: Property<String>

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
        failOnEmptyScan(imported)
        // D-7 — 소유 판정 = 이 배선이 스캔한 집합이거나 루트 패키지 아래(verifier r2 H-1 — 모듈
        // 경계를 넘는 소유 클래스 상속도 계수하기 위해 스캔 집합만으로는 부족했다).
        val ownedTypeNames = imported.map { it.name }.toSet()
        val prefix = rootPackagePrefix.get()
        val shapes = imported.map { it.toTypeShape(ownedTypeNames, prefix) }

        writeReport(policy, shapes)
        failOnViolations(policy, shapes)
    }

    /**
     * verifier r2 M-1 — `classes` 입력 경로가 배선 실수로 어긋나면(예: 존재하지 않는 디렉터리)
     * `roots`가 비어 `imported`도 비고, 그러면 조용히 「위반 0건」으로 통과했다 —
     * `cpdReportPresenceGate`(D-4)가 막는 것과 같은 계열의 퇴화다. 자리표시자 모듈(타입 1개)은
     * 이 단언에 걸리지 않는다 — 걸리는 것은 정확히 0개일 때뿐이다.
     */
    private fun failOnEmptyScan(imported: List<JavaClass>) {
        if (imported.isNotEmpty()) return
        throw GradleException(
            "스캔한 타입이 0개다 — 입력 경로가 비었거나 배선이 어긋났다고 의심된다(D-4 대칭, " +
                "verifier r2 M-1). `classes` 입력이 실제 컴파일 산출물 디렉터리를 가리키는지 확인한다.",
        )
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
