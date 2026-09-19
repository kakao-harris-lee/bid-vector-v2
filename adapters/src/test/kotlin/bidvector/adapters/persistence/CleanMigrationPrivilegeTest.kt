package bidvector.adapters.persistence

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * S-5 축 9(유효 권한 행렬, GRANT ratchet) — `CleanMigrationTest`(sizeGate 500줄,
 * v2-지침서.md §5)에서 분리한 파일 — `CleanMigrationTriggerTest`(축7)·
 * `CleanMigrationCheckTest`(축8)·`CleanMigrationColumnTest`(축2·3·4)와 같은 이유·같은
 * 전례다. M6/6F-5-a·6F-6 병합 뒤 두 slice가 각자 정당하게 늘린 합이 501줄이 되어(각자는
 * 한도 안이었다) 이번에 축9를 떼어냈다 — D-3D-6 여덟 축 중 유효 권한 행렬 하나만 다룬다
 * (다른 축은 `CleanMigrationTest`·`CleanMigrationColumnTest`·`CleanMigrationTriggerTest`·
 * `CleanMigrationCheckTest`).
 *
 * [queryStrings]는 `CleanMigrationTest`(축1·축5)와 같은 조회 형태를 쓰지만, `dataSource()`가
 * `PersistenceTestSupport`의 `protected` 멤버라 클래스 경계를 넘는 공유가 안 돼 사본을
 * 둔다(원본 `CleanMigrationTest.queryStrings`는 축1·축5가 계속 쓰므로 그대로 둔다).
 * **정정(D-6F5-32, r6 LOW-1)** — 이 사본의 근거로 처음 든 두 문장이 틀렸다: ⓐ
 * `CleanMigrationCheckTest`의 `queryConstraintDef`·`queryCheckBodies`는 이 헬퍼의 사본이
 * 아니라 **다른 질의를 하는 별개 헬퍼**다 — `fun queryStrings`는 이 저장소에 **정확히
 * 이 두 파일**에만 있고, **그 중복은 이 병합이 처음 만들었다**(전례가 없다) ⓑ 막히지
 * 않는 제3안이 있었다 — `PersistenceTestSupport`에 `protected fun`으로 올리는 것(그
 * 자리에 `dataSource()`가 이미 있고 그 파일은 in_scope다). 그래도 **사본을 유지한다** —
 * 11줄짜리 배관이고, `PersistenceTestSupport`는 6F-4가 지금 편집 중이라 기반 클래스를
 * 건드리는 비용이 크다. 좁은 주장(「`protected dataSource()`라 클래스 간 위임이
 * 불가능하다」)은 참이고 그것만으로 이 선택의 근거로 충분하다.
 */
class CleanMigrationPrivilegeTest : PersistenceTestSupport() {
    /**
     * **verifier r1 H-1 뒤, r2 M-3 시정으로 술어 교체**(4C-2) — 설계 검토 (2b) 「V6 테이블
     * 자체 | GRANT 목록을 test 가 대조」의 실측이 없었다(r1). r1 이 쓴
     * `information_schema.role_table_grants WHERE grantee = 'bidvector_app'`은 **역할에
     * 직접 부여된 것만** 본다 — `GRANT DELETE ON outbox TO PUBLIC;` 한 줄이면 이 술어는
     * 못 보는데 `bidvector_app`은 `PUBLIC` 경유로 실제 DELETE를 행사할 수 있었다(verifier
     * r2 실측, `PROBE-PUB … OK rows=1`). [effectivePrivileges]는
     * `has_table_privilege(role, table, priv)`로 **역할 직접 부여 + PUBLIC 부여 + 역할
     * 상속**을 전부 해소한 유효 권한을 축 일곱 전부(SELECT/INSERT/UPDATE/DELETE/
     * TRUNCATE/REFERENCES/TRIGGER) true/false로 못 박는다.
     *
     * **M3/3G — 전 테이블로 전수화.** 4C-2는 이 술어를 `outbox`·`inbox` 둘에만 적용했다.
     * 3D의 기존 권한 test 둘(`provenance_authority` SELECT만·`notice_audit` INSERT없음,
     * `role_table_grants` 술어)이 같은 PUBLIC 경유 사각을 그대로 갖고 있어
     * (`OPEN-3D-GRANT-PUBLIC-BLINDSPOT`, 4C-2 verifier r2 M-3이 열었다) 이 slice가 그 둘을
     * 아래 행렬로 흡수하고 **테이블 목록을 DB에서 발견**해 전 테이블로 넓힌다 — 기대
     * 행렬에 없는 테이블이 나오면(새 마이그레이션이 권한 선언을 빠뜨리면) 이 test가
     * 떨어진다(래칫의 본체).
     *
     * **`provenance_authority`는 SELECT만**(V2 GRANT + V3 REVOKE 방어 심층).
     * **`notice_audit`는 SELECT만, INSERT 없음(F-6)** — 감사 행은
     * `notice_audit_insert()`(V2, SECURITY DEFINER)가 대신 쓴다. app 역할이 직접 INSERT로
     * 위조 이력을 넣는 경로를 막는다. 두 근거는 이 행렬의 해당 행이 나른다 — 옛 술어
     * test 둘은 지웠다(단언은 약해지지 않는다, 행렬이 그 둘을 행으로 포함한다).
     *
     * **`flyway_schema_history`는 실측값**이다(설계 검토 (2) — Flyway 이력 표도 발견에
     * 잡히므로 제외하지 않고 명시적으로 못 박는다. `bidvector_app`에 대한 GRANT가 어느
     * 마이그레이션에도 없어 축 일곱이 전부 false — 컨테이너에서 질의해 확인한 값이다,
     * 추측이 아니다).
     */
    private val expectedPrivilegeMatrix: Map<String, TablePrivileges> =
        mapOf(
            "raw_observation" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = false,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            "provenance_authority" to
                TablePrivileges(
                    select = true,
                    insert = false,
                    update = false,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            "notice" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = true,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            "notice_audit" to
                TablePrivileges(
                    select = true,
                    insert = false,
                    update = false,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            "rejected_write" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = false,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            "opening_result" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = true,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            "qualification_text" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = true,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            "collection_run" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = false,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            "opening_reserve_price" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = true,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            "outbox" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = true,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            "inbox" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = false,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            // M6/6F-1 D-6F1-1 — operator_strategy 는 upsert(SELECT·INSERT·UPDATE)를 진다.
            // operator_strategy_revision 은 append-only(SELECT·INSERT만, outbox·raw_observation과
            // 같은 관례).
            "operator_strategy" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = true,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            "operator_strategy_revision" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = false,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            // M6/6F-6 D-6F6-1 — operator_profile 도 upsert(SELECT·INSERT·UPDATE)만 진다
            // (operator_strategy와 같은 관례). DELETE·TRUNCATE 는 주지 않는다(보존·파기는
            // 이 slice 밖).
            "operator_profile" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = true,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            "flyway_schema_history" to
                TablePrivileges(
                    select = false,
                    insert = false,
                    update = false,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            // M6/6B-1 — 조회·최초 생성·전이 저장(추가만, D-6B1-8). GRANT SELECT, INSERT,
            // UPDATE ON edit_session(V8__edit_session.sql) — DELETE·TRUNCATE 는 주지 않는다
            // (보존·파기는 6B-3 소관, D-6B1-1).
            "edit_session" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = true,
                    delete = false,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            // M6/6F-5-a — 헤더는 upsert + 리셋(DataAbsent로 되돌리는 save() 경로가 DELETE를
            // 쓴다, D-6F5-4). 행은 delete-then-insert(UPDATE 없음 — save()가 행을 갱신하지
            // 않고 항상 통째로 교체한다). V13__notice_requirement.sql.
            "notice_requirement" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = true,
                    delete = true,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
            "notice_requirement_row" to
                TablePrivileges(
                    select = true,
                    insert = true,
                    update = false,
                    delete = true,
                    truncate = false,
                    references = false,
                    trigger = false,
                ),
        )

    @Test
    fun `축9 유효 권한 행렬 — bidvector_app 이 public 의 전 BASE TABLE 에 대해 갖는 권한이 기대와 정확히 일치한다`() {
        val discoveredTables =
            queryStrings(
                "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
            )
        discoveredTables shouldContainExactlyInAnyOrder expectedPrivilegeMatrix.keys

        val actualPrivileges = discoveredTables.associateWith { effectivePrivileges(it) }
        actualPrivileges shouldBe expectedPrivilegeMatrix
    }

    /** 축 일곱 전부를 `has_table_privilege`로 해소한 유효 권한 — 직접 부여·PUBLIC 부여·역할 상속을 모두 본다(verifier r2 M-3). */
    private data class TablePrivileges(
        val select: Boolean,
        val insert: Boolean,
        val update: Boolean,
        val delete: Boolean,
        val truncate: Boolean,
        val references: Boolean,
        val trigger: Boolean,
    )

    private fun effectivePrivileges(table: String): TablePrivileges =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT " +
                        "has_table_privilege('bidvector_app', ?, 'SELECT') AS p_select, " +
                        "has_table_privilege('bidvector_app', ?, 'INSERT') AS p_insert, " +
                        "has_table_privilege('bidvector_app', ?, 'UPDATE') AS p_update, " +
                        "has_table_privilege('bidvector_app', ?, 'DELETE') AS p_delete, " +
                        "has_table_privilege('bidvector_app', ?, 'TRUNCATE') AS p_truncate, " +
                        "has_table_privilege('bidvector_app', ?, 'REFERENCES') AS p_references, " +
                        "has_table_privilege('bidvector_app', ?, 'TRIGGER') AS p_trigger",
                ).use { statement ->
                    for (index in 1..7) statement.setString(index, table)
                    statement.executeQuery().use { rs ->
                        rs.next()
                        TablePrivileges(
                            select = rs.getBoolean("p_select"),
                            insert = rs.getBoolean("p_insert"),
                            update = rs.getBoolean("p_update"),
                            delete = rs.getBoolean("p_delete"),
                            truncate = rs.getBoolean("p_truncate"),
                            references = rs.getBoolean("p_references"),
                            trigger = rs.getBoolean("p_trigger"),
                        )
                    }
                }
        }

    /**
     * [CleanMigrationTest.queryStrings]와 같은 조회 형태의 사본 — `dataSource()`가
     * `protected`라 클래스 경계를 넘는 공유가 안 돼 복제한다(근거 상세는 클래스 KDoc의
     * D-6F5-32 정정 참고 — 이 중복이 이 패키지의 기존 관례는 **아니다**, 이 병합이
     * 처음 만들었다).
     */
    private fun queryStrings(sql: String): Set<String> {
        val actual = mutableSetOf<String>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    while (rs.next()) actual += rs.getString(1)
                }
            }
        }
        return actual
    }
}
