package bidvector.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * **게이트 등재 장부가 소스와 같은지 잰다** — `gateExecutionGate` 의 반대축이다.
 *
 * `gateExecutionGate` 는 **등재된** test 가 돌았는지만 본다. 등재되지 않은 게이트 test 는 지워도
 * 비활성화해도 `check` 가 조용히 초록이다. 이 task 가 그 방향을 닫는다: 모듈의 **컴파일된 test 클래스
 * 전수**(`@Test` 계열 메서드를 가진 최상위 클래스) ∖ **제외** == 등재.
 *
 * 앞 판은 모듈·패키지마다 손으로 건 test 였다(app 1 · workflow 1 · adapters 여섯). 그 술어는 파일 이름을
 * 보았고 패키지 하나만 훑었다 — adapters 열두 패키지 중 여섯이 덮이지 않아 등재 124 중 78 이 등식 밖이었고,
 * 한 파일에 test 클래스가 둘이면 둘째가 보이지 않았다. task 로 올리면 모집단이 바이트코드이고 모듈 전수라
 * 그 두 구멍이 정의상 사라진다. **정책 파일만 바꾼 편집도 다시 돈다** — 그 파일을 입력으로 선언한다.
 */
abstract class GateRegistrationGateTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val policyFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testClasses: ConfigurableFileCollection

    /** 메타 애노테이션을 푸는 자리 — `@ParameterizedTest` 가 `@TestTemplate` 임을 열거하지 않는다. */
    @get:Classpath
    abstract val testRuntimeClasspath: ConfigurableFileCollection

    @get:Input
    abstract val moduleName: Property<String>

    /** Gradle `Test.filter` 의 제외 패턴 — 제외의 **출처**다(손 목록이 아니다). */
    @get:Input
    abstract val excludePatterns: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun gate() {
        val policy = readPolicy(policyFile.get().asFile)
        val module = moduleName.get()
        val census =
            GateRegistration.census(
                testClassFactsIn(testClasses.files),
                TestDiscoveryVocabulary(
                    annotations = policy.requireList(DISCOVERY_ANNOTATIONS).toSet(),
                    conditionPackages = policy.requireList(CONDITION_PACKAGES).toSet(),
                ),
                excludePatterns.get(),
                ClasspathMetaAnnotations(testRuntimeClasspath.files),
            )
        val violations =
            GateRegistration.violations(
                census,
                registered = policy.coordinates("gate.tests.$module"),
                declaredExcluded = policy.coordinates("gate.tests.$module.excluded"),
            )

        writeReport(module, census, violations)
        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    prefix = "게이트 등재 장부가 소스와 다르다 — 등재되지 않은 게이트는 꺼져도 조용하다:\n  ",
                    separator = "\n  ",
                ),
            )
        }
    }

    private fun writeReport(
        module: String,
        census: GateRegistrationCensus,
        violations: List<String>,
    ) {
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(
            (
                listOf(
                    "module=$module",
                    "population=${census.population.size}",
                    "excluded=${census.excluded.size}",
                ) + violations
            ).joinToString("\n", postfix = "\n"),
        )
    }

    private companion object {
        const val DISCOVERY_ANNOTATIONS = "gate.tests.discovery.annotations"
        const val CONDITION_PACKAGES = "gate.tests.discovery.condition-packages"

        /** 줄이 없는 모듈은 단언 대상이 없다 — 빈 집합이고 오류가 아니다(`settlement` 은 test 가 없다). */
        fun Map<String, String>.coordinates(key: String): Set<String> =
            this[key]
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
    }
}
