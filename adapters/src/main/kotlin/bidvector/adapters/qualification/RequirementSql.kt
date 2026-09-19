package bidvector.adapters.qualification

/**
 * 자격 요건 영속 SQL 문자열 상수(3D `Sql.kt` 관례 — mapper/adapter 코드와 분리, sizeGate) —
 * `bidvector.adapters.event.EventSql`와 같은 이유·같은 전례다. M6/6F-5-a·6F-6 병합 뒤 두
 * slice가 각자 정당하게 `persistence.Sql`에 더한 상수의 합이 타입 멤버 31개가 되어(각자는
 * 30개 한도 안이었다, OPEN-ADR-06 (a)) 이 slice(6F-5-a) 몫 여섯을 떼어냈다 — `persistence
 * .Sql`은 6F-6의 `PROFILE_*` 상수를 포함해 그대로 둔다(남의 몫을 옮기지 않는다).
 *
 * D-6F5-4 — `JdbcRequirementStore.save()`는 매번 헤더를 upsert하고 행을 통째로 교체한다
 * (delete-then-insert, UPDATE 없음) — `JdbcRequirementStore`가 한 트랜잭션에서 순서대로
 * 쓴다.
 */
internal object RequirementSql {
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
