package bidvector.adapters.persistence

/**
 * V21 입찰 건 넷의 컬럼 등재 — `CleanMigrationColumnTest`(sizeGate 500줄, v2-지침서 §5)에서
 * 갈라 나왔다. `CleanMigrationCheckTest`·`CleanMigrationTriggerTest`·`CleanMigrationPrivilegeTest`
 * 가 같은 이유로 갈라진 것과 같은 전례다(기능 변경 없음 — 그 test 의 합성에 이 목록이 그대로
 * 더해진다).
 *
 * 등재가 **전수**라는 것이 이 목록의 뜻이다: 컬럼 하나를 더하거나 타입을 바꾸면 그 test 가
 * 붉어진다. **사업자번호·대표자명 슬롯이 없다**(계약 산출물 8)는 주장도 이 목록이 증거다 —
 * 그 열이 생기면 여기 없는 컬럼으로 잡힌다.
 */
internal val bidCaseColumns =
    listOf(
        // M7/7B 신규(추가만, 스키마 스냅샷 래칫 예외). V21__bid_case.sql.
        // **사업자번호·대표자명 슬롯이 없다**(계약 산출물 8) — 이 목록이 그 부재의 증거다.
        // --- bid_case
        ColumnSpec("bid_case", "id", "bigint", false),
        ColumnSpec("bid_case", "account_id", "bigint", false),
        ColumnSpec("bid_case", "notice_number", "text", false),
        ColumnSpec("bid_case", "notice_round", "text", false),
        ColumnSpec("bid_case", "bid_kind", "text", false),
        ColumnSpec("bid_case", "state", "text", false),
        ColumnSpec("bid_case", "case_version", "integer", false),
        ColumnSpec("bid_case", "remind_at", "timestamp with time zone", true),
        ColumnSpec("bid_case", "opening_at", "timestamp with time zone", true),
        ColumnSpec("bid_case", "document_due_at", "timestamp with time zone", true),
        ColumnSpec("bid_case", "outcome_notice_expected_at", "timestamp with time zone", true),
        ColumnSpec("bid_case", "skip_reason", "text", true),
        ColumnSpec("bid_case", "created_at", "timestamp with time zone", false, true),
        ColumnSpec("bid_case", "updated_at", "timestamp with time zone", false),
        // --- bid_case_transition
        ColumnSpec("bid_case_transition", "id", "bigint", false),
        ColumnSpec("bid_case_transition", "case_id", "bigint", false),
        ColumnSpec("bid_case_transition", "account_id", "bigint", false),
        ColumnSpec("bid_case_transition", "case_version", "integer", false),
        ColumnSpec("bid_case_transition", "from_state", "text", true),
        ColumnSpec("bid_case_transition", "to_state", "text", false),
        ColumnSpec("bid_case_transition", "command", "text", false),
        ColumnSpec("bid_case_transition", "actor_kind", "text", false),
        ColumnSpec("bid_case_transition", "actor_detail", "text", true),
        ColumnSpec("bid_case_transition", "reason", "text", true),
        ColumnSpec("bid_case_transition", "retroactive", "boolean", false),
        ColumnSpec("bid_case_transition", "occurred_at", "timestamp with time zone", false),
        ColumnSpec("bid_case_transition", "recorded_at", "timestamp with time zone", false, true),
        // --- bid_draft
        ColumnSpec("bid_draft", "id", "bigint", false),
        ColumnSpec("bid_draft", "case_id", "bigint", false),
        ColumnSpec("bid_draft", "account_id", "bigint", false),
        ColumnSpec("bid_draft", "draft_seq", "integer", false),
        ColumnSpec("bid_draft", "amount_won", "numeric", false),
        ColumnSpec("bid_draft", "amount_currency", "text", false),
        ColumnSpec("bid_draft", "amount_vat", "text", false),
        ColumnSpec("bid_draft", "amount_provenance", "text", false),
        ColumnSpec("bid_draft", "chosen_option_ref", "text", true),
        ColumnSpec("bid_draft", "presented_at", "timestamp with time zone", false),
        ColumnSpec("bid_draft", "snapshot_notice_revision", "bigint", false),
        ColumnSpec("bid_draft", "snapshot_taken_at", "timestamp with time zone", false),
        ColumnSpec("bid_draft", "snapshot_deadline_at", "timestamp with time zone", true),
        ColumnSpec("bid_draft", "snapshot_floor_rate_fraction", "numeric", true),
        ColumnSpec("bid_draft", "snapshot_floor_rate_origin_kind", "text", true),
        ColumnSpec("bid_draft", "snapshot_floor_rate_origin_detail", "text", true),
        ColumnSpec("bid_draft", "snapshot_base_amount_won", "numeric", true),
        ColumnSpec("bid_draft", "snapshot_base_amount_currency", "text", true),
        ColumnSpec("bid_draft", "snapshot_base_amount_vat", "text", true),
        ColumnSpec("bid_draft", "snapshot_base_amount_provenance", "text", true),
        ColumnSpec("bid_draft", "created_at", "timestamp with time zone", false, true),
        // --- bid_record
        ColumnSpec("bid_record", "id", "bigint", false),
        ColumnSpec("bid_record", "case_id", "bigint", false),
        ColumnSpec("bid_record", "account_id", "bigint", false),
        ColumnSpec("bid_record", "record_seq", "integer", false),
        ColumnSpec("bid_record", "amount_won", "numeric", false),
        ColumnSpec("bid_record", "amount_currency", "text", false),
        ColumnSpec("bid_record", "amount_vat", "text", false),
        ColumnSpec("bid_record", "amount_provenance", "text", false),
        ColumnSpec("bid_record", "chosen_option_ref", "text", true),
        ColumnSpec("bid_record", "submitted_at", "timestamp with time zone", false),
        ColumnSpec("bid_record", "confirmed_at", "timestamp with time zone", false),
        ColumnSpec("bid_record", "draft_id", "bigint", true),
        ColumnSpec("bid_record", "retroactive", "boolean", false),
        ColumnSpec("bid_record", "draw_numbers", "ARRAY", true),
        ColumnSpec("bid_record", "evidence_ref", "text", true),
        ColumnSpec("bid_record", "evidence_retain_until", "date", true),
        ColumnSpec("bid_record", "memo", "text", true),
        ColumnSpec("bid_record", "created_at", "timestamp with time zone", false, true),
    )
