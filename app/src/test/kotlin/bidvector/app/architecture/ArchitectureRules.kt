package bidvector.app.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
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
     * domain 이 볼 수 있는 것을 열거하고 나머지를 전부 막는다. 층은 넷이고 각 층의 **실패 방향**이
     * 고정돼 있다 — 정확 패키지(T-B)와 클래스 단위 허용(T-C)은 「목록에 없으면 닫힘」이라
     * 생각해 내지 못한 좌표가 자동으로 막힌다. 근거와 규모 실측은 `scope.md` 「계약 갱신」.
     */
    fun domainMayOnlyDependOnAllowedPackages(root: String): List<ArchRule> =
        listOf(
            noClasses()
                .that()
                .resideInAnyPackage(*packagesOf(root, policy.domainModules))
                .should()
                .dependOnClassesThat(outsideAllowList(root))
                .because("v2-지침서.md §3.1 · milestone-1.md 「구현 규칙」 — domain 은 허용 목록 밖을 보지 못한다"),
            noClasses()
                .that()
                .resideInAnyPackage(*packagesOf(root, policy.domainModules))
                .should(accessForbiddenMember())
                .because("허용된 클래스 안에도 환경을 읽는 멤버가 있다 — 클래스 단위로 가를 수 없는 자리"),
        )

    private fun outsideAllowList(root: String): DescribedPredicate<JavaClass> {
        val subtrees = policy.allowedSubtrees.map { if (it == ROOT_PLACEHOLDER) root else it }
        val exact = policy.allowedExactPackages.toSet()
        val byClass = policy.byClassPackages.toSet()
        val allowedClasses = policy.allowedClasses.toSet()
        return object : DescribedPredicate<JavaClass>(
            "허용 목록 밖 (하위까지=$subtrees, 정확 패키지=$exact, 클래스 단위=$byClass 중 ${allowedClasses.size}종)",
        ) {
            override fun test(target: JavaClass): Boolean {
                if (target.isPrimitive || target.isArray) return false
                val admitted =
                    target.packageName in exact ||
                        target.packageName.isUnder(subtrees) ||
                        (target.packageName in byClass && target.name in allowedClasses)
                return !admitted
            }
        }
    }

    /**
     * **호출 지점의 owner 가 아니라 선언 클래스로 잰다.** `IllegalStateException("x").printStackTrace()`
     * 의 owner 는 구체 예외 타입이고 그 멤버는 `java.lang.Throwable` 이 선언한다 — owner 로 재면
     * 잡히지 않고(Codex 6차 #1), `Throwable` 을 허용 목록에서 빼도 `require`/`check`/`toInt()` 가
     * 컴파일러 산출로 내는 구체 예외 타입들이 같은 멤버를 상속으로 갖는다.
     *
     * 목록은 손 열거가 아니라 `memberEffectGate` 가 도출한 후보의 분류다.
     */
    private fun accessForbiddenMember(): ArchCondition<JavaClass> {
        val forbidden = policy.forbiddenMembers.toSet()
        return object : ArchCondition<JavaClass>("금지 멤버에 접근한다 (${forbidden.size} 종)") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                // `noClasses().should(...)` 는 조건을 뒤집는다 — **만족**이 곧 위반이다.
                // `violated` 로 내면 뒤집혀 사라진다(실측으로 그 상태를 만났다).
                item.accessesFromSelf
                    .filter { access -> access.declaringKeys().any { it in forbidden } }
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, it.description)) }
            }
        }
    }

    /**
     * 접근이 가리키는 멤버의 **선언 클래스 후보**. 해석되면 한 자리로 확정되고, 해석이 안 되면
     * owner 의 상위 타입을 훑어 **닫히는 쪽으로** 넓게 본다.
     *
     * **상위 타입 훑기가 덮는 범위는 좁다 — 실측으로 확인했다.** ArchUnit 의 classpath 해석을
     * 끄면 이 corpus 의 접근이 전부 미해결이 되는데, 그때는 owner 의 **상위 타입도 함께
     * 알 수 없어** 훑기가 아무것도 잡지 못한다. 즉 이 판정을 실제로 떠받치는 것은 해석이고
     * 훑기는 「owner 의 계보는 알지만 그 멤버만 못 찾는」 좁은 경우를 위한 것이다.
     * 그 의존을 조용히 두지 않으려고 `ArchitectureGateTest` 가 해석 설정을 단언한다.
     */
    private fun JavaAccess<*>.declaringKeys(): List<String> {
        val declared =
            target
                .resolveMember()
                .orElse(null)
                ?.owner
                ?.name
        val owners =
            declared?.let(::listOf)
                ?: (listOf(targetOwner) + targetOwner.allRawSuperclasses + targetOwner.allRawInterfaces)
                    .map(JavaClass::getName)
        return owners.map { "$it#${target.name}" }
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
