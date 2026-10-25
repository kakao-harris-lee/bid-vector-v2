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
 * 장부에는 「cutoff 질의를 지원한다」가 남아 있는데 실물은 다른 열을 색인한다.
 *
 * 비교 범위는 `USING` 부터다 — **접근 방법까지** 본다(vr R3-F3: 열 목록만 보던 앞 판에서는
 * `USING hash` 로 바꾼 변이가 살아남았다. 해시 인덱스는 범위 술어(`< cutoff`)에 쓰이지 못하므로
 * 「cutoff 질의를 지원한다」가 조용히 거짓이 된다).
 *
 * 집합은 **제약이 만든 인덱스까지 전수**다(PK 포함) — 「명시 CREATE INDEX 만」으로 좁히면 PK 가
 * 사라지는 변경이 장부 밖이 된다. 새 표가 들어오면 그 PK 때문에 이 등식이 붉어지고, 그 자리에서
 * 보존 조회가 쓸 인덱스를 함께 생각하게 된다.
 */
class CleanMigrationIndexTest : PersistenceTestSupport() {
    private data class IndexSpec(
        val table: String,
        val name: String,
        /** `pg_indexes.indexdef` 의 `USING` 부터 끝까지 — **접근 방법** · 열 목록 · 부분 술어. */
        val keyDefinition: String,
    )

    private val expectedIndexes =
        setOf(
            // --- 제약이 만드는 인덱스(PK) ---
            IndexSpec("raw_observation", "raw_observation_pkey", "USING btree (observation_key)"),
            IndexSpec("provenance_authority", "provenance_authority_pkey", "USING btree (provenance)"),
            IndexSpec("notice", "notice_pkey", "USING btree (notice_number, notice_round)"),
            IndexSpec("notice_audit", "notice_audit_pkey", "USING btree (id)"),
            IndexSpec("rejected_write", "rejected_write_pkey", "USING btree (id)"),
            IndexSpec("opening_result", "opening_result_pkey", "USING btree (notice_number, notice_round)"),
            IndexSpec(
                "opening_reserve_price",
                "opening_reserve_price_pkey",
                "USING btree (notice_number, notice_round, reserve_price_sequence)",
            ),
            IndexSpec("qualification_text", "qualification_text_pkey", "USING btree (notice_number, notice_round)"),
            IndexSpec("collection_run", "collection_run_pkey", "USING btree (id)"),
            IndexSpec("outbox", "outbox_pkey", "USING btree (entry_id)"),
            IndexSpec("inbox", "inbox_pkey", "USING btree (idempotency_key)"),
            IndexSpec("edit_session", "edit_session_pkey", "USING btree (id)"),
            IndexSpec("operator_strategy", "operator_strategy_pkey", "USING btree (id)"),
            IndexSpec("operator_strategy_revision", "operator_strategy_revision_pkey", "USING btree (revision)"),
            IndexSpec("operator_profile", "operator_profile_pkey", "USING btree (id)"),
            IndexSpec("api_request_audit", "api_request_audit_pkey", "USING btree (id)"),
            // M7/7D(추가만). V20 — 세션 PK 는 쿠키 값의 **해시**이고, 계정의 UNIQUE 제약이
            // 자기 인덱스를 함께 만든다(축5 의 등재 하나와 같은 자리).
            IndexSpec("account", "account_pkey", "USING btree (id)"),
            IndexSpec("account", "account_login_name_key", "USING btree (login_name)"),
            IndexSpec("browser_session", "browser_session_pkey", "USING btree (session_digest)"),
            IndexSpec("notice_requirement", "notice_requirement_pkey", "USING btree (notice_number, notice_round)"),
            IndexSpec(
                "notice_requirement_row",
                "notice_requirement_row_pkey",
                "USING btree (notice_number, notice_round, serial_no)",
            ),
            IndexSpec("flyway_schema_history", "flyway_schema_history_pk", "USING btree (installed_rank)"),
            // Flyway 자신이 만드는 보조 인덱스 — 우리 마이그레이션이 만들지 않지만 카탈로그에 있다
            // (집합 등식의 정의가 「우리가 만든 것」이 아니라 「있는 것」이다).
            IndexSpec("flyway_schema_history", "flyway_schema_history_s_idx", "USING btree (success)"),
            // --- 명시 인덱스(V1·V6·V15) ---
            IndexSpec("notice_audit", "notice_audit_notice_idx", "USING btree (notice_number, notice_round)"),
            IndexSpec("collection_run", "collection_run_reference_date_idx", "USING btree (reference_date)"),
            IndexSpec(
                "outbox",
                "outbox_pending_order_idx",
                "USING btree (inserted_at) WHERE (state = 'PENDING'::text)",
            ),
            IndexSpec("api_request_audit", "idx_api_request_audit_occurred_at", "USING btree (occurred_at)"),
            // --- M6/6B-3 V19 — 마스킹 후보 조회 ---
            IndexSpec(
                "raw_observation",
                "raw_observation_pii_unmasked_idx",
                "USING btree (inserted_at) WHERE (pii_masked_at IS NULL)",
            ),
            // --- M6/6B-3 V19 — 파기 cutoff 지원. `api_request_audit` 는 위 인덱스가 이미 진다 ---
            IndexSpec("notice", "notice_retention_cutoff_idx", "USING btree (created_at)"),
            IndexSpec("opening_result", "opening_result_retention_cutoff_idx", "USING btree (created_at)"),
            IndexSpec(
                "opening_reserve_price",
                "opening_reserve_price_retention_cutoff_idx",
                "USING btree (created_at)",
            ),
            IndexSpec("qualification_text", "qualification_text_retention_cutoff_idx", "USING btree (created_at)"),
            IndexSpec("notice_requirement", "notice_requirement_retention_cutoff_idx", "USING btree (created_at)"),
            // CASCADE 기한 가드용. 선행 열을 조인 키로 둔 것은 **정상 운영 규모의 선택**이다 —
            // 중첩 루프 안티 조인에서 index-only scan 을 주고, 해시 안티 조인(첫 실행 규모)에서는
            // `created_at` 선행이 더 싸다. 사유와 실측 수는 V19 주석이 정본이다(mig M-2).
            IndexSpec(
                "notice_requirement_row",
                "notice_requirement_row_retention_cutoff_idx",
                "USING btree (notice_number, notice_round, created_at)",
            ),
            IndexSpec("rejected_write", "rejected_write_retention_cutoff_idx", "USING btree (recorded_at)"),
            IndexSpec("edit_session", "edit_session_retention_cutoff_idx", "USING btree (created_at)"),
            IndexSpec("outbox", "outbox_retention_cutoff_idx", "USING btree (state, inserted_at)"),
            // M7/7D — 세션도 파기 대상이다(보존 분류 PURGE) — 그 cutoff 질의를 받는다.
            IndexSpec("browser_session", "browser_session_retention_cutoff_idx", "USING btree (created_at)"),
            // M7/7B — V21 입찰 건 넷. PK 넷 + UNIQUE 넷(제약이 만든 인덱스) + 질의 인덱스 셋.
            // `bid_case_owner_idx` 의 **선두 열이 `account_id`** 다 — 소유 필터가 색인의 첫
            // 축이라 그 필터를 빠뜨린 질의는 느린 것으로도 드러난다.
            // 자식 셋에는 보존 cutoff 인덱스가 없다 — 시간 기준 파기 대상이 아니기 때문이다
            // (RetentionTableRegistry 의 `OPERATOR_BID_HISTORY`).
            IndexSpec("bid_case", "bid_case_pkey", "USING btree (id)"),
            IndexSpec(
                "bid_case",
                "bid_case_account_id_notice_number_notice_round_bid_kind_key",
                "USING btree (account_id, notice_number, notice_round, bid_kind)",
            ),
            IndexSpec("bid_case", "bid_case_owner_idx", "USING btree (account_id, bid_kind, state)"),
            IndexSpec("bid_case", "bid_case_id_account_key", "USING btree (id, account_id)"),
            IndexSpec("bid_case_transition", "bid_case_transition_pkey", "USING btree (id)"),
            IndexSpec(
                "bid_case_transition",
                "bid_case_transition_case_id_case_version_key",
                "USING btree (case_id, case_version)",
            ),
            IndexSpec("bid_draft", "bid_draft_pkey", "USING btree (id)"),
            IndexSpec("bid_draft", "bid_draft_case_id_draft_seq_key", "USING btree (case_id, draft_seq)"),
            IndexSpec("bid_draft", "bid_draft_id_case_account_key", "USING btree (id, case_id, account_id)"),
            IndexSpec("bid_record", "bid_record_pkey", "USING btree (id)"),
            IndexSpec("bid_record", "bid_record_case_id_record_seq_key", "USING btree (case_id, record_seq)"),
        )

    @Test
    fun `축9 인덱스 목록이 (표, 이름, 정의) 쌍으로 기대와 같다`() {
        val actual = mutableSetOf<IndexSpec>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT tablename, indexname, " +
                            "substring(indexdef from position('USING' in indexdef)) AS key_definition " +
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
