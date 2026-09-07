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
    private fun insertRawObservation(observation: RawNoticeObservation): ObservationKey =
        appendRawObservation(observation)

    /** deadline만 실은 최소 notice 하나를 심는다 — audit 관련 test 셋이 공유하는 준비 단계. */
    private fun seedSimpleNotice(
        id: NoticeId,
        observation: RawNoticeObservation,
        deadline: Instant,
    ): ObservationKey {
        val key = insertRawObservation(observation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = deadline,
                openingScheduledAt = null,
                raw = observation,
            )
        JdbcNoticeRepository(dataSource()).persist(command, key)
        return key
    }

    /** [seedSimpleNotice]가 심은 행의 deadline을 새 관측으로 갱신한다(revision 증가 유발). */
    private fun bumpDeadline(
        id: NoticeId,
        raw: RawNoticeObservation,
        nextObservation: RawNoticeObservation,
        newDeadline: Instant,
    ) {
        val nextKey = insertRawObservation(nextObservation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = newDeadline,
                openingScheduledAt = null,
                raw = raw,
            )
        JdbcNoticeRepository(dataSource()).persist(command, nextKey)
    }

    @Test
    fun `애플리케이션 역할은 raw_observation 을 UPDATE 할 권한이 없다`() {
        val key = insertRawObservation(observationAt(0))
        val sql = "UPDATE raw_observation SET payload = 'TAMPERED' WHERE observation_key = ?"

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
        val sql = "UPDATE raw_observation SET payload = 'TAMPERED' WHERE observation_key = ?"

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
        seedSimpleNotice(id, firstObservation, Instant.parse("2026-09-07T03:00:00Z"))
        bumpDeadline(id, firstObservation, observationAt(4), Instant.parse("2026-09-07T05:00:00Z"))

        dataSource().connection.use { connection ->
            connection.autoCommit = false
            shouldThrow<PSQLException> {
                connection.createStatement().use { it.execute("UPDATE notice_audit SET reason = 'TAMPERED'") }
            }
            connection.rollback()
        }
    }

    @Test
    fun `F-6 재현 — 애플리케이션 역할은 notice_audit 에 위조 행을 직접 INSERT 할 수 없다`() {
        appConnection().use { connection ->
            shouldThrow<PSQLException> {
                connection
                    .prepareStatement(
                        "INSERT INTO notice_audit " +
                            "(notice_number, notice_round, revision, observation_key, reason, previous_row) " +
                            "VALUES ('FORGED', '000', 1, 'forged-key', 'FORGED', '{}'::jsonb)",
                    ).use { it.executeUpdate() }
            }
            connection.rollback()
        }
    }

    @Test
    fun `F-6 — 애플리케이션 역할이 정상 UPDATE 로 값을 바꾸면 SECURITY DEFINER 트리거가 audit 행을 여전히 남긴다`() {
        // notice_audit에 직접 INSERT 권한이 없어도(위 test), 트리거를 통한 audit 삽입은
        // SECURITY DEFINER로 동작해야 한다 — 권한 축소가 정상 감사 경로까지 막으면 안 된다.
        val id = NoticeId(NoticeNumber.of("AUDIT-SECURITY-DEFINER"), NoticeRound.of("000"))
        seedSimpleNotice(id, observationAt(5), Instant.parse("2026-09-07T03:00:00Z"))

        val secondKey = insertRawObservation(observationAt(6))
        appConnection().use { connection ->
            connection
                .prepareStatement(
                    "UPDATE notice SET deadline_at = ?, observation_key = ? " +
                        "WHERE notice_number = ? AND notice_round = ?",
                ).use { statement ->
                    statement.setTimestamp(1, java.sql.Timestamp.from(Instant.parse("2026-09-07T09:00:00Z")))
                    statement.setString(2, secondKey.value)
                    statement.setString(3, id.number.value)
                    statement.setString(4, id.round.value)
                    statement.executeUpdate()
                }
            connection.commit()
        }

        val auditCount =
            dataSource().connection.use { connection ->
                connection
                    .prepareStatement(
                        "SELECT count(*) FROM notice_audit WHERE notice_number = ? AND notice_round = ?",
                    ).use { statement ->
                        statement.setString(1, id.number.value)
                        statement.setString(2, id.round.value)
                        statement.executeQuery().use { rs ->
                            rs.next()
                            rs.getLong(1)
                        }
                    }
            }
        auditCount shouldBe 1L
    }
}
