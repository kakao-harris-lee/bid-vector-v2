package bidvector.adapters.e2e

import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.WorkloadPort
import bidvector.workflow.notification.ContentRenderer
import bidvector.workflow.notification.NotificationSender
import bidvector.workflow.notification.RouteDirectory
import bidvector.workflow.strategy.Clock
import java.lang.reflect.Modifier
import java.util.IdentityHashMap

/*
 * 조립이 **실제로 쥔** 협력자를 세는 자리(설계 검토 (2) 우회 6, verifier r1 F-1 / review G-2).
 *
 * 앞 판은 목록을 따로 지어 검사했다 — use case 안의 배선을 대역으로 바꿔도 목록 쪽은 여전히
 * production 생성자를 불러 초록이었다(verifier 실측: 전략·후보 소스를 위임 대역으로 바꿔도
 * 12/12 GREEN). 그래서 **목록을 쓰지 않는다**: 살아 있는 use case 인스턴스에서 시작해 필드
 * 그래프를 내려가며 닿는 객체를 전부 모은다. 배선을 바꾸면 그래프에 그 객체가 나타나므로
 * 「목록을 같이 고치면 통과」가 성립하지 않는다.
 *
 * 출처 판정은 소스 문자열 grep 이 아니라 **클래스로더가 아는 사실**(`CodeSource`)이다 —
 * 스타일을 바꿔도 값이 변하지 않고, production 을 test 대역으로 바꾸면 반드시 변한다.
 */

internal enum class ClassOrigin { MAIN, TEST, UNKNOWN }

internal fun originOf(instance: Any): ClassOrigin {
    val location = instance.javaClass.protectionDomain?.codeSource?.location?.path ?: return ClassOrigin.UNKNOWN
    return when {
        location.contains("/classes/kotlin/test/") || location.contains("/classes/java/test/") -> ClassOrigin.TEST
        location.contains("/classes/kotlin/main/") ||
            location.contains("/classes/java/main/") ||
            location.contains("/build/libs/") -> ClassOrigin.MAIN

        else -> ClassOrigin.UNKNOWN
    }
}

/**
 * [roots] 에서 필드를 따라 닿는 `bidvector.*` 객체 전수. 컬렉션·맵은 원소로 내려가고, 순환은
 * 동일성 집합으로 끊는다. `bidvector.*` 밖 객체(드라이버·JDK·gRPC)는 모으지도 않고 그 필드로
 * 내려가지도 않는다 — 이 단언이 묻는 것은 **우리 코드의 출처**뿐이다.
 */
internal fun collaboratorGraph(roots: List<Any>): List<Any> {
    val seen = IdentityHashMap<Any, Boolean>()
    val collected = mutableListOf<Any>()
    roots.forEach { visit(it, 0, seen, collected) }
    return collected
}

private fun visit(
    value: Any?,
    depth: Int,
    seen: IdentityHashMap<Any, Boolean>,
    collected: MutableList<Any>,
) {
    if (value == null || depth > MAX_GRAPH_DEPTH) return
    if (seen.put(value, true) != null) return
    if (visitContainer(value, depth, seen, collected)) return
    if (!value.javaClass.name.startsWith(BIDVECTOR_PACKAGE_PREFIX)) return
    collected += value
    declaredInstanceFields(value.javaClass).forEach { field ->
        runCatching {
            field.isAccessible = true
            visit(field.get(value), depth + 1, seen, collected)
        }
    }
}

private fun visitContainer(
    value: Any,
    depth: Int,
    seen: IdentityHashMap<Any, Boolean>,
    collected: MutableList<Any>,
): Boolean =
    when (value) {
        is Iterable<*> -> {
            value.forEach { visit(it, depth + 1, seen, collected) }
            true
        }

        is Map<*, *> -> {
            value.forEach { (key, entry) ->
                visit(key, depth + 1, seen, collected)
                visit(entry, depth + 1, seen, collected)
            }
            true
        }

        is Pair<*, *> -> {
            visit(value.first, depth + 1, seen, collected)
            visit(value.second, depth + 1, seen, collected)
            true
        }

        else -> false
    }

private fun declaredInstanceFields(type: Class<*>) =
    generateSequence(type) { it.superclass }
        .takeWhile { it != Any::class.java }
        .flatMap { it.declaredFields.asSequence() }
        .filterNot { Modifier.isStatic(it.modifiers) }
        .toList()

/**
 * 이 slice 가 대역을 둘 수 있다고 선언한 **포트 경계 여섯**. 그래프에서 production 출력이
 * 아닌 `bidvector.*` 객체는 이 여섯 중 하나를 구현해야 하고, 여섯은 각각 실제로 하나씩
 * 나타나야 한다(양방향) — 목록이 늘면 그만큼 경계가 넓어진 것이고, 줄면 죽은 항목이 남는다.
 *
 * 넷(`WorkloadPort`·`RouteDirectory`·`ContentRenderer`·`NotificationSender`)은 **production
 * 구현이 저장소에 아직 없다**. 나머지 둘은 시각과 식별자의 출처라 재현 등식(⑤)이 요구한다.
 */
internal val PORT_BOUNDARY_TYPES: Set<Class<*>> =
    setOf(
        WorkloadPort::class.java,
        RouteDirectory::class.java,
        ContentRenderer::class.java,
        NotificationSender::class.java,
        Clock::class.java,
        CorrelationIdFactory::class.java,
    )

internal fun portBoundariesOf(instance: Any): Set<Class<*>> =
    PORT_BOUNDARY_TYPES.filterTo(mutableSetOf()) { it.isInstance(instance) }

private const val BIDVECTOR_PACKAGE_PREFIX = "bidvector."
private const val MAX_GRAPH_DEPTH = 10
