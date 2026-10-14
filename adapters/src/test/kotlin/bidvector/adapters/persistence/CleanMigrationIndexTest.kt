package bidvector.adapters.persistence

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.Test

/**
 * 축 9(인덱스) — mig L-2. 열·트리거·권한·CHECK 는 장부가 있었는데 **인덱스만 없었다**: 뒤의
 * 마이그레이션이 보존 조회의 인덱스를 지워도 빌드는 초록이고, 그 조회가 조용히 전수 스캔으로
 * 내려간다(`green-but-silent` 부류 — 종료 코드는 0 이고 답도 맞다, 비용만 자란다).
 *
 * 집합은 **제약이 만든 인덱스까지 전수**다(PK 포함) — 「명시 CREATE INDEX 만」으로 좁히면 PK 가
 * 사라지는 변경이 장부 밖이 된다. 새 표가 들어오면 그 PK 때문에 이 등식이 붉어지고, 그 자리에서
 * 보존 조회가 쓸 인덱스를 함께 생각하게 된다.
 */
class CleanMigrationIndexTest : PersistenceTestSupport() {
    private data class IndexSpec(
        val table: String,
        val name: String,
    )

    private val expectedIndexes =
        setOf(
            // --- 제약이 만드는 인덱스(PK) ---
            IndexSpec("raw_observation", "raw_observation_pkey"),
            IndexSpec("provenance_authority", "provenance_authority_pkey"),
            IndexSpec("notice", "notice_pkey"),
            IndexSpec("notice_audit", "notice_audit_pkey"),
            IndexSpec("rejected_write", "rejected_write_pkey"),
            IndexSpec("opening_result", "opening_result_pkey"),
            IndexSpec("opening_reserve_price", "opening_reserve_price_pkey"),
            IndexSpec("qualification_text", "qualification_text_pkey"),
            IndexSpec("collection_run", "collection_run_pkey"),
            IndexSpec("outbox", "outbox_pkey"),
            IndexSpec("inbox", "inbox_pkey"),
            IndexSpec("edit_session", "edit_session_pkey"),
            IndexSpec("operator_strategy", "operator_strategy_pkey"),
            IndexSpec("operator_strategy_revision", "operator_strategy_revision_pkey"),
            IndexSpec("operator_profile", "operator_profile_pkey"),
            IndexSpec("api_request_audit", "api_request_audit_pkey"),
            IndexSpec("notice_requirement", "notice_requirement_pkey"),
            IndexSpec("notice_requirement_row", "notice_requirement_row_pkey"),
            IndexSpec("flyway_schema_history", "flyway_schema_history_pk"),
            // Flyway 자신이 만드는 보조 인덱스(`success`) — 우리 마이그레이션이 만들지 않지만
            // 카탈로그에 있으므로 등재한다(집합 등식이 「우리가 만든 것」이 아니라 「있는 것」이다).
            IndexSpec("flyway_schema_history", "flyway_schema_history_s_idx"),
            // --- 명시 인덱스(V1·V6·V15) ---
            IndexSpec("notice_audit", "notice_audit_notice_idx"),
            IndexSpec("collection_run", "collection_run_reference_date_idx"),
            IndexSpec("outbox", "outbox_pending_order_idx"),
            IndexSpec("api_request_audit", "idx_api_request_audit_occurred_at"),
            // --- M6/6B-3 V19 — 마스킹 후보 조회 ---
            IndexSpec("raw_observation", "raw_observation_pii_unmasked_idx"),
            // --- M6/6B-3 V19 — 파기 cutoff 지원(mig M-2). `api_request_audit` 는 위 인덱스가 이미 진다 ---
            IndexSpec("notice", "notice_retention_cutoff_idx"),
            IndexSpec("opening_result", "opening_result_retention_cutoff_idx"),
            IndexSpec("opening_reserve_price", "opening_reserve_price_retention_cutoff_idx"),
            IndexSpec("qualification_text", "qualification_text_retention_cutoff_idx"),
            IndexSpec("notice_requirement", "notice_requirement_retention_cutoff_idx"),
            IndexSpec("notice_requirement_row", "notice_requirement_row_retention_cutoff_idx"),
            IndexSpec("rejected_write", "rejected_write_retention_cutoff_idx"),
            IndexSpec("edit_session", "edit_session_retention_cutoff_idx"),
            IndexSpec("outbox", "outbox_retention_cutoff_idx"),
        )

    @Test
    fun `축9 인덱스 목록이 (표, 이름) 쌍으로 기대와 같다`() {
        val actual = mutableSetOf<IndexSpec>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT tablename, indexname FROM pg_indexes WHERE schemaname = 'public'",
                    ).use { rs ->
                        while (rs.next()) {
                            actual += IndexSpec(rs.getString("tablename"), rs.getString("indexname"))
                        }
                    }
            }
        }
        actual shouldContainExactlyInAnyOrder expectedIndexes
    }

    /**
     * `CREATE INDEX CONCURRENTLY` 는 실패하면 **invalid 인덱스를 남긴다**(그 인덱스는 질의에
     * 쓰이지 않고 쓰기 비용만 낸다). 이름 등재만으로는 그 상태가 보이지 않으므로 유효성도 센다 —
     * runbook 의 재시도 절차(`DROP INDEX` → 재생성)가 필요한 순간을 빌드가 알린다.
     */
    @Test
    fun `invalid 인덱스가 하나도 없다 — CONCURRENTLY 실패 잔해가 남지 않았다`() {
        val invalid = mutableListOf<String>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT c.relname FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid " +
                            "WHERE NOT i.indisvalid",
                    ).use { rs ->
                        while (rs.next()) invalid += rs.getString(1)
                    }
            }
        }
        invalid shouldContainExactlyInAnyOrder emptyList()
    }
}
