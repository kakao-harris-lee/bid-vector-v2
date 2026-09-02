package bidvector.archfixture.violating.decision

/**
 * 시스템 프로퍼티 읽기. **클래스 단위로는 막을 수 없다** — `Boolean` 은 박싱에 필수이고
 * `Boolean.getBoolean` 만 환경을 읽는다. T-D(멤버 단위)가 있어야 하는 이유의 실물이다.
 */
class SystemPropertyLeak {
    fun flag(name: String): Boolean = java.lang.Boolean.getBoolean(name)
}
