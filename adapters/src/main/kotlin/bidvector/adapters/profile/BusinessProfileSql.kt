package bidvector.adapters.profile

/**
 * V22 표 둘의 SQL(M7/7C④). **조회 둘과 삭제는 첫 조건이 `account_id = ?` 로 행을 좁힌다** — 계정으로
 * 좁히지 않는 조회 · 삭제 문장이 이 객체에 없다. upsert 둘에서 `account_id` 는 **필터가 아니라 실리는
 * 값이자 `ON CONFLICT` 대상**이다(r1 CR M-1 — 쓰기의 소유 경계는 호출자 전제).
 * 바인딩 매개변수만 쓴다(문자열 연결로 값을 싣는 문장이 없다).
 *
 * 사업 정보 upsert 의 `DO UPDATE SET` 은 V22 가 UPDATE 를 준 열과 **같은 집합**이다 —
 * `account_id` 를 바꾸는 문장이 없고, 권한 층도 그 열의 UPDATE 를 주지 않는다. 그 심층 방어가 막는
 * 것은 「있는 행을 다른 계정으로 옮기는 일」이고, 「틀린 계정으로 쓰는 일」은 막지 못한다.
 */
internal object BusinessProfileSql {
    const val SELECT_DETAILS =
        """
        SELECT trade_name, headquarters_region, nara_registration, nara_registered_fields,
               business_certificate_expires_on
        FROM account_business_profile
        WHERE account_id = ?
        """

    const val UPSERT_DETAILS =
        """
        INSERT INTO account_business_profile (
            account_id, trade_name, headquarters_region, nara_registration, nara_registered_fields,
            business_certificate_expires_on, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (account_id) DO UPDATE SET
            trade_name = EXCLUDED.trade_name,
            headquarters_region = EXCLUDED.headquarters_region,
            nara_registration = EXCLUDED.nara_registration,
            nara_registered_fields = EXCLUDED.nara_registered_fields,
            business_certificate_expires_on = EXCLUDED.business_certificate_expires_on,
            updated_at = EXCLUDED.updated_at
        """

    const val SELECT_CAPACITIES =
        """
        SELECT license_name, amount_won, assessment_year
        FROM account_construction_capacity
        WHERE account_id = ?
        ORDER BY license_name
        """

    const val UPSERT_CAPACITY =
        """
        INSERT INTO account_construction_capacity (account_id, license_name, amount_won, assessment_year, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (account_id, license_name) DO UPDATE SET
            amount_won = EXCLUDED.amount_won,
            assessment_year = EXCLUDED.assessment_year,
            updated_at = EXCLUDED.updated_at
        """

    const val DELETE_CAPACITY =
        """
        DELETE FROM account_construction_capacity
        WHERE account_id = ? AND license_name = ?
        """
}
