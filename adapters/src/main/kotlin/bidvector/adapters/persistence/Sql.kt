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
        demand_agency_code, demand_agency_name, notice_agency_code, notice_agency_name,
        deadline_at, revision
        """

    const val SELECT_NOTICE_FOR_UPDATE =
        "SELECT $NOTICE_COLUMNS FROM notice WHERE notice_number = ? AND notice_round = ? FOR UPDATE"

    const val SELECT_NOTICE = "SELECT $NOTICE_COLUMNS FROM notice WHERE notice_number = ? AND notice_round = ?"

    /**
     * 후보 다건 스캔(M6/6F-2, D-6F2-2~5) — 상태 집합은 `= ANY(?)`로 바인딩한다(호출부가
     * `bidvector.adapters.evaluation.biddableStatuses()`로 도메인 술어에서 기계 산출한
     * 값을 넘긴다 — 여기 상태 리터럴을 적지 않는다). `deadline_at > ?`는 반개구간(D-6F2-3,
     * `?`는 주입된 `Clock.now()` — DB `now()`를 쓰지 않는다). `LIMIT ?`는 호출부가
     * `cap + 1`을 넘겨 절삭 여부를 판단한다(D-6F2-4, 조용한 절삭이 아니다). 정렬은
     * 결정적이다(D-6F2-5) — `analysisBudget`이 이 순서 그대로 앞에서부터 쓴다.
     */
    const val SELECT_OPEN_CANDIDATES =
        """
        SELECT notice_number, notice_round, $NOTICE_COLUMNS
        FROM notice
        WHERE status = ANY(?) AND deadline_at > ?
        ORDER BY deadline_at ASC, notice_number ASC, notice_round ASC
        LIMIT ?
        """

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
            demand_agency_code, demand_agency_name, notice_agency_code, notice_agency_name,
            deadline_at, revision, observation_key
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
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
            demand_agency_code = ?, demand_agency_name = ?, notice_agency_code = ?, notice_agency_name = ?,
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

    // M6/6F-1 — 전략 영속(D-6F1-1). `operator_strategy`는 싱글턴(id=1, 리터럴), `revision`이
    // 두 표 모두 플레이스홀더 순서의 첫 자리다(`StrategyRow.bindStrategyRow`가 그 순서로
    // 채운다 — 표 둘이 한 바인더를 공유한다).
    private const val STRATEGY_COLUMNS =
        """
        focus_categories, focus_region_terms, exclude_region_terms,
        required_keyword_terms, exclude_keyword_terms,
        min_budget_won, min_budget_currency, min_budget_vat, min_budget_provenance, min_budget_provenance_detail,
        max_budget_won, max_budget_currency, max_budget_vat, max_budget_provenance, max_budget_provenance_detail,
        minimum_match_score, minimum_probability_score, bid_now_threshold, review_threshold, candidate_limit
        """

    const val SELECT_STRATEGY = "SELECT revision, $STRATEGY_COLUMNS FROM operator_strategy WHERE id = 1"

    const val UPSERT_STRATEGY =
        """
        INSERT INTO operator_strategy (id, revision, $STRATEGY_COLUMNS)
        VALUES (
            1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
        )
        ON CONFLICT (id) DO UPDATE SET
            revision = EXCLUDED.revision,
            focus_categories = EXCLUDED.focus_categories,
            focus_region_terms = EXCLUDED.focus_region_terms,
            exclude_region_terms = EXCLUDED.exclude_region_terms,
            required_keyword_terms = EXCLUDED.required_keyword_terms,
            exclude_keyword_terms = EXCLUDED.exclude_keyword_terms,
            min_budget_won = EXCLUDED.min_budget_won,
            min_budget_currency = EXCLUDED.min_budget_currency,
            min_budget_vat = EXCLUDED.min_budget_vat,
            min_budget_provenance = EXCLUDED.min_budget_provenance,
            min_budget_provenance_detail = EXCLUDED.min_budget_provenance_detail,
            max_budget_won = EXCLUDED.max_budget_won,
            max_budget_currency = EXCLUDED.max_budget_currency,
            max_budget_vat = EXCLUDED.max_budget_vat,
            max_budget_provenance = EXCLUDED.max_budget_provenance,
            max_budget_provenance_detail = EXCLUDED.max_budget_provenance_detail,
            minimum_match_score = EXCLUDED.minimum_match_score,
            minimum_probability_score = EXCLUDED.minimum_probability_score,
            bid_now_threshold = EXCLUDED.bid_now_threshold,
            review_threshold = EXCLUDED.review_threshold,
            candidate_limit = EXCLUDED.candidate_limit,
            updated_at = now()
        """

    // D-6F1-1 ① — 개정 이력(append-only). `revision`이 PK라 같은 값 재저장은 거부된다
    // (재확인·중복 confirm 재전달은 EditStrategyWorkflow 의 idempotency 판정이 앞단에서 막는다 —
    // 이 표까지 같은 revision 이 두 번 오는 정상 경로가 없다).
    const val INSERT_STRATEGY_REVISION =
        """
        INSERT INTO operator_strategy_revision (revision, $STRATEGY_COLUMNS)
        VALUES (
            ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
        )
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

    const val SELECT_EDIT_SESSION =
        """
        SELECT operator_id, state, state_payload, expires_at, session_version, last_command
        FROM edit_session WHERE id = ?
        """

    // M6/6B-1 D-6B1-3·D-6B1-4 — 낙관적 동시성의 유일한 write 경로(우회 (3)). WHERE 절이
    // 전제조건 둘 중 하나를 요구한다: (a) **저장소의 현재 state 가 이미 종단(APPLIED·
    // CANCELLED·EXPIRED)이고 동시에 이번 쓰기가 새 episode(EXCLUDED.session_version = 0,
    // beginSession) 일 때만** 버전을 안 보고 갈아 끼운다 — 끝난 세션 위에 새로 여는 것은
    // "잃어버린 갱신"이 아니다. (b) 저장소의 session_version 이 정확히 "새 값 - 1"이면
    // 정상 순차 전이다. 그 외(동시 writer 가 먼저 썼다, 또는 두 begin() 이 같은 id 로
    // 경합한다 — 저장된 state 가 아직 비종단인데 버전도 안 맞는다)는 WHERE 가 거짓이라
    // ON CONFLICT 분기 전체가 no-op 이고 RETURNING 이 0행이다(Sql.kt KDoc 의
    // `toUpsertOutcome` 관용구와 같은 계열, 여기는 계수만 본다).
    //
    // **verifier r1 HIGH-1 수정** — 이전 판은 (a) 를 `edit_session.state IN (...)` 만으로
    // 열어 뒀다. 그러면 **저장소가 이미 종단**이기만 하면(과거의 정상 전이로 종단이 된
    // 경우도 포함) **어떤 버전의 새 쓰기든** 통과했다 — 같은 비종단 세션을 버전 N 에서
    // 읽은 두 writer 가 각각 종단 전이(APPLIED·CANCELLED)를 저장하면 **둘 다 성공**하고
    // 뒤에 온 쪽이 앞의 결정을 조용히 덮었다(재현: `state=CANCELLED v=2` 위에
    // `(APPLIED,v2)`→1행, 이어서 `(CANCELLED,v2)`→1행, 정상이면 두 번째는 0행이어야
    // 한다). `AND EXCLUDED.session_version = 0` 을 더해 "새 episode" 로 뜻을 좁힌다 —
    // 정당한 재시작(v0, 저장소가 종단)은 여전히 통과하고, 종단 위의 임의 버전 갈아치우기는
    // 이제 (b) 로 떨어져 버전이 안 맞으면 거부된다. **처음 값 EXCLUDED.session_version=0
    // 만으로 무조건 통과시키지도 않는다** — 그러면 같은 id 로 경합하는 두 begin() 이
    // 서로 조용히 덮어써 우회 (3)이 다시 열린다(구현 레인 실측, 별도 회귀).
    const val UPSERT_EDIT_SESSION =
        """
        INSERT INTO edit_session (id, operator_id, state, state_payload, expires_at, session_version, last_command)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            operator_id = EXCLUDED.operator_id,
            state = EXCLUDED.state,
            state_payload = EXCLUDED.state_payload,
            expires_at = EXCLUDED.expires_at,
            session_version = EXCLUDED.session_version,
            last_command = EXCLUDED.last_command
        WHERE (edit_session.state IN ('APPLIED', 'CANCELLED', 'EXPIRED') AND EXCLUDED.session_version = 0)
           OR edit_session.session_version = EXCLUDED.session_version - 1
        RETURNING id
        """

    // M6/6F-5-a — 자격 요건 영속(D-6F5-4). save()는 매번 헤더를 upsert하고 행을 통째로
    // 교체한다(delete-then-insert, UPDATE 없음) — `bidvector.adapters.qualification
    // .JdbcRequirementStore`가 한 트랜잭션에서 순서대로 쓴다.
    const val SELECT_REQUIREMENT_STATUS =
        "SELECT status FROM notice_requirement WHERE notice_number = ? AND notice_round = ?"

    const val SELECT_REQUIREMENT_ROWS =
        """
        SELECT serial_no, kind, group_no, source_field, license_names
        FROM notice_requirement_row
        WHERE notice_number = ? AND notice_round = ?
        ORDER BY serial_no
        """

    const val UPSERT_REQUIREMENT_HEADER =
        """
        INSERT INTO notice_requirement (notice_number, notice_round, status)
        VALUES (?, ?, ?)
        ON CONFLICT (notice_number, notice_round) DO UPDATE SET
            status = EXCLUDED.status,
            updated_at = now()
        """

    /** DataAbsent로 되돌리는 경로 — `ON DELETE CASCADE`(V13)가 자식 행을 함께 지운다. */
    const val DELETE_REQUIREMENT_HEADER =
        "DELETE FROM notice_requirement WHERE notice_number = ? AND notice_round = ?"

    /** 헤더가 남아 있는(FAILED→COLLECTED 등) 상태 전이에서 옛 행을 지운다 — CASCADE로는 못 잡는다. */
    const val DELETE_REQUIREMENT_ROWS =
        "DELETE FROM notice_requirement_row WHERE notice_number = ? AND notice_round = ?"

    const val INSERT_REQUIREMENT_ROW =
        """
        INSERT INTO notice_requirement_row
            (notice_number, notice_round, serial_no, kind, group_no, source_field, license_names)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """
}
