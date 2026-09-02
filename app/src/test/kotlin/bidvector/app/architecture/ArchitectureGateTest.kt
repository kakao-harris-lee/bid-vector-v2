package bidvector.app.architecture

import com.tngtech.archunit.ArchConfiguration
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * 경계 게이트의 **양성** 쪽 — production 바이트코드가 규칙을 지킨다.
 *
 * `ArchRule.check` 는 `that()` 이 아무 클래스도 고르지 못하면 실패한다. 그래서 모듈이 비면
 * 규칙이 공허하게 통과하지 않고 **실패한다** — 모듈 경계 앵커가 그 실패를 막는 유일한 것이며
 * 도메인 코드가 들어오면 앵커 없이도 규칙이 선다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchitectureGateTest {
    private val policy = ArchitecturePolicy.load()
    private val rules = ArchitectureRules(policy)
    private val production: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages(policy.packageRoot)

    /**
     * **멤버 규칙은 ArchUnit 의 classpath 해석에 기대고 있다.** 그 설정을 끄고 재 보면 이
     * corpus 의 접근이 전부 미해결이 되고, 그때는 owner 의 상위 타입도 함께 알 수 없어
     * 상위 타입 훑기 fallback 이 **아무것도 잡지 못한다** — `printStackTrace` 보고가 0 이 된다.
     *
     * 기본값이 켜짐이라 지금은 참이지만, `archunit.properties` 한 줄로 꺼질 수 있고 그러면
     * 게이트가 **조용히** 눈을 감는다. 그 부재를 시끄럽게 만드는 것이 이 단언이다.
     */
    @Test
    fun `멤버 판정이 기대는 classpath 해석이 켜져 있다`() {
        ArchConfiguration.get().resolveMissingDependenciesFromClassPath() shouldBe true
    }

    @Test
    fun `루트 아래 1급 패키지는 승인된 모듈 집합과 정확히 같다`() {
        val prefix = "${policy.packageRoot}."
        val observed =
            production
                .map { it.packageName }
                .filter { it.startsWith(prefix) }
                .map { it.removePrefix(prefix).substringBefore('.') }
                .toSortedSet()

        observed shouldBe policy.allModules.toSortedSet()
    }

    @Test
    fun `도메인 모듈이 허용 목록 밖을 보지 않는다`() {
        rules.domainMayOnlyDependOnAllowedPackages(policy.packageRoot).checkAll()
    }

    @Test
    fun `업무 모듈이 서로를 직접 참조하지 않는다`() {
        rules.businessDomainModulesMustNotReferenceEachOther(policy.packageRoot).checkAll()
    }

    @Test
    fun `의존 방향이 한 방향이다`() {
        rules.dependencyDirectionIsOneWay(policy.packageRoot).checkAll()
    }

    @Test
    fun `패키지에 순환이 없다`() {
        rules.packagesMustBeFreeOfCycles(policy.packageRoot).checkAll()
    }

    @Test
    fun `기술 계층 이름을 패키지로 쓰지 않는다`() {
        rules.packageNamesMustNotBeTechnicalLayers(policy.packageRoot).checkAll()
    }

    private fun List<ArchRule>.checkAll() = forEach { rule -> rule.check(production) }
}
