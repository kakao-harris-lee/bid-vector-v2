package bidvector.app.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
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
    /**
     * domain 이 볼 수 있는 것을 **열거하고 나머지를 전부 막는다.** 금지 열거는 세 라운드 연속
     * 새는 좌표를 냈고 마지막 것(`kotlin.io`)은 승인 문서가 이름으로 든 적이 없어 출처 대조로도
     * 잡히지 않았다 — 「생각해 낸 것만 막는」 방향의 한계다. allow-list 는 그 비대칭을 뒤집는다.
     */
    fun domainMayOnlyDependOnAllowedPackages(root: String): List<ArchRule> =
        listOf(
            noClasses()
                .that()
                .resideInAnyPackage(*packagesOf(root, policy.domainModules))
                .should()
                .dependOnClassesThat(outsideAllowList(root))
                .because("v2-지침서.md §3.1 · milestone-1.md 「구현 규칙」 — domain 은 허용 목록 밖을 보지 못한다"),
        )

    private fun outsideAllowList(root: String): DescribedPredicate<JavaClass> {
        val subtrees = policy.allowedSubtrees.map { if (it == ROOT_PLACEHOLDER) root else it }
        val exact = policy.allowedExactPackages.toSet()
        val forbiddenClasses = policy.forbiddenClasses.toSet()
        return object : DescribedPredicate<JavaClass>(
            "허용 목록 밖 (하위까지=$subtrees, 정확 패키지=$exact, 그중 금지 클래스=$forbiddenClasses)",
        ) {
            override fun test(target: JavaClass): Boolean {
                if (target.isPrimitive || target.isArray) return false
                val admittedPackage = target.packageName in exact || target.packageName.isUnder(subtrees)
                return target.name in forbiddenClasses || !admittedPackage
            }
        }
    }

    private fun String.isUnder(roots: List<String>): Boolean = roots.any { this == it || startsWith("$it.") }

    fun businessDomainModulesMustNotReferenceEachOther(root: String): List<ArchRule> =
        policy.businessDomainModules.map { module ->
            val others = policy.businessDomainModules - module
            noClasses()
                .that()
                .resideInAPackage("$root.$module..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(*packagesOf(root, others))
                .because("ADR 0006 D-4 — 업무 모듈 교차는 workflow 가 조합하고 공유는 shared-kernel 로만 한다")
        }

    fun dependencyDirectionIsOneWay(root: String): List<ArchRule> =
        listOf(
            layeredArchitecture()
                .consideringOnlyDependenciesInAnyPackage("$root..")
                .layer(DOMAIN)
                .definedBy(*packagesOf(root, policy.domainModules))
                .layer(APPLICATION)
                .definedBy(*packagesOf(root, policy.applicationModules))
                .layer(ADAPTERS)
                .definedBy(*packagesOf(root, policy.adapterModules))
                .layer(APP)
                .definedBy(*packagesOf(root, policy.appModules))
                .whereLayer(APP)
                .mayNotBeAccessedByAnyLayer()
                .whereLayer(ADAPTERS)
                .mayOnlyBeAccessedByLayers(APP)
                .whereLayer(APPLICATION)
                .mayOnlyBeAccessedByLayers(ADAPTERS, APP)
                .whereLayer(DOMAIN)
                .mayOnlyBeAccessedByLayers(APPLICATION, ADAPTERS, APP)
                .because("v2-지침서.md §3.1 — 의존 방향은 domain <- application <- adapters/app 이다"),
        )

    fun packagesMustBeFreeOfCycles(root: String): List<ArchRule> =
        listOf(
            slices()
                .matching("$root.(**)")
                .should()
                .beFreeOfCycles()
                .because("v2-지침서.md §5 — 순환 의존을 래칫 축으로 잰다"),
        )

    fun packageNamesMustNotBeTechnicalLayers(root: String): List<ArchRule> =
        policy.forbiddenPackageSegments.map { segment ->
            noClasses()
                .that()
                .resideInAPackage("$root..")
                .should()
                .resideInAPackage("..$segment..")
                .because("모듈 경계가 이미 계층을 표현한다 — 모듈 안에 기술 계층을 또 만들지 않는다")
        }

    private fun packagesOf(
        root: String,
        modules: List<String>,
    ): Array<String> = modules.map { "$root.$it.." }.toTypedArray()

    private companion object {
        /** 정책의 `bidvector` 항목은 평가 루트로 바뀐다 — fixture 루트에서도 같은 규칙이 서게. */
        const val ROOT_PLACEHOLDER = "bidvector"
        const val DOMAIN = "domain"
        const val APPLICATION = "application"
        const val ADAPTERS = "adapters"
        const val APP = "app"
    }
}
