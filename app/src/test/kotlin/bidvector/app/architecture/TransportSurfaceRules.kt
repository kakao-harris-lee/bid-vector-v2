package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * 참조를 모으는 범위. [OWNER_ONLY] 는 ArchUnit 의존 그래프 그대로 — **호출 대상의 소유 타입**까지만
 * 본다. [FULL] 은 거기에 **호출 대상의 인자·반환 타입**과 필드 접근의 필드 타입을 더한다.
 *
 * [OWNER_ONLY] 는 쓰이는 게이트가 아니라 **양성 대조**다: `uri.toURL().readText()` 처럼 타입을 쥐지 않고
 * 호출 사슬로만 지나는 길은 [OWNER_ONLY] 에 보이지 않는다. [FULL] 이 조용히 [OWNER_ONLY] 로 되돌려지면
 * 그 대조가 RED 가 된다.
 */
enum class ReferenceCollection { OWNER_ONLY, FULL }

/**
 * D-6G2b-1·2·3 — **관문 밖으로 바이트를 내는 길**을 (클래스, 전송 표면 타입) 쌍의 정확 집합으로 닫는다.
 *
 * 금지는 타입 이름 열거가 아니라 [surfacePackages] **패키지 뿌리**다 — 그 패키지에 새 타입이 생겨도
 * 닫혀 있다. [surfaceTypes] 는 뿌리로 금지할 수 없는 자리(`java.lang`)의 낱개 타입이다.
 * 허용은 등재 쌍과의 등식이고, 쌍이라 등재된 보유자가 **새 전송 타입을 더 쥐는** 것도 붉어진다.
 */
class TransportSurfaceRules(
    private val surfacePackages: Set<String>,
    private val surfaceTypes: Set<String>,
    private val collection: ReferenceCollection = ReferenceCollection.FULL,
) {
    /** [roots] 아래 클래스가 등재 밖 전송 표면 타입을 참조하지 않는다. */
    fun rules(
        roots: List<String>,
        registeredPairs: Set<Pair<String, String>>,
    ): List<ArchRule> =
        listOf(
            noClasses()
                .that()
                .resideInAnyPackage(*roots.map { "$it.." }.toTypedArray())
                .should(referenceTransportSurfaceOutside(registeredPairs))
                .because("D-6G2b-1·2·3 — (클래스, 전송 표면 타입) 쌍은 등재된 쌍뿐이다"),
        )

    /** [roots] 아래에서 **관측한** (최상위 클래스, 전송 표면 타입) 쌍 전수 — 등재 집합과 같아야 한다. */
    fun observedPairs(
        classes: JavaClasses,
        roots: List<String>,
    ): Set<Pair<String, String>> =
        classes
            .filter { origin -> roots.any { origin.packageName == it || origin.packageName.startsWith("$it.") } }
            .flatMap { origin -> transportTypesOf(origin).map { origin.topLevel().fullName to it } }
            .toSet()

    /** 등재된 쌍의 타입이 전송 표면 술어 안에 있는지 — 술어 밖 타입을 등재해 집합을 채우는 길을 막는다. */
    fun isSurfaceType(type: String): Boolean =
        type in surfaceTypes || surfacePackages.any { type == it || type.startsWith("$it.") }

    private fun transportTypesOf(origin: JavaClass): Set<String> {
        val found = linkedSetOf<String>()

        fun collect(type: JavaClass) {
            val name = type.baseComponentType.topLevel().fullName
            if (isSurfaceType(name)) found += name
        }

        origin.directDependenciesFromSelf.forEach { collect(it.targetClass) }
        if (collection == ReferenceCollection.FULL) {
            origin.codeUnitAccessesFromSelf.forEach { access ->
                collect(access.targetOwner)
                access.target.rawParameterTypes.forEach(::collect)
                collect(access.target.rawReturnType)
            }
            origin.fieldAccessesFromSelf.forEach { collect(it.target.rawType) }
        }
        return found
    }

    private fun referenceTransportSurfaceOutside(registered: Set<Pair<String, String>>): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>(
            "등재 밖 전송 표면 타입을 참조한다 (뿌리 ${surfacePackages.size}종 · 낱개 ${surfaceTypes.size}종 · 등재 쌍 ${registered.size})",
        ) {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val holder = item.topLevel().fullName
                transportTypesOf(item)
                    .filterNot { holder to it in registered }
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, "$holder -> $it")) }
            }
        }

    /** 중첩 클래스(`Outer$Inner`)를 가장 바깥 클래스로 접는다 — 등재는 최상위 이름으로 적는다. */
    private fun JavaClass.topLevel(): JavaClass = enclosingClass.map { it.topLevel() }.orElse(this)
}
