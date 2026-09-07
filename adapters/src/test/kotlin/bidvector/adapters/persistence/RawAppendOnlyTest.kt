package bidvector.adapters.persistence

import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ObservationKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.sql.Timestamp
import java.time.Instant

private fun observationAt(second: Int): RawNoticeObservation {
    val observedAt = Instant.parse("2026-09-07T00:00:0${second}Z")
    return RawNoticeObservation.of(emptyMap(), SourceEndpoint.NOTICE_LIST, observedAt)
}

/**
 * ② raw는 갱신·삭제 경로가 없다(우회 (9)(10)) — 두 겹 방어: 권한(GRANT 미부여) + 트리거
 * (BEFORE UPDATE OR DELETE RAISE). `notice_audit`도 같은 append-only 성질을 갖는다(우회 (10)
 * 「audit을 지워 이력 소거」).
 */
class RawAppendOnlyTest : PersistenceTestSupport() {
    private fun insertRawObservation(observation: RawNoticeObservation): ObservationKey {
        val key = ObservationKey.of(observation)
        dataSource().connection.use { connection ->
            connection.prepareStatement(Sql.INSERT_RAW_OBSERVATION).use { statement ->
                statement.setString(1, key.value)
                statement.setString(2, observation.sourceEndpoint.name)
                statement.setString(3, "{}")
                statement.setTimestamp(4, Timestamp.from(observation.observedAt))
                statement.setString(5, "test-release")
                statement.executeUpdate()
            }
        }
        return key
    }

    @Test
    fun `애플리케이션 역할은 raw_observation 을 UPDATE 할 권한이 없다`() {
        val key = insertRawObservation(observationAt(0))
        val sql = "UPDATE raw_observation SET payload = '{\"x\":1}'::jsonb WHERE observation_key = ?"

        appConnection().use { connection ->
            shouldThrow<PSQLException> {
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, key.value)
                    statement.executeUpdate()
                }
            }
            connection.rollback()
        }
    }

    @Test
    fun `superuser 의 직접 UPDATE 도 append-only 트리거가 거부한다`() {
        val key = insertRawObservation(observationAt(1))
        val sql = "UPDATE raw_observation SET payload = '{\"x\":1}'::jsonb WHERE observation_key = ?"

        dataSource().connection.use { connection ->
            connection.autoCommit = false
            shouldThrow<PSQLException> {
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, key.value)
                    statement.executeUpdate()
                }
            }
            connection.rollback()
        }
    }

    @Test
    fun `superuser 의 직접 DELETE 도 append-only 트리거가 거부한다`() {
        val key = insertRawObservation(observationAt(2))

        dataSource().connection.use { connection ->
            connection.autoCommit = false
            shouldThrow<PSQLException> {
                connection.prepareStatement("DELETE FROM raw_observation WHERE observation_key = ?").use { statement ->
                    statement.setString(1, key.value)
                    statement.executeUpdate()
                }
            }
            connection.rollback()
        }
    }

    @Test
    fun `애플리케이션 역할은 raw_observation 에 TRUNCATE 권한이 없다`() {
        appConnection().use { connection ->
            shouldThrow<PSQLException> {
                connection.createStatement().use { it.execute("TRUNCATE raw_observation") }
            }
            connection.rollback()
        }
    }

    @Test
    fun `애플리케이션 역할은 notice_audit 을 DELETE 할 권한이 없다 — 행이 없어도 GRANT 미부여가 먼저 막는다`() {
        appConnection().use { connection ->
            shouldThrow<PSQLException> {
                connection.createStatement().use { it.execute("DELETE FROM notice_audit") }
            }
            connection.rollback()
        }
    }

    @Test
    fun `superuser 의 직접 UPDATE 도 notice_audit 의 append-only 트리거가 거부한다`() {
        // notice_audit에 실제 행을 하나 만든다 — insert(초기 행) 다음 update로 revision을
        // 올려야 AFTER UPDATE 트리거가 audit 행을 낸다(초기 insert 자체는 audit을 만들지 않는다).
        val id = NoticeId(NoticeNumber.of("AUDIT-APPEND-ONLY"), NoticeRound.of("000"))
        val firstObservation = observationAt(3)
        val firstKey = insertRawObservation(firstObservation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = Instant.parse("2026-09-07T03:00:00Z"),
                openingScheduledAt = null,
                raw = firstObservation,
            )
        val repository = JdbcNoticeRepository(dataSource())
        repository.persist(command, firstKey)

        val secondObservation = observationAt(4)
        val secondKey = insertRawObservation(secondObservation)
        repository.persist(command.copy(deadlineAt = Instant.parse("2026-09-07T05:00:00Z")), secondKey)

        dataSource().connection.use { connection ->
            connection.autoCommit = false
            shouldThrow<PSQLException> {
                connection.createStatement().use { it.execute("UPDATE notice_audit SET reason = 'TAMPERED'") }
            }
            connection.rollback()
        }
    }
}
