package bidvector.adapters.retention

/**
 * `notice_audit` 가 **파기도 마스킹도 받지 않는** 근거의 데이터 쪽(D-6B3-1 ①, vr F-2 · privacy F-2).
 *
 * 그 표의 `previous_row` 는 `to_jsonb(OLD)`(V2 `notice_audit_insert`)이므로 담는 키 집합이
 * **`notice` 의 열 집합**이다. 그래서 「담당자 PII 가 들어올 경로가 없다」는 「`notice` 에
 * 담당자 열이 없다」와 같은 문장이고, 그것은 **산문이 아니라 등식으로** 잠겨야 한다:
 * `notice` 에 열이 하나 더해지면 그 값은 코드 변경 0 으로 `previous_row` 에 실리고
 * (그것이 그 트리거의 설계다), 그 표는 EXEMPT 이며 append-only 트리거가 UPDATE 를 전부
 * 거부하므로(V18 은 `raw_observation` 만 열었다) **사후 마스킹이 구조적으로 불가능**하다.
 *
 * 그 순간을 RED 로 만드는 자리가 [NOTICE_SNAPSHOT_CLASSIFIED_COLUMNS] 다 — 새 열은 이
 * 목록에 들어오지 않고는 초록이 되지 않고, 들어오려면 「이 열은 담당자 PII 가 아니다」를
 * 사람이 판단해야 한다. 열 **이름의 꼴**을 보는 문자열 술어가 아니다(「`Ofcl` 을 담지 않은
 * 담당자 키」가 그 술어를 지난다 — privacy F-2).
 */
internal val NOTICE_SNAPSHOT_CLASSIFIED_COLUMNS: Set<String> =
    setOf(
        // 식별자·상태·분류
        "notice_number",
        "notice_round",
        "status",
        "business_category_code",
        "business_category_label",
        "business_division",
        "service_division",
        "main_construction_type",
        "notice_title",
        // 금액 축 셋(값 + 통화·과세·provenance 라벨)
        "base_amount_won",
        "base_amount_currency",
        "base_amount_vat",
        "base_amount_provenance",
        "base_amount_provenance_detail",
        "estimated_amount_won",
        "estimated_amount_currency",
        "estimated_amount_vat",
        "estimated_amount_provenance",
        "estimated_amount_provenance_detail",
        "estimated_amount_source_key",
        "allocated_budget_won",
        "allocated_budget_provenance",
        "allocated_budget_provenance_detail",
        // 하한율·마감
        "floor_rate_fraction",
        "floor_rate_origin_kind",
        "floor_rate_origin_detail",
        "deadline_at",
        // 기관 — **기관 이름**이고 담당자 이름이 아니다(V7 의 네 열).
        "demand_agency_code",
        "demand_agency_name",
        "notice_agency_code",
        "notice_agency_name",
        // 이력·시각
        "revision",
        "observation_key",
        "created_at",
        "updated_at",
    )

/** 그 스냅숏을 담는 표와 열 — 등식을 재는 쪽이 이름을 짓지 않게 한 자리에 둔다. */
internal const val NOTICE_AUDIT_SNAPSHOT_TABLE = "notice_audit"
internal const val NOTICE_AUDIT_SNAPSHOT_COLUMN = "previous_row"
internal const val NOTICE_SNAPSHOT_SOURCE_TABLE = "notice"
