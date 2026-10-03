package bidvector.archfixture.violating.workflow.external

import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.SocketHandler

// D-6G2b-25 음성 fixture — 같은 우회를 **workflow 모듈 범위**에서. 허용 집합이 모듈별이므로 모듈마다
// 재야 한다(V2). 실행되지 않는다.

/** V2 — use case 층에서 TCP 로그 전송. */
class RogueWorkflowSocketSend {
    fun send(
        host: String,
        port: Int,
        message: String,
    ) = SocketHandler(host, port).publish(LogRecord(Level.SEVERE, message))
}
