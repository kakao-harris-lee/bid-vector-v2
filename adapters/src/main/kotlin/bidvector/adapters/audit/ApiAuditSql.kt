package bidvector.adapters.audit

/**
 * 요청 감사 SQL 문자열 상수(3D `Sql.kt` 관례 — mapper/adapter 코드와 분리, sizeGate) —
 * `bidvector.adapters.event.EventSql`·`bidvector.adapters.qualification.RequirementSql`과
 * 같은 이유·같은 전례다. `persistence.Sql`에 더하지 않는다 — 이 slice와 병행하는 6F-4도
 * 같은 파일을 늘리고 있어(팀장 preflight 지시), 두 레인이 같은 함수를 편집하지 않게
 * 처음부터 별 파일로 둔다.
 */
internal object ApiAuditSql {
    const val INSERT_API_REQUEST_AUDIT =
        """
        INSERT INTO api_request_audit (
            occurred_at, subject, method, path, status_code, duration_ms, correlation_id
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """
}
