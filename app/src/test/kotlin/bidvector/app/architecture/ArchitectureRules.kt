package bidvector.app.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethodCall
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
     * 잡히지 않고, `Throwable` 을 허용 목록에서 빼도 `require`/`check`/`toInt()` 가
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

    /**
     * M6/6A-3+6F-3 D-6A3-9 — `bidvector.strategy.TextKt.assemble*`(감시 텍스트 조립 커널)를
     * 부르는 production 클래스 집합은 [ArchitecturePolicy.allowedAssembleCallers] 뿐이다
     * (부재 쪽 — 존재 쪽은 `EvaluationAdapterDependencyTest`, adapters 모듈이 잠근다).
     * `noClasses().that(허용 밖)` 형태라 새 어댑터가 같은 텍스트를 다시 이어붙이면(우회
     * — assemble* 를 안 거치고 직접 조립) 그 클래스가 허용 목록에 없는 한 이 규칙이 곧바로
     * 걸린다.
     */
    fun assembleCallersMustBeAllowedSet(allowedCallers: List<String>): List<ArchRule> {
        val allowed = allowedCallers.toSet()
        return listOf(
            noClasses()
                .that(isNotAllowedAssembleCaller(allowed))
                .should(callAssembleKernel())
                .because("D-6A3-9 — assemble* 호출자 집합은 architecture-policy.properties 의 허용 목록과 같다"),
        )
    }

    private fun isNotAllowedAssembleCaller(allowed: Set<String>): DescribedPredicate<JavaClass> =
        object : DescribedPredicate<JavaClass>("허용된 assemble* 호출자가 아니다 (${allowed.size}종)") {
            override fun test(target: JavaClass): Boolean = target.fullName !in allowed
        }

    private fun callAssembleKernel(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("bidvector.strategy.TextKt.assemble* 를 호출한다") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.accessesFromSelf
                    .filter { access -> access.declaringKeys().any { it.startsWith(ASSEMBLE_KERNEL_PREFIX) } }
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, it.description)) }
            }
        }

    /**
     * M6/6A-3+6F-3 D-6A3-17(a) — HIGH-1 시정(검토 라운드 1). 이름 목록(`KNOWN_
     * NOTIFICATION_REQUEST_PORT_IMPLS`)이 아니라 **구조**로 닫는다. ① [appRoot] 안의
     * 클래스가 [portTypeName] 을 스스로 구현하지 않는다(집합==∅ — app 안에 숨겨 심는
     * 우회를 막는다, verifier M1). ② [appRoot] 가 참조하는 포트 구현 타입 집합(classpath
     * 전체에서 `isAssignableTo` 로 도출)은 [allowedImpls] 의 부분집합이다(다른 모듈에
     * 새 구현이 생겨 그것을 배선해도 걸린다). ③ [forbiddenOutboxTypes](outbox 쓰기 타입
     * 전수) 참조 집합은 ∅ 다(포트를 거치지 않고 직접 쓰는 우회를 막는다).
     */
    fun notificationPortMustBeStructurallyClosed(
        appRoot: String,
        portTypeName: String,
        allowedImpls: Set<String>,
        forbiddenOutboxTypes: Set<String>,
    ): List<ArchRule> =
        listOf(
            noClasses()
                .that()
                .resideInAPackage("$appRoot..")
                .should(beAssignableToType(portTypeName))
                .because("D-6A3-17(a)① — app 이 NotificationRequestPort 구현체를 스스로 정의하지 않는다"),
            noClasses()
                .that()
                .resideInAPackage("$appRoot..")
                .should(referenceDisallowedImplementation(portTypeName, allowedImpls))
                .because("D-6A3-17(a)② — app 이 참조하는 NotificationRequestPort 구현 타입 집합은 허용 목록의 부분집합이다"),
            noClasses()
                .that()
                .resideInAPackage("$appRoot..")
                .should(referenceAnyOf(forbiddenOutboxTypes, "outbox 쓰기 타입"))
                .because("D-6A3-17(a)③ — app 은 outbox 쓰기 타입을 참조하지 않는다(dry-run effect 0)"),
        )

    /**
     * D-6A3-17(b) — HIGH-3 시정. `app.wiring` 만이 아니라 [appRoot] 전체(루트 패키지 포함
     * — `@Bean` 을 아무 패키지에나 둘 수 있다, verifier M4)가 `adapters.ml`([mlPackage])
     * 에서 참조하는 클래스 집합은 [allowedTypes] 의 부분집합이다.
     */
    fun appMustOnlyReferenceMlTypes(
        appRoot: String,
        mlPackage: String,
        allowedTypes: Set<String>,
    ): List<ArchRule> =
        listOf(
            noClasses()
                .that()
                .resideInAPackage("$appRoot..")
                .should(referenceDisallowedInPackage(mlPackage, allowedTypes))
                .because("D-6A3-17(b) — app production 이 참조하는 adapters.ml 타입 집합은 허용 목록의 부분집합이다"),
        )

    /**
     * D-6A3-25 — 검토 라운드 2 HIGH 시정(우회 5). 이전 (c)(인터페이스 이름 등식 × `app.http`
     * 패키지 하나)는 호출 지점 owner 가 구체 타입이면(verifier N6) 또는 헬퍼가 `app.http`
     * 밖에 있으면(verifier N5) 보지 못했다 — 두 축을 각각 한 걸음씩 옮긴 변이가 둘 다
     * 초록이었다. 이 규칙은 [appRoot] 전체(패키지 무관)에서 [ports] 의 메서드를 **호출**하는
     * 모든 접근을 owner 의 `isAssignableTo`(구현 타입 전부 포함 — classpath 계층 해석,
     * 이름 목록이 아니다)로 판정하고, (호출자, 포트.메서드) 쌍이 [allowedPairs] 의 부분집합인지
     * 본다. 포트를 만들어 생성자로 **넘기기만** 하는 조립 코드(`EvaluationWiring`)는 메서드
     * 호출이 아니라 걸리지 않는다 — 오직 실제로 포트 메서드를 부르는 지점만 판정한다.
     */
    fun appPortCallsMustBeAllowedPairs(
        appRoot: String,
        ports: Set<String>,
        allowedPairs: Set<Pair<String, String>>,
    ): List<ArchRule> =
        listOf(
            noClasses()
                .that()
                .resideInAPackage("$appRoot..")
                .should(callDisallowedPortMethod(ports, allowedPairs))
                .because(
                    "D-6A3-25 — app production 전체에서 평가·전략 포트 메서드를 호출하는 " +
                        "(호출자, 포트.메서드) 쌍은 허용 쌍의 부분집합이다",
                ),
        )

    private fun beAssignableToType(typeName: String): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("$typeName 에 assignable 하다(그 타입 자신은 제외)") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                if (item.fullName != typeName && item.isAssignableTo(typeName)) {
                    events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} implements/extends $typeName"))
                }
            }
        }

    private fun referenceDisallowedImplementation(
        portTypeName: String,
        allowed: Set<String>,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("허용 목록 밖의 $portTypeName 구현체를 참조한다 (허용 ${allowed.size} 종)") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.directDependenciesFromSelf
                    .map { it.targetClass }
                    .filter { target -> target.fullName != portTypeName && target.isAssignableTo(portTypeName) }
                    .filter { target -> target.fullName !in allowed }
                    .distinct()
                    .forEach { target ->
                        events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} -> ${target.fullName}"))
                    }
            }
        }

    private fun referenceAnyOf(
        forbidden: Set<String>,
        label: String,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("$label 을 참조한다 (${forbidden.size} 종)") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.directDependenciesFromSelf
                    .map { it.targetClass }
                    .filter { it.fullName in forbidden }
                    .distinct()
                    .forEach { target ->
                        events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} -> ${target.fullName}"))
                    }
            }
        }

    private fun referenceDisallowedInPackage(
        packagePrefix: String,
        allowed: Set<String>,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("$packagePrefix 안의 허용 목록 밖 타입을 참조한다 (허용 ${allowed.size} 종)") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.directDependenciesFromSelf
                    .map { it.targetClass }
                    .filter { it.packageName == packagePrefix || it.packageName.startsWith("$packagePrefix.") }
                    .filter { it.fullName !in allowed }
                    .distinct()
                    .forEach { target ->
                        events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} -> ${target.fullName}"))
                    }
            }
        }

    private fun callDisallowedPortMethod(
        ports: Set<String>,
        allowedPairs: Set<Pair<String, String>>,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>(
            "허용되지 않은 (호출자, 포트.메서드) 쌍으로 평가·전략 포트 메서드를 호출한다 " +
                "(포트 ${ports.size} 종, 허용 ${allowedPairs.size} 쌍)",
        ) {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.accessesFromSelf
                    .flatMap { access -> access.matchedPortCalls(ports) }
                    .filter { portMethod -> (item.fullName to portMethod) !in allowedPairs }
                    .distinct()
                    .forEach { portMethod ->
                        events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} -> $portMethod"))
                    }
            }
        }

    /**
     * **메서드 호출만 본다 — 생성자 호출은 제외한다.** [access] 가 [JavaMethodCall] 이 아니면
     * (조립 코드가 어댑터를 **생성**만 하고 넘기는 `JavaConstructorCall`) 빈 목록이다. 처음
     * 구현은 owner 의 `isAssignableTo` 만 봤는데, `EvaluationWiring` 이 `JdbcCandidateSource(...)`
     * 를 **짓기만** 해도 그 생성자 호출의 owner(`JdbcCandidateSource`)가 `CandidateSourcePort`
     * 에 assignable 이라 오탐이 났다(실측). 같은 이유로 `RecordingNotificationRequestPort.
     * requested()`(포트에 없는 어댑터 전용 메서드)도 owner 만 보면 오탐이었다 — 그래서 **포트가
     * 실제로 그 이름의 메서드를 선언하는지**까지 함께 본다. owner 자신과 그 상위(클래스·
     * 인터페이스) 후보 가운데 [ports] 에 속하면서 [access] 의 메서드 이름을 **직접 선언**하는
     * 포트만 판정 키("포트FQCN.메서드명")를 낸다 — owner 가 구체 구현 타입이어도(예:
     * `JdbcCandidateSource.openCandidates()`) 그 타입이 포트의 그 메서드를 구현하는 한
     * 걸린다(verifier N6).
     */
    private fun JavaAccess<*>.matchedPortCalls(ports: Set<String>): List<String> {
        if (this !is JavaMethodCall) return emptyList()
        val owner = targetOwner
        val declaringCandidates = listOf(owner) + owner.allRawSuperclasses + owner.allRawInterfaces
        return declaringCandidates
            .filter { candidate -> candidate.fullName in ports }
            .filter { portClass -> portClass.methods.any { method -> method.name == target.name } }
            .map { portClass -> "${portClass.fullName}.${target.name}" }
            .distinct()
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

        /** D-6A3-9 — `declaringKeys()`가 내는 `"owner#member"` 형태의 접두사. */
        const val ASSEMBLE_KERNEL_PREFIX = "bidvector.strategy.TextKt#assemble"
    }
}
