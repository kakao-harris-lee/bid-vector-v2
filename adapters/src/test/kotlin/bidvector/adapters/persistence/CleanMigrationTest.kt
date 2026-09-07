package bidvector.adapters.persistence

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * S-5 — 빈 컨테이너에서 `V1__`부터 migrate한 스키마가 test가 기대하는 스키마와 같다(⑧,
 * D-3D-6). `information_schema`·`pg_catalog` 질의로 테이블·트리거 목록을 코드 안 기대
 * 목록과 대조한다 — pg_dump 텍스트 골든은 쓰지 않는다(D-3D-6 근거, `PersistenceTestSupport`
 * companion object의 `init` 블록이 이미 clean 컨테이너에 전건 migrate를 실행했다).
 */
class CleanMigrationTest : PersistenceTestSupport() {
    private val expectedTables =
        setOf(
            "raw_observation",
            "provenance_authority",
            "notice",
            "notice_audit",
            "rejected_write",
            "opening_result",
            "qualification_text",
            "collection_run",
            "flyway_schema_history",
        )

    private val expectedTriggers =
        setOf(
            "guard_notice_base_amount",
            "guard_notice_estimated_amount",
            "guard_notice_allocated_budget",
            "guard_notice_floor_rate",
            "notice_revision_bump_trigger",
            "notice_audit_insert_trigger",
            "raw_observation_append_only",
            "notice_audit_append_only",
        )

    @Test
    fun `clean migrate 결과의 테이블 목록이 기대 목록과 같다`() {
        val actual = mutableSetOf<String>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                    ).use { rs ->
                        while (rs.next()) actual += rs.getString("table_name")
                    }
            }
        }

        actual shouldContainExactlyInAnyOrder expectedTables
    }

    @Test
    fun `clean migrate 결과의 트리거 목록이 기대 목록과 같다`() {
        val actual = mutableSetOf<String>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT DISTINCT trigger_name FROM information_schema.triggers WHERE trigger_schema = 'public'",
                    ).use { rs ->
                        while (rs.next()) actual += rs.getString("trigger_name")
                    }
            }
        }

        actual shouldContainExactlyInAnyOrder expectedTriggers
    }

    @Test
    fun `flyway validate 가 통과한다 — migration 이력과 파일이 어긋나지 않는다`() {
        val flyway =
            org.flywaydb.core.Flyway
                .configure()
                .dataSource(dataSource())
                .locations("classpath:db/migration")
                .load()

        // validate()가 예외를 던지지 않으면 통과 — 별도 assertion 없이 호출 자체가 단언이다.
        flyway.validate()
        true shouldBe true
    }

    @Test
    fun `애플리케이션 역할 bidvector_app 은 provenance_authority 를 SELECT 만 할 수 있다`() {
        var grantedPrivileges = emptyList<String>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT privilege_type FROM information_schema.role_table_grants " +
                            "WHERE grantee = 'bidvector_app' AND table_name = 'provenance_authority'",
                    ).use { rs ->
                        val collected = mutableListOf<String>()
                        while (rs.next()) collected += rs.getString("privilege_type")
                        grantedPrivileges = collected
                    }
            }
        }

        grantedPrivileges shouldContainExactlyInAnyOrder listOf("SELECT")
    }
}
