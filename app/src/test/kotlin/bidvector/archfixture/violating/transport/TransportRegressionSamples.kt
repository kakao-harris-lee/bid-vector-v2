package bidvector.archfixture.violating.transport

import java.net.URI
import java.net.http.HttpClient

// 6G 가 이미 잡던 형태들의 **회귀 fixture** 와, 게이트가 과잉이 아님을 재는 대조들.

/** KA2 — 등재 밖 클래스가 HTTP 클라이언트를 쥔다(6G 의 첫 게이트가 잡던 형태). */
class RogueUnlistedHttpClientHolder {
    private val client: HttpClient = HttpClient.newHttpClient()

    fun client(): HttpClient = client
}

private typealias HiddenClient = HttpClient

/** KA4 — `typealias` 로 이름을 가린다. 바이트코드에는 원 타입이 남는다. */
class RogueTypealiasedClient {
    fun build(): HiddenClient = HttpClient.newHttpClient()
}

/**
 * 쌍 등식 축 — 이 클래스는 [java.net.URI] 쌍으로 **등재된 것처럼** 다루고(test 가 합성 등재 집합을
 * 넘긴다) 거기에 전송 타입을 하나 더 쥔다. 클래스 단위 등재였다면 초록인 자리다.
 */
class RogueRegisteredHolderGainingTransport {
    fun uri(raw: String): URI = URI.create(raw)

    fun open(
        host: String,
        port: Int,
    ): java.net.Socket = java.net.Socket(host, port)
}

/**
 * 메서드 핸들로 대상 메서드를 문자열로 지어 부른다 — **`java.lang.invoke.MethodHandles` 가 남아 잡힌다**
 * (뿌리에 `java.lang.invoke` 가 있다). 경계 밖은 이 형태가 아니라 **반사 타입이 전혀 남지 않는** 쪽이다
 * (서드파티 반사 도구 경유 — checklist 알려진 제한).
 */
class RogueMethodHandleInvoke {
    fun call(target: Any): Any? {
        val lookup =
            java.lang.invoke.MethodHandles
                .lookup()
        val signature =
            java.lang.invoke.MethodType
                .methodType(Any::class.java)
        val handle = lookup.findVirtual(target.javaClass, "openStream", signature)
        return handle.invoke(target)
    }
}

/** 과잉 대조 — 들어오는 HTTP(서블릿 표면)는 바이트를 밖으로 내지 않는다. 신고되면 과잉이다. */
class CleanInboundServletHandler {
    fun handle(
        request: jakarta.servlet.http.HttpServletRequest,
        response: jakarta.servlet.http.HttpServletResponse,
    ) {
        response.status = request.contentLength
    }
}

/** 과잉 대조 — 전송 표면을 아예 언급하지 않는 클래스. */
class CleanLocalComputation {
    fun sum(values: List<Int>): Int = values.sum()
}
