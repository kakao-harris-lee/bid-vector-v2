package bidvector.adapters.persistence

import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Instant

/** F-2 재검증 — "Aa"/"BB" 충돌 쌍이 이제 서로 다른 키를 내고, 둘 다 raw 행으로 남는다. */
class F2CollisionRegressionTest : PersistenceTestSupport() {
    @Test
    fun `Aa 와 BB 는 String hashCode 충돌쌍이지만 이제 서로 다른 ObservationKey 를 낸다`() {
        val observedAt = Instant.parse("2026-09-01T00:00:00Z")
        val obsA = RawNoticeObservation.of(mapOf(RawKey("bidNtceNo") to "Aa"), SourceEndpoint.NOTICE_DETAIL, observedAt)
        val obsB = RawNoticeObservation.of(mapOf(RawKey("bidNtceNo") to "BB"), SourceEndpoint.NOTICE_DETAIL, observedAt)

        ("Aa".hashCode()) shouldBe ("BB".hashCode()) // 전제 확인 — 여전히 32비트 충돌쌍이다.

        val store = JdbcRawObservationStore(dataSource(), testFieldContracts(), TEST_RELEASE_SHA)
        val keyA = store.append(obsA)
        val keyB = store.append(obsB)

        keyA shouldNotBe keyB

        val rowCount =
            dataSource().connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT count(*) FROM raw_observation").use { rs ->
                        rs.next()
                        rs.getLong(1)
                    }
                }
            }
        rowCount shouldBe 2L
    }
}
