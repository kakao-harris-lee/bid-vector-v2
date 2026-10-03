package bidvector.archfixture.violating.adapters.external

import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.SocketHandler
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL
import javax.xml.parsers.DocumentBuilderFactory

// D-6G2b-25 음성 fixture — **전송 표면 뿌리 밖의 JDK API** 로 관문을 지나지 않고 바이트를 내는 길.
// verifier r1 H-1 이 이 넷을 production 에 심고 전체 `check` 가 초록임을 실측했다(금지 뿌리 열거는 목록
// 밖을 못 잡는다). 기본 거부 허용 목록(D-6G2b-22)이 그것을 닫는지 재는 자리다.
//
// 실행되지 않는다 — 게이트는 바이트코드만 읽는다.

/** V1 — `java.util.logging.SocketHandler` 가 TCP 로 로그를 보낸다. 전송 표면 타입이 하나도 남지 않는다. */
class RogueLoggingSocketSend {
    fun send(
        host: String,
        port: Int,
        message: String,
    ) = SocketHandler(host, port).publish(LogRecord(Level.SEVERE, message))
}

/** V3 — `DocumentBuilder.parse(String)` 은 URL 을 받으면 HTTP GET 을 낸다. */
class RogueXmlParseFetch {
    fun fetch(url: String): String? =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(url)
            .documentElement
            .textContent
}

/** V4 — JMX 원격 연결(RMI/TCP). */
class RogueJmxConnect {
    fun connect(url: String): Any = JMXConnectorFactory.connect(JMXServiceURL(url))
}

/** V5 — Swing 컴포넌트가 URL 을 받아 HTTP GET 을 낸다. */
class RogueSwingPageFetch {
    fun fetch(url: String): String = javax.swing.JEditorPane(url).text
}

/** 신규 — 데스크톱 브라우저를 띄워 바깥 호출을 맡긴다. */
class RogueDesktopBrowse {
    fun open(uri: java.net.URI) =
        java.awt.Desktop
            .getDesktop()
            .browse(uri)
}

/** 신규 — 스크립트 엔진에 바깥 호출을 맡긴다(코드가 문자열이다). */
class RogueScriptEval {
    fun eval(script: String): Any? =
        javax.script
            .ScriptEngineManager()
            .getEngineByName("js")
            ?.eval(script)
}

/** 신규 — `ServiceLoader` 로 바깥 구현을 이름으로 실어 온다. `java.util` 은 허용 패키지라 낱개 타입이 든다. */
class RogueServiceLoaderExtension {
    fun load(): Any? =
        java.util.ServiceLoader
            .load(Runnable::class.java)
            .firstOrNull()
}
