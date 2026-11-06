package bidvector.adapters.event

import bidvector.adapters.persistence.TEST_RELEASE_SHA
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.PolicyVersion
import bidvector.strategy.StrategyEvent
import bidvector.strategy.StrategyRevision
import bidvector.workflow.event.NotificationEvidencePayload
import bidvector.workflow.event.NotificationRequestedPayload
import java.sql.Connection
import javax.sql.DataSource

/** 기본값으로 심을 payload — 실제 값은 중요하지 않은 test(claim 경합 등)가 쓴다. */
private val PLACEHOLDER_EVENT =
    StrategyEvent.StrategyUpdated(StrategyRevision(0), PolicyVersion(EffectiveFrom.Initial, "placeholder"))

/**
 * `outbox` 행을 손으로 하나 심는 공용 test 헬퍼(중복 금지, 3D `PersistenceTestSupport` 관례)
 * — [JdbcOutboxPortTest]·[OutboxClaimConcurrencyTest]·[OutboxTransitionSqlTest]가 공유한다.
 * production 경로(`JdbcOutboxPort.register`)를 지나지 않고 임의 상태·payload_type 을 직접
 * 심어야 하는 test(claim 경합·전이 SQL·fail-closed 복원)만 이 헬퍼를 쓴다 — 정상 등록
 * 경로를 재는 test는 `JdbcOutboxPort.register`(또는 `OutboxEventSink`)를 그대로 쓴다.
 *
 * 기본 payload_type/payload는 **유효한** `StrategyUpdated` 인코딩이다 — `claim`이 항상
 * payload를 디코드하므로(fail-closed), payload 자체가 test의 관심사가 아닌 경우(claim
 * 경합·전이 SQL)에도 디코드가 실패하지 않아야 한다. payload_type만 의도적으로 깨는
 * test(fail-closed 복원 실측)는 `payloadType`을 명시적으로 넘긴다.
 */
internal fun insertPendingOutboxRow(
    dataSource: DataSource,
    entryId: String,
    idempotencyKey: String,
    payloadType: String = OutboxPayloadCodec.STRATEGY_UPDATED_TYPE,
    payload: String = OutboxPayloadCodec.encode(PLACEHOLDER_EVENT),
) {
    dataSource.connection.use { connection ->
        insertPendingOutboxRow(connection, entryId, idempotencyKey, payloadType, payload)
    }
}

internal fun insertPendingOutboxRow(
    connection: Connection,
    entryId: String,
    idempotencyKey: String,
    payloadType: String,
    payload: String,
) = insertOutboxRowDirectly(
    connection = connection,
    entryId = entryId,
    idempotencyKey = idempotencyKey,
    payloadType = payloadType,
    payload = payload,
)

/**
 * 저장 층의 관문이 INSERT 에서 보는 세 값 — 기본값은 **출하 경로가 싣는 값**이다(PENDING ·
 * 사유 없음 · 릴리스 있음). 관문에 걸릴 값은 호출자가 **명시**해야 하므로, 이 기본값을 쓰는
 * 헬퍼가 관문을 우회하는 자리가 되지 않는다.
 */
internal data class OutboxGateValues(
    val state: String = "PENDING",
    val terminalReason: String? = null,
    val releaseSha: String? = TEST_RELEASE_SHA,
)

/**
 * 상태·사유·릴리스를 **호출자가 정하는** 원시 INSERT — 저장 층의 관문이 「출하 경로가 아닌 다른
 * 경로로 들어오는 INSERT」를 어떻게 거부하는지 재는 자리가 쓴다(V26 종단 사유 · V27 릴리스
 * 식별자). 정상 등록 경로는 `JdbcOutboxPort.register` 이고 이 헬퍼가 그것을 대신하지 않는다.
 *
 * test 소스에서 `outbox` 에 INSERT 하는 문장은 **이 하나**다 — 사본을 만들면 관문이 늘 때
 * 한쪽만 고쳐져 그 사본이 우회로로 남는다.
 */
internal fun insertOutboxRowDirectly(
    connection: Connection,
    entryId: String,
    idempotencyKey: String,
    payloadType: String = OutboxPayloadCodec.STRATEGY_UPDATED_TYPE,
    payload: String = OutboxPayloadCodec.encode(PLACEHOLDER_EVENT),
    gate: OutboxGateValues = OutboxGateValues(),
) {
    connection
        .prepareStatement(
            "INSERT INTO outbox (entry_id, event_id, aggregate_id, aggregate_version, occurred_at, " +
                "correlation_id, causation_id, idempotency_key, actor_kind, actor_detail, " +
                "payload_type, payload, state, terminal_reason, release_sha) " +
                "VALUES (?, ?, 'operator-strategy', 1, now(), ?, NULL, ?, NULL, NULL, ?, ?, ?, ?, ?)",
        ).use { statement ->
            statement.setString(1, entryId)
            statement.setString(2, "evt-$entryId")
            statement.setString(3, "corr-$entryId")
            statement.setString(4, idempotencyKey)
            statement.setString(5, payloadType)
            statement.setString(6, payload)
            statement.setString(7, gate.state)
            statement.setString(8, gate.terminalReason)
            statement.setString(9, gate.releaseSha)
            statement.executeUpdate()
        }
}

/** 같은 INSERT 를 `DataSource` 에서 — 연결을 쥘 이유가 없는 호출자가 쓴다. */
internal fun insertOutboxRowDirectly(
    dataSource: DataSource,
    entryId: String,
    idempotencyKey: String,
    gate: OutboxGateValues,
) = dataSource.connection.use { connection ->
    insertOutboxRowDirectly(
        connection = connection,
        entryId = entryId,
        idempotencyKey = idempotencyKey,
        gate = gate,
    )
}

/** 릴리스 식별자 전수(M9 OPS-10) — 열 추가 전 행은 `null` 이라 목록으로 본다. */
internal fun outboxReleaseShaValues(dataSource: DataSource): List<String?> =
    dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT release_sha FROM outbox ORDER BY inserted_at").use { statement ->
            statement.executeQuery().use { rs ->
                val values = mutableListOf<String?>()
                while (rs.next()) values += rs.getString(1)
                values
            }
        }
    }

/** 종단 사유(M9 OPS-05) — 비종단 행과 열 추가 전에 종단한 행은 `null` 이다. */
internal fun outboxTerminalReasonOf(
    dataSource: DataSource,
    entryId: String,
): String? =
    dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT terminal_reason FROM outbox WHERE entry_id = ?").use { statement ->
            statement.setString(1, entryId)
            statement.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

internal fun outboxStateOf(
    dataSource: DataSource,
    entryId: String,
): String? =
    dataSource.connection.use { connection ->
        connection.prepareStatement(EventSql.SELECT_OUTBOX_STATE).use { statement ->
            statement.setString(1, entryId)
            statement.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

/**
 * 알림 요청 payload 의 test 표본 — 종류별 claim·relay test 가 공유한다(§5 중복 금지). 값은
 * 중요하지 않고 **종류가 `NotificationRequested` 라는 사실**이 중요한 자리가 쓴다.
 */
internal fun notificationRequestedFixture(
    noticeId: String = "fixture-notice-000",
    revision: Int = 1,
): NotificationRequestedPayload =
    NotificationRequestedPayload(
        noticeId = noticeId,
        bidNowReasons = emptyList(),
        ladderPolicyVersion = PolicyVersion(EffectiveFrom.Initial, "fixture"),
        strategyRevision = StrategyRevision(revision),
        evidence = NotificationEvidencePayload.NotPredicted(reason = "CircuitOpen"),
    )
