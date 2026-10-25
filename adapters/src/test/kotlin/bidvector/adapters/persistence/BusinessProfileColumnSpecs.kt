package bidvector.adapters.persistence

/**
 * V22 계정 사업 정보 둘의 컬럼 등재 — `CleanMigrationColumnTest`(sizeGate 500줄)에서 갈라 나왔다
 * (7B 의 `BidCaseColumnSpecs` 와 같은 전례 — 그 test 의 합성에 이 목록이 그대로 더해진다).
 *
 * 등재가 **전수**라는 것이 이 목록의 뜻이다: 컬럼 하나를 더하거나 타입을 바꾸면 그 test 가
 * 붉어진다. **사업자번호 · 대표자명 · 인증서 원문 슬롯이 없다**(scope P4)는 주장도 이 목록이
 * 증거다 — 그 열이 생기면 여기 없는 컬럼으로 잡힌다.
 *
 * DEFAULT 가 있는 열이 **하나도 없다** — 시각은 애플리케이션이 싣는다(V22 머리말).
 */
internal val businessProfileColumns =
    listOf(
        // M7/7C④ 신규(추가만, 스키마 스냅샷 래칫 예외). V22__account_business_profile.sql.
        // --- account_business_profile
        ColumnSpec("account_business_profile", "account_id", "bigint", false),
        ColumnSpec("account_business_profile", "trade_name", "text", true),
        ColumnSpec("account_business_profile", "headquarters_region", "text", true),
        ColumnSpec("account_business_profile", "nara_registration", "text", false),
        ColumnSpec("account_business_profile", "nara_registered_fields", "ARRAY", false),
        ColumnSpec("account_business_profile", "business_certificate_expires_on", "date", true),
        ColumnSpec("account_business_profile", "updated_at", "timestamp with time zone", false),
        // --- account_construction_capacity
        ColumnSpec("account_construction_capacity", "account_id", "bigint", false),
        ColumnSpec("account_construction_capacity", "license_name", "text", false),
        ColumnSpec("account_construction_capacity", "amount_won", "bigint", false),
        ColumnSpec("account_construction_capacity", "assessment_year", "smallint", false),
        ColumnSpec("account_construction_capacity", "updated_at", "timestamp with time zone", false),
    )
