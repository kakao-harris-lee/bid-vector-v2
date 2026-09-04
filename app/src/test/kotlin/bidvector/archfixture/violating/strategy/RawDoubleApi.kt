package bidvector.archfixture.violating.strategy

/**
 * public API 에 원시 `Double` 표면 셋 — 반환 타입 · nullable 프로퍼티 · 제네릭 파라미터.
 * `milestone-1.md` 「완료 조건」이 금지하는 형태다.
 */
class RawDoubleApi {
    fun rate(): Double = 0.0

    val ratio: Double? = null

    fun weights(values: List<Double>): Long = values.size.toLong()
}
