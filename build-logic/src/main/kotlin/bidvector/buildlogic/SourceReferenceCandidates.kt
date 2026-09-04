package bidvector.buildlogic

// [SourceReferences] 의 후보 조립 — `SourceReferences.kt` 와 같은 detekt `TooManyFunctions`
// 예산을 나눠 쓴다(v2-지침서.md §5 「크기와 결합도」, `PublicApiTypes`/`PublicApiTypeSurface`
// 분리와 같은 이유).

/**
 * 소문자 세그먼트를 접두로 모으고 첫 대문자 세그먼트까지가 후보다(설계 검토 §4 단계 2·3).
 * 대문자 세그먼트가 이어지면 `$` 로 이어 붙인 형태도 함께 낸다 — **전부** 허용이어야
 * 한다(`java.util.Map`·`java.util.Map$Entry` 를 둘 다 재는 이유, 단계 4).
 *
 * 첫 세그먼트가 대문자면 단순 이름 참조라 건너뛴다 — import 나 같은 파일 선언이 이미 푼다.
 * 대문자 세그먼트가 아예 없으면 값 체인이라 건너뛴다.
 *
 * **뿌리 다음 세그먼트를 잇는 판별은 존재다**(verifier r19 M-1·M-3 — [nestedSegments] 참고).
 */
internal fun candidateForms(segments: List<String>): List<String> {
    val typeIndex = segments.indexOfFirst { it.startsWithUpper() }
    val isSimpleNameOrValueChain = segments.isEmpty() || segments.first().startsWithUpper() || typeIndex < 0
    if (isSimpleNameOrValueChain) return emptyList()

    val prefix = segments.subList(0, typeIndex).joinToString(".")
    val root = segments[typeIndex]
    val typeSegments = listOf(root) + nestedSegments(prefix, root, segments.subList(typeIndex + 1, segments.size))
    return typeSegments.indices.map { i -> "$prefix." + typeSegments.subList(0, i + 1).joinToString("$") }
}

/**
 * 뿌리 다음 대문자 세그먼트를 얼마나 이어 붙이는지 — **글자 모양이 아니라 존재로 판별한다**
 * (verifier r19 M-1·M-3). 이전의 SCREAMING_CASE 규칙은 한 글자(`Math.E`)·혼합 대소문자
 * (`Double.NaN`) 상수를 멤버로 못 보고(M-1 오탐), 반대 방향으로 전대문자 이름의 **실재하는**
 * 중첩 클래스가 있다면 그것도 멤버로 오인했을 것이다(M-3).
 *
 * JDK 이름공간(`java.`·`javax.`·`jdk.`·`kotlin.`)은 후보 FQN 이 **실재하는 클래스일 때만**
 * 잇는다 — `Class.forName` 이 이 코드를 실은 Gradle JVM 의 JDK/stdlib 판을 기준으로 판정한다
 * (`memberEffectGate` 의 T-D 도출과 같은 성질의 의존 — 알려진 제한). 존재하지 않으면(예:
 * `java.lang.Integer$MAX_VALUE`) 그 세그먼트부터 멤버로 보고 멈춘다.
 *
 * 비 JDK 이름공간은 실재를 확인할 수 없으므로 **닫히는 방향을 유지한다** — 대문자로 시작하는
 * 세그먼트는 예전 규칙대로 전부 잇는다(전부 허용 목록에 있어야 통과한다).
 */
private fun nestedSegments(
    prefix: String,
    root: String,
    remaining: List<String>,
): List<String> {
    if (!prefix.isJdkNamespace()) {
        return remaining.takeWhile { it.startsWithUpper() }
    }
    val nested = mutableListOf<String>()
    var owner = "$prefix.$root"
    for (segment in remaining) {
        val candidate = "$owner\$$segment"
        if (!segment.startsWithUpper() || !classExists(candidate)) break
        nested += segment
        owner = candidate
    }
    return nested
}

private fun String.isJdkNamespace(): Boolean =
    this == "java" || startsWith("java.") ||
        this == "javax" || startsWith("javax.") ||
        this == "jdk" || startsWith("jdk.") ||
        this == "kotlin" || startsWith("kotlin.")

private fun String.startsWithUpper(): Boolean = isNotEmpty() && first().isUpperCase()

/** 재귀 게이트 실행 한 번에 같은 이름을 여러 번 물을 수 있어 판정을 캐싱한다. */
private val classExistenceCache = mutableMapOf<String, Boolean>()

private fun classExists(fqcn: String): Boolean =
    classExistenceCache.getOrPut(fqcn) {
        try {
            Class.forName(fqcn, false, ClassExistenceProbe::class.java.classLoader)
            true
        } catch (ignoredNotFound: ClassNotFoundException) {
            false
        } catch (ignoredLinkageError: LinkageError) {
            false
        }
    }

/** `Class.forName` 에 넘길 클래스로더의 앵커 — 이 파일을 실은 로더가 JDK·kotlin-stdlib 를 본다. */
private object ClassExistenceProbe
