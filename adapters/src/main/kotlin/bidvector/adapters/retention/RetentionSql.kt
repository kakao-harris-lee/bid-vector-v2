package bidvector.adapters.retention

import java.sql.Connection

/**
 * 스키마에 **실제로 있는** 표 — 분류 레지스트리와 집합 등식을 재는 쪽의 정본이다
 * (`CleanMigrationTest` 축1 과 같은 질의 — 그 test 가 이 집합을 `expectedTables` 에 다시
 * 묶으므로, 레지스트리는 전이적으로 스키마 스냅숏 래칫에 묶인다).
 */
internal const val PUBLIC_TABLES_SQL =
    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"

private const val FOREIGN_KEY_SQL =
    """
    SELECT con.conname                        AS constraint_name,
           child_class.relname                AS child_table,
           parent_class.relname               AS parent_table,
           con.confdeltype                    AS delete_rule,
           child_att.attname                  AS child_column,
           parent_att.attname                 AS parent_column,
           ord.n                              AS position
      FROM pg_constraint con
      JOIN pg_class child_class  ON child_class.oid = con.conrelid
      JOIN pg_class parent_class ON parent_class.oid = con.confrelid
      JOIN LATERAL unnest(con.conkey, con.confkey) WITH ORDINALITY AS ord(child_attnum, parent_attnum, n)
        ON true
      JOIN pg_attribute child_att  ON child_att.attrelid = con.conrelid  AND child_att.attnum = ord.child_attnum
      JOIN pg_attribute parent_att ON parent_att.attrelid = con.confrelid AND parent_att.attnum = ord.parent_attnum
     WHERE con.contype = 'f'
       AND con.connamespace = 'public'::regnamespace
     ORDER BY con.conname, ord.n
    """

/** `pg_constraint.confdeltype` 의 `ON DELETE CASCADE` 표기. */
private const val CASCADE_DELETE_RULE = "c"

internal fun readForeignKeyEdges(connection: Connection): List<ForeignKeyEdge> {
    val pairsByConstraint = linkedMapOf<String, MutableList<Pair<String, String>>>()
    val headerByConstraint = linkedMapOf<String, Triple<String, String, Boolean>>()
    connection.createStatement().use { statement ->
        statement.executeQuery(FOREIGN_KEY_SQL).use { rs ->
            while (rs.next()) {
                val name = rs.getString("constraint_name")
                headerByConstraint[name] =
                    Triple(
                        rs.getString("child_table"),
                        rs.getString("parent_table"),
                        rs.getString("delete_rule") == CASCADE_DELETE_RULE,
                    )
                pairsByConstraint
                    .getOrPut(name) { mutableListOf() }
                    .add(rs.getString("child_column") to rs.getString("parent_column"))
            }
        }
    }
    return headerByConstraint.map { (name, header) ->
        val (child, parent, cascade) = header
        ForeignKeyEdge(child, parent, cascade, pairsByConstraint.getValue(name))
    }
}

/**
 * 파기 문 — 일괄 상한을 `ctid` 부분질의로 건다. 상한을 **부분질의 안**에 두는 이유: 자식
 * 가드를 밖에 두면 「고른 n 행 가운데 지울 수 있는 것만」이 되어 한 run 이 일찍 멈춘다.
 *
 * [blockingChildren] — 부모를 지울 때 **막는** 자식(`ON DELETE CASCADE` 가 아닌 FK)이 아직
 * 있으면 그 부모 행은 이 run 에서 지우지 않는다(설계 검토 우회 6). 자식이 기한 안쪽이면 그
 * 자식은 살아야 하고(「기한 안쪽 행은 한 행도 사라지지 않는다」), 그래도 부모를 지우려 들면
 * FK 위반으로 run 전체가 중단된다 — 그 둘 사이에서 **부모를 남기는 쪽**을 고른다.
 */
internal fun purgeSqlFor(
    rule: RetentionRule.Purge,
    blockingChildren: List<ForeignKeyEdge>,
): String {
    val conditions = mutableListOf("t.${rule.insertedAtColumn} < ?")
    when (val condition = rule.condition) {
        PurgeCondition.TimeOnly -> Unit
        is PurgeCondition.TerminalStates -> conditions += "t.${condition.column} = ANY (?)"
    }
    blockingChildren.forEachIndexed { index, edge ->
        val alias = "c$index"
        val join =
            edge.columnPairs.joinToString(" AND ") { (childColumn, parentColumn) ->
                "$alias.$childColumn = t.$parentColumn"
            }
        conditions += "NOT EXISTS (SELECT 1 FROM ${edge.child} $alias WHERE $join)"
    }
    return "DELETE FROM ${rule.table} WHERE ctid IN (" +
        "SELECT t.ctid FROM ${rule.table} t WHERE ${conditions.joinToString(" AND ")} " +
        "ORDER BY t.${rule.insertedAtColumn} LIMIT ?)"
}

/**
 * 마스킹 후보 조회 — 남길 키를 **그 행의 투영 열 키 집합**으로 함께 읽는다
 * ([maskRawPayload] 의 allow-list 출처). `pii_masked_at IS NULL` 이 「이미 마스킹된 행
 * 건너뜀」을 질의 자체로 만든다.
 */
internal fun maskSelectSqlFor(rule: RetentionRule.MaskPii): String =
    "SELECT ${rule.keyColumn}, ${rule.maskedColumn}, " +
        "ARRAY(SELECT jsonb_object_keys(${rule.keptKeySourceColumn})) AS kept_keys " +
        "FROM ${rule.table} WHERE ${rule.insertedAtColumn} < ? AND ${rule.maskedAtColumn} IS NULL " +
        "ORDER BY ${rule.insertedAtColumn} LIMIT ?"

/**
 * 마스킹 문 — 표지 열은 **DB 시계**가 찍는다(적재 시각 축과 같은 시계). `AND <표지> IS NULL`
 * 은 트리거 ③(NULL → 값 한 번)과 같은 조건을 질의에도 두는 방어 심층이다.
 */
internal fun maskUpdateSqlFor(rule: RetentionRule.MaskPii): String =
    "UPDATE ${rule.table} SET ${rule.maskedColumn} = ?, ${rule.maskedAtColumn} = now() " +
        "WHERE ${rule.keyColumn} = ? AND ${rule.maskedAtColumn} IS NULL"
