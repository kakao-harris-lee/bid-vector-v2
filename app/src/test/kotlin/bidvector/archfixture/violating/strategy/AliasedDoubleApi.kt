package bidvector.archfixture.violating.strategy

import kotlin.Double as Scalar

/**
 * 별칭 import(D-9)와 타입 미명시(D-7)를 구현이 틀리기 쉬운 조합으로 심는다.
 *
 * `scalar` 는 **파라미터**로 별칭을 쓴다 — `import kotlin.Double as Scalar` 뒤 그 이름으로
 * **값을 구성**하려 하면(`0.0 as Scalar`·`Double.valueOf(0.0)`) `kotlin.Double`·`java.lang.Double`
 * 사이 플랫폼 타입 불일치로 컴파일러가 거부한다(실측) — 선언 위치의 타입 표면만 있으면 되므로
 * 파라미터 자리가 이 fixture 의 요점을 그대로 지키면서 이 자리를 피한다.
 */
class AliasedDoubleApi {
    fun scalar(x: Scalar): Long = 0L

    val inferred = 0.5
}
