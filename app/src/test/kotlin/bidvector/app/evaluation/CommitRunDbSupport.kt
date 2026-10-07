package bidvector.app.evaluation

import javax.sql.DataSource

/**
 * `EvaluationCommitRunE2ETest` 가 DB 를 **들여다보는** 질의들 — 파일 500줄 한도로 갈라냈다.
 *
 * 기계적 분할이 아니다: 여기 모인 것은 전부 「사실을 확인하려고 거는 읽기 질의」와 「전제를
 * 세우려고 거는 쓰기 질의」이고, 남는 파일은 **무엇을 단언하는가**만 든다. 수신자가 클래스
 * 상태를 쓰지 않아 [DataSource] 인자 하나로 완결된다.
 */
internal fun rowCountsByTable(dataSource: DataSource): Map<String, Long> =
    dataSource.connection.use { connection ->
        val tables = mutableListOf<String>()
        connection
            .prepareStatement(
                "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
            ).use { statement ->
                statement.executeQuery().use { rs -> while (rs.next()) tables += rs.getString(1) }
            }
        tables.associateWith { table ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM \"$table\"").use { rs ->
                    check(rs.next())
                    rs.getLong(1)
                }
            }
        }
    }

internal fun changedTables(
    before: Map<String, Long>,
    after: Map<String, Long>,
): Set<String> = before.keys.filter { before.getValue(it) != after.getValue(it) }.toSet()

/** 저장된 outbox 행의 두 칸 — 타입 열과 payload 문자열. */
internal data class OutboxRow(
    val payloadType: String,
    val payload: String,
)

internal fun notificationRows(dataSource: DataSource): List<OutboxRow> =
    dataSource.connection.use { connection ->
        connection
            .prepareStatement(
                "SELECT payload_type, payload FROM outbox " +
                    "WHERE payload_type = 'NotificationRequested' ORDER BY inserted_at, entry_id",
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(OutboxRow(rs.getString(1), rs.getString(2))) }
                }
            }
    }

internal fun outboxStates(dataSource: DataSource): Map<String, Int> =
    dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT state, count(*) FROM outbox GROUP BY state").use { statement ->
            statement.executeQuery().use { rs ->
                buildMap { while (rs.next()) put(rs.getString(1), rs.getInt(2)) }
            }
        }
    }

internal fun clearOutbox(dataSource: DataSource) =
    dataSource.connection.use { connection ->
        connection.createStatement().use { it.execute("TRUNCATE TABLE outbox, inbox") }
    }

internal fun setStrategyRevision(
    dataSource: DataSource,
    revision: Int,
) = dataSource.connection.use { connection ->
    connection.prepareStatement("UPDATE operator_strategy SET revision = ? WHERE id = 1").use { statement ->
        statement.setInt(1, revision)
        statement.executeUpdate()
    }
}

/**
 * `NotificationRequested` payload 의 INSERT 를 거부하는 CHECK 제약 — outbox 쓰기 실패를
 * **주입**한다(`OutboxNotificationRequestPort` 가 `SQLException → Failed` 로 가는 경로).
 */
internal fun blockNotificationInserts(
    dataSource: DataSource,
    constraint: String,
) = dataSource.connection.use { connection ->
    connection.createStatement().use {
        it.execute("ALTER TABLE outbox ADD CONSTRAINT $constraint CHECK (payload_type <> 'NotificationRequested')")
    }
}

internal fun unblockNotificationInserts(
    dataSource: DataSource,
    constraint: String,
) = dataSource.connection.use { connection ->
    connection.createStatement().use { it.execute("ALTER TABLE outbox DROP CONSTRAINT $constraint") }
}
