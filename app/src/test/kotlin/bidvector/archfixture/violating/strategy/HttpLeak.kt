package bidvector.archfixture.violating.strategy

import java.net.HttpURLConnection

/** HTTP 가족 위반. **JDK 타입이라 Maven group 이 없다** — 1차 게이트가 볼 수 없는 부류다. */
class HttpLeak {
    fun status(connection: HttpURLConnection): Int = connection.responseCode
}
