package bidvector.adapters.persistence

import bidvector.procurement.KonepsFieldContractRegistry
import bidvector.procurement.ObservationKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RawObservationStore
import java.sql.Timestamp
import javax.sql.DataSource

/**
 * [RawObservationStore] JDBC 구현(①·②·③) — `ON CONFLICT (observation_key) DO NOTHING`으로
 * 재시도를 멱등하게 흡수한다. `releaseSha`는 구성 근이 주입한다(운영 배선은 M6 6C 소관,
 * 빈 문자열만 이 클래스가 거부한다).
 */
class JdbcRawObservationStore(
    private val dataSource: DataSource,
    private val fieldContracts: KonepsFieldContractRegistry,
    private val releaseSha: String,
) : RawObservationStore {
    init {
        require(releaseSha.isNotBlank()) { "releaseSha는 빈 문자열일 수 없다" }
    }

    override fun append(observation: RawNoticeObservation): ObservationKey {
        val key = ObservationKey.of(observation)
        val payload = ObservationPayloadCodec.encode(observation, fieldContracts)
        dataSource.connection.use { connection ->
            connection.prepareStatement(Sql.INSERT_RAW_OBSERVATION).use { statement ->
                var index = 1
                statement.setString(index++, key.value)
                statement.setString(index++, observation.sourceEndpoint.name)
                statement.setString(index++, payload)
                statement.setTimestamp(index++, Timestamp.from(observation.observedAt))
                statement.setString(index, releaseSha)
                statement.executeUpdate()
            }
        }
        return key
    }
}
