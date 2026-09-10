package bidvector.adapters.persistence

import bidvector.procurement.KonepsFieldContractRegistry
import bidvector.procurement.ObservationKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.RawObservationStore
import bidvector.procurement.RowDiscriminator
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

    override fun append(
        observation: RawNoticeObservation,
        rowDiscriminator: RowDiscriminator?,
    ): ObservationKey {
        val payloadFields = ObservationPayloadCodec.encode(observation, fieldContracts)
        // F-7 운영자 결정 — 원문(sourceText)이 있으면 그대로 싣는다(재직렬화 없이, 바이트
        // 동일). 원문이 없는 관측(koneps 밖 호출부·구 fixture)은 등재분 투영으로 대신한다
        // — 그 경우 payload 는 payload_fields 와 같은 문자열이 되어 바이트 동일을
        // 보장하지 않는다(알려진 제한, evidence 기록).
        val payload = observation.sourceText ?: payloadFields
        // M3/3E — rowDiscriminator(값 우선/부재 시 위치)를 키 재료에 더한다
        // (OPEN-3B2-STORAGE-ROW-KEY-COLLISION). `null`이면 기존 유도와 동치다.
        val key = ObservationKeyDerivation.of(observation, payloadFields, rowDiscriminator)
        dataSource.connection.use { connection ->
            connection.prepareStatement(Sql.INSERT_RAW_OBSERVATION).use { statement ->
                var index = 1
                statement.setString(index++, key.value)
                statement.setString(index++, observation.sourceEndpoint.name)
                statement.setString(index++, payload)
                statement.setString(index++, payloadFields)
                statement.setTimestamp(index++, Timestamp.from(observation.observedAt))
                statement.setString(index, releaseSha)
                statement.executeUpdate()
            }
        }
        return key
    }
}
