package bidvector.app.http

import bidvector.strategy.OperatorStrategy
import bidvector.workflow.strategy.StrategyRepository
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Statement
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/**
 * 제안 목록 E2E 의 관측 장치 둘(M7/7C①b 수정 라운드 1, D-7C1B-3) — **출하 조립의 빈 둘을 감싼다.**
 * 조립 자체는 `productionApplication()` 그대로이고, 이 후처리기는 초기화자로 덧붙을 뿐이다.
 *
 *  - `strategyRepository`(PersistenceWiring, 타입 [StrategyRepository]) → [MidComputationStrategyChanger].
 *    그 빈 하나를 편집 세션·dry-run·전략 조회·제안 목록이 함께 받는다. 무장하지 않으면 위임만 한다.
 *  - `dataSource`(PersistenceWiring, 컨텍스트의 유일한 `DataSource`) → [StatementRecordingDataSource].
 *    무장 구간에만 문장을 적는다.
 *
 * 무장 구간은 test 가 정한 GET 한 번이다 — 무장·해제는 그 test 안에서 짝으로 일어난다.
 */
class SuggestionE2EProbes : BeanPostProcessor {
    val statements: StatementRecorder = StatementRecorder()
    private val changer = AtomicReference<MidComputationStrategyChanger?>(null)

    val strategy: MidComputationStrategyChanger
        get() = changer.get() ?: error("strategyRepository 빈이 감싸지지 않았다 — 조립에서 빈 이름이 바뀌었나")

    override fun postProcessAfterInitialization(
        bean: Any,
        beanName: String,
    ): Any =
        when (beanName) {
            STRATEGY_BEAN -> MidComputationStrategyChanger(bean as StrategyRepository).also { changer.set(it) }
            DATA_SOURCE_BEAN -> StatementRecordingDataSource(bean as DataSource, statements)
            else -> bean
        }

    /** `productionApplication().initializers(…)` 에 덧붙이는 자리. */
    fun initializer(): ApplicationContextInitializer<ConfigurableApplicationContext> =
        ApplicationContextInitializer { context -> context.beanFactory.addBeanPostProcessor(this) }

    private companion object {
        const val STRATEGY_BEAN = "strategyRepository"
        const val DATA_SOURCE_BEAN = "dataSource"
    }
}

/**
 * 계산 **중** 전략 변경의 대역 — `load()` 가 돌아온 **직후** 무장된 순번이면 그 동작(실 SQL)을 돌린다.
 * 그래서 그 `load()` 의 반환값은 바뀌기 전 값이고, 다음 `load()` 부터 새 값이다.
 */
class MidComputationStrategyChanger(
    private val delegate: StrategyRepository,
) : StrategyRepository by delegate {
    private val plan = AtomicReference<Map<Int, () -> Unit>>(emptyMap())
    private val loads = AtomicInteger(0)

    /** [afterLoads] 의 키는 무장 뒤 몇 번째 `load()` 인가(1 부터). */
    fun arm(afterLoads: Map<Int, () -> Unit>) {
        loads.set(0)
        plan.set(afterLoads)
    }

    /** 해제하고 무장 구간의 `load()` 횟수를 낸다. */
    fun disarm(): Int {
        plan.set(emptyMap())
        return loads.get()
    }

    override fun load(): OperatorStrategy {
        val loaded = delegate.load()
        val armed = plan.get()
        if (armed.isNotEmpty()) armed[loads.incrementAndGet()]?.invoke()
        return loaded
    }
}

/** 무장 구간에 실행 준비된 SQL 문을 순서대로 적는다. */
class StatementRecorder {
    private val recorded = AtomicReference<MutableList<String>?>(null)

    fun arm() {
        recorded.set(Collections.synchronizedList(mutableListOf()))
    }

    fun disarm(): List<String> = recorded.getAndSet(null)?.toList() ?: error("무장하지 않았다")

    fun record(sql: String) {
        recorded.get()?.add(sql)
    }
}

/**
 * [DataSource] 를 그대로 위임하되, 내준 연결의 `prepareStatement`·`prepareCall` 과 `createStatement` 로
 * 만든 문의 `execute*`·`addBatch` SQL 을 [recorder] 에 적는다. `close` 는 풀에 그대로 넘긴다(종료 시
 * 풀이 닫히는 거동을 바꾸지 않는다).
 */
class StatementRecordingDataSource(
    private val delegate: DataSource,
    private val recorder: StatementRecorder,
) : DataSource by delegate,
    AutoCloseable {
    override fun getConnection(): Connection = recording(delegate.connection)

    override fun getConnection(
        username: String?,
        password: String?,
    ): Connection = recording(delegate.getConnection(username, password))

    override fun close() {
        (delegate as? AutoCloseable)?.close()
    }

    private fun recording(connection: Connection): Connection =
        proxy<Connection> { method, args ->
            if (method.name in PREPARING && args?.firstOrNull() is String) recorder.record(args.first() as String)
            val result = method.invokeUnwrapped(connection, args)
            if (method.name == CREATE_STATEMENT && result is Statement) recordingStatement(result) else result
        }

    private fun recordingStatement(statement: Statement): Statement =
        proxy<Statement> { method, args ->
            if (method.name in EXECUTING && args?.firstOrNull() is String) recorder.record(args.first() as String)
            method.invokeUnwrapped(statement, args)
        }

    private companion object {
        val PREPARING = setOf("prepareStatement", "prepareCall")
        const val CREATE_STATEMENT = "createStatement"
        val EXECUTING = setOf("execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "addBatch")
    }
}

/** [T] 인터페이스의 JDK 프록시 — 호출은 전부 [handle] 이 받는다(위임 대상은 [handle] 이 쥔다). */
private inline fun <reified T : Any> proxy(crossinline handle: (Method, Array<out Any?>?) -> Any?): T =
    Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
        InvocationHandler { _, method, args -> handle(method, args) },
    ) as T

private fun Method.invokeUnwrapped(
    target: Any,
    args: Array<out Any?>?,
): Any? =
    try {
        invoke(target, *(args ?: emptyArray()))
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }

/** 읽기 문(`SELECT … FROM <표>`)만 골라 표 이름별로 센다 — 감사 INSERT 등 쓰기는 세지 않는다. */
fun List<String>.selectCountsByTable(): Map<String, Int> =
    mapNotNull { sql ->
        val trimmed = sql.trim()
        if (!trimmed.startsWith("SELECT", ignoreCase = true)) return@mapNotNull null
        SELECT_FROM.find(trimmed)?.groupValues?.get(1)
    }.groupingBy { it }.eachCount()

private val SELECT_FROM = Regex("""\bFROM\s+(\w+)""", RegexOption.IGNORE_CASE)
