package bidvector.adapters.persistence

/**
 * SQL 문자열 상수 — mapper 함수와 분리한다(3D 설계 검토 「구현 지침」, sizeGate 500).
 * `notice`는 DB constraint(V2)와 별개로 **Kotlin write 규칙이 같은 데이터
 * (`bidvector.procurement.mayOverwrite`)로 미리 거른다** — 여기 SQL은 그 결과값을 그대로
 * SET하는 평범한 UPDATE다. `opening_result`·`qualification_text`는 provenance 점유 가드가
 * 없는 「최신 관측 우선」 축이라 `ON CONFLICT ... WHERE observed_at >= ...` 한 문으로
 * insert/update/no-op을 다 낸다 — `RETURNING (xmax = 0) AS inserted`로 어느 경로였는지
 * 왕복 한 번에 안다(PostgreSQL 관용구: 이 문이 실제로 삽입한 행은 `xmax`가 0이다).
 */
internal object Sql {
    const val INSERT_RAW_OBSERVATION =
        """
        INSERT INTO raw_observation
            (observation_key, source_endpoint, payload, payload_fields, observed_at, release_sha)
        VALUES (?, ?, ?, ?::jsonb, ?, ?)
        ON CONFLICT (observation_key) DO NOTHING
        """

    private const val NOTICE_COLUMNS =
        """
        status, business_category_code, business_category_label,
        base_amount_won, base_amount_currency, base_amount_vat, base_amount_provenance, base_amount_provenance_detail,
        estimated_amount_won, estimated_amount_currency, estimated_amount_vat,
        estimated_amount_provenance, estimated_amount_provenance_detail, estimated_amount_source_key,
        allocated_budget_won, allocated_budget_provenance, allocated_budget_provenance_detail,
        floor_rate_fraction, floor_rate_origin_kind, floor_rate_origin_detail,
        deadline_at, revision
        """

    const val SELECT_NOTICE_FOR_UPDATE =
        "SELECT $NOTICE_COLUMNS FROM notice WHERE notice_number = ? AND notice_round = ? FOR UPDATE"

    const val SELECT_NOTICE = "SELECT $NOTICE_COLUMNS FROM notice WHERE notice_number = ? AND notice_round = ?"

    const val INSERT_NOTICE =
        """
        INSERT INTO notice (
            notice_number, notice_round, status, business_category_code, business_category_label,
            base_amount_won, base_amount_currency, base_amount_vat,
            base_amount_provenance, base_amount_provenance_detail,
            estimated_amount_won, estimated_amount_currency, estimated_amount_vat,
            estimated_amount_provenance, estimated_amount_provenance_detail, estimated_amount_source_key,
            allocated_budget_won, allocated_budget_provenance, allocated_budget_provenance_detail,
            floor_rate_fraction, floor_rate_origin_kind, floor_rate_origin_detail,
            deadline_at, revision, observation_key
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
        """

    const val UPDATE_NOTICE =
        """
        UPDATE notice SET
            business_category_code = ?, business_category_label = ?,
            base_amount_won = ?, base_amount_currency = ?, base_amount_vat = ?,
            base_amount_provenance = ?, base_amount_provenance_detail = ?,
            estimated_amount_won = ?, estimated_amount_currency = ?, estimated_amount_vat = ?,
            estimated_amount_provenance = ?, estimated_amount_provenance_detail = ?, estimated_amount_source_key = ?,
            allocated_budget_won = ?, allocated_budget_provenance = ?, allocated_budget_provenance_detail = ?,
            floor_rate_fraction = ?, floor_rate_origin_kind = ?, floor_rate_origin_detail = ?,
            deadline_at = ?, observation_key = ?
        WHERE notice_number = ? AND notice_round = ?
        """

    const val INSERT_REJECTED_WRITE =
        """
        INSERT INTO rejected_write (notice_number, notice_round, observation_key, reason, attempted_value)
        VALUES (?, ?, ?, ?, ?::jsonb)
        """

    const val SELECT_OPENING_RESULT =
        """
        SELECT winning_rate_fraction, derived_base_amount_won, derived_base_amount_currency,
               derived_base_amount_vat, observed_at, revision
        FROM opening_result WHERE notice_number = ? AND notice_round = ?
        """

    const val UPSERT_OPENING_RESULT =
        """
        INSERT INTO opening_result (
            notice_number, notice_round, winning_rate_fraction,
            derived_base_amount_won, derived_base_amount_currency, derived_base_amount_vat,
            observed_at, revision, observation_key
        ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)
        ON CONFLICT (notice_number, notice_round) DO UPDATE SET
            winning_rate_fraction = EXCLUDED.winning_rate_fraction,
            derived_base_amount_won = EXCLUDED.derived_base_amount_won,
            derived_base_amount_currency = EXCLUDED.derived_base_amount_currency,
            derived_base_amount_vat = EXCLUDED.derived_base_amount_vat,
            observed_at = EXCLUDED.observed_at,
            revision = opening_result.revision + 1,
            observation_key = EXCLUDED.observation_key,
            updated_at = now()
        WHERE EXCLUDED.observed_at >= opening_result.observed_at
        RETURNING (xmax = 0) AS inserted, revision
        """

    const val SELECT_QUALIFICATION_TEXT =
        "SELECT raw_text, observed_at, revision FROM qualification_text WHERE notice_number = ? AND notice_round = ?"

    const val UPSERT_QUALIFICATION_TEXT =
        """
        INSERT INTO qualification_text (notice_number, notice_round, raw_text, observed_at, revision, observation_key)
        VALUES (?, ?, ?, ?, 1, ?)
        ON CONFLICT (notice_number, notice_round) DO UPDATE SET
            raw_text = EXCLUDED.raw_text,
            observed_at = EXCLUDED.observed_at,
            revision = qualification_text.revision + 1,
            observation_key = EXCLUDED.observation_key,
            updated_at = now()
        WHERE EXCLUDED.observed_at >= qualification_text.observed_at
        RETURNING (xmax = 0) AS inserted, revision
        """

    const val INSERT_COLLECTION_RUN =
        """
        INSERT INTO collection_run (
            reference_date, source_endpoint, started_at, finished_at,
            received, normalized, duplicate, dropped, drop_reasons,
            source_total, pages_fetched, truncated, unknown_fields,
            truncation_cause, quota_exceeded, backoff_skipped
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
        """
}
