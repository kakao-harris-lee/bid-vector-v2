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

    // M3/3E ③ — §1.9.7 실측 정정으로 예정가격·기초금액·총예가건수·실개찰일시가 공고 층(부모)
    // 컬럼으로 붙는다(층 C, 추가만). final_award_amount·planned_price는 AwardAmount·
    // YegaAmount(vatTreatment 항상 UNKNOWN 고정)라 vat 컬럼이 없다. verifier r1 H-1 뒤
    // (V5) — 세 금액 축에 provenance(kind+detail) 컬럼을 더해 notice 관례를 따른다(왕복
    // 시 값을 지어내지 않는다).
    // M3/3F — 개찰완료 축 부모 슬롯(층 C, 추가만). opening_rank_one_* 는 OpeningRankOneOutcome,
    // draw_numbers_* 는 DrawNumberObservation 을 그대로 편다(D-3F-4 (a)).
    private const val OPENING_RESULT_COLUMNS =
        """
        winning_rate_fraction, derived_base_amount_won, derived_base_amount_currency,
        derived_base_amount_vat, observed_at, revision,
        final_award_amount_won, final_award_amount_currency,
        final_award_amount_provenance, final_award_amount_provenance_detail,
        final_award_company_name, participant_count, progress_division,
        planned_price_won, planned_price_currency,
        planned_price_provenance, planned_price_provenance_detail,
        opening_base_amount_won, opening_base_amount_currency, opening_base_amount_vat,
        opening_base_amount_provenance, opening_base_amount_provenance_detail,
        total_reserve_price_candidate_count, actual_opening_at,
        opening_rank_one_kind, opening_rank_one_duplicate_count, opening_rank_one_bidder_name,
        opening_rank_one_bid_amount_won, opening_rank_one_bid_amount_currency, opening_rank_one_bid_rate_fraction,
        opening_rank_one_observed_at,
        draw_numbers_kind, draw_numbers, draw_numbers_observed_at, draw_numbers_valid_range_max
        """

    const val SELECT_OPENING_RESULT =
        "SELECT $OPENING_RESULT_COLUMNS FROM opening_result WHERE notice_number = ? AND notice_round = ?"

    const val UPSERT_OPENING_RESULT =
        """
        INSERT INTO opening_result (
            notice_number, notice_round, winning_rate_fraction,
            derived_base_amount_won, derived_base_amount_currency, derived_base_amount_vat,
            final_award_amount_won, final_award_amount_currency,
            final_award_amount_provenance, final_award_amount_provenance_detail,
            final_award_company_name, participant_count, progress_division,
            planned_price_won, planned_price_currency,
            planned_price_provenance, planned_price_provenance_detail,
            opening_base_amount_won, opening_base_amount_currency, opening_base_amount_vat,
            opening_base_amount_provenance, opening_base_amount_provenance_detail,
            total_reserve_price_candidate_count, actual_opening_at,
            opening_rank_one_kind, opening_rank_one_duplicate_count, opening_rank_one_bidder_name,
            opening_rank_one_bid_amount_won, opening_rank_one_bid_amount_currency, opening_rank_one_bid_rate_fraction,
            opening_rank_one_observed_at,
            draw_numbers_kind, draw_numbers, draw_numbers_observed_at, draw_numbers_valid_range_max,
            observed_at, revision, observation_key
        ) VALUES (
            ?, ?, ?,
            ?, ?, ?,
            ?, ?,
            ?, ?,
            ?, ?, ?,
            ?, ?,
            ?, ?,
            ?, ?, ?,
            ?, ?,
            ?, ?,
            ?, ?, ?,
            ?, ?, ?,
            ?,
            ?, ?, ?, ?,
            ?, 1, ?
        )
        ON CONFLICT (notice_number, notice_round) DO UPDATE SET
            -- M-c(verifier r2) — COALESCE로 NULL 유입이 기존 값을 지우지 않게 한다(설계
            -- 검토 ④ 「비었으면 지우지 않는다」). winningRate만 실은 더 늦은 관측이 오면
            -- derivedBaseAmount 세 컬럼은 EXCLUDED에서 전부 NULL인데, COALESCE 없이 그대로
            -- SET하면 기존 값을 지워 존재 가드가 항목을 통째로 실패시켰다(부분 관측이
            -- 정상인데도). 신규 컬럼도 같은 관례를 따른다.
            winning_rate_fraction = COALESCE(EXCLUDED.winning_rate_fraction, opening_result.winning_rate_fraction),
            derived_base_amount_won =
                COALESCE(EXCLUDED.derived_base_amount_won, opening_result.derived_base_amount_won),
            derived_base_amount_currency =
                COALESCE(EXCLUDED.derived_base_amount_currency, opening_result.derived_base_amount_currency),
            derived_base_amount_vat =
                COALESCE(EXCLUDED.derived_base_amount_vat, opening_result.derived_base_amount_vat),
            final_award_amount_won =
                COALESCE(EXCLUDED.final_award_amount_won, opening_result.final_award_amount_won),
            final_award_amount_currency =
                COALESCE(EXCLUDED.final_award_amount_currency, opening_result.final_award_amount_currency),
            final_award_amount_provenance =
                COALESCE(EXCLUDED.final_award_amount_provenance, opening_result.final_award_amount_provenance),
            final_award_amount_provenance_detail = COALESCE(
                EXCLUDED.final_award_amount_provenance_detail, opening_result.final_award_amount_provenance_detail),
            final_award_company_name =
                COALESCE(EXCLUDED.final_award_company_name, opening_result.final_award_company_name),
            participant_count = COALESCE(EXCLUDED.participant_count, opening_result.participant_count),
            progress_division = COALESCE(EXCLUDED.progress_division, opening_result.progress_division),
            planned_price_won = COALESCE(EXCLUDED.planned_price_won, opening_result.planned_price_won),
            planned_price_currency =
                COALESCE(EXCLUDED.planned_price_currency, opening_result.planned_price_currency),
            planned_price_provenance =
                COALESCE(EXCLUDED.planned_price_provenance, opening_result.planned_price_provenance),
            planned_price_provenance_detail = COALESCE(
                EXCLUDED.planned_price_provenance_detail, opening_result.planned_price_provenance_detail),
            opening_base_amount_won =
                COALESCE(EXCLUDED.opening_base_amount_won, opening_result.opening_base_amount_won),
            opening_base_amount_currency =
                COALESCE(EXCLUDED.opening_base_amount_currency, opening_result.opening_base_amount_currency),
            opening_base_amount_vat =
                COALESCE(EXCLUDED.opening_base_amount_vat, opening_result.opening_base_amount_vat),
            opening_base_amount_provenance = COALESCE(
                EXCLUDED.opening_base_amount_provenance, opening_result.opening_base_amount_provenance),
            opening_base_amount_provenance_detail = COALESCE(
                EXCLUDED.opening_base_amount_provenance_detail,
                opening_result.opening_base_amount_provenance_detail),
            total_reserve_price_candidate_count = COALESCE(
                EXCLUDED.total_reserve_price_candidate_count, opening_result.total_reserve_price_candidate_count),
            actual_opening_at = COALESCE(EXCLUDED.actual_opening_at, opening_result.actual_opening_at),
            -- verifier r1 F-1 — 컬럼별 COALESCE 는 opening_rank_one 축(kind + 동반 값 다섯)의
            -- 짝을 깨뜨린다: kind 만 새 값으로 덮이고 동반 컬럼(예: bidderName)이 옛 값으로
            -- 남으면 V5 페어 CHECK 를 위반한다(RankMissing 인데 bidderName 이 이전 Determined
            -- 것으로 남는 경우 등). 그래서 **축 전체를 한 CASE 조건으로 갱신**한다 —
            -- EXCLUDED.opening_rank_one_kind 가 NULL(이 관측이 이 축을 안 실음, NotObserved)
            -- 이면 여섯 컬럼 전부 옛 값을 보존하고, NULL 이 아니면(관측됨) 여섯 컬럼 전부
            -- EXCLUDED 값으로 교체한다 — 컬럼 단위 부분 갱신이 없다. draw_numbers 축도 같다.
            opening_rank_one_kind =
                CASE WHEN EXCLUDED.opening_rank_one_kind IS NULL
                     THEN opening_result.opening_rank_one_kind ELSE EXCLUDED.opening_rank_one_kind END,
            opening_rank_one_duplicate_count =
                CASE WHEN EXCLUDED.opening_rank_one_kind IS NULL
                     THEN opening_result.opening_rank_one_duplicate_count
                     ELSE EXCLUDED.opening_rank_one_duplicate_count END,
            opening_rank_one_bidder_name =
                CASE WHEN EXCLUDED.opening_rank_one_kind IS NULL
                     THEN opening_result.opening_rank_one_bidder_name ELSE EXCLUDED.opening_rank_one_bidder_name END,
            opening_rank_one_bid_amount_won =
                CASE WHEN EXCLUDED.opening_rank_one_kind IS NULL
                     THEN opening_result.opening_rank_one_bid_amount_won
                     ELSE EXCLUDED.opening_rank_one_bid_amount_won END,
            opening_rank_one_bid_amount_currency =
                CASE WHEN EXCLUDED.opening_rank_one_kind IS NULL
                     THEN opening_result.opening_rank_one_bid_amount_currency
                     ELSE EXCLUDED.opening_rank_one_bid_amount_currency END,
            opening_rank_one_bid_rate_fraction =
                CASE WHEN EXCLUDED.opening_rank_one_kind IS NULL
                     THEN opening_result.opening_rank_one_bid_rate_fraction
                     ELSE EXCLUDED.opening_rank_one_bid_rate_fraction END,
            opening_rank_one_observed_at =
                CASE WHEN EXCLUDED.opening_rank_one_kind IS NULL
                     THEN opening_result.opening_rank_one_observed_at ELSE EXCLUDED.opening_rank_one_observed_at END,
            draw_numbers_kind =
                CASE WHEN EXCLUDED.draw_numbers_kind IS NULL
                     THEN opening_result.draw_numbers_kind ELSE EXCLUDED.draw_numbers_kind END,
            draw_numbers =
                CASE WHEN EXCLUDED.draw_numbers_kind IS NULL
                     THEN opening_result.draw_numbers ELSE EXCLUDED.draw_numbers END,
            draw_numbers_observed_at =
                CASE WHEN EXCLUDED.draw_numbers_kind IS NULL
                     THEN opening_result.draw_numbers_observed_at ELSE EXCLUDED.draw_numbers_observed_at END,
            draw_numbers_valid_range_max =
                CASE WHEN EXCLUDED.draw_numbers_kind IS NULL
                     THEN opening_result.draw_numbers_valid_range_max ELSE EXCLUDED.draw_numbers_valid_range_max END,
            observed_at = EXCLUDED.observed_at,
            revision = opening_result.revision + 1,
            observation_key = EXCLUDED.observation_key,
            updated_at = now()
        WHERE EXCLUDED.observed_at >= opening_result.observed_at
        RETURNING (xmax = 0) AS inserted, revision
        """

    // M3/3E ⑤⑥ — 복수예비가격 후보 자식 표(층 B, D-3E-2 (a)). 「최신 관측 우선」이라
    // opening_result와 같은 COALESCE 관례를 쓴다 — 부모 upsert가 자식을 조용히 덮지 않도록
    // 별도 문으로 갈랐다(한 항목 트랜잭션 안에서 반복 실행, JdbcOpeningResultRepository).
    // verifier r1 H-2 뒤 — observed_at을 더 골라 D-3E-3 (a)의 「관측 시각으로 구분한다」가
    // 읽기 경로에도 서게 한다(15→12 재수집 뒤 낡은 행을 소비자가 판별할 수 있어야 한다).
    const val SELECT_OPENING_RESERVE_PRICES =
        """
        SELECT reserve_price_sequence, base_reserve_price_won, base_reserve_price_currency,
               is_drawn, draw_count, observed_at
        FROM opening_reserve_price WHERE notice_number = ? AND notice_round = ?
        ORDER BY reserve_price_sequence
        """

    const val UPSERT_OPENING_RESERVE_PRICE =
        """
        INSERT INTO opening_reserve_price (
            notice_number, notice_round, reserve_price_sequence,
            base_reserve_price_won, base_reserve_price_currency,
            is_drawn, draw_count, observed_at, revision, observation_key
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
        ON CONFLICT (notice_number, notice_round, reserve_price_sequence) DO UPDATE SET
            base_reserve_price_won =
                COALESCE(EXCLUDED.base_reserve_price_won, opening_reserve_price.base_reserve_price_won),
            base_reserve_price_currency =
                COALESCE(EXCLUDED.base_reserve_price_currency, opening_reserve_price.base_reserve_price_currency),
            is_drawn = COALESCE(EXCLUDED.is_drawn, opening_reserve_price.is_drawn),
            draw_count = COALESCE(EXCLUDED.draw_count, opening_reserve_price.draw_count),
            observed_at = EXCLUDED.observed_at,
            revision = opening_reserve_price.revision + 1,
            observation_key = EXCLUDED.observation_key,
            updated_at = now()
        WHERE EXCLUDED.observed_at >= opening_reserve_price.observed_at
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
