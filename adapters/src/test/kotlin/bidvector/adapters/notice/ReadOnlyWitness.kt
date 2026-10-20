package bidvector.adapters.notice

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement
import javax.sql.DataSource

/**
 * 검색이 **실제로 실행한 문장**과 그 순간의 트랜잭션 읽기 전용 상태(verifier r2 R2-M-1).
 * `sql` 은 문장 텍스트, `readOnly` 는 같은 연결에서 바로 읽은 `transaction_read_only` 다.
 */
internal data class ObservedStatement(
    val sql: String,
    val readOnly: Boolean,
    val autoCommit: Boolean,
)

/**
 * 읽기 전용을 켜는 문장 — 면제의 **등식 기준**이다. 어댑터의 같은 문면과 값이 같아야 하고,
 * 어댑터가 문면을 바꾸면 면제가 성립하지 않아 이 witness 를 쓰는 test 가 먼저 붉어진다.
 */
internal const val BEGIN_READ_ONLY_SQL = "SET TRANSACTION READ ONLY"

/**
 * 검색 어댑터가 받는 `DataSource` 를 감싸, 연결이 **문장을 만들 때마다** 그 순간의 읽기 전용
 * 상태를 같은 연결에서 직접 물어 기록한다(verifier r2 가 처방한 기록 프록시 형태).
 *
 * **왜 이 형태인가.** 앞 판의 두 test 는 ⓐ 「그 함수가 연 연결에서는 DML 이 막힌다」(함수를
 * 직접 불러서)와 ⓑ 「상수 풀에 그 함수 이름이 있다」만 쟀다. 그래서 호출을 **건수 질의 뒤로
 * 옮기거나** 호출 **앞에 UPDATE 를 끼우는** 변이가 둘 다 초록이었다 — 「검색의 모든 문장이 그
 * 트랜잭션 안에서 돈다」는 잠겨 있지 않았다. 이 witness 는 그 문장을 직접 잰다.
 *
 * 상태 조회는 **위임 연결**로 돌려 프록시를 다시 타지 않는다(재귀·자기 기록 방지).
 */
internal class ReadOnlyWitness {
    val observed: MutableList<ObservedStatement> = mutableListOf()

    /**
     * 면제는 **하나**다 — 관측된 **첫** 문장이 [BEGIN_READ_ONLY_SQL] 과 **정확히 같을 때** 그
     * 하나뿐이다(위치 + 등식). 앞 판은 「그 문구를 포함하면」이라는 **부분 문자열** 면제였고,
     * 그래서 그 문구를 **SQL 주석으로 단 쓰기**(`UPDATE …` 뒤에 그 문구를 주석으로 붙인 문장)가
     * 면제됐다(verifier r3 변이 w). 포함이 아니라 등식이라 주석으로는 더 이상 면제되지 않는다.
     */
    private fun exemptCount(): Int = if (observed.firstOrNull()?.sql == BEGIN_READ_ONLY_SQL) 1 else 0

    fun statementsOutsideReadOnly(): List<ObservedStatement> = observed.drop(exemptCount()).filterNot { it.readOnly }

    fun queries(): List<ObservedStatement> = observed.drop(exemptCount())
}

internal fun witnessedDataSource(
    delegate: DataSource,
    witness: ReadOnlyWitness,
): DataSource =
    Proxy.newProxyInstance(
        DataSource::class.java.classLoader,
        arrayOf(DataSource::class.java),
        InvocationHandler { _, method, args ->
            val result = method.invoke(delegate, *(args ?: emptyArray()))
            if (method.name == "getConnection") witnessedConnection(result as Connection, witness) else result
        },
    ) as DataSource

private fun witnessedConnection(
    delegate: Connection,
    witness: ReadOnlyWitness,
): Connection =
    Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
        InvocationHandler { _, method, args ->
            val arguments = args ?: emptyArray()
            when (method.name) {
                "prepareStatement" -> {
                    witness.record(delegate, arguments[0] as String)
                    method.invoke(delegate, *arguments)
                }

                "createStatement" -> {
                    witnessedStatement(method.invoke(delegate, *arguments) as Statement, delegate, witness)
                }

                else -> {
                    method.invoke(delegate, *arguments)
                }
            }
        },
    ) as Connection

/**
 * `createStatement()` 는 SQL 을 나중에 받으므로 실행 시점에 기록한다 — `execute*(sql)` 다섯 가지
 * 이름을 전부 같은 자리에서 본다(이름을 하나만 보면 다른 이름으로 지나간다).
 */
private fun witnessedStatement(
    delegate: Statement,
    connection: Connection,
    witness: ReadOnlyWitness,
): Statement =
    Proxy.newProxyInstance(
        Statement::class.java.classLoader,
        arrayOf(if (delegate is PreparedStatement) PreparedStatement::class.java else Statement::class.java),
        InvocationHandler { _, method, args ->
            val arguments = args ?: emptyArray()
            val sql = arguments.firstOrNull() as? String
            if (method.name.startsWith("execute") && sql != null) {
                witness.record(connection, sql)
            }
            method.invoke(delegate, *arguments)
        },
    ) as Statement

/** 같은 연결에 `SHOW transaction_read_only` 를 물어 **그 순간의 사실**을 적는다. */
private fun ReadOnlyWitness.record(
    connection: Connection,
    sql: String,
) {
    val readOnly =
        connection.createStatement().use { statement ->
            statement.executeQuery("SHOW transaction_read_only").use { rows ->
                rows.next() && rows.getString(1) == "on"
            }
        }
    observed += ObservedStatement(sql.trim(), readOnly, connection.autoCommit)
}
