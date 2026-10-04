package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaCodeUnit
import com.tngtech.archunit.core.domain.JavaModifier
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
 * 한 클래스가 **실제로 참조하는** 타입 이름 전수 — 의존 그래프 + [ReferenceCollection.FULL] 에서는 호출
 * 대상(호출·메서드 참조)의 소유·인자·반환 타입과 필드 접근의 필드 타입까지. 이름은 배열을 벗기고
 * [outermostName] 으로 접은 것이다.
 *
 * **정의가 하나다** — 전송 표면 · 바깥 참조 · 반사 세 게이트가 같은 함수를 쓴다. 수집 범위를 좁히는 편집이
 * 몇 개를 붉게 만드는지는 축마다 다르다(KA1 의 구멍이 이 정의에 있었다):
 *
 *  - **전송 쌍** — production 에서 깊은 수집이 쌍 14 를 더하므로 양성·음성 양쪽 대조가 든다.
 *  - **바깥 참조** — production 관측이 같아 **양성 쪽은 들지 않는다**. 음성 fixture 쪽 대조가 든다.
 *  - **반사** — 깊은 수집이 더하는 쌍이 0 이라 양쪽 다 들지 않는다. 그 사실을 대조 test 가 적어 둔다.
 */
internal fun JavaClass.referencedTypeNames(collection: ReferenceCollection): Set<String> {
    val found = linkedSetOf<String>()

    fun collect(type: JavaClass) {
        found += type.baseComponentType.fullName.outermostName()
    }

    directDependenciesFromSelf.forEach { collect(it.targetClass) }
    if (collection == ReferenceCollection.FULL) {
        codeUnitAccessesFromSelf.forEach { access ->
            collect(access.targetOwner)
            access.target.rawParameterTypes.forEach(::collect)
            collect(access.target.rawReturnType)
        }
        fieldAccessesFromSelf.forEach { collect(it.target.rawType) }
    }
    return found
}

/**
 * 중첩 클래스를 가장 바깥 클래스로 접는다 — **이름의 첫 `$` 앞까지** 자른다(cr M-1).
 *
 * ArchUnit 의 `enclosingClass` 로 접으면 결과가 **해소 여부에 달린다**: 호출 대상 소유 타입으로 등장한
 * JDK 클래스는 classpath 에서 해소되어 접히고, 인자·반환 타입으로만 등장한 클래스는 서술자에서 만든
 * 자리표라 접히지 않았다. 그래서 전송 표면을 하나도 늘리지 않는 편집(`BodyHandler` 에 메서드 하나를
 * 부르는 것)이 등재 철자를 바꿔 등식을 깼다. 이름 기준 절단은 그 결합을 끊는다.
 *
 * 접지 않는 선택지도 쟀다 — Kotlin 합성 람다 클래스 이름이 정책 파일에 들어와 더 자주 낡는다.
 *
 * **D-6G2g-14 — 저장소의 접기는 이제 이 함수 하나다**(`OPEN-6G2B-FOLDING-UNIFICATION` 종결). 앞서 관례가
 * 셋이었다: 접지 않음(`collection.key-hash.holders` 의 `NoticeKeyHash$Companion` ·
 * `app.injection.allowed-types` 의 `Resolution$Resolved`) · 이름 절단(쌍 등식 셋) · `enclosingClass` 접기
 * (`outermostClass()` 와 그것을 각자 복사한 `topLevel()` 셋). `enclosingClass` 접기는 위 cr M-1 의 결함을
 * 그대로 안고 있어 그쪽으로 통일할 수 없고, 접지 않는 둘은 중첩 이름을 등재에 남긴다. 그래서 이름 절단
 * 하나로 모으고 나머지를 지웠다 — 재등재 둘은 아래 두 키에서 중첩 접미가 떨어진 것이다.
 */
internal fun String.outermostName(): String = substringBefore('$')

/** [outermostName] 의 `JavaClass` 판 — 보유자 쪽 이름을 같은 규칙으로 접는다. */
internal fun JavaClass.outermostClassName(): String = fullName.outermostName()

/**
 * D-6G2b-1·2·3 — **관문 밖으로 바이트를 내는 길**을 (클래스, 전송 표면 타입) 쌍의 정확 집합으로 닫는다.
 *
 * 금지는 타입 이름 열거가 아니라 [surfacePackages] **패키지 뿌리**다 — 그 패키지에 새 타입이 생겨도
 * 닫혀 있다. [surfaceTypes] 는 뿌리로 금지할 수 없는 자리(`java.lang`)의 낱개 타입이다.
 * 허용은 등재 쌍과의 등식이고, 쌍이라 등재된 보유자가 **새 전송 타입을 더 쥐는** 것도 붉어진다.
 */
class TransportSurfaceRules(
    private val packageRoot: String,
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
            .flatMap { origin -> transportTypesOf(origin).map { origin.outermostClassName() to it } }
            .toSet()

    /**
     * D-6G2b-22(vr H-1) — **바깥 참조의 기본 거부**. 전송 표면을 「금지 뿌리 열거」로 두면 목록 밖 패키지가
     * 네트워크를 열 때 처음부터 대상이 아니다(`java.util.logging.SocketHandler` · `javax.xml.parsers` ·
     * `javax.management.remote` · `javax.swing` 변이가 전부 초록이었다). 그래서 방향을 뒤집는다:
     * [moduleRoot] 아래 production 이 참조하는 `bidvector..` 밖 타입은 **그 패키지가 [allowedPackages] 에
     * 있어야** 한다.
     *
     * **두 층이다.** 타입이 전송 표면이면([isSurfaceType]) 이 층을 보지 않고 (클래스, 타입) 쌍 등식만
     * 본다 — 그래서 `java.lang` 이 허용 패키지여도 `java.lang.ProcessBuilder` 는 쌍 층에서 붉고, 전송
     * 뿌리는 허용 집합에 들어가지 않는다.
     *
     * 허용은 **정확한 패키지 이름**이다(접두 뿌리가 아니다). 접두로 두면 `java.util` 이 `java.util.logging`
     * 을, `javax.xml` 이 `javax.xml.parsers` 를 끌고 들어와 거부 하위를 또 열거해야 한다 — 그 열거가 바로
     * H-1 이 벌한 방향이다.
     */
    fun externalReferenceRules(
        moduleRoot: String,
        allowedPackages: Set<String>,
    ): List<ArchRule> =
        listOf(
            noClasses()
                .that()
                .resideInAnyPackage("$moduleRoot..")
                .should(referenceExternalPackageOutside(allowedPackages))
                .because("D-6G2b-22 — $moduleRoot 의 바깥 참조는 허용 패키지 집합 안뿐이다(기본 거부)"),
        )

    /** [moduleRoot] 아래에서 관측한 **바깥 패키지** 전수(전송 표면 타입 제외) — 허용 집합과 같아야 한다. */
    fun observedExternalPackages(
        classes: JavaClasses,
        moduleRoot: String,
    ): Set<String> =
        classes
            .filter { it.packageName == moduleRoot || it.packageName.startsWith("$moduleRoot.") }
            .flatMap { origin -> externalPackagesOf(origin) }
            .toSet()

    /**
     * 한 클래스가 참조하는 바깥 패키지 이름 — `bidvector..` 와 전송 표면 타입과 **원시 타입**을 뺀다.
     * 전송 표면을 빼는 것이 두 층의 분기다.
     *
     * 원시 타입은 [PRIMITIVE_TYPE_NAMES] 로 **이름을 보고** 가른다(cr r2 L-7). 앞 판은 「패키지가 비면
     * 버린다」였는데 그러면 **무패키지(default package) 클래스**도 같이 조용히 사라졌다. 지금은 그런 타입을
     * [NO_PACKAGE] 로 돌려주므로 허용 집합에 들 수 없고(허용은 패키지 이름이다) 신고된다 — production 관측은
     * 0 이라 오늘 결과는 같다.
     */
    private fun externalPackagesOf(origin: JavaClass): Set<String> =
        origin
            .referencedTypeNames(collection)
            .filterNot { it.startsWith("$packageRoot.") || it == packageRoot }
            .filterNot(::isSurfaceType)
            .filterNot { it in PRIMITIVE_TYPE_NAMES }
            .map { it.substringBeforeLast('.', NO_PACKAGE) }
            .toSet()

    private fun referenceExternalPackageOutside(allowed: Set<String>): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("허용 밖 바깥 패키지를 참조한다 (허용 ${allowed.size}종)") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val referencer = item.outermostClassName()
                externalPackagesOf(item)
                    .filterNot { it in allowed }
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, "$referencer -> $it")) }
            }
        }

    /**
     * D-6G2g-9 — **3층(클래스, 멤버)**. 2층의 (클래스, 타입) 쌍은 등재 보유자 **안**을 보지 못한다:
     * 보유자가 `fun sendRaw(s: String)` 처럼 **시그니처에 전송 타입이 없는** 멤버를 두면, 그 멤버를
     * 반사로 부르는 길이 (클래스, 타입) 해상도 밖에 남는다(`OPEN-6G2B-HOLDER-INTERNAL-SURFACE`,
     * 6G-2c vr I-1 의 `release$bid_vector_adapters` 자리).
     *
     * 그래서 보유자의 **비-private 멤버 전수** 가운데 「시그니처는 전송 타입을 말하지 않는데 몸이 전송
     * 멤버를 부르는」 것을 세어 등재와 양방향으로 맞댄다. 그런 멤버가 하나 생기면 등재가 함께 움직여야
     * 초록이다.
     *
     * **깊이는 1 이다**(`memberEffectGate` 의 「서명 ∪ depth-1 본문」 관례). 같은 클래스의 다른 멤버를
     * 거쳐 부르는 두 걸음은 이 층이 재지 못한다 — 경계 문장에 등재한다.
     */
    fun observedMemberSurface(
        classes: JavaClasses,
        holders: Set<String>,
    ): Set<Pair<String, String>> =
        classes
            .filter { it.outermostClassName() in holders }
            .flatMap(::hiddenSendMembers)
            .toSet()

    /** [roots] 아래 등재 보유자가 등재 밖 숨은 송신 멤버를 두지 않는다. */
    fun memberSurfaceRules(
        roots: List<String>,
        holders: Set<String>,
        registeredMembers: Set<Pair<String, String>>,
    ): List<ArchRule> =
        listOf(
            noClasses()
                .that()
                .resideInAnyPackage(*roots.map { "$it.." }.toTypedArray())
                .should(holdHiddenSendMemberOutside(holders, registeredMembers))
                .because("D-6G2g-9 — 등재 보유자의 (클래스, 멤버) 쌍은 등재된 쌍뿐이다(3층)"),
        )

    private fun hiddenSendMembers(origin: JavaClass): List<Pair<String, String>> =
        (origin.methods + origin.constructors)
            .filterNot { JavaModifier.PRIVATE in it.modifiers }
            .filterNot(::signatureNamesSurface)
            .filter(::bodyCallsSurface)
            .map { origin.outermostClassName() to it.name }

    /** 시그니처가 전송 타입을 말하는가 — 말하면 2층의 쌍 등식이 이미 그 자리를 든다. */
    private fun signatureNamesSurface(member: JavaCodeUnit): Boolean =
        (member.rawParameterTypes.map(JavaClass::getName) + member.rawReturnType.name)
            .any { isSurfaceType(it.outermostName()) }

    private fun bodyCallsSurface(member: JavaCodeUnit): Boolean =
        member.accessesFromSelf.any { isSurfaceType(it.targetOwner.outermostClassName()) }

    private fun holdHiddenSendMemberOutside(
        holders: Set<String>,
        registered: Set<Pair<String, String>>,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("등재 밖 숨은 송신 멤버를 둔다 (등재 쌍 ${registered.size})") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                if (item.outermostClassName() !in holders) return
                hiddenSendMembers(item)
                    .filterNot { it in registered }
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, "${it.first}#${it.second}")) }
            }
        }

    /** 등재된 쌍의 타입이 전송 표면 술어 안에 있는지 — 술어 밖 타입을 등재해 집합을 채우는 길을 막는다. */
    fun isSurfaceType(type: String): Boolean = type in surfaceTypes || surfacePackages.any { type.startsWith("$it.") }

    private fun transportTypesOf(origin: JavaClass): Set<String> =
        origin.referencedTypeNames(collection).filterTo(linkedSetOf(), ::isSurfaceType)

    private fun referenceTransportSurfaceOutside(registered: Set<Pair<String, String>>): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>(
            "등재 밖 전송 표면 타입을 참조한다 (뿌리 ${surfacePackages.size}종 · 낱개 ${surfaceTypes.size}종 · 등재 쌍 ${registered.size})",
        ) {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val holder = item.outermostClassName()
                transportTypesOf(item)
                    .filterNot { holder to it in registered }
                    .forEach { events.add(SimpleConditionEvent.satisfied(item, "$holder -> $it")) }
            }
        }

    private companion object {
        /** JVM 원시 타입과 `void` — 패키지가 없는 이름이지만 클래스가 아니다(바깥 참조 판정 밖). */
        val PRIMITIVE_TYPE_NAMES =
            setOf("boolean", "byte", "char", "short", "int", "long", "float", "double", "void")

        /** 무패키지 클래스를 가리키는 자리표 — 허용 집합의 어떤 패키지 이름과도 같지 않아 신고된다. */
        const val NO_PACKAGE = "<무패키지>"
    }
}
