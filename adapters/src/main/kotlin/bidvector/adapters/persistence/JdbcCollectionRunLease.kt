package bidvector.adapters.persistence

import java.sql.Connection
import javax.sql.DataSource

/**
 * 수집 실행의 배타 임차(D-6G-42 M-3) — **한 번에 한 실행만.**
 *
 * 두 실행이 겹치면 같은 표본을 두 번 부르고, 두 상한 회계가 서로의 호출을 보지 못해 승인 상한이
 * 사실상 두 배가 된다(각자 자기 몫만 센다). 그 사고는 호출이 나간 뒤에 알게 되므로 되돌릴 수 없다.
 *
 * Postgres **advisory lock** 으로 잠근다 — 새 표를 만들지 않고(D-6G-1 「마이그레이션 없음」),
 * **세션이 끊기면 자동으로 풀린다**. 잠금 행을 표에 두면 죽은 실행이 잠금을 들고 남아 다음 실행을
 * 영원히 막고, 그것을 푸는 수동 절차가 또 필요하다.
 */
class JdbcCollectionRunLease(
    private val dataSource: DataSource,
    private val key: Long,
) : CollectionRunLease {
    override fun acquire(): RunLease {
        val connection = dataSource.connection
        val taken =
            runCatching {
                connection.prepareStatement(TRY_LOCK_SQL).use { statement ->
                    statement.setLong(1, key)
                    statement.executeQuery().use { rows ->
                        rows.next()
                        rows.getBoolean(1)
                    }
                }
            }.getOrElse {
                connection.close()
                throw it
            }
        return if (taken) RunLease.Acquired(connection) else RunLease.Busy.also { connection.close() }
    }
}

/** 실행 임차 — 구현은 advisory lock 이지만 호출부는 「내가 지금 도는 유일한 실행인가」만 묻는다. */
interface CollectionRunLease {
    fun acquire(): RunLease
}

/**
 * 임차 결과 — 예외가 아니라 값이다. 「이미 누가 돌고 있다」는 오류가 아니라 **정상적인 답**이고,
 * 호출부는 그 답을 보고 조용히 끝내야 한다(두 번째 실행이 스택 트레이스를 남길 이유가 없다).
 */
sealed interface RunLease {
    class Acquired(
        private val connection: Connection,
    ) : RunLease {
        /** 세션을 놓으면 잠금도 풀린다 — 따로 unlock 을 부르지 않는다. */
        fun release() = connection.close()
    }

    data object Busy : RunLease
}

private const val TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)"
