package bidvector.adapters.persistence

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 축 9(인덱스) — mig L-2. 열·트리거·권한·CHECK 는 장부가 있었는데 **인덱스만 없었다**: 뒤의
 * 마이그레이션이 보존 조회의 인덱스를 지워도 빌드는 초록이고, 그 조회가 조용히 전수 스캔으로
 * 내려간다(`green-but-silent` 부류 — 종료 코드는 0 이고 답도 맞다, 비용만 자란다).
 *
 * **이름이 아니라 정의로 잰다**(vr R2-F3). (표, 이름) 쌍으로만 재면 **같은 이름으로 다른 열에** 인덱스를
 * 다시 정의하는 변경이 초록이다(실측: `… ON notice (created_at)` → `(updated_at)` 변이가 생존했다) —
 * 장부에는 「cutoff 질의를 지원한다」가 남아 있는데 실물은 다른 열을 색인한다. 정의의 **열 목록과 부분
 * 술어**까지 비교하면 그 변경이 RED 가 된다.
 *
 * 집합은 **제약이 만든 인덱스까지 전수**다(PK 포함) — 「명시 CREATE INDEX 만」으로 좁히면 PK 가
 * 사라지는 변경이 장부 밖이 된다. 새 표가 들어오면 그 PK 때문에 이 등식이 붉어지고, 그 자리에서
 * 보존 조회가 쓸 인덱스를 함께 생각하게 된다.
 */
class CleanMigrationIndexTest : PersistenceTestSupport() {
    private data class IndexSpec(
        val table: String,
        val name: String,
        /** `pg_indexes.indexdef` 의 괄호부터 끝까지 — 열 목록과 부분 술어. */
        val keyDefinition: String,
    )

    private val expectedIndexes =
        setOf(
            // --- 제약이 만드는 인덱스(PK) ---
            IndexSpec("raw_observation", "raw_observation_pkey", "(observation_key)"),
            IndexSpec("provenance_authority", "provenance_authority_pkey", "(provenance)"),
            IndexSpec("notice", "notice_pkey", "(notice_number, notice_round)"),
            IndexSpec("notice_audit", "notice_audit_pkey", "(id)"),
            IndexSpec("rejected_write", "rejected_write_pkey", "(id)"),
            IndexSpec("opening_result", "opening_result_pkey", "(notice_number, notice_round)"),
            IndexSpec(
                "opening_reserve_price",
                "opening_reserve_price_pkey",
                "(notice_number, notice_round, reserve_price_sequence)",
            ),
            IndexSpec("qualification_text", "qualification_text_pkey", "(notice_number, notice_round)"),
            IndexSpec("collection_run", "collection_run_pkey", "(id)"),
            IndexSpec("outbox", "outbox_pkey", "(entry_id)"),
            IndexSpec("inbox", "inbox_pkey", "(idempotency_key)"),
            IndexSpec("edit_session", "edit_session_pkey", "(id)"),
            IndexSpec("operator_strategy", "operator_strategy_pkey", "(id)"),
            IndexSpec("operator_strategy_revision", "operator_strategy_revision_pkey", "(revision)"),
            IndexSpec("operator_profile", "operator_profile_pkey", "(id)"),
            IndexSpec("api_request_audit", "api_request_audit_pkey", "(id)"),
            IndexSpec("notice_requirement", "notice_requirement_pkey", "(notice_number, notice_round)"),
            IndexSpec(
                "notice_requirement_row",
                "notice_requirement_row_pkey",
                "(notice_number, notice_round, serial_no)",
            ),
            IndexSpec("flyway_schema_history", "flyway_schema_history_pk", "(installed_rank)"),
            // Flyway 자신이 만드는 보조 인덱스 — 우리 마이그레이션이 만들지 않지만 카탈로그에 있다
            // (집합 등식의 정의가 「우리가 만든 것」이 아니라 「있는 것」이다).
            IndexSpec("flyway_schema_history", "flyway_schema_history_s_idx", "(success)"),
            // --- 명시 인덱스(V1·V6·V15) ---
            IndexSpec("notice_audit", "notice_audit_notice_idx", "(notice_number, notice_round)"),
            IndexSpec("collection_run", "collection_run_reference_date_idx", "(reference_date)"),
            IndexSpec("outbox", "outbox_pending_order_idx", "(inserted_at) WHERE (state = 'PENDING'::text)"),
            IndexSpec("api_request_audit", "idx_api_request_audit_occurred_at", "(occurred_at)"),
            // --- M6/6B-3 V19 — 마스킹 후보 조회 ---
            IndexSpec(
                "raw_observation",
                "raw_observation_pii_unmasked_idx",
                "(inserted_at) WHERE (pii_masked_at IS NULL)",
            ),
            // --- M6/6B-3 V19 — 파기 cutoff 지원. `api_request_audit` 는 위 인덱스가 이미 진다 ---
            IndexSpec("notice", "notice_retention_cutoff_idx", "(created_at)"),
            IndexSpec("opening_result", "opening_result_retention_cutoff_idx", "(created_at)"),
            IndexSpec("opening_reserve_price", "opening_reserve_price_retention_cutoff_idx", "(created_at)"),
            IndexSpec("qualification_text", "qualification_text_retention_cutoff_idx", "(created_at)"),
            IndexSpec("notice_requirement", "notice_requirement_retention_cutoff_idx", "(created_at)"),
            // CASCADE 기한 가드는 `(notice_number, notice_round)` 로 조인한 뒤 `created_at` 을 본다 —
            // 선행 열이 그 쌍이어야 그 부분질의가 이 인덱스를 쓴다(cr L-2: `created_at` 단독은 계획기가
            // 고르지 않는다 — 장부가 「가드를 돕는다」를 적고 있으면서 돕지 못하는 모양이었다).
            IndexSpec(
                "notice_requirement_row",
                "notice_requirement_row_retention_cutoff_idx",
                "(notice_number, notice_round, created_at)",
            ),
            IndexSpec("rejected_write", "rejected_write_retention_cutoff_idx", "(recorded_at)"),
            IndexSpec("edit_session", "edit_session_retention_cutoff_idx", "(created_at)"),
            IndexSpec("outbox", "outbox_retention_cutoff_idx", "(state, inserted_at)"),
        )

    @Test
    fun `축9 인덱스 목록이 (표, 이름, 정의) 쌍으로 기대와 같다`() {
        val actual = mutableSetOf<IndexSpec>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT tablename, indexname, " +
                            "substring(indexdef from position('(' in indexdef)) AS key_definition " +
                            "FROM pg_indexes WHERE schemaname = 'public'",
                    ).use { rs ->
                        while (rs.next()) {
                            actual +=
                                IndexSpec(
                                    rs.getString("tablename"),
                                    rs.getString("indexname"),
                                    rs.getString("key_definition"),
                                )
                        }
                    }
            }
        }
        actual shouldContainExactlyInAnyOrder expectedIndexes
    }

    /**
     * `CREATE INDEX CONCURRENTLY` 는 실패하면 **invalid 인덱스를 남긴다**(질의에 쓰이지 않고 쓰기 비용만
     * 낸다). V19 는 비동시 인덱스를 한 트랜잭션에서 만들므로 오늘 그 잔해가 생길 수 없지만, 뒤에
     * `CONCURRENTLY` 를 쓰는 마이그레이션이 생기면 이 단언이 그 잔해를 빌드에서 보이게 한다.
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
        invalid shouldBe emptyList()
    }
}
