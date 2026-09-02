package bidvector.app.architecture

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
    fun `도메인 모듈이 프레임워크에 의존하지 않는다`() {
        rules.domainMustNotDependOnFrameworks(policy.packageRoot).checkAll()
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
