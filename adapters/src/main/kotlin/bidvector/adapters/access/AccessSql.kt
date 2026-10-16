package bidvector.adapters.access

/**
 * V20 두 표의 SQL — **원문을 담는 문장이 없다.** 비밀 문구는 해시 문면으로, 세션 식별자와
 * CSRF 값은 해시로만 바인딩된다(바인딩 타입 자체가 해시 타입이다).
 *
 * 쓰는 열은 V20 의 GRANT 와 **짝**이다: 요청 경로의 UPDATE 는 `account` 의 실패 계수·잠금과
 * `browser_session` 의 사용 시각·유휴 시한·폐기 시각뿐이고, 그 밖의 열을 적는 문장이 여기
 * 없다 — 열 단위 GRANT 가 1차 방어이고 이 파일이 2차다(둘 중 하나만으로는 조용히 열린다).
 */
internal object AccessSql {
    const val SELECT_ACCOUNT_BY_LOGIN =
        "SELECT id, passphrase_hash, failed_attempts, locked_until FROM account WHERE login_name = ?"

    const val UPDATE_ACCOUNT_FAILURE =
        "UPDATE account SET failed_attempts = ?, locked_until = ? WHERE id = ?"

    const val SELECT_ACCOUNT_EXISTS = "SELECT EXISTS (SELECT 1 FROM account)"

    /** 운영자 명령 전용(소유자 연결) — 생성 시각·갱신 시각을 명령의 시계가 싣는다. */
    const val INSERT_ACCOUNT =
        "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, failed_attempts) " +
            "VALUES (?, ?, ?, ?, 0) RETURNING id"

    /**
     * 운영자 명령 전용(소유자 연결) — 해시 교체. **계정을 만들지 않는다**(`ON CONFLICT` 없이
     * `UPDATE` 하나라, 없는 계정에는 0 행이 바뀌고 호출부가 그것을 「대상 없음」으로 읽는다).
     * 교체는 실패 계수·잠금도 비운다 — 새 값을 쥔 운영자가 앞 잠금에 묶이지 않는다.
     */
    const val UPDATE_ACCOUNT_HASH =
        "UPDATE account SET passphrase_hash = ?, updated_at = ?, failed_attempts = 0, locked_until = NULL " +
            "WHERE login_name = ? RETURNING id"

    const val INSERT_BROWSER_SESSION =
        "INSERT INTO browser_session " +
            "(session_digest, account_id, csrf_digest, last_used_at, idle_expires_at, absolute_expires_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)"

    const val SELECT_BROWSER_SESSION =
        "SELECT session_digest, account_id, csrf_digest, last_used_at, idle_expires_at, absolute_expires_at, " +
            "revoked_at FROM browser_session WHERE session_digest = ?"

    const val TOUCH_BROWSER_SESSION =
        "UPDATE browser_session SET last_used_at = ?, idle_expires_at = ? WHERE session_digest = ?"

    /** 이미 폐기된 행의 시각을 덮지 않는다 — 처음 폐기된 시각이 감사의 값이다. */
    const val REVOKE_BROWSER_SESSION =
        "UPDATE browser_session SET revoked_at = ? WHERE session_digest = ? AND revoked_at IS NULL"

    const val REVOKE_ACCOUNT_SESSIONS =
        "UPDATE browser_session SET revoked_at = ? WHERE account_id = ? AND revoked_at IS NULL"
}
