package bidvector.archfixture.violating.transport

import java.beans.Expression
import java.beans.Statement
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.channels.AsynchronousSocketChannel

// 관문 밖으로 바이트를 내는 길의 **음성 fixture** — 전송 표면 게이트가 각 형태를 걸러 내는지
// `TransportSurfaceGateCatchesViolationsTest` 가 잰다. 이름은 verifier 변이 번호를 따른다.
//
// 여기 어떤 클래스도 실행되지 않는다(게이트는 바이트코드만 읽는다). 호출 형태만 컴파일되면 된다.

/** KA1 — 타입을 쥐지 않고 **호출 사슬의 반환 타입**으로만 `java.net.URL` 을 지난다. */
class RogueUrlChainFetch {
    fun fetch(uri: URI): String = uri.toURL().readText()
}

/** KA12 — `java.beans.Expression` 으로 메서드를 부른다(대상 타입 이름이 문자열이다). */
class RogueBeanExpressionCall {
    fun call(target: Any): Any? = Expression(target, "openStream", emptyArray()).value
}

/** 새 변이 — `java.beans.Statement`. `Expression` 의 형제라 타입 하나 열거로는 또 열린다. */
class RogueBeanStatementCall {
    fun call(target: Any) = Statement(target, "connect", emptyArray()).execute()
}

/** KA13 — 프로세스를 띄워 바깥 호출을 맡긴다. */
class RogueProcessCurl {
    fun fetch(url: String): Process = ProcessBuilder("curl", "-s", url).start()
}

/** 새 변이 — `Runtime.exec`. `ProcessBuilder` 하나만 막으면 남는 형제. */
class RogueRuntimeExec {
    fun fetch(url: String): Process = Runtime.getRuntime().exec(arrayOf("curl", "-s", url))
}

/** KA14 — 비동기 소켓 채널. 6G 목록의 `SocketChannel` 과 다른 타입이다. */
class RogueAsyncChannelFetch {
    fun open(
        host: String,
        port: Int,
    ): AsynchronousSocketChannel = AsynchronousSocketChannel.open().apply { connect(InetSocketAddress(host, port)) }
}

/** KA3 — 평범한 소켓. 6G 가 이미 잡던 형태(회귀 유지). */
class RogueRawSocket {
    fun open(
        host: String,
        port: Int,
    ): Socket = Socket(host, port)
}

/** 새 변이 — UDP. 열거 목록에 없던 전송 형태. */
class RogueDatagramSend {
    fun open(): DatagramSocket = DatagramSocket()
}

/** 새 변이 — `javax.net.SocketFactory`. 6G 목록은 `javax.net.ssl` 쪽만 열거했다. */
class RogueSocketFactoryFetch {
    fun open(
        host: String,
        port: Int,
    ): Socket =
        javax.net.SocketFactory
            .getDefault()
            .createSocket(host, port)
}

/** 새 변이 — JDK 내장 HTTP 서버로 관문 밖 표면을 연다. */
class RogueHttpServerExposure {
    fun open(port: Int): com.sun.net.httpserver.HttpServer =
        com.sun.net.httpserver.HttpServer
            .create(InetSocketAddress(port), 0)
}

/** 새 변이 — RMI 레지스트리 조회. 이름이 문자열이고 결과가 원격 객체다. */
class RogueRmiLookup {
    fun lookup(name: String): Any? = java.rmi.Naming.lookup(name)
}

/** 새 변이 — JNDI. LDAP URL 하나로 바깥에서 객체를 받아 온다. */
class RogueJndiLookup {
    fun lookup(name: String): Any? = javax.naming.InitialContext().lookup(name)
}

/** 새 변이 — Spring `RestClient`(KA15). 클래스패스에 이미 있는 전송 표면이다. */
class RogueSpringRestClient {
    fun fetch(uri: URI): String? =
        org.springframework.web.client.RestClient
            .create()
            .get()
            .uri(uri)
            .retrieve()
            .body(String::class.java)
}

/** 새 변이 — `org.springframework.http.client` 의 저수준 요청 팩토리. */
class RogueSpringRequestFactory {
    fun factory(): org.springframework.http.client.ClientHttpRequestFactory =
        org.springframework.http.client
            .JdkClientHttpRequestFactory()
}

/** 새 변이 — 자원 URL 을 얻어 Kotlin 확장으로 읽는다. 소유 타입은 `Class` 와 `kotlin.io` 뿐이다. */
class RogueResourceUrlRead {
    fun read(path: String): String? = javaClass.getResource(path)?.readText()
}
