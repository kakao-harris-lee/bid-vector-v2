package bidvector.adapters.event

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.event.OutboxConsumerKind
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import javax.sql.DataSource

/**
 * OPS-01 셋째 acceptance — **홀더 세션을 강제 종료하면 임대가 즉시 풀린다.**
 *
 * `PostgresAdvisoryLockLeaseTest` 가 재는 해제는 **본문이 끝난 뒤**다(정상 종료·예외 종료 둘 다
 * `heldOrRelease` 의 `finally` 를 지난다) — 홀더가 **살아서 스스로 놓는** 길이다. 운영이 겁내는
 * 것은 그 반대다: 홀더가 죽은 뒤 자물쇠가 남아 스케줄을 영구 점유하는 것이고, capability-map
 * OPS-01 의 분류 근거가 「TTL 이 없는 것이 의도된 설계」라고 적은 이유가 그것이다.
 *
 * 그 성질은 PostgreSQL 의 것이지만 **구현이 세션 advisory lock 인 동안에만** 성립한다. 그래서
 * 이 test 는 기각된 대안(행 플래그·TTL 키·프로세스 범위 파일 잠금 — `PostgresAdvisoryLockLease`
 * KDoc 이 그 셋을 이름으로 기각했다)으로 자물쇠를 바꿔치우는 날 붉어지는 자리다.
 *
 * **본문이 아직 도는 중에** 끊는다 — 그래서 이 단언은 `finally` 경로를 지나지 않는다. 다음
 * 세션이 재시도도 대기도 없이 `withLease` **한 번**으로 `Held` 를 받는 것이 「즉시」의 형태다.
 *
 * 끊는 질의를 이 파일이 다시 쓰는 이유: 같은 모양이 `adapters.relay` 의 임대 상실 test 에 있지만
 * 그 쪽은 `private` 이고 다른 패키지다. 공유하려면 이 slice 의 in_scope 밖 파일을 고쳐야 하므로
 * 여기서는 베끼고 사유를 적는다(test source set 의 중복은 관측 전용 축이다).
 */
class LeaseHolderTerminationTest : PersistenceTestSupport() {
    @Test
    fun `홀더 세션을 강제 종료하면 본문이 도는 중에도 다음 세션이 임대를 즉시 얻는다`() {
        val holder = PostgresAdvisoryLockLease(pooledDataSource())
        var terminated = 0
        var next: LeaseAttempt<String>? = null

        val held =
            holder.withLease(KIND) {
                // 서버 쪽에서 홀더 백엔드를 끊는다 — 프로세스는 살아 있고 본문도 아직 돈다.
                terminated = terminateLeaseHolderBackends(pooledDataSource())
                // 같은 어댑터·**다른 연결** — 다른 프로세스의 대역(기다리지 않는 한 번의 시도).
                next = PostgresAdvisoryLockLease(pooledDataSource()).withLease(KIND) { "다음 세션" }
                "홀더 본문"
            }

        // 끊을 대상이 `pg_locks` 에 실제로 있었다 — 임대가 **DB 세션**에 걸려 있다는 사실이다.
        terminated shouldBe 1
        next.shouldBeInstanceOf<LeaseAttempt.Held<String>>().result shouldBe "다음 세션"
        // 홀더 본문의 결과는 끊김에 덮이지 않는다(해제 실패를 삼키는 자리의 거동).
        held.shouldBeInstanceOf<LeaseAttempt.Held<String>>().result shouldBe "홀더 본문"
    }

    /**
     * 음성 대조 — **끊지 않으면** 같은 자리에서 다음 세션은 `Busy` 다. 이 대조가 없으면 위
     * test 는 「애초에 배제하지 않는 구현」에서도 초록이고, 그러면 위 `Held` 가 세션 종료 때문에
     * 왔다는 말을 할 수 없다.
     */
    @Test
    fun `끊지 않으면 본문이 도는 중 다음 세션은 Busy 다 — 음성 대조`() {
        val holder = PostgresAdvisoryLockLease(pooledDataSource())
        var next: LeaseAttempt<String>? = null

        holder.withLease(KIND) {
            next = PostgresAdvisoryLockLease(pooledDataSource()).withLease(KIND) { "다음 세션" }
        }

        next shouldBe LeaseAttempt.Busy
    }
}

private val KIND = OutboxConsumerKind.NotificationRequested

/**
 * 알림 종류의 advisory 잠금을 쥔 백엔드를 끊고 **끊은 수**를 돌려준다. bigint 키는
 * `classid`(상위 32비트)·`objid`(하위 32비트)로 나뉘고 이 키는 2^32 미만이라 `classid` 가 0 이다.
 */
private fun terminateLeaseHolderBackends(dataSource: DataSource): Int =
    dataSource.connection.use { connection ->
        connection
            .prepareStatement(
                "SELECT pg_terminate_backend(pid) FROM pg_locks " +
                    "WHERE locktype = 'advisory' AND classid = 0 AND objid = ? AND objsubid = 1",
            ).use { statement ->
                statement.setLong(1, NOTIFICATION_RELAY_LOCK_KEY)
                statement.executeQuery().use { rs ->
                    var count = 0
                    while (rs.next()) count++
                    count
                }
            }
    }
