package bidvector.adapters.e2e

import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.WorkloadPort
import bidvector.workflow.notification.ContentRenderer
import bidvector.workflow.notification.NotificationSender
import bidvector.workflow.notification.RouteDirectory
import bidvector.workflow.strategy.Clock
import java.lang.reflect.Field
import java.lang.reflect.InaccessibleObjectException
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.IdentityHashMap

/*
 * 조립이 **실제로 쥔** 협력자를 세는 자리(설계 검토 (2) 우회 6).
 *
 * 첫 판은 목록을 따로 지어 검사했다 — 배선을 대역으로 바꿔도 목록 쪽은 여전히 production
 * 생성자를 불러 초록이었다. 그래서 목록을 버리고 살아 있는 객체에서 필드 그래프를 내려간다.
 *
 * 둘째 판은 **클래스 이름이 `bidvector.` 로 시작하는** 객체만 모았다. 그래서 저장소 관례 밖
 * 패키지에 둔 대역은 그래프에 아예 나타나지 않았다(verifier r2 R2-M-1 실측: `outoftree.fake`
 * 패키지의 감시 대상 대역으로 13/13 GREEN). 이름 술어는 이름을 바꾸는 것만으로 열린다.
 *
 * 그래서 수집·하강 기준이 **이름이 아니라 출처**다 — 우리 build 출력(main·test)에서 온 객체면
 * 패키지와 무관하게 모은다. 출처가 미상이어도 **우리 타입을 구현하면** 모아서 필터로 보낸다:
 * 우리 포트를 구현한 출처 미상 객체가 곧 대역이고, JDK 동적 `Proxy` 가 바로 그 모양이다.
 * 우리 타입과 무관한 미상(제3자 jar·JDK 값)만 모으지도 내려가지도 않는다.
 *
 * 순회는 필드 값을 **읽기만** 한다(`Field.get`). 쓰기는 없고, 바꾸는 것은 접근 가능 표시
 * (`setAccessible`)뿐이다. 컨테이너 가지는 `Collection`·`Map`·`Pair` 로 한정한다 — 임의
 * `Iterable` 을 돌면 일회성 iterator 를 소모할 수 있다(review r2 R-3).
 */

/**
 * 순회 결과 — 모은 객체와 **건너뛴 흔적**을 함께 낸다(review r2 R-2). 깊이 상한이나 필드 읽기
 * 실패로 가지가 통째로 사라지면 그 아래 대역을 못 잡으므로, 둘을 세어 test 가 0 을 단언한다.
 */
internal class CollaboratorGraph(
    val collected: List<Any>,
    val depthLimitHits: Int,
    val traversalFailures: List<String>,
    val skippedHolders: List<String>,
)

/** [roots] 에서 필드를 따라 닿는, **우리 build 출력에서 온** 객체 전수. 순환은 동일성 집합으로 끊는다. */
internal fun collaboratorGraph(roots: List<Any>): CollaboratorGraph {
    val walk = GraphWalk()
    roots.forEach { walk.visit(it, 0) }
    return CollaboratorGraph(
        walk.collected,
        walk.depthLimitHits,
        walk.traversalFailures,
        walk.skippedHolders.toList(),
    )
}

private class GraphWalk {
    private val seen = IdentityHashMap<Any, Boolean>()
    val collected = mutableListOf<Any>()
    var depthLimitHits = 0
        private set
    val traversalFailures = mutableListOf<String>()
    val skippedHolders = linkedSetOf<String>()

    /**
     * `seen` 검사가 깊이 검사보다 **앞**이다(review PR62 I) — 이미 본 객체를 다른 경로로 다시
     * 만났을 뿐인데 깊이 상한에 세면 거짓 신호가 된다.
     */
    fun visit(
        value: Any?,
        depth: Int,
    ) {
        if (value == null || seen.put(value, true) != null) return
        if (depth > MAX_GRAPH_DEPTH) {
            depthLimitHits += 1
        } else if (!visitContainer(value, depth)) {
            collectOwned(value, depth)
        }
    }

    private fun visitContainer(
        value: Any,
        depth: Int,
    ): Boolean =
        when (value) {
            is Array<*> -> {
                value.forEach { visit(it, depth + 1) }
                true
            }

            is Collection<*> -> {
                value.forEach { visit(it, depth + 1) }
                true
            }

            is Map<*, *> -> {
                value.forEach { (key, entry) ->
                    visit(key, depth + 1)
                    visit(entry, depth + 1)
                }
                true
            }

            is Pair<*, *> -> {
                visit(value.first, depth + 1)
                visit(value.second, depth + 1)
                true
            }

            else -> {
                false
            }
        }

    /**
     * 출처가 미상이어도 **우리 타입을 구현하면 모은다**(verifier r3 R3-M-1). 앞 판은 미상을
     * 모으지도 내려가지도 않고 돌아갔고, 그래서 미상은 두 필터에 **닿지도 않았다** — JDK 동적
     * `Proxy` 는 `CodeSource` 가 없어 미상이라, 우리 포트를 구현한 Proxy 대역이 그대로 통과했다.
     *
     * 우리 포트를 구현한 **출처 미상 객체가 곧 대역**이다. 그래서 그런 것만 모아 비-MAIN 필터로
     * 보낸다(경계 여섯이 아니면 붉다). JDK·드라이버처럼 우리 타입과 무관한 미상은 그대로 건너뛴다.
     */
    private fun collectOwned(
        value: Any,
        depth: Int,
    ) {
        val type = value.javaClass
        if (originOf(value) == ClassOrigin.UNKNOWN && !implementsOwnedType(type)) {
            if (isOpaqueHolder(type)) skippedHolders += type.name
            return
        }
        collected += value
        if (Proxy.isProxyClass(type)) visit(Proxy.getInvocationHandler(value), depth + 1)
        declaredInstanceFields(type).forEach { field -> readField(value, field, depth) }
    }

    private fun readField(
        owner: Any,
        field: Field,
        depth: Int,
    ) {
        try {
            field.isAccessible = true
            visit(field.get(owner), depth + 1)
        } catch (failure: InaccessibleObjectException) {
            traversalFailures += failureNote(owner, field, failure)
        } catch (failure: ReflectiveOperationException) {
            traversalFailures += failureNote(owner, field, failure)
        }
    }

    private fun failureNote(
        owner: Any,
        field: Field,
        failure: Throwable,
    ): String = "${owner.javaClass.name}#${field.name}: ${failure.javaClass.simpleName}"
}

/**
 * 우리 타입과 무관한 출처 미상 객체 가운데 **그 아래에 무엇이든 담을 수 있는 보유자**인가
 * (review PR62 C). `AtomicReference`·`Optional`·`Lazy` 류는 `Object` 로 선언된 필드를 갖는다 —
 * 그 아래에 대역이 숨으면 순회가 거기서 멈추므로 **멈췄다는 사실**을 신호로 남긴다.
 *
 * 값 자체인 리프(문자열·금액·시각·드라이버 설정)는 그런 필드가 없어 신호에 오르지 않는다 —
 * 신호가 소음이 되면 0 단언이 무의미해진다.
 */
private fun isOpaqueHolder(type: Class<*>): Boolean = allDeclaredInstanceFields(type).any { it.type == Any::class.java }

private fun allDeclaredInstanceFields(type: Class<*>): List<java.lang.reflect.Field> =
    generateSequence(type) { it.superclass }
        .takeWhile { it != Any::class.java }
        .flatMap { it.declaredFields.asSequence() }
        .filterNot { Modifier.isStatic(it.modifiers) }
        .toList()

/**
 * [type] 의 상위 타입(인터페이스·상위 클래스) 가운데 **우리 build 출력**에서 온 것이 있는가.
 * `Proxy` 는 선언 필드가 없으므로 이 판정과 handler 하강이 그 대역을 드러내는 유일한 길이다.
 */
private fun implementsOwnedType(type: Class<*>): Boolean {
    val seen = mutableSetOf<Class<*>>()
    val queue = ArrayDeque<Class<*>>()
    enqueueSupertypes(type, queue)
    while (queue.isNotEmpty()) {
        val next = queue.removeFirst()
        if (!seen.add(next)) continue
        if (ClassOrigin.of(next) != ClassOrigin.UNKNOWN) return true
        enqueueSupertypes(next, queue)
    }
    return false
}

private fun enqueueSupertypes(
    type: Class<*>,
    queue: ArrayDeque<Class<*>>,
) {
    type.interfaces.forEach { queue += it }
    type.superclass?.let { queue += it }
}

/**
 * 선언 클래스 사슬은 **우리 build 출력까지만** 올라간다. JDK 상위 클래스(`java.lang.Enum` 등)의
 * 필드는 모듈 경계가 막아 접근 자체가 실패하고(실측), 그 실패는 대역 은닉과 무관한 잡음이다 —
 * 애초에 읽을 대상이 아니다.
 */
private fun declaredInstanceFields(type: Class<*>) =
    generateSequence(type) { it.superclass }
        .takeWhile { it != Any::class.java && ClassOrigin.of(it) != ClassOrigin.UNKNOWN }
        .flatMap { it.declaredFields.asSequence() }
        .filterNot { Modifier.isStatic(it.modifiers) }
        .toList()

/**
 * 이 slice 가 대역을 둘 수 있다고 선언한 **포트 경계 여섯**. 그래프에서 production 출력이
 * 아닌 객체는 이 여섯 중 하나를 구현해야 하고, 여섯은 각각 실제로 하나씩 나타나야 한다
 * (양방향) — 목록이 늘면 그만큼 경계가 넓어진 것이고, 줄면 죽은 항목이 남는다.
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

private const val MAX_GRAPH_DEPTH = 10
