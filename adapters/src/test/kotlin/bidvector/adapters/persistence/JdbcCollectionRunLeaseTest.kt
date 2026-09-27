package bidvector.adapters.persistence

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

private const val LOCK_KEY = 6_020_260_927L
private const val OTHER_KEY = 6_020_260_928L

/**
 * D-6G-42 M-3 — 겹쳐 도는 두 실행은 같은 표본을 두 번 부르고, 두 상한 회계가 서로의 호출을 보지
 * 못해 승인 상한이 사실상 두 배가 된다. 호출이 나간 뒤에 아는 사고라 되돌릴 수 없다.
 */
class JdbcCollectionRunLeaseTest : PersistenceTestSupport() {
    private fun lease(key: Long = LOCK_KEY) = JdbcCollectionRunLease(dataSource(), key)

    @Test
    fun `잠금을 든 실행이 있으면 두 번째는 Busy 다`() {
        val held = lease().acquire().shouldBeInstanceOf<RunLease.Acquired>()
        try {
            lease().acquire() shouldBe RunLease.Busy
        } finally {
            held.release()
        }
    }

    /** 세션을 놓으면 잠금이 풀린다 — 죽은 실행이 잠금을 들고 남아 다음 실행을 영원히 막지 않는다. */
    @Test
    fun `놓으면 다음 실행이 다시 든다`() {
        lease().acquire().shouldBeInstanceOf<RunLease.Acquired>().release()

        lease().acquire().shouldBeInstanceOf<RunLease.Acquired>().release()
    }

    @Test
    fun `다른 키는 서로 막지 않는다`() {
        val held = lease().acquire().shouldBeInstanceOf<RunLease.Acquired>()
        try {
            lease(OTHER_KEY).acquire().shouldBeInstanceOf<RunLease.Acquired>().release()
        } finally {
            held.release()
        }
    }
}
