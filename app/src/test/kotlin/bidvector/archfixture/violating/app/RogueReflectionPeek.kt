package bidvector.archfixture.violating.app

import java.lang.invoke.MethodHandles

/**
 * D-6F8-13 F2-2 위반 표본 — 원문 타입을 이름 붙이지 않고 리플렉션으로 값을 꺼내는 네 길. 타입 참조·멤버 접근 규칙만으로는
 * 보이지 않아 반사 표면을 막는 규칙이 있어야 잡힌다. production classpath 에는 오르지 않는다.
 */
class RogueReflectionPeek(
    private val observation: Any,
) {
    fun viaGetMethod(): Any? = observation.javaClass.getMethod("getSourceText").invoke(observation)

    fun viaForName(): Class<*> = Class.forName("bidvector.procurement.RawNoticeObservation")

    fun viaMethodHandles(): MethodHandles.Lookup = MethodHandles.lookup()

    fun viaKotlinReflection(): Int = observation::class.members.size
}
