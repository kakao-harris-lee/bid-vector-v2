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
 * 식별자 인용(cr L-3) — 오늘 출처는 컴파일 상수(`RETENTION_TABLE_RULES`)와 pg 카탈로그
 * (`pg_class.relname`)뿐이고 값은 전부 `?` 바인딩이라 주입 경로는 없다. 그래도 인용하는
 * 이유: 대문자·특수문자 이름의 표가 들어오면 인용 없는 문장이 **구문 오류**가 되고, 그때
 * 깨지는 것이 되돌릴 수 없는 경로의 문장이다.
 */
internal fun quoteIdent(name: String): String {
    require(!name.contains('"')) { "식별자에 따옴표를 담을 수 없다: $name" }
    return "\"$name\""
}

/**
 * 부모를 지울 때 자식이 **막는가** — FK 간선 하나의 가드(설계 검토 우회 6).
 *
 * [inWindowColumn] 이 `null` 이면 자식 행이 **하나라도** 있으면 막는다(비-CASCADE 간선: 그
 * 자식은 자기 기준으로 **먼저** 파기되므로 그 run 에 남은 행은 이미 기한 안쪽이다). 값이
 * 있으면 **기한 안쪽 자식만** 막는다(CASCADE 간선: 자기 기준 파기가 없으므로 기한을 이 가드가
 * 직접 본다 — 운영자 결정 2026-10-14, D-6B3-3 ②).
 */
internal data class ChildGuard(
    val edge: ForeignKeyEdge,
    val inWindowColumn: String?,
)

/**
 * 파기 문 — 일괄 상한을 `ctid` 부분질의로 건다. 상한을 **부분질의 안**에 두는 이유: 자식
 * 가드를 밖에 두면 「고른 n 행 가운데 지울 수 있는 것만」이 되어 한 run 이 일찍 멈춘다.
 *
 * cutoff 를 **CTE 한 자리**에 둔다 — 가드마다 `?` 를 늘리면 바인딩 순서가 문장 조립 순서에
 * 묶이고, 그 둘이 어긋난 날 지워지는 것은 되돌릴 수 없다. 인자는 언제나 「cutoff · (상태 배열)
 * · 일괄 상한」 셋뿐이다.
 */
internal fun purgeSqlFor(
    rule: RetentionRule.Purge,
    guards: List<ChildGuard>,
): String {
    val table = quoteIdent(rule.table)
    val cutoffColumn = "t.${quoteIdent(rule.insertedAtColumn)}"
    val conditions = mutableListOf("$cutoffColumn < bound.cutoff")
    when (val condition = rule.condition) {
        PurgeCondition.TimeOnly -> Unit
        is PurgeCondition.TerminalStates -> conditions += "t.${quoteIdent(condition.column)} = ANY (?)"
    }
    guards.forEachIndexed { index, guard ->
        val alias = "c$index"
        val join =
            guard.edge.columnPairs.joinToString(" AND ") { (childColumn, parentColumn) ->
                "$alias.${quoteIdent(childColumn)} = t.${quoteIdent(parentColumn)}"
            }
        val window =
            guard.inWindowColumn?.let { column -> " AND $alias.${quoteIdent(column)} >= bound.cutoff" } ?: ""
        conditions += "NOT EXISTS (SELECT 1 FROM ${quoteIdent(guard.edge.child)} $alias WHERE $join$window)"
    }
    return "WITH bound AS (SELECT ?::timestamptz AS cutoff) " +
        "DELETE FROM $table WHERE ctid IN (" +
        "SELECT t.ctid FROM $table t, bound WHERE ${conditions.joinToString(" AND ")} " +
        "ORDER BY $cutoffColumn LIMIT ?)"
}

/**
 * 마스킹 후보 조회 — **남길 키를 행에서 읽지 않는다**(D-6B3-5 ②). 그 집합은 계약 레지스트리가
 * 정하므로(`keptRawNames`) 질의가 가져올 것은 키와 원문뿐이다. 앞 판은 그 행의 투영 열 키를
 * allow-list 로 읽었고, 그래서 적재자가 투영에 심은 키가 남김 집합에 들어왔다(vr R2-F2(a)).
 * `pii_masked_at IS NULL` 이 「이미 마스킹된 행 건너뜀」을 질의 자체로 만든다.
 */
internal fun maskSelectSqlFor(rule: RetentionRule.MaskPii): String =
    "SELECT ${quoteIdent(rule.keyColumn)}, ${quoteIdent(rule.rawColumn)} " +
        "FROM ${quoteIdent(rule.table)} " +
        "WHERE ${quoteIdent(rule.insertedAtColumn)} < ? AND ${quoteIdent(rule.maskedAtColumn)} IS NULL " +
        "ORDER BY ${quoteIdent(rule.insertedAtColumn)} LIMIT ?"

/**
 * 마스킹 문 — **두 열을 같은 allow-list 로** 줄인다(D-6B3-5 ②). 원문은 남김 집합으로 다시 쓴
 * 값으로, 투영은 `jsonb_each` → `jsonb_object_agg` 로 **남길 키만** 모아 다시 세운다.
 *
 * 앞 판은 투영을 `- <지울 키>` 라는 **deny-list** 로 줄였다. 그러면 지울 키 목록에 없는 키는 무엇이든
 * 남으므로, 적재자가 담당자 키를 투영에 실으면 그 키가 그대로 남았다(vr R2-F2(a)). allow-list 는
 * 「계약이 아는 키 가운데 업체 식별이 아닌 것」만 남기므로 그 경로가 닫힌다 — 지울 키 목록을 두
 * 벌 두지도 않는다.
 *
 * 남길 키가 하나도 맞지 않으면 `jsonb_object_agg` 가 NULL 을 내므로 `coalesce` 로 빈 객체를 둔다
 * (그 열은 `NOT NULL` 이고 V19 의 CHECK 가 객체를 요구한다).
 *
 * 표지 열은 **DB 시계**가 찍는다(적재 시각 축과 같은 시계). `AND <표지> IS NULL` 은 트리거의 표지
 * 관문과 같은 조건을 질의에도 두는 방어 심층이다.
 */
internal fun maskUpdateSqlFor(rule: RetentionRule.MaskPii): String =
    "UPDATE ${quoteIdent(rule.table)} SET ${quoteIdent(rule.rawColumn)} = ?, " +
        "${quoteIdent(rule.projectionColumn)} = (" +
        "SELECT coalesce(jsonb_object_agg(e.key, e.value), '{}'::jsonb) " +
        "FROM jsonb_each(${quoteIdent(rule.projectionColumn)}) AS e WHERE e.key = ANY (?)), " +
        "${quoteIdent(rule.maskedAtColumn)} = now() " +
        "WHERE ${quoteIdent(rule.keyColumn)} = ? AND ${quoteIdent(rule.maskedAtColumn)} IS NULL"
