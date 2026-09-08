package bidvector.adapters.persistence

import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RowDiscriminator
import bidvector.procurement.SourceEndpoint
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * `OPEN-3B2-STORAGE-ROW-KEY-COLLISION` 종결(D-3E-1a (a), 층 A) — 순번(예:
 * `compnoRsrvtnPrceSno`)이 부재해 raw 재료(`sourceEndpoint`·`observedAt`·등재분 투영·
 * `sourceText`)가 완전히 같은 복수 행이 한 응답 안에 왔을 때, [RowDiscriminator]를 재료에
 * 더하지 않으면 `ON CONFLICT (observation_key) DO NOTHING`이 둘째 행부터 조용히 버린다는
 * 결함을 먼저 재현(RED 대응 회귀)하고, 더하면 15행 전부가 남는다는 것을 증명한다.
 */
class RowDiscriminatorRawKeyTest : PersistenceTestSupport() {
    private val observedAt = Instant.parse("2026-09-08T00:00:00Z")

    /** 순번이 부재한 복수예비가격 행 15개를 흉내낸다 — 등재 필드가 전부 같다(재료 동일). */
    private fun identicalReserveRowObservation(): RawNoticeObservation =
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to "20260908-00001"),
            SourceEndpoint.RESERVE_PRICE_DETAIL,
            observedAt,
        )

    @Test
    fun `행 구별 축 없이 재료가 같은 두 행을 append 하면 둘째가 조용히 버려진다 — 결함 재현`() {
        val store = JdbcRawObservationStore(dataSource(), testFieldContracts(), TEST_RELEASE_SHA)
        val first = store.append(identicalReserveRowObservation())
        val second = store.append(identicalReserveRowObservation())

        first shouldBe second
        rawObservationRowCount() shouldBe 1L
    }

    @Test
    fun `순번 부재 15행이 Positional discriminator 로 raw 에 15행 그대로 남는다`() {
        val store = JdbcRawObservationStore(dataSource(), testFieldContracts(), TEST_RELEASE_SHA)
        val keys =
            (0 until 15).map { ordinal ->
                store.append(identicalReserveRowObservation(), RowDiscriminator.Positional(ordinal))
            }

        keys.toSet() shouldHaveSize 15
        rawObservationRowCount() shouldBe 15L
    }

    @Test
    fun `값이 있으면 위치가 섞여도 같은 키를 낸다 — 재수집 순서 변화에 안전하다`() {
        val store = JdbcRawObservationStore(dataSource(), testFieldContracts(), TEST_RELEASE_SHA)
        val observation = identicalReserveRowObservation()

        val keyAtPositionZero = store.append(observation, RowDiscriminator.of("003", ordinalIfAbsent = 0))
        val secondStore = JdbcRawObservationStore(dataSource(), testFieldContracts(), TEST_RELEASE_SHA)
        val keyAfterReshuffleToPositionFive =
            secondStore.append(observation, RowDiscriminator.of("003", ordinalIfAbsent = 5))

        keyAtPositionZero shouldBe keyAfterReshuffleToPositionFive
        // 같은 키라 재-append는 ON CONFLICT DO NOTHING으로 멱등하게 흡수된다 — 새 행이 아니다.
        rawObservationRowCount() shouldBe 1L
    }

    @Test
    fun `Identified 값이 다른 두 행은 서로 다른 키를 낸다`() {
        val store = JdbcRawObservationStore(dataSource(), testFieldContracts(), TEST_RELEASE_SHA)
        val observation = identicalReserveRowObservation()

        val keyForRowThree = store.append(observation, RowDiscriminator.Identified("003"))
        val keyForRowFour = store.append(observation, RowDiscriminator.Identified("004"))

        keyForRowThree shouldNotBe keyForRowFour
        rawObservationRowCount() shouldBe 2L
    }

    private fun rawObservationRowCount(): Long =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM raw_observation").use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }
}
