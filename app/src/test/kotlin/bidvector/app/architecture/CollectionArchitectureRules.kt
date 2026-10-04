package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.nio.charset.StandardCharsets

/**
 * D-6F8 수집 배선 게이트 — `ArchitectureRules` 와 같은 형태(패키지 루트를 값으로 받아 production 을 지키는
 * 규칙을 fixture 루트에 그대로 적용한다)이고 전부 **집합**이다. 500줄 한도 때문에 별도 파일로 갈렸다.
 * 허용 집합은 전부 `architecture-policy.properties` 에서 온다.
 */
class CollectionArchitectureRules(
    private val collection: ReferenceCollection,
) {
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
        depth: ReferenceCollection,
    ): List<ArchRule> =
        listOf(
            noClasses()
                .that()
                .resideInAPackage("$collectionPackage..")
                .should(referenceProcurementTypesOutside(procurementPackage, allowedTypes, depth))
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
        classes: JavaClasses,
        collectionPackage: String,
        procurementPackage: String,
        depth: ReferenceCollection,
    ): Set<String> =
        classes
            .filter { it.packageName == collectionPackage || it.packageName.startsWith("$collectionPackage.") }
            .flatMap { origin -> origin.referencedTypeNames(depth) }
            .filter { it.startsWith("$procurementPackage.") }
            .toSet()

    /**
     * D-6F8-6 — 원문 값 획득의 **모듈 전체** 봉쇄. [roots] 아래 production **전체**(수집 패키지 밖 이웃 패키지·app
     * 포함)가 입력이다 — 이름·패키지 하나를 지키는 규칙은 한 걸음(헬퍼를 이웃 패키지로) 옮기면 열린다.
     * ① [rawAccessTypes](계약·레지스트리·원문 키·값·존재 판별·개념 토큰)를 참조하는 클래스는 [allowedReferencers] 의
     * 부분집합, ② [passThroughTypes] 의 멤버에 접근하는 클래스는 [allowedMemberAccessors] 의 부분집합이다.
     * 원문 값을 얻는 길은 `RawNoticeObservation` 의 멤버이므로 둘이 닫히면 procurement·adapters 밖에는 길이 없다.
     */
    fun moduleMustNotReadRawFields(
        roots: List<String>,
        rawAccessTypes: Set<String>,
        allowedReferencers: Set<String>,
        passThroughTypes: Set<String>,
        allowedMemberAccessors: Set<String>,
        depth: ReferenceCollection,
    ): List<ArchRule> {
        val rootPackages = roots.map { "$it.." }.toTypedArray()
        return listOf(
            noClasses()
                .that()
                .resideInAnyPackage(*rootPackages)
                .and(isOutside(allowedReferencers))
                .should(referenceAnyOf(rawAccessTypes, depth))
                .because("D-6F8-6 우회 1 — 원문 키 접근 타입 참조 집합은 허용 집합의 부분집합이다(모듈 전체)"),
            noClasses()
                .that()
                .resideInAnyPackage(*rootPackages)
                .and(isOutside(allowedMemberAccessors))
                .should(touchMembersOf(passThroughTypes))
                .because("D-6F8-6 우회 1 — 통과 전용 타입의 멤버 접근 집합은 허용 집합의 부분집합이다(모듈 전체)"),
        )
    }

    /**
     * D-6F8-13 · A-2(D-6G2b-6) — 리플렉션으로 원문 값을 얻는 길(`javaClass.getMethod("getSourceText").invoke(...)`)은
     * 원문 타입을 이름 붙이지 않아 위 규칙 둘이 못 본다. 그래서 값 획득 규칙을 **타입 이름이 아니라 반사 표면**으로도 닫는다:
     * [roots] 아래 production 이 ① [reflectionPackages] 의 타입을 [allowedTypePairs] 밖에서 참조하지 못하고,
     * ② [classType] 의 멤버를 [allowedMemberPairs] 밖에서 부르지 못한다.
     *
     * 허용은 **(클래스, 타입)·(클래스, 멤버) 쌍**이다(A-2 운영자 결정). 멤버 이름만 전역으로 허용하면
     * (앞 판의 `getName`) 어느 클래스든 그 이름을 쓸 수 있고, 클래스만 허용하면 등재된 클래스가 **새 반사
     * 멤버를 더 부르는** 길이 열린다 — 쌍은 둘 다 닫는다. 쌍이 없으면 기본 거부다.
     */
    fun moduleMustNotUseReflection(
        roots: List<String>,
        reflectionPackages: Set<String>,
        allowedTypePairs: Set<Pair<String, String>>,
        classType: String,
        allowedMemberPairs: Set<Pair<String, String>>,
    ): List<ArchRule> {
        val rootPackages = roots.map { "$it.." }.toTypedArray()
        return listOf(
            noClasses()
                .that()
                .resideInAnyPackage(*rootPackages)
                .should(referenceReflectionOutsidePairs(reflectionPackages, allowedTypePairs))
                .because("D-6F8-13 F2-2 · A-2 — (클래스, 리플렉션 타입) 쌍은 등재된 쌍뿐이다"),
            noClasses()
                .that()
                .resideInAnyPackage(*rootPackages)
                .should(accessClassMembersOutsidePairs(classType, allowedMemberPairs))
                .because("D-6F8-13 F2-2 · A-2 — (클래스, Class 멤버) 쌍은 등재된 쌍뿐이다(새 반사 멤버는 기본 거부)"),
        )
    }

    /** [roots] 아래에서 관측한 (최상위 클래스, [packages] 의 타입) 쌍 전수 — 등재 집합과 같아야 한다. */
    fun observedReflectionTypePairs(
        classes: JavaClasses,
        roots: List<String>,
        packages: Set<String>,
    ): Set<Pair<String, String>> =
        classes
            .filter { inRoots(it, roots) }
            .flatMap { origin ->
                origin
                    .referencedTypeNames(collection)
                    .filter { isInPackageNames(it, packages) }
                    .map { origin.outermostClassName() to it }
            }.toSet()

    /** [roots] 아래에서 관측한 (최상위 클래스, [classType] 의 멤버) 쌍 전수 — 등재 집합과 같아야 한다. */
    fun observedClassMemberPairs(
        classes: JavaClasses,
        roots: List<String>,
        classType: String,
    ): Set<Pair<String, String>> =
        classes
            .filter { inRoots(it, roots) }
            .flatMap { origin ->
                origin.accessesFromSelf
                    .filter { it.targetOwner.fullName == classType }
                    .map { origin.outermostClassName() to it.name }
            }.toSet()

    /** [roots] 아래 클래스 가운데 [types] 를 참조하는 것의 최상위 클래스 이름 집합 — 허용 집합과 같아야 한다. */
    fun observedReferencers(
        classes: JavaClasses,
        roots: List<String>,
        types: Set<String>,
        depth: ReferenceCollection,
    ): Set<String> =
        classes
            .filter { inRoots(it, roots) }
            .filter { origin ->
                origin.referencedTypeNames(depth).any { it in types && it != origin.outermostClassName() }
            }.map { it.outermostClassName() }
            .toSet()

    /** [roots] 아래 클래스 가운데 [types] 의 멤버에 접근하는 것의 최상위 클래스 이름 집합 — 허용 집합과 같아야 한다. */
    fun observedMemberAccessors(
        classes: JavaClasses,
        roots: List<String>,
        types: Set<String>,
    ): Set<String> =
        classes
            .filter { inRoots(it, roots) }
            .filter { origin -> origin.accessesFromSelf.any { it.targetOwner.outermostClassName() in types } }
            .map { it.outermostClassName() }
            .toSet()

    /** 타입 **이름**이 [packages] 뿌리 안인가 — 쌍 등식과 규칙이 같은 술어를 쓴다. */
    private fun isInPackageNames(
        type: String,
        packages: Set<String>,
    ): Boolean = packages.any { type.startsWith("$it.") }

    private fun referenceReflectionOutsidePairs(
        packages: Set<String>,
        registered: Set<Pair<String, String>>,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("등재 밖 리플렉션 타입을 참조한다 (패키지 ${packages.size}종 · 등재 쌍 ${registered.size})") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val referencer = item.outermostClassName()
                item
                    .referencedTypeNames(collection)
                    .filter { isInPackageNames(it, packages) }
                    .filterNot { referencer to it in registered }
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, "$referencer -> $it")) }
            }
        }

    private fun accessClassMembersOutsidePairs(
        classType: String,
        registered: Set<Pair<String, String>>,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("$classType 의 등재 밖 멤버에 접근한다 (등재 쌍 ${registered.size})") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val accessor = item.outermostClassName()
                item.accessesFromSelf
                    .filter { it.targetOwner.fullName == classType && accessor to it.name !in registered }
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, "$accessor -> $classType.${it.name}")) }
            }
        }

    private fun inRoots(
        item: JavaClass,
        roots: List<String>,
    ): Boolean = roots.any { item.packageName == it || item.packageName.startsWith("$it.") }

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
     * D-6F9-2 우회 4 — 업무구분 세부 분류 **원시 키 리터럴들**([keys], 값은 그 개념들의 필드 계약이 정한다)을 상수 풀에 가진
     * production 클래스 집합은 키마다 [allowedClasses] 의 부분집합이다. 공고명 키 게이트([titleKeyLiteralMustStayInAllowedClasses])와
     * 같은 형태의 집합 규칙 — 키마다 규칙을 따로 내서 위반 상세가 어느 키 때문인지 가른다.
     */
    fun classificationKeyLiteralsMustStayInAllowedClasses(
        keys: Set<String>,
        allowedClasses: Set<String>,
    ): List<ArchRule> =
        keys.sorted().map { key ->
            noClasses()
                .that(isOutside(allowedClasses))
                .should(containLiteralInClassFile(key))
                .because("D-6F9-2 우회 4 — 업무구분 세부 분류 원시 키 리터럴은 계약 데이터를 실은 파일 클래스 밖에 없다")
        }

    /** [keys] 중 하나라도 상수 풀에 가진 production 클래스(최상위 이름) 집합 — 허용 집합과 **같아야** 한다(낡은 항목 금지). */
    fun classesContainingAnyLiteral(
        production: JavaClasses,
        keys: Set<String>,
    ): Set<String> =
        production
            .filter { item -> keys.any { constantPoolContains(item, it) } }
            .map { it.outermostClassName() }
            .toSet()

    /**
     * 우회 4·5 — [types] 를 참조하는 [appRoot] 안의 클래스 집합은 [allowedReferencers] 의 부분집합이다(타입 자신은
     * 제외). 러너·서비스 키 설정·로거처럼 「쓰는 자리가 하나여야 하는」 타입에 쓴다.
     */
    fun appTypesMustBeReferencedOnlyBy(
        appRoot: String,
        types: Set<String>,
        allowedReferencers: Set<String>,
        reason: String,
        depth: ReferenceCollection,
    ): List<ArchRule> =
        listOf(
            noClasses()
                .that(isOutside(allowedReferencers))
                .and()
                .resideInAPackage("$appRoot..")
                .should(referenceAnyOf(types, depth))
                .because(reason),
        )

    private fun isOutside(allowed: Set<String>) =
        object : com.tngtech.archunit.base.DescribedPredicate<JavaClass>("허용 집합 밖 클래스 (${allowed.size}종)") {
            override fun test(target: JavaClass): Boolean = target.outermostClassName() !in allowed
        }

    private fun referenceProcurementTypesOutside(
        procurementPackage: String,
        allowed: Set<String>,
        depth: ReferenceCollection,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("$procurementPackage 의 허용 밖 타입을 참조한다 (허용 ${allowed.size}종)") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item
                    .referencedTypeNames(depth)
                    .filter { it.startsWith("$procurementPackage.") }
                    .filterNot { it in allowed }
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} -> $it")) }
            }
        }

    private fun touchMembersOf(types: Set<String>): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("통과 전용 타입 ${types.size}종의 멤버(호출·필드·생성자)에 접근한다") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.accessesFromSelf
                    .filter { access -> access.targetOwner.outermostClassName() in types }
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
                            .outermostClassName() in types
                    }.forEach {
                        events.add(
                            SimpleConditionEvent.satisfied(item, "${item.fullName}.${it.name}: ${it.rawType.name}"),
                        )
                    }
            }
        }

    /**
     * 클래스 파일 바이트를 ISO-8859-1 로 읽어 [literal] 을 **부분 문자열**로 찾는다(상수 풀 항목 단위
     * 파싱이 아니다). 그래서 어떤 계약 키가 허용 클래스에 있는 다른 키의 부분 문자열이면 그 개념이 허위로
     * 「허용 클래스에 있다」로 판정되어 개념 집합을 무관한 개념까지 넓혀야 초록이 된다 — 오늘 등재된 키들은 서로
     * 부분 문자열이 아니라 잠재적 한계다. 반대 방향(위반을 놓치는 쪽)은 생기지 않는다:
     * 리터럴이 상수 풀에 있으면 바이트열에도 반드시 있다. 항목 단위 일치가 필요해지면 `CONSTANT_Utf8` 파싱으로 좁힌다.
     */
    private fun constantPoolContains(
        item: JavaClass,
        literal: String,
    ): Boolean {
        val uri = item.source.orElse(null)?.uri ?: return false
        val bytes = uri.toURL().openStream().use { it.readBytes() }
        return String(bytes, StandardCharsets.ISO_8859_1).contains(literal)
    }

    private fun containLiteralInClassFile(literal: String): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("클래스 파일 상수 풀에 리터럴 '$literal' 을 가진다") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                if (constantPoolContains(item, literal)) {
                    events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} 상수 풀에 '$literal'"))
                }
            }
        }

    private fun referenceAnyOf(
        types: Set<String>,
        depth: ReferenceCollection,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("${types.size}종 타입 중 하나를 참조한다") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item
                    .referencedTypeNames(depth)
                    .filter { it in types && it != item.outermostClassName() }
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, "${item.fullName} -> $it")) }
            }
        }

    private fun JavaAccess<*>.describe(): String = "${origin.fullName} -> ${target.fullName}"
}
