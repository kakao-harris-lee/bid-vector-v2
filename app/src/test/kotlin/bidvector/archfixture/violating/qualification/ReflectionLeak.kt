package bidvector.archfixture.violating.qualification

/** `kotlin.reflect` 는 정확 패키지 목록에 없다 — 하위를 열지 않는 설계가 이것을 자동으로 닫는다. */
class ReflectionLeak {
    fun name(): String? = ReflectionLeak::class.simpleName
}
