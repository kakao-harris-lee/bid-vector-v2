package bidvector.adapters.retention

import bidvector.procurement.KonepsFieldContractRegistry
import bidvector.workflow.event.LeaseAttempt
import bidvector.workflow.retention.MaskBatchCounts
import bidvector.workflow.retention.RetentionCutoff
import bidvector.workflow.retention.RetentionStorePort
import bidvector.workflow.retention.RetentionTarget
import bidvector.workflow.retention.RetentionWriter
import bidvector.workflow.retention.TableDependency
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 보존 러너 전용 advisory lock 키 — 6F-10 의 relay 임대와 **같은 메커니즘, 다른 키**다.
 * `ConsumerLeasePort` 를 재사용하지 않는 이유 둘: ⓐ 그 port 의 키는 `OutboxConsumerKind`(outbox
 * payload 종류의 어휘)이고 거기에 「보존」을 더하면 `claim` 질의가 읽는 어휘가 함께 늘어난다
 * ⓑ 그 구현은 **풀 `DataSource`** 를 받는데 보존은 풀을 지나지 않는 소유자 연결로만 돈다
 * (운영자 결정 A-1). 중복되는 것은 `pg_try_advisory_lock` 호출 한 줄이다.
 */
private const val RETENTION_LOCK_KEY = 6_110_003L

private const val TRY_ADVISORY_LOCK = "SELECT pg_try_advisory_lock(?)"
private const val ADVISORY_UNLOCK = "SELECT pg_advisory_unlock(?)"

/** 마스킹 후보 한 행 — 「판독했다」와 「판독할 수 없어 값을 버렸다」를 접지 않는다. */
private data class MaskCandidate(
    val key: String,
    val replacement: String,
    val failClosed: Boolean,
)

/**
 * FK 한 간선 — 열 쌍까지 **순서대로** 싣는다. `information_schema` 경유 조회는 복합 FK 를
 * cross product 로 보여 주므로(`CleanMigrationTest` 축6 KDoc 의 실측) 쌍을 복원할 수 없다 —
 * `pg_constraint` 의 `conkey`/`confkey` 를 `WITH ORDINALITY` 로 펴서 짝을 그대로 얻는다.
 */
internal data class ForeignKeyEdge(
    val child: String,
    val parent: String,
    val cascade: Boolean,
    val columnPairs: List<Pair<String, String>>,
)

/**
 * [RetentionStorePort] JDBC 구현(M6/6B-3 P-3) — **소유자 비풀링 연결**로만 돈다(운영자 결정
 * A-1, `PersistenceWiring.migrateAsOwner` 선례). 런타임 풀은 `SET ROLE bidvector_app` 으로
 * 전환되고 그 역할에는 대상 표 어디에도 DELETE·UPDATE 권한이 없다 — 이 클래스에 풀을 넘기면
 * 모든 파기가 권한 오류로 끝난다(그 오류가 곧 경계가 살아 있다는 증거다).
 *
 * 이 클래스는 **빈이 아니다**: 배선의 `@Bean` 함수 안에서 지역 객체로 만들어 use case 에
 * 넘긴다(설계 검토 (2b) 「소유자 연결 팩토리」 행 — 컨텍스트에 소유자 연결을 주입받을 수
 * 있는 자리를 만들지 않는다).
 *
 * cutoff 를 **만들지 못한다** — [RetentionCutoff] 의 생성자가 `workflow` 의 `internal` 이다.
 * 이 어댑터는 받은 값을 읽어 술어에 싣는 일만 한다.
 */
class JdbcRetentionStore internal constructor(
    private val owner: DataSource,
    private val fieldContracts: KonepsFieldContractRegistry,
    private val rules: List<RetentionRule>,
) : RetentionStorePort {
    /**
     * 유일한 공개 생성자 — **계약 레지스트리를 받지 않고 기준일을 받는다.** 받으면 조립(app)이 그
     * 레지스트리를 손에 쥐게 되고, 그것이 D-6F8-6 게이트가 닫아 둔 「원문 값 획득」 표면이다
     * (그 게이트가 이 자리를 실제로 막았다 — `commands.md`). 어느 키가 PII 인지 아는 일은
     * **원문 키 매핑을 소유한 이 모듈**의 몫이고, 그래서 해소도 여기서 한다.
     */
    constructor(
        owner: DataSource,
        referenceDate: LocalDate,
    ) : this(owner, retentionFieldContracts(referenceDate), RETENTION_TABLE_RULES)

    private val rulesByTable: Map<String, RetentionRule> = rules.associateBy { it.table }

    init {
        require(rulesByTable.size == rules.size) { "보존 분류 레지스트리에 중복 표가 있다" }
    }

    override fun classifiedTargets(): List<RetentionTarget> = rules.map { it.asTarget() }

    override fun storedTables(): Set<String> =
        owner.connection.use { connection ->
            val tables = mutableSetOf<String>()
            connection.createStatement().use { statement ->
                statement.executeQuery(PUBLIC_TABLES_SQL).use { rs ->
                    while (rs.next()) tables += rs.getString(1)
                }
            }
            tables
        }

    override fun dependencies(): Set<TableDependency> =
        owner.connection
            .use(::readForeignKeyEdges)
            .map { TableDependency(it.child, it.parent, it.cascade) }
            .toSet()

    /**
     * 본문이 도는 내내 **연결 하나**를 쥔다 — 세션 advisory lock 은 그 연결이 끊기면 서버가
     * 즉시 놓는다(`PostgresAdvisoryLockLease` 와 같은 근거). 못 쥐면 본문을 **부르지 않는다**:
     * 다른 보존 러너가 돌고 있을 때 같은 행을 두 번 세거나 교착하지 않는다.
     */
    override fun <T> withExclusiveLease(body: (RetentionWriter) -> T): LeaseAttempt<T> =
        owner.connection.use { connection ->
            if (!tryLock(connection)) return@use LeaseAttempt.Busy
            try {
                val writer =
                    JdbcRetentionWriter(
                        connection = connection,
                        rulesByTable = rulesByTable,
                        edges = readForeignKeyEdges(connection),
                        fieldContracts = fieldContracts,
                    )
                LeaseAttempt.Held(body(writer))
            } finally {
                unlockQuietly(connection)
            }
        }

    private fun tryLock(connection: Connection): Boolean =
        connection.prepareStatement(TRY_ADVISORY_LOCK).use { statement ->
            statement.setLong(1, RETENTION_LOCK_KEY)
            statement.executeQuery().use { rs ->
                check(rs.next()) { "pg_try_advisory_lock 이 행을 돌려주지 않았다" }
                rs.getBoolean(1)
            }
        }

    /**
     * 해제 실패를 삼킨다 — 근거는 `PostgresAdvisoryLockLease.releaseQuietly` 와 같다:
     * `pg_advisory_unlock` 은 쥐지 않은 잠금에 예외가 아니라 `false` 를 내므로 여기서 던지는
     * 경우는 **연결이 이미 끊긴 것**뿐이고, 그때 서버는 잠금을 이미 놓았다. 본문의 예외를
     * 해제 예외가 덮으면 러너의 원인 코드가 엉뚱해진다.
     */
    private fun unlockQuietly(connection: Connection) =
        try {
            connection.prepareStatement(ADVISORY_UNLOCK).use { statement ->
                statement.setLong(1, RETENTION_LOCK_KEY)
                statement.execute()
            }
            Unit
        } catch (
            @Suppress("SwallowedException") release: SQLException,
        ) {
            Unit
        }
}

/**
 * 임대를 쥔 연결 위에서만 사는 쓰기 능력 — 생성자가 `internal` 이고 만드는 자리는
 * [JdbcRetentionStore.withExclusiveLease] 하나다.
 *
 * 문장마다 자기 트랜잭션이다(`autoCommit` 기본) — 한 일괄이 커밋되고 다음 일괄이 실패하면
 * **앞 일괄은 남는다**. 그것이 의도다: cutoff 가 시각으로 정해지므로 다음 run 이 이어받는 데
 * 상태가 필요 없고, 90일 치 백로그 전부를 한 트랜잭션에 넣으면 그 표의 쓰기가 그만큼 막힌다.
 */
internal class JdbcRetentionWriter(
    private val connection: Connection,
    private val rulesByTable: Map<String, RetentionRule>,
    private val edges: List<ForeignKeyEdge>,
    private val fieldContracts: KonepsFieldContractRegistry,
) : RetentionWriter {
    override fun purge(
        target: RetentionTarget,
        cutoff: RetentionCutoff,
        batchSize: Int,
    ): Int {
        val rule = ruleFor<RetentionRule.Purge>(target)
        return connection.prepareStatement(purgeSqlFor(rule, guardsFor(rule))).use { statement ->
            var index = 1
            statement.setTimestamp(index++, Timestamp.from(cutoff.instant))
            when (val condition = rule.condition) {
                PurgeCondition.TimeOnly -> {
                    // 시각 술어 하나뿐이라 묶을 인자가 없다.
                }

                is PurgeCondition.TerminalStates -> {
                    statement.setArray(
                        index++,
                        connection.createArrayOf("text", condition.values.toTypedArray()),
                    )
                }
            }
            statement.setInt(index, batchSize)
            statement.executeUpdate()
        }
    }

    override fun mask(
        target: RetentionTarget,
        cutoff: RetentionCutoff,
        batchSize: Int,
    ): MaskBatchCounts {
        val rule = ruleFor<RetentionRule.MaskPii>(target)
        // 지울 키는 **개념을 계약에 물어** 얻는다(D-6B3-3 ①) — 일괄마다 한 번, 두 열이 같은 집합을 쓴다.
        val removedKeys = maskedRawNamesOf(rule, fieldContracts)
        var masked = 0
        var failClosed = 0
        var skipped = 0
        for (candidate in maskCandidates(rule, cutoff, batchSize, removedKeys)) {
            when {
                !applyMask(rule, candidate, removedKeys) -> skipped++
                candidate.failClosed -> failClosed++
                else -> masked++
            }
        }
        return MaskBatchCounts(masked = masked, failClosed = failClosed, skipped = skipped)
    }

    /**
     * 이 부모를 막는 자식 간선 전수. CASCADE 간선은 **기한 열까지** 싣는다(D-6B3-3 ②) — 그 열을
     * 분류가 말하지 않으면 **구성 오류로 던진다**: 조용히 가드 없이 지나가면 기한 안쪽 자식이
     * cascade 로 사라진다.
     */
    private fun guardsFor(rule: RetentionRule.Purge): List<ChildGuard> =
        edges
            .filter { it.parent == rule.table }
            .map { edge -> ChildGuard(edge, if (edge.cascade) windowColumnOf(edge.child) else null) }

    private fun windowColumnOf(childTable: String): String =
        when (val childRule = rulesByTable[childTable]) {
            is RetentionRule.CascadeWithParent -> childRule.insertedAtColumn
            is RetentionRule.Purge -> childRule.insertedAtColumn
            is RetentionRule.MaskPii -> childRule.insertedAtColumn
            else -> error("CASCADE 자식의 기한 열을 분류가 말하지 않는다: $childTable")
        }

    private fun maskCandidates(
        rule: RetentionRule.MaskPii,
        cutoff: RetentionCutoff,
        batchSize: Int,
        removedKeys: Set<String>,
    ): List<MaskCandidate> =
        connection.prepareStatement(maskSelectSqlFor(rule)).use { statement ->
            statement.setTimestamp(1, Timestamp.from(cutoff.instant))
            statement.setInt(2, batchSize)
            statement.executeQuery().use { rs ->
                val candidates = mutableListOf<MaskCandidate>()
                while (rs.next()) {
                    val projectedKeys =
                        (rs.getArray("kept_keys").array as Array<*>).mapNotNull { it as? String }.toSet()
                    val key = rs.getString(rule.keyColumn)
                    candidates +=
                        when (val outcome = maskRawPayload(rs.getString(rule.rawColumn), projectedKeys - removedKeys)) {
                            is PayloadMaskOutcome.Masked -> {
                                MaskCandidate(key, outcome.payload, failClosed = false)
                            }

                            PayloadMaskOutcome.FailClosed -> {
                                MaskCandidate(key, PAYLOAD_MASK_FAIL_CLOSED, failClosed = true)
                            }
                        }
                }
                candidates
            }
        }

    private fun applyMask(
        rule: RetentionRule.MaskPii,
        candidate: MaskCandidate,
        removedKeys: Set<String>,
    ): Boolean =
        connection.prepareStatement(maskUpdateSqlFor(rule)).use { statement ->
            // 인자 순서는 `maskUpdateSqlFor` 의 문장 순서다 — 치환값 · 지울 키 배열 · 행 키.
            var index = 1
            statement.setString(index++, candidate.replacement)
            statement.setArray(index++, connection.createArrayOf("text", removedKeys.toTypedArray()))
            statement.setString(index, candidate.key)
            statement.executeUpdate() == 1
        }

    /**
     * 분류와 호출이 어긋나면 **구성 오류로 던진다** — 「파기 대상이 아닌 표를 파기해 달라」는
     * 요청을 조용히 0 으로 처리하면 분류 누락이 「지울 것이 없었다」로 보인다.
     */
    private inline fun <reified T : RetentionRule> ruleFor(target: RetentionTarget): T {
        val rule = rulesByTable[target.table] ?: error("보존 분류에 없는 표다: ${target.table}")
        return rule as? T ?: error("${target.table} 의 분류는 ${rule.treatment} 다 — 요청과 다르다")
    }
}
