package bidvector.adapters.opening

import bidvector.adapters.snapshot.jsonbKeyOf
import bidvector.procurement.NOTICE_NUMBER_RAW_KEY
import bidvector.procurement.NOTICE_ROUND_RAW_KEY
import bidvector.procurement.SourceEndpoint

/**
 * 개찰 결과 조회가 읽는 **열 목록**(M7/7C⑤-a) — 업체 상호 두 열(`final_award_company_name` ·
 * `opening_rank_one_bidder_name`)이 **여기 없다**는 것이 D-7C5-3 의 구조적 자리다. 응답 DTO 에
 * 칸이 없는 것만으로는 질의가 그 값을 쥐는 것을 막지 못하므로, 읽는 자리에서 먼저 막는다.
 * `JdbcOpeningResultViewTest` 가 질의가 **실제로 읽은** 열 집합을 `ResultSetMetaData` 로 거둬
 * 이 목록과 등식으로 잠근다 — 열을 더하면 그 등식이 먼저 깨진다.
 *
 * 예정가격·개찰 기초금액·사정률·예비가격·추첨번호 열도 없다(§0.10 과잉 — 화면 E 가 요구하지
 * 않는다).
 */
internal object OpeningResultViewSql {
    /** 확정 축 네 열 + 잠정 축 다섯 열 + 공고 층 셋. 상호 열은 없다. */
    val RESULT_COLUMNS =
        listOf(
            "progress_division",
            "participant_count",
            "actual_opening_at",
            "observed_at",
            "final_award_amount_won",
            "final_award_amount_currency",
            "final_award_amount_provenance",
            "final_award_amount_provenance_detail",
            "opening_rank_one_kind",
            "opening_rank_one_duplicate_count",
            "opening_rank_one_bid_amount_won",
            "opening_rank_one_bid_amount_currency",
            "opening_rank_one_bid_rate_fraction",
            "opening_rank_one_observed_at",
        )

    val SELECT_RESULT =
        """
        SELECT ${RESULT_COLUMNS.joinToString(", ")}
        FROM opening_result
        WHERE notice_number = ? AND notice_round = ?
        """

    /** 공고 행의 존재와 마감 — 마감은 「아직 개찰 전」 유도의 유일한 입력이다. */
    const val SELECT_NOTICE = "SELECT deadline_at FROM notice WHERE notice_number = ? AND notice_round = ?"

    /**
     * 그 공고의 **개찰 결과 축 원문**이 적재돼 있는가(승격 전 판정의 근거) — 「승격 전 ↔ 수집
     * 전」을 가르는 유일한 질의다. 승격 경로(7C⑤-0)가 서면 같은 공고의 답이 canonical 행으로
     * 바뀌므로, 그 레인이 보는 술어가 이 문장이다(evidence 에 명시).
     *
     * **공고번호의 대조는 SQL 이 아니라 Kotlin 에서 한다**(`JdbcCollectedAxisStore` 가 COL-05 로
     * 세운 규율과 같은 자리). `raw_observation` 의 공고번호는 온 그대로의 원문이고 물어보는 쪽은
     * canonical ([bidvector.procurement.NoticeNumber.of]: trim · ASCII 대문자화 · **연속** 공백류
     * → `-`)이다. 둘을 SQL 에서 그대로 맞대면 대소문자·공백이 든 번호에서 조용히 빗나가 「원문도
     * 없다」고 답한다 — 승격을 기다리는 공고가 「수집 전」으로 보인다(실측: 어댑터·E2E 회귀 test 가
     * RED 였다). 규칙을 SQL 에 **한 벌 더 쓰지 않는다**(COL-05 「규칙은 여기 하나뿐」): `upper` ·
     * `replace` · `btrim` 을 엮은 **부분 사본**은 탭·연속 공백·앞뒤 공백에서 값 객체와 갈라지고
     * 그 갈라진 자리가 다시 결함이 된다(vr r2 가 그 변이를 실측했다 — 회귀 test 의 표기가 이제
     * 그 부분 사본을 붉힌다).
     *
     * **차수는 SQL 에서 등식으로 비교한다**(cr r2 M-2). [bidvector.sharedkernel.NoticeRound] 는
     * 정규화가 **아니라 거부**다(제로패딩 3자리가 아니면 값이 서지 않는다) — 그래서 문자열 등식과
     * 「값 객체로 접어 비교」가 **정확히 같은 답**을 낸다. 규칙의 사본이 아니라 같은 판정의 다른
     * 표기이고, 이 자리에서는 돌려받는 행을 차수 하나로 줄인다.
     *
     * **마스킹된 행은 세지 않는다** — 보존 규율이 표지를 찍은 행은 업체 식별 개념이 지워진 뒤라
     * 개찰 결과로 승격될 수 없다. 세어 주면 그 공고는 영영 「승격 전」으로 남아 일시적이라던
     * 상태가 영구 오답이 된다. **이것은 승격 적격과의 일치가 아니다**(cr r2 M-1): 승격은 보존창
     * (`inserted_at`)과 **확정 표본 목록**으로도 좁혀지고 그 둘은 이 조회가 볼 수 있는 축이
     * 아니다 — 남는 갈래는 알려진 제한이다(`OPEN-7C5-PROMOTION-ELIGIBILITY`).
     *
     * 키 이름의 출처는 필드 계약이다 — 이 문장이 스스로 짓지 않는다.
     *
     * **대가**: 이 문장은 개찰 축 원문을 훑는다(그 축의 인덱스는 없다 — 마이그레이션 0). canonical
     * 행이 없을 때마다 돌고, 그 공고가 **아직 개찰 전**일 수도 있어 드문 경우가 아니다. 차수 등식과
     * `DISTINCT` 가 돌려받는 행을 줄이지만 스캔 자체는 남는다 — `OPEN-7C5-RAW-AXIS-INDEX` 가 그 축이다.
     */
    val SELECT_OPENING_AXIS_RAW_KEYS =
        """
        SELECT DISTINCT payload_fields ->> '${jsonbKeyOf(NOTICE_NUMBER_RAW_KEY)}' AS notice_number
        FROM raw_observation
        WHERE source_endpoint = ANY (?)
          AND payload_fields ->> '${jsonbKeyOf(NOTICE_ROUND_RAW_KEY)}' = ?
          AND pii_masked_at IS NULL
        """

    /**
     * 개찰 **결과**를 나르는 축 둘 — 개찰결과 목록(진행구분·참가업체수·개찰일시·낙찰 정보)과
     * 개찰완료(투찰자 행). 예비가격 상세·기초금액·A값은 같은 수집 갈래가 받지만 개찰 결과가
     * 아니라 **투찰 판단 입력**이라 들지 않는다 — 그 축만 관측된 공고를 「승격 전」이라 하면
     * 「개찰 결과 원문이 있다」가 거짓이 된다(과잉 차단 대조 test 가 그 경계를 잰다).
     */
    val OPENING_RESULT_ENDPOINTS =
        listOf(SourceEndpoint.OPENING_RESULT_LIST, SourceEndpoint.OPENING_COMPLETE)
}
