package bidvector.adapters.result

import bidvector.adapters.opening.OpeningResultViewSql
import bidvector.adapters.snapshot.jsonbKeyOf
import bidvector.bidding.BidKind
import bidvector.procurement.NOTICE_NUMBER_RAW_KEY
import bidvector.procurement.NOTICE_ROUND_RAW_KEY

/**
 * 내 입찰 결과 조회가 읽는 **열 목록과 문장**(M7/7C⑤-b) — 읽기만 한다.
 *
 * 목록을 상수로 두는 이유는 7C⑤-a 와 같다: 응답 DTO 에 칸이 없는 것만으로는 질의가 값을 쥐는
 * 것을 막지 못하므로 **읽는 자리**에서 먼저 막고, `ResultSetMetaData` 로 거둔 실제 열 집합을
 * 이 목록과 등식으로 잠근다(`JdbcMyBidResultViewTest`). 열을 더하면 그 등식이 먼저 깨진다.
 */
internal object MyBidResultSql {
    /**
     * 투찰 기록 조회가 읽는 열 — **메모 · 증빙 참조 · 추첨번호 · 건너뜀 사유가 여기 없다**
     * (D-7C5B-5). 앞 셋은 `bid_record` 의 열이고 넷째는 `bid_case` 의 열이다. 화면 E 가 요구하지
     * 않고, 담을 자리도 포트 값에 없다.
     */
    val RECORDED_BID_COLUMNS =
        listOf(
            "case_id",
            "notice_number",
            "notice_round",
            "bid_kind",
            "state",
            "amount_won",
            "amount_currency",
            "amount_vat",
            "amount_provenance",
            "chosen_option_ref",
            "submitted_at",
            "confirmed_at",
        )

    /**
     * 건마다 **마지막 기록 하나**(`record_seq` 최대) — 기록은 append-only 라 소급 정정이 행을
     * 더하고, 「지금 기록된 투찰」은 그 가운데 마지막이다.
     *
     * **소유는 두 겹**이다: `bid_record.account_id = ?` 와 조인 조건의 `c.account_id =
     * r.account_id`. 한 겹만 두면 조인을 고치는 변경이 조용히 남의 건을 끌어온다(V21 의 복합 FK 가
     * 같은 불변식의 셋째 겹이다).
     *
     * 종류는 **질의가 거르고 타입이 또 거른다** — `recordedRealBidOrNull` 이 페이퍼에 `null` 을
     * 주므로 이 필터가 틀려도 값이 서지 않는다.
     *
     * 바깥 정렬이 따로 있는 이유: `DISTINCT ON` 은 정렬의 **앞머리가 그 식**이어야 해서 건별
     * 고르기와 목록 순서를 한 문장에 둘 수 없다.
     */
    val SELECT_RECORDED_BIDS =
        """
        SELECT ${RECORDED_BID_COLUMNS.joinToString(", ")}
        FROM (
            SELECT DISTINCT ON (r.case_id)
                r.case_id AS case_id,
                c.notice_number AS notice_number,
                c.notice_round AS notice_round,
                c.bid_kind AS bid_kind,
                c.state AS state,
                r.amount_won AS amount_won,
                r.amount_currency AS amount_currency,
                r.amount_vat AS amount_vat,
                r.amount_provenance AS amount_provenance,
                r.chosen_option_ref AS chosen_option_ref,
                r.submitted_at AS submitted_at,
                r.confirmed_at AS confirmed_at
            FROM bid_record r
            JOIN bid_case c ON c.id = r.case_id AND c.account_id = r.account_id
            WHERE r.account_id = ? AND c.bid_kind = ?
            ORDER BY r.case_id, r.record_seq DESC
        ) latest
        ORDER BY confirmed_at DESC, case_id DESC
        """

    /** 질의가 거르는 종류 — 어휘를 문자열로 적지 않는다(열거가 정본이다). */
    val REAL_BID_KIND: String = BidKind.REAL.name

    /**
     * **확정 낙찰자 상호만** 읽는 질의 — 이 slice 에서 그 열을 읽는 **유일한 자리**다(D-7C5B-9).
     *
     * 7C⑤-a 의 [OpeningResultViewSql.RESULT_COLUMNS] 에 열을 더해 재사용하지 않는다: 그 목록이
     * **상호 둘이 없다**는 것을 등식 test 로 잠그고 있어(D-7C5-3) 더하는 순간 그 구조가 무너진다.
     * 여기서 읽은 값은 `AwardWinnerTradeName` 에 실려 커널로만 들어간다.
     */
    val WINNER_NAME_COLUMNS = listOf("notice_number", "notice_round", "final_award_company_name")

    val SELECT_WINNER_NAMES =
        """
        SELECT ${WINNER_NAME_COLUMNS.joinToString(", ")}
        FROM opening_result
        WHERE notice_number = ANY (?)
        """

    /** 공고 행의 존재와 마감 — 「아직 개찰 전」 유도의 유일한 입력이다(7C⑤-a 와 같은 축). */
    val NOTICE_COLUMNS = listOf("notice_number", "notice_round", "deadline_at")

    val SELECT_NOTICES =
        """
        SELECT ${NOTICE_COLUMNS.joinToString(", ")}
        FROM notice
        WHERE notice_number = ANY (?)
        """

    /**
     * canonical 개찰 결과 — 열 목록이 **7C⑤-a 의 것 그대로**다(상호 두 열이 없는 그 목록).
     * 사본을 만들지 않으므로 그 목록이 바뀌면 이 질의도 같이 바뀐다.
     */
    val CANONICAL_COLUMNS = listOf("notice_number", "notice_round") + OpeningResultViewSql.RESULT_COLUMNS

    val SELECT_CANONICAL =
        """
        SELECT ${CANONICAL_COLUMNS.joinToString(", ")}
        FROM opening_result
        WHERE notice_number = ANY (?)
        """

    /**
     * 개찰 결과 축 **원문**이 있는 공고들 — 「승격 전 ↔ 수집 전」을 가르는 질의다.
     *
     * 7C⑤-a 의 단건 쌍둥이와 같은 규율을 따른다: 축 둘·마스킹은 SQL 이 거르고 **공고번호와
     * 차수의 대조는 Kotlin 에서** 값 객체로 한다(COL-05 — 정규화 규칙의 자리는 하나다). 단건
     * 질의가 차수를 SQL 등식으로 좁힌 것과 달리 여기서는 차수도 Kotlin 이 맞춘다: 묶어 묻는
     * 질의라 차수가 여럿이고, `ANY` 로 거른 뒤에도 (번호, 차수) **짝**을 맞춰야 하기 때문이다.
     *
     * **대가**는 단건 질의와 같다(개찰 축 원문 훑기, `OPEN-7C5-RAW-AXIS-INDEX`). 다만 요청당
     * **한 번**이다 — 건마다 단건 포트를 부르면 건 수만큼 훑는다.
     */
    val SELECT_OPENING_AXIS_RAW_KEYS =
        """
        SELECT DISTINCT
            payload_fields ->> '${jsonbKeyOf(NOTICE_NUMBER_RAW_KEY)}' AS notice_number,
            payload_fields ->> '${jsonbKeyOf(NOTICE_ROUND_RAW_KEY)}' AS notice_round
        FROM raw_observation
        WHERE source_endpoint = ANY (?)
          AND ${OpeningResultViewSql.UNMASKED_RAW_PREDICATE}
        """

    /** 개찰 **결과**를 나르는 축 둘 — 7C⑤-a 의 목록을 그대로 쓴다(사본을 만들지 않는다). */
    val OPENING_RESULT_ENDPOINTS = OpeningResultViewSql.OPENING_RESULT_ENDPOINTS
}
