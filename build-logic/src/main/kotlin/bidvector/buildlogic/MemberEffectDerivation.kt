package bidvector.buildlogic

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaCodeUnit
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter

/**
 * **효과 표면** — 「부작용」을 좌표로 적은 것. 목록의 출처는 승인 문면의 어휘다
 * (`milestone-1.md` 「구현 규칙」의 I/O · `v2-지침서.md` §3.1 의 경계), 우리의 상상이 아니다.
 */
internal class EffectSurface(
    private val packages: List<String>,
    private val classes: Set<String>,
) {
    /** 표면에 닿으면 그 좌표를, 아니면 null. 배열은 원소 타입으로 본다. */
    fun match(type: JavaClass): String? {
        val name = (if (type.isArray) type.baseComponentType else type).name
        val onSurface = name in classes || packages.any { name == it || name.startsWith("$it.") }
        return name.takeIf { onSurface }
    }
}

internal class MemberEffect(
    val key: String,
    val surface: String,
)

/**
 * 허용 클래스의 멤버 가운데 **효과 표면에 닿는 것**을 도출한다.
 *
 * 왜 도출인가 — 「어떤 멤버가 위험한가」를 손으로 열거하면 라운드마다 목록이 늘고(여섯 라운드
 * 연속 늘었다) 생각해 내지 못한 멤버는 조용히 열린다. 반대로 **여집합**(허용 목록 밖에 닿으면
 * 불순)은 무너진다 — `ArrayList#add` 가 `System#arraycopy` 를, `String#substring` 이
 * `jdk.internal` 을 부르므로 컬렉션·문자열이 전부 불순이 된다(설계 검토 실측).
 * 그래서 금지 표면을 **효과 어휘**로 고정하고 거기 닿는 멤버를 낸다.
 *
 * 키는 **`선언클래스#멤버이름`**이다. 디스크립터가 아닌 이유는 위임으로 효과를 감추는 오버로드를
 * 이름이 흡수하기 때문이다 — `printStackTrace()` 는 `System.err` 로 쓰고
 * `printStackTrace(PrintWriter)` 는 서명으로 드러나는데, 둘은 같은 이름이다.
 *
 * 이 도출은 **게이트가 아니라 래칫**이다. 나온 후보는 사람이 한 번 분류하고
 * (`member-effects.properties`), 미분류가 남으면 빌드가 실패한다.
 */
internal fun deriveMemberEffects(
    allowedClasses: List<String>,
    surface: EffectSurface,
): List<MemberEffect> {
    val signature = sortedMapOf<String, MutableSet<String>>()
    val body = sortedMapOf<String, MutableSet<String>>()
    importWithSupertypes(allowedClasses).forEach { type ->
        type.codeUnits.filter { it.isVisibleOutside() }.forEach { unit ->
            val key = "${type.name}#${unit.name}"
            unit.signatureTypes().mapNotNull(surface::match).forEach { signature.hit(key, it) }
            unit.accessesFromSelf.mapNotNull { surface.match(it.targetOwner) }.forEach { body.hit(key, it) }
        }
    }
    return (signature.keys + body.keys).sorted().map { key ->
        // 서명을 먼저 든다 — 호출자가 표면 타입을 **이름 붙여야** 하는 자리라 T-B·T-C 가 이미
        // 닫고 있을 수 있고, 그 구분이 분류의 근거가 된다. 같은 층에서는 사전순 최소를 고른다
        // (오버로드 순회 순서에 값이 흔들리면 재생성 대조가 무의미해진다).
        val hit = signature[key]?.first()?.let { "signature:$it" } ?: "body:${body.getValue(key).first()}"
        MemberEffect(key, hit)
    }
}

internal fun renderMemberEffects(
    effects: List<MemberEffect>,
    jdkVersion: String,
): String =
    buildString {
        appendLine("# 생성물이다 — `memberEffectGate` 가 낸다. 손으로 고치지 않는다.")
        appendLine("# 분류는 `member-effects.properties` 가 갖는다. 이 파일이 바뀌면 그쪽도 바뀌어야 한다.")
        appendLine("jdk.version=$jdkVersion")
        effects.forEach { appendLine("${it.key}=${it.surface}") }
    }

/**
 * 허용 클래스와 **그 상위 타입**을 임포트한다. 상속으로 들어오는 멤버의 선언 클래스가 허용
 * 목록에 없을 수 있다 — `ArrayList#toArray` 의 일부는 `AbstractCollection` 이 선언한다.
 */
private fun importWithSupertypes(names: List<String>): Set<JavaClass> {
    val seeds = names.map(::loadClass)
    val withSupertypes = LinkedHashSet(seeds)
    ClassFileImporter().importClasses(seeds).forEach { type ->
        (type.allRawSuperclasses + type.allRawInterfaces).forEach { withSupertypes += loadClass(it.name) }
    }
    return ClassFileImporter().importClasses(withSupertypes).toSet()
}

private fun loadClass(name: String): Class<*> =
    runCatching { Class.forName(name) }
        .getOrElse { error("효과 도출: 클래스 '$name' 을 읽을 수 없다 — 정책의 좌표가 이 JDK 에 없다") }

private fun JavaCodeUnit.isVisibleOutside(): Boolean =
    JavaModifier.PUBLIC in modifiers || JavaModifier.PROTECTED in modifiers

private fun JavaCodeUnit.signatureTypes(): List<JavaClass> =
    rawParameterTypes + listOfNotNull((this as? JavaMethod)?.rawReturnType)

private fun MutableMap<String, MutableSet<String>>.hit(
    key: String,
    surface: String,
) {
    getOrPut(key) { sortedSetOf() } += surface
}
