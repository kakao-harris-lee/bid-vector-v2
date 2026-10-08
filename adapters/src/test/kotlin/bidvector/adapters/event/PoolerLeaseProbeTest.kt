package bidvector.adapters.event

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.event.OutboxConsumerKind
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import javax.sql.DataSource

/** 잠금을 쥐고 놓는 질의 — 이 낱말이 든 `prepareStatement` 만 **홀더 연결**로 보낸다. */
private val LOCK_QUERY_MARKERS = listOf("advisory_lock", "advisory_unlock")

/**
 * **RT2-M-1** — 잠금 인식 probe 가 **pooler 모양**에서 상실을 판정하는지 상설로 잰다.
 *
 * 왜 이 test 가 필요했나: PR #63 finding 3 이 `SELECT 1`(연결 생존)을 잠금 인식 probe 로
 * 바꿨는데, 그 변경을 **한 줄 되돌려도** relay·event 표적 230 test 가 전부 초록이었다.
 * 상실 test 들은 백엔드를 **종료**하므로 두 probe 가 똑같이 거짓을 내기 때문이다. 그래서
 * 조치가 아무 소리도 내지 않았다(verifier 표적 재검증 2).
 *
 * 어떻게 싸게 만드나: transaction 모드 pooler 의 성질은 「질의마다 다른 서버 backend」다.
 * 그 모양은 `DataSource` 프록시로 흉내 낼 수 있다 — **잠금 질의만** 연결 A 로, 나머지는 연결
 * B 로 보낸다. 그러면 잠금은 A 의 backend 가 들고 있고 probe 는 B 의 backend 에서 돌아,
 * 실제 pooler 뒤에서 벌어지는 「둘이 동시에 `Held`」의 입력이 그대로 재현된다.
 *
 * 무엇을 주장하지 않는가: 이 test 는 pooler **지원**을 뜻하지 않는다(알려진 제한 22 그대로).
 * 재는 것은 「그 어긋남을 **조용히 넘기지 않는다**」 한 줄이고, 그 문장이 제한 22 에 있다.
 */
class PoolerLeaseProbeTest : PersistenceTestSupport() {
    @Test
    fun `잠금과 생존 질의가 다른 backend 로 가면 임대를 잃은 것으로 판정한다`() {
        val held = askStillHeld(poolerShaped(pooledDataSource()))

        held shouldBe false
    }

    /**
     * **음성 대조** — 같은 질문을 직접 연결로 하면 쥐고 있다고 답한다. 이 칸이 없으면 위
     * test 는 「probe 가 늘 거짓」인 구현에서도 초록이다.
     */
    @Test
    fun `직접 연결에서는 쥐고 있다고 판정한다`() {
        val held = askStillHeld(pooledDataSource())

        held shouldBe true
    }

    private fun askStillHeld(source: DataSource): Boolean {
        val attempt =
            PostgresAdvisoryLockLease(source).withLease(OutboxConsumerKind.NotificationRequested) { guard ->
                guard.stillHeld()
            }
        return attempt.shouldBeInstanceOf<LeaseAttempt.Held<Boolean>>().result
    }
}

/**
 * 질의를 **둘로 가르는** `DataSource` — 잠금 질의는 홀더 연결(A), 나머지는 다른 연결(B)로
 * 보낸다. `java.lang.reflect.Proxy` 를 쓰는 이유는 `Connection` 의 메서드가 수십 개인데 이
 * test 가 바꾸는 것은 `prepareStatement` 하나이기 때문이다(나머지는 B 에 위임).
 *
 * `close()` 는 **둘 다** 닫는다 — A 를 남기면 잠금이 다음 test 까지 살아 `Busy` 를 만든다.
 */
private fun poolerShaped(source: DataSource): DataSource =
    Proxy.newProxyInstance(
        DataSource::class.java.classLoader,
        arrayOf(DataSource::class.java),
        InvocationHandler { _, method, args ->
            if (method.name == "getConnection") {
                splitConnection(source.connection, source.connection)
            } else {
                invoke(method, source, args)
            }
        },
    ) as DataSource

private fun splitConnection(
    holder: Connection,
    other: Connection,
): Connection =
    Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
        InvocationHandler { _, method, args ->
            when {
                method.name == "close" -> closeBoth(holder, other)
                method.name == "prepareStatement" && isLockQuery(args) -> invoke(method, holder, args)
                else -> invoke(method, other, args)
            }
        },
    ) as Connection

private fun isLockQuery(args: Array<out Any?>?): Boolean {
    val sql = args?.firstOrNull() as? String ?: return false
    return LOCK_QUERY_MARKERS.any { marker -> sql.contains(marker) }
}

private fun closeBoth(
    holder: Connection,
    other: Connection,
) {
    other.close()
    holder.close()
}

private fun invoke(
    method: Method,
    target: Any,
    args: Array<out Any?>?,
): Any? = method.invoke(target, *(args ?: emptyArray()))
