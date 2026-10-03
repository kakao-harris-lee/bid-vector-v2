package bidvector.archfixture.violating.adapters

/**
 * A-2 음성 fixture — **`adapters` 층의** 반사다. 반사 게이트의 뿌리가 `workflow`·`app` 뿐이던 판에서는
 * 이 자리가 게이트 밖이었다(뿌리를 좁히는 변이의 표적).
 *
 * 실행되지 않는다 — 게이트는 바이트코드만 읽는다.
 */
class RogueAdapterReflectionPeek {
    fun peek(
        target: Any,
        getter: String,
    ): Any? = target.javaClass.getMethod(getter).invoke(target)

    fun byName(name: String): Any = Class.forName(name).getDeclaredConstructor().newInstance()
}

/** A-2 쌍 축 — 등재된 클래스가 **새 반사 멤버**를 더 부르는 길. 멤버 쌍 등식이 아니면 초록인 자리다. */
class RogueAdapterNameLookupGainingReflection {
    fun typeName(value: Any): String = value.javaClass.name

    fun method(
        value: Any,
        getter: String,
    ) = value.javaClass.getDeclaredMethod(getter)
}
