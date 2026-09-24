package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.nio.charset.StandardCharsets

/**
 * M6/6F-8 D-6F8 수집 배선 게이트 — `ArchitectureRules` 와 같은 형태(패키지 루트를 값으로 받아 production 을 지키는
 * 규칙을 fixture 루트에 그대로 적용한다)이고 전부 **집합**이다. 500줄 한도 때문에 별도 파일로 갈렸다.
 * 허용 집합은 전부 `architecture-policy.properties` 에서 온다.
 */
class CollectionArchitectureRules {
    /**
     * 우회 1 — 수집 use case 는 원문 필드를 직접 읽지 않는다. ① [collectionPackage] 가 [procurementPackage]
     * 에서 참조하는 **최상위 타입** 집합은 [allowedTypes] 의 부분집합이다(원문 키 접근 타입 —
     * `KonepsFieldContract`·`RawKey`·`FieldConcept` 등 — 을 이름 붙이지 못한다). ② [passThroughTypes] 는
     * 시그니처로만 옮기고 멤버는 건드리지 못한다(`RawNoticeObservation.valueOf` 류 0). ③ [forbiddenFieldTypes]
     * 를 필드로 가진 클래스가 없다(결과 타입이 원문·키를 나르지 않는다).
     */
    fun collectionMustNotReadRawFields(
        collectionPackage: String,
        procurementPackage: String,
        allowedTypes: Set<String>,
        passThroughTypes: Set<String>,
        forbiddenFieldTypes: Set<String>,
    ): List<ArchRule> =
        listOf(
            noClasses()
                .that()
                .resideInAPackage("$collectionPackage..")
                .should(referenceProcurementTypesOutside(procurementPackage, allowedTypes))
                .because("D-6F8-1 우회 1 — 수집 use case 의 procurement 참조 집합은 허용 집합의 부분집합이다"),
            noClasses()
                .that()
                .resideInAPackage("$collectionPackage..")
                .should(touchMembersOf(passThroughTypes))
                .because("D-6F8-1 우회 1 — 원문·정책·정규화 결과는 통과만 한다(멤버 접근 0)"),
            noClasses()
                .that()
                .resideInAPackage("$collectionPackage..")
                .should(declareFieldOfType(forbiddenFieldTypes))
                .because("D-6F8 (2b) — 수집 결과·계수 타입은 원문·관측 키를 나르지 않는다"),
        )

    /**
     * [collectionPackage] 가 [procurementPackage] 에서 **실제로** 참조하는 최상위 타입 집합 — 허용 집합에 낡은 항목이
     * 남아 게이트가 조용히 느슨해지는 것(허용됐지만 아무도 안 쓰는 타입)을 「허용 집합 == 관측 집합」 단언으로 막는다.
     */
    fun observedProcurementTypes(
        classes: com.tngtech.archunit.core.domain.JavaClasses,
        collectionPackage: String,
        procurementPackage: String,
    ): Set<String> =
        classes
            .filter { it.packageName == collectionPackage || it.packageName.startsWith("$collectionPackage.") }
            .flatMap { it.directDependenciesFromSelf }
            .map { it.targetClass.baseComponentType.topLevel() }
            .filter { it.packageName == procurementPackage || it.packageName.startsWith("$procurementPackage.") }
            .map { it.fullName }
            .toSet()

    /**
     * 우회 2 — [key](공고명 원시 키, 값은 필드 계약이 정한다)를 상수 풀에 가진 production 클래스 집합은
     * [allowedClasses] 의 부분집합이다. 클래스 파일 바이트를 읽는다(소스 텍스트가 아니라 컴파일 산출물 —
     * import 없이 쓴 리터럴·문자열 상수도 같은 자리에 남는다).
     */
    fun titleKeyLiteralMustStayInAllowedClasses(
        key: String,
        allowedClasses: Set<String>,
    ): List<ArchRule> =
        listOf(
            noClasses()
                .that(isOutside(allowedClasses))
                .should(containLiteralInClassFile(key))
                .because("D-6F8-2 우회 2 — 공고명 원시 키 리터럴은 계약 데이터를 실은 정책 클래스 밖에 없다"),
        )

    /**
     * 우회 4·5 — [types] 를 참조하는 [appRoot] 안의 클래스 집합은 [allowedReferencers] 의 부분집합이다(타입 자신은
     * 제외). 러너·서비스 키 설정·로거처럼 「쓰는 자리가 하나여야 하는」 타입에 쓴다.
     */
    fun appTypesMustBeReferencedOnlyBy(
        appRoot: String,
        types: Set<String>,
        allowedReferencers: Set<String>,
        reason: String,
    ): List<ArchRule> =
        listOf(
            noClasses()
                .that(isOutside(allowedReferencers))
                .and()
                .resideInAPackage("$appRoot..")
                .should(referenceAnyOf(types))
                .because(reason),
        )

    private fun isOutside(allowed: Set<String>) =
        object : com.tngtech.archunit.base.DescribedPredicate<JavaClass>("허용 집합 밖 클래스 (${allowed.size}종)") {
            override fun test(target: JavaClass): Boolean = target.fullName !in allowed
        }

    private fun referenceProcurementTypesOutside(
        procurementPackage: String,
        allowed: Set<String>,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("$procurementPackage 의 허용 밖 타입을 참조한다 (허용 ${allowed.size}종)") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.directDependenciesFromSelf
                    .map { it.targetClass.baseComponentType.topLevel() }
                    .filter {
                        it.packageName == procurementPackage ||
                            it.packageName.startsWith(
                                "$procurementPackage.",
                            )
                    }.filter { it.fullName !in allowed }
                    .distinct()
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} -> ${it.fullName}")) }
            }
        }

    private fun touchMembersOf(types: Set<String>): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("통과 전용 타입 ${types.size}종의 멤버(호출·필드·생성자)에 접근한다") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.accessesFromSelf
                    .filter { access -> access.targetOwner.topLevel().fullName in types }
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, it.describe())) }
            }
        }

    private fun declareFieldOfType(types: Set<String>): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("금지 타입 ${types.size}종의 필드를 가진다") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.fields
                    .filter { field ->
                        field.rawType.baseComponentType
                            .topLevel()
                            .fullName in types
                    }.forEach {
                        events.add(
                            SimpleConditionEvent.satisfied(item, "${item.fullName}.${it.name}: ${it.rawType.name}"),
                        )
                    }
            }
        }

    private fun containLiteralInClassFile(literal: String): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("클래스 파일 상수 풀에 리터럴 '$literal' 을 가진다") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val uri = item.source.orElse(null)?.uri ?: return
                val bytes = uri.toURL().openStream().use { it.readBytes() }
                if (String(bytes, StandardCharsets.ISO_8859_1).contains(literal)) {
                    events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} 상수 풀에 '$literal'"))
                }
            }
        }

    private fun referenceAnyOf(types: Set<String>): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("${types.size}종 타입 중 하나를 참조한다") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.directDependenciesFromSelf
                    .map { it.targetClass.baseComponentType }
                    .filter { it.fullName in types && it.fullName != item.fullName }
                    .distinct()
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} -> ${it.fullName}")) }
            }
        }

    /** 중첩 클래스(`Outer$Inner`)를 가장 바깥 클래스로 접는다 — 허용 집합은 최상위 이름으로 적는다. */
    private fun JavaClass.topLevel(): JavaClass = enclosingClass.map { it.topLevel() }.orElse(this)

    private fun JavaAccess<*>.describe(): String = "${origin.fullName} -> ${target.fullName}"
}
