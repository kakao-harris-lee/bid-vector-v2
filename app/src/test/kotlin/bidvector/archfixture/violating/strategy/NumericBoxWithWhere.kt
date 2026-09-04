package bidvector.archfixture.violating.strategy

/**
 * Codex 14차 #2 — `where` 절(`typeConstraints`)의 타입 제약이 금지 타입이면 `extendsBound`
 * (`T : Number`)와 같은 축으로 잡혀야 한다. `class`·`fun` 두 형태를 함께 심는다.
 */
class NumericBoxWithWhere<T>(
    val value: T,
) where T : Number

fun <T> compareToLimit(value: T): Int where T : Comparable<Double> = value.compareTo(0.0)
