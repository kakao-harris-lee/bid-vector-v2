package bidvector.archfixture.violating.qualification

import java.sql.Connection

/** SQL 가족 위반. HttpLeak 과 같이 JDK 타입이라 1차 게이트 밖이다. */
class SqlLeak {
    fun closed(connection: Connection): Boolean = connection.isClosed
}
