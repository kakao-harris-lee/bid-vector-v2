package bidvector.adapters.e2e

/**
 * 객체가 **어느 컴파일 출력**에서 왔는지 — 소스 문자열 grep 이 아니라 클래스로더가 아는
 * 사실이다(설계 검토 「게이트 술어는 문자열이 아니라 구조로」). 스타일을 바꿔도 값이 변하지
 * 않고, production 을 test 대역으로 바꾸면 반드시 변한다. 우리 모듈의 production 산출물은
 * 디렉터리(같은 모듈)로도 jar(다른 모듈)로도 올라오므로 둘 다 `MAIN` 이다.
 */
internal enum class ClassOrigin {
    MAIN,
    TEST,
    UNKNOWN,
    ;

    internal companion object {
        fun of(instance: Any): ClassOrigin = of(instance.javaClass)

        fun of(type: Class<*>): ClassOrigin {
            val location =
                type.protectionDomain
                    ?.codeSource
                    ?.location
                    ?.path ?: return UNKNOWN
            return when {
                location.contains("/classes/kotlin/test/") || location.contains("/classes/java/test/") -> TEST

                location.contains("/classes/kotlin/main/") ||
                    location.contains("/classes/java/main/") ||
                    location.contains("/build/libs/") -> MAIN

                else -> UNKNOWN
            }
        }
    }
}

internal fun originOf(instance: Any): ClassOrigin = ClassOrigin.of(instance)
