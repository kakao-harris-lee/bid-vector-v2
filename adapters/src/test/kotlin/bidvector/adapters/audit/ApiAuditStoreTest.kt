package bidvector.adapters.audit

import bidvector.adapters.persistence.PersistenceTestSupport
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * S-41이 아니라 이 slice의 audit 왕복 자체를 잰다 — `RequestAuditFilter`가 이 store를
 * 그대로 소비한다(app/http 쪽 test는 fake sink로 fail-closed를 잠그고, 이 test는 실제
 * DB 왕복만 본다, 관심사 분리).
 */
class ApiAuditStoreTest : PersistenceTestSupport() {
    @Test
    fun `append한 행이 그대로 저장된다`() {
        val store = ApiAuditStore(dataSource())
        val row =
            ApiAuditRow(
                occurredAt = Instant.parse("2026-09-19T00:00:00Z"),
                subject = "operator",
                method = "GET",
                path = "/api/strategy",
                statusCode = 200,
                durationMillis = 12,
                correlationId = "corr-1",
            )

        store.append(row)

        val stored =
            dataSource().connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement
                        .executeQuery(
                            "SELECT subject, method, path, status_code, duration_ms, correlation_id " +
                                "FROM api_request_audit",
                        ).use { rs ->
                            check(rs.next()) { "행이 저장되지 않았다" }
                            listOf(
                                rs.getString("subject"),
                                rs.getString("method"),
                                rs.getString("path"),
                                rs.getInt("status_code"),
                                rs.getLong("duration_ms"),
                                rs.getString("correlation_id"),
                            )
                        }
                }
            }

        stored shouldBe listOf("operator", "GET", "/api/strategy", 200, 12L, "corr-1")
    }

    @Test
    fun `추가 전용이다 — 읽기·삭제 메서드가 없다`() {
        val methodNames =
            ApiAuditStore::class.java.declaredMethods
                .map { it.name }
                .toSet()
        methodNames shouldBe setOf("append")
    }
}
