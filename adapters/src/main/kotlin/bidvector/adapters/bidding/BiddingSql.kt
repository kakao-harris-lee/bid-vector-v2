package bidvector.adapters.bidding

/**
 * V21 네 표의 SQL — **모든 문장에 `account_id = ?` 가 있다.**
 *
 * 그것이 소유 경계의 본체다. 포트 서명이 주체를 요구하는 것(`BidCaseStorePort`)은 「계정 없이
 * 부를 수 없다」까지이고, **그 계정이 실제로 질의를 좁히는가**는 이 파일이 진다. 둘 다 없으면
 * 조용히 열린다 — `BidCaseOwnershipTest` 가 실 DB 위에서 그 등식을 잰다.
 *
 * 쓰는 열은 V21 의 열 단위 GRANT 와 **짝**이다: `bid_case` 의 UPDATE 는 전이가 바꾸는 일곱
 * 열뿐이고, `account_id` · 공고 키 · `bid_kind` 를 적는 UPDATE 문장이 여기 **없다**.
 * 나머지 세 표에는 UPDATE · DELETE 문장이 아예 없다(append-only).
 */
internal object BiddingSql {
    private const val CASE_COLUMNS =
        "id, notice_number, notice_round, bid_kind, state, case_version, " +
            "deadline_at, remind_at, opening_at, document_due_at, outcome_notice_expected_at, skip_reason"

    const val SELECT_CASE_BY_ID =
        "SELECT $CASE_COLUMNS FROM bid_case WHERE account_id = ? AND id = ?"

    const val SELECT_CASE_BY_KEY =
        "SELECT $CASE_COLUMNS FROM bid_case " +
            "WHERE account_id = ? AND notice_number = ? AND notice_round = ? AND bid_kind = ?"

    /**
     * 상태 탭은 **선택**이다 — `?::text IS NULL` 로 한 문장에 담는다(문장 둘을 두면 한쪽에만
     * 필터를 빠뜨리는 자리가 생긴다).
     */
    const val SELECT_CASES =
        "SELECT $CASE_COLUMNS FROM bid_case " +
            "WHERE account_id = ? AND bid_kind = ? AND (?::text IS NULL OR state = ?::text) " +
            "ORDER BY id"

    /**
     * 마감 판정 후보(계약 D-7B-4 결정 ⑧) — 유효 마감은 **공고 행이 있으면 그 값, 없으면 사본**이다(결정 ⑥과 같은 정의).
     * ① 판정을 세우는 상태(전이표에서 뽑아 배열로 넘긴다) 중 유효 마감이 지난 건 ② 「마감 지남」 중 유효 마감이 미래 · 미정인
     * 건(되돌림 후보). 상위집합이어도 된다 — 판정은 use case 가 건마다 다시 한다.
     */
    const val SELECT_SETTLE_CANDIDATES =
        "SELECT c.id, c.notice_number, c.notice_round, c.bid_kind, c.state, c.case_version, c.deadline_at, " +
            "c.remind_at, c.opening_at, c.document_due_at, c.outcome_notice_expected_at, c.skip_reason " +
            "FROM bid_case c LEFT JOIN notice n " +
            "ON n.notice_number = c.notice_number AND n.notice_round = c.notice_round " +
            "WHERE c.account_id = ? AND c.bid_kind = ? AND (" +
            "(c.state = ANY (?::text[]) AND " +
            "(CASE WHEN n.notice_number IS NULL THEN c.deadline_at ELSE n.deadline_at END) < ?) " +
            "OR (c.state = 'DEADLINE_PASSED_UNRECORDED' AND " +
            "((CASE WHEN n.notice_number IS NULL THEN c.deadline_at ELSE n.deadline_at END) IS NULL " +
            "OR (CASE WHEN n.notice_number IS NULL THEN c.deadline_at ELSE n.deadline_at END) >= ?))" +
            ") ORDER BY c.id"

    const val INSERT_CASE =
        "INSERT INTO bid_case (" +
            "account_id, notice_number, notice_round, bid_kind, state, case_version, deadline_at, updated_at" +
            ") VALUES (?, ?, ?, ?, 'WATCHING', 0, ?, ?) " +
            "ON CONFLICT (account_id, notice_number, notice_round, bid_kind) DO NOTHING " +
            "RETURNING $CASE_COLUMNS"

    /**
     * 전이의 **조건부 갱신** — `case_version = ?` 가 낙관적 잠금이다. 0 행이면 경합이고,
     * 그때 아무것도 쓰이지 않는다(타임라인 INSERT 는 같은 트랜잭션 뒤에 온다).
     *
     * 일정 축 셋과 사유는 **전이가 정한 값으로 통째 덮는다** — 「이전 상태의 잔재가 남는」
     * 경우를 없앤다(건너뜀이 아닌 상태에 사유가 남으면 CHECK 가 거부한다).
     *
     * `outcome_notice_expected_at` 은 **이 문장에 없다**(계약 D-7B-3 결정 ②) — 전이가 그 축을 정하지
     * 않으므로 덮으면 지운다. 쓰는 경로는 아직 없다(`OPEN-7B-NOTICE-EXPECTED-WRITER`). `deadline_at`
     * 사본도 전이가 고치지 않는다 — 둘 다 V21 의 열 GRANT 밖이다.
     */
    const val UPDATE_CASE_STATE =
        "UPDATE bid_case SET " +
            "state = ?, case_version = ?, remind_at = ?, opening_at = ?, document_due_at = ?, " +
            "skip_reason = ?, updated_at = ? " +
            "WHERE account_id = ? AND id = ? AND case_version = ?"

    const val INSERT_TRANSITION =
        "INSERT INTO bid_case_transition (" +
            "case_id, account_id, case_version, from_state, to_state, command, " +
            "actor_kind, actor_detail, reason, retroactive, occurred_at" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

    const val SELECT_TIMELINE =
        "SELECT case_version, from_state, to_state, command, actor_kind, actor_detail, " +
            "reason, retroactive, occurred_at FROM bid_case_transition " +
            "WHERE account_id = ? AND case_id = ? ORDER BY case_version"

    /** 마지막 타임라인 한 줄 — 되돌림 판단은 이것만 본다(D-7B-5 C3-L8). 열은 [SELECT_TIMELINE] 과 같다. */
    const val SELECT_LAST_TRANSITION =
        "SELECT case_version, from_state, to_state, command, actor_kind, actor_detail, " +
            "reason, retroactive, occurred_at FROM bid_case_transition " +
            "WHERE account_id = ? AND case_id = ? ORDER BY case_version DESC LIMIT 1"

    const val NEXT_DRAFT_SEQ =
        "SELECT COALESCE(MAX(draft_seq) + 1, 0) FROM bid_draft WHERE account_id = ? AND case_id = ?"

    const val INSERT_DRAFT =
        "INSERT INTO bid_draft (" +
            "case_id, account_id, draft_seq, amount_won, amount_currency, amount_vat, " +
            "amount_provenance, chosen_option_ref, presented_at, snapshot_notice_revision, " +
            "snapshot_taken_at, snapshot_deadline_at, snapshot_floor_rate_fraction, " +
            "snapshot_floor_rate_origin_kind, snapshot_floor_rate_origin_detail, " +
            "snapshot_base_amount_won, snapshot_base_amount_currency, snapshot_base_amount_vat, " +
            "snapshot_base_amount_provenance, snapshot_base_amount_provenance_detail" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

    const val NEXT_RECORD_SEQ =
        "SELECT COALESCE(MAX(record_seq) + 1, 0) FROM bid_record WHERE account_id = ? AND case_id = ?"

    /**
     * **기록 시점의 최신 초안**이다 — 화면의 기본값과 같다는 보장은 없다(요청이 초안 식별자를 싣지 않는다). 「이 초안대로
     * 냈다」는 뜻도 아니다 — 금액은 기록이 따로 싣는다(계약 D-7B-4, C-M6 문면).
     */
    const val LATEST_DRAFT_ID =
        "SELECT id FROM bid_draft WHERE account_id = ? AND case_id = ? ORDER BY draft_seq DESC LIMIT 1"

    const val INSERT_RECORD =
        "INSERT INTO bid_record (" +
            "case_id, account_id, record_seq, amount_won, amount_currency, amount_vat, " +
            "amount_provenance, chosen_option_ref, submitted_at, confirmed_at, draft_id, " +
            "retroactive, draw_numbers, evidence_ref, evidence_retain_until, memo" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

    /** 공고 마감 — 사용자 소유 데이터가 아니라 계정 축이 없다(`NoticeDeadlineQueryPort` KDoc). */
    const val SELECT_NOTICE_DEADLINE =
        "SELECT notice_number, notice_round, deadline_at FROM notice " +
            "WHERE (notice_number, notice_round) IN (SELECT * FROM unnest(?::text[], ?::text[]))"
}
