package bidvector.archfixture.violating.decision

import java.net.HttpURLConnection

/**
 * class literal. 상수 풀의 Class 엔트리는 ArchUnit 의존으로 기록되지 않는다(알려진 제한 11) —
 * 반환 타입을 `Any` 로 둔 것이 요점이다, `Class<*>` 로 두면 다음 호출의 owner 가
 * `java.lang.Class` 라 T-C 가 그 자리에서 잡아 「바이트코드가 못 본다」가 성립하지 않는다.
 */
class ClassLiteralLeak {
    fun target(): Any = HttpURLConnection::class.java
}
