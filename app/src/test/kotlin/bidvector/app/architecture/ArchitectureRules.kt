package bidvector.app.architecture

import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/**
 * 규칙은 **패키지 루트를 받는 값**이다. 그래야 production(`bidvector`)을 지키는 바로 그 규칙을
 * 위반 fixture 루트에 그대로 적용해 「이 규칙이 위반을 잡는다」를 양성 단언할 수 있다 —
 * 규칙을 따로 만들어 fixture 에만 걸면 그 단언은 production 게이트에 대해 아무것도 말하지 않는다.
 */
class ArchitectureRules(
    private val policy: ArchitecturePolicy,
) {
    fun domainMustNotDependOnFrameworks(root: String): List<ArchRule> =
        listOf(
            noClasses()
                .that().resideInAnyPackage(*packagesOf(root, policy.domainModules))
                .should().dependOnClassesThat().resideInAnyPackage(
                    *policy.forbiddenPackages.map { "$it.." }.toTypedArray(),
                )
                .because("v2-지침서.md §3.1 — domain 은 Spring·JPA·JSON·HTTP 를 import 하지 않는다"),
        )

    fun businessDomainModulesMustNotReferenceEachOther(root: String): List<ArchRule> =
        policy.businessDomainModules.map { module ->
            val others = policy.businessDomainModules - module
            noClasses()
                .that().resideInAPackage("$root.$module..")
                .should().dependOnClassesThat().resideInAnyPackage(*packagesOf(root, others))
                .because("ADR 0006 D-4 — 업무 모듈 교차는 workflow 가 조합하고 공유는 shared-kernel 로만 한다")
        }

    fun dependencyDirectionIsOneWay(root: String): List<ArchRule> =
        listOf(
            layeredArchitecture()
                .consideringOnlyDependenciesInAnyPackage("$root..")
                .layer(DOMAIN).definedBy(*packagesOf(root, policy.domainModules))
                .layer(APPLICATION).definedBy(*packagesOf(root, policy.applicationModules))
                .layer(ADAPTERS).definedBy(*packagesOf(root, policy.adapterModules))
                .layer(APP).definedBy(*packagesOf(root, policy.appModules))
                .whereLayer(APP).mayNotBeAccessedByAnyLayer()
                .whereLayer(ADAPTERS).mayOnlyBeAccessedByLayers(APP)
                .whereLayer(APPLICATION).mayOnlyBeAccessedByLayers(ADAPTERS, APP)
                .whereLayer(DOMAIN).mayOnlyBeAccessedByLayers(APPLICATION, ADAPTERS, APP)
                .because("v2-지침서.md §3.1 — 의존 방향은 domain <- application <- adapters/app 이다"),
        )

    fun packagesMustBeFreeOfCycles(root: String): List<ArchRule> =
        listOf(
            slices()
                .matching("$root.(**)")
                .should().beFreeOfCycles()
                .because("v2-지침서.md §5 — 순환 의존을 래칫 축으로 잰다"),
        )

    fun packageNamesMustNotBeTechnicalLayers(root: String): List<ArchRule> =
        policy.forbiddenPackageSegments.map { segment ->
            noClasses()
                .that().resideInAPackage("$root..")
                .should().resideInAPackage("..$segment..")
                .because("모듈 경계가 이미 계층을 표현한다 — 모듈 안에 기술 계층을 또 만들지 않는다")
        }

    private fun packagesOf(
        root: String,
        modules: List<String>,
    ): Array<String> = modules.map { "$root.$it.." }.toTypedArray()

    private companion object {
        const val DOMAIN = "domain"
        const val APPLICATION = "application"
        const val ADAPTERS = "adapters"
        const val APP = "app"
    }
}
