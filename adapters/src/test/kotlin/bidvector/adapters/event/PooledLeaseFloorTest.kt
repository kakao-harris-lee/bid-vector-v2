package bidvector.adapters.event

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.event.OutboxConsumerKind
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.sql.SQLTransientConnectionException

/**
 * **풀 크기 하한 2 의 근거를 실측한다**(M6/6E-2a P-3, 설계 검토 우회 6).
 *
 * [PostgresAdvisoryLockLease] 는 본문이 도는 내내 전용 연결 하나를 쥔다 — 세션 범위 잠금이
 * 그것을 요구한다. 그 연결이 **풀에서 나오면** 풀의 남은 자리로 본문이 일해야 한다. 자리가
 * 없으면 relay 는 **자기 자신이 놓을 연결을 기다린다**: 교착이고, 증상은 「아무것도 안 하는
 * 러너」다(종료 코드도 로그도 정상으로 보인다).
 *
 * 그래서 하한을 수로만 적지 않는다. 아래 둘이 **양쪽 방향**으로 그 수를 고정한다 — 2 면
 * 성립하고 1 이면 실제로 굶는다. 접속 역할 축은 여기가 아니라 출하 조립을 띄우는
 * `ProductionPoolRoleTest`(app) 가 진다.
 */
class PooledLeaseFloorTest : PersistenceTestSupport() {
    @Test
    fun `크기 2 인 풀에서는 임대를 쥔 채 작업 연결을 받는다`() {
        newPool(LEASE_AND_WORK_FLOOR, PROBE_TIMEOUT_MS).use { pool ->
            val attempt =
                PostgresAdvisoryLockLease(pool).withLease(KIND) {
                    pool.connection.use { work -> work.createStatement().use { it.execute("SELECT 1") } }
                }

            attempt.shouldBeInstanceOf<LeaseAttempt.Held<Boolean>>().result shouldBe true
        }
    }

    /**
     * **음성 대조이자 하한의 근거** — 같은 코드가 크기 1 에서는 작업 연결을 받지 못하고
     * 대여 시간 초과로 끝난다. 이 test 가 없으면 위 test 는 「풀 크기와 무관하게 통과」인
     * 구현에서도 초록이다.
     */
    @Test
    fun `크기 1 인 풀에서는 임대가 작업 연결을 굶긴다`() {
        newPool(1, PROBE_TIMEOUT_MS).use { pool ->
            shouldThrow<SQLTransientConnectionException> {
                PostgresAdvisoryLockLease(pool).withLease(KIND) { pool.connection.use { it.isClosed } }
            }
        }
    }
}

private val KIND = OutboxConsumerKind.NotificationRequested

/** 하한의 수 — production 상수의 사본이 아니라 그 상수가 만족해야 하는 값이다. */
private const val LEASE_AND_WORK_FLOOR = 2

/** 굶는 쪽이 실제로 기다리는 시간. 짧게 둬 test 가 그만큼만 쉰다. */
private const val PROBE_TIMEOUT_MS = 2_000L
