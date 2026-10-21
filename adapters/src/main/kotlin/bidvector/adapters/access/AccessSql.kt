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
        "SELECT id, passphrase_hash, locked_until FROM account WHERE login_name = ?"

    /**
     * 실패 하나 — **한 문장**이다(code-review r1 H-1). 계수는 애플리케이션이 읽어 둔 값이 아니라
     * **행의 현재 값**에서 오르므로, 느린 검증을 각자 도느라 같은 값을 읽은 동시 요청 K 건이
     * 계수를 K 올린다(행 잠금이 UPDATE 를 줄 세우고 READ COMMITTED 가 뒤 문장에 갱신된 행을
     * 보인다).
     *
     * 상한에 닿은 그 문장이 잠금까지 싣고 계수를 비운다 — 비우지 않으면 잠금이 풀린 직후의
     * 한 번이 다시 잠근다(잠금 창이 겹겹이 길어진다). **상한 아래면 `locked_until` 은 자기 값을
     * 유지한다**: 절대값 `NULL` 을 쓰던 자리가 「늦게 끝난 실패가 막 걸린 잠금을 지우는」 창이었다.
     */
    const val REGISTER_ACCOUNT_FAILURE =
        "UPDATE account SET " +
            "failed_attempts = CASE WHEN failed_attempts + 1 >= ? THEN 0 ELSE failed_attempts + 1 END, " +
            "locked_until = CASE WHEN failed_attempts + 1 >= ? THEN ? ELSE locked_until END " +
            "WHERE id = ?"

    /**
     * 확정 트랜잭션의 **대기 상한**(code-review r3 L-2) — 이 문장 뒤의 잠금 대기가 상한을 넘으면
     * PostgreSQL 이 `55P03`(lock_not_available)로 끊고, 호출부는 그것을 **로그인 거부**로 읽는다.
     *
     * `SET LOCAL` 대신 `set_config(..., true)` 를 쓰는 이유는 **바인딩**이다 — 유틸리티 문장인
     * `SET` 은 매개변수를 받지 않아 값을 문자열로 이어 붙여야 하고, 그러면 정책 값이 SQL 문면에
     * 섞인다. 셋째 인자 `true` 가 「이 트랜잭션 안에서만」이다.
     */
    const val SET_LOCAL_LOCK_WAIT = "SELECT set_config('lock_timeout', ?, true)"

    /**
     * 로그인 확정의 **첫 문장** — 성공의 초기화이면서 동시에 그 트랜잭션의 **잠금과 조건**이다.
     *
     *  · `passphrase_hash = ?` — 검증 때 읽은 자격이 **아직 그 자격일 때만**(verifier r1 F1).
     *    느린 KDF 가 도는 사이에 교체가 커밋되면 이 조건이 깨져 0 행이고, 그 로그인은 세션을
     *    받지 못한다. 비교가 코드가 아니라 **행 조건**이라 제시값이 섞이지 않는다.
     *  · `locked_until …` — 그 사이에 잠기지 않았을 때만(code-review r1 H-1 의 조건 그대로).
     *  · `UPDATE … WHERE id` 가 거는 **행 잠금**이 동시 로그인 둘과 비밀 문구 교체를 이 단위와
     *    직렬화한다(code-review r2 M-1). 뒤에 선 쪽은 READ COMMITTED 재평가로 바뀐 행을 다시
     *    보고, 통과하면 **새 스냅숏**으로 폐기 문장을 돌아 앞사람의 세션을 본다.
     *  · 잠그는 수단이 `SELECT … FOR UPDATE` 가 아니라 이 UPDATE 인 것은 **권한** 때문이다 —
     *    앱 역할의 `account` 권한은 SELECT + 두 열의 UPDATE 뿐이고, 이 문장은 그 두 열만 쓴다.
     */
    const val CLAIM_ACCOUNT_FOR_SIGN_IN =
        "UPDATE account SET failed_attempts = 0, locked_until = NULL " +
            "WHERE id = ? AND passphrase_hash = ? AND (locked_until IS NULL OR locked_until <= ?) " +
            "RETURNING id"

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
