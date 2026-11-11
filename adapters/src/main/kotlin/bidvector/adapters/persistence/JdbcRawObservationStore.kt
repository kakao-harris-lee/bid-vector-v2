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
 * 재시도를 멱등하게 흡수한다. 릴리스 식별자는 구성 근이 주입한다 — 공백 거부는
 * [ReleaseIdentifier] 가 지므로(M9 OPS-10, 값 객체로 통일) 이 클래스에 `init` 검사가 없다.
 *
 * **D-4C2-1 갈래 (b) 개조** — 주 생성자가 [ConnectionSource]를 받는다(`DataSource`가
 * 아니다). [TransactionBoundary]가 주는 [ConnectionSource]로 append하면 outbox 등록과 같은
 * 트랜잭션에서 커밋된다(scope.md ②의 유일한 실 도메인 write 참여자, 설계 검토 (0-a) 갈래
 * (b)). 기존 `DataSource` 생성자는 [DataSourceConnectionSource]로 위임하는 보조
 * 생성자로 남는다 — 호출부·경계 밖 거동은 무변경이다(3D의 다른 네 repository는 이 개조에
 * 참여하지 않는다, 알려진 제한).
 */
class JdbcRawObservationStore(
    private val connections: ConnectionSource,
    private val fieldContracts: KonepsFieldContractRegistry,
    private val release: ReleaseIdentifier,
) : RawObservationStore {
    constructor(
        dataSource: DataSource,
        fieldContracts: KonepsFieldContractRegistry,
        release: ReleaseIdentifier,
    ) : this(DataSourceConnectionSource(dataSource), fieldContracts, release)

    override fun append(
        observation: RawNoticeObservation,
        rowDiscriminator: RowDiscriminator?,
    ): ObservationKey {
        val payloadFields = ObservationPayloadCodec.encode(observation, fieldContracts)
        // 운영자 결정 — 원문(sourceText)이 있으면 그대로 싣는다(재직렬화 없이, 바이트
        // 동일). 원문이 없는 관측(koneps 밖 호출부·구 fixture)은 등재분 투영으로 대신한다
        // — 그 경우 payload 는 payload_fields 와 같은 문자열이 되어 바이트 동일을
        // 보장하지 않는다(알려진 제한, evidence 기록).
        val payload = observation.sourceText ?: payloadFields
        // rowDiscriminator(값 우선/부재 시 위치)를 키 재료에 더한다
        // (OPEN-3B2-STORAGE-ROW-KEY-COLLISION). `null`이면 기존 유도와 동치다.
        val key = ObservationKeyDerivation.of(observation, payloadFields, rowDiscriminator)
        connections.withConnection { connection ->
            connection.prepareStatement(Sql.INSERT_RAW_OBSERVATION).use { statement ->
                var index = 1
                statement.setString(index++, key.value)
                statement.setString(index++, observation.sourceEndpoint.name)
                statement.setString(index++, payload)
                statement.setString(index++, payloadFields)
                statement.setTimestamp(index++, Timestamp.from(observation.observedAt))
                statement.setString(index, release.value)
                statement.executeUpdate()
            }
        }
        return key
    }
}
