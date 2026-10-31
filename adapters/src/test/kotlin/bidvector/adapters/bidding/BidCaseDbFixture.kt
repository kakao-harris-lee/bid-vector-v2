package bidvector.adapters.bidding

import bidvector.adapters.notificationinbox.JdbcNotificationInbox
import bidvector.adapters.persistence.FloorRateOriginCodec
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.persistence.ProvenanceCodec
import bidvector.bidding.BidAmountOrigin
import bidvector.bidding.BidSubmission
import bidvector.bidding.NoticeKey
import bidvector.bidding.SubmittedBidAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.BidCaseWorkflow
import bidvector.workflow.bidding.EvidenceRetentionPolicyData
import java.math.BigDecimal
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.Period
import javax.sql.DataSource

/**
 * 입찰 건 실 DB test 의 공용 자리(계약 D-7B-3 라운드에서 신설) — **출하 저장소와 출하 질의**를 그대로
 * 조립한다(`BidCaseWiring` 과 같은 모양: JDBC 저장소 · 마감 질의 · 스냅샷 질의 · use case).
 *
 * 시계는 test 가 옮길 수 있다([now]) — 마감 판정(결정 ①)은 「지금이 마감 뒤인가」가 축이다.
 */
abstract class BidCaseDbFixture : PersistenceTestSupport() {
    protected var now: Instant = Instant.parse("2026-10-21T04:00:00Z")

    /**
     * [source] 위의 출하 조립 — 기본은 관리자 연결이고, 권한 축은 [appRoleDataSource] 를 넘긴다.
     *
     * 알림 포트도 **출하 어댑터 그대로**다(M7/7C③-b) — 이중체로 바꾸면 되비춤이 실 DB 의 제약
     * (계정 FK · 종류 CHECK · 중복 억제 PK)을 지나는지 이 자리에서 보이지 않는다.
     */
    protected fun workflow(source: DataSource = dataSource()): BidCaseWorkflow =
        BidCaseWorkflow(
            cases = JdbcBidCaseStore(source),
            deadlines = JdbcNoticeDeadlineQuery(source),
            snapshots = JdbcNoticeSnapshotQuery(source),
            clock = { now },
            retentionPolicy =
                Resolution.Resolved(
                    EvidenceRetentionPolicyData(Period.ofYears(3)),
                    PolicyVersion(EffectiveFrom.Initial, "test"),
                ),
            notifications = JdbcNotificationInbox(source),
        )

    /**
     * 커넥션마다 `SET ROLE bidvector_app` 을 거는 `DataSource` — **출하 앱 역할로 끝까지** 도는 양성
     * 경로를 위한 것이다(관리자 연결로만 재면 GRANT 가 무엇을 막는지, 무엇을 허락해야 하는지 보이지 않는다).
     */
    protected fun appRoleDataSource(): DataSource = RoleDataSource(dataSource(), "bidvector_app")

    protected fun account(login: String): AccountId =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, " +
                        "failed_attempts) VALUES (?, 'x', ?, ?, 0) RETURNING id",
                ).use { statement ->
                    statement.setString(1, login)
                    statement.setTimestamp(2, Timestamp.from(now))
                    statement.setTimestamp(3, Timestamp.from(now))
                    statement.executeQuery().use { rs ->
                        rs.next()
                        AccountId(rs.getLong(1))
                    }
                }
        }

    protected fun notice(suffix: String) = NoticeKey("R26BK-$suffix", NoticeRound.of("000"))

    protected fun amount(won: Long) =
        SubmittedBidAmount(won, Currency.KRW, VatTreatment.INCLUSIVE, BidAmountOrigin.OperatorEntered)

    protected fun submission() =
        BidSubmission(
            amount = amount(1_000_000L),
            submittedAt = now,
            drawNumbers = listOf(3, 11),
            evidence = null,
            memo = null,
        )

    /**
     * 공고 한 건의 금액 — 도메인 값으로 받고 **정본 코덱으로 적는다**(검토 r2 V2-W5: 손 리터럴로 심으면 「공고를 쓰는
     * 쪽의 어휘」가 아니라 test 가 믿는 어휘를 재게 된다). `NoticeRow.toRow` 와 같은 코덱이다.
     */
    protected data class NoticeMoney(
        val baseWon: Long,
        val baseProvenance: Provenance,
        val floorFraction: BigDecimal,
        val floorOrigin: FloorRateOrigin,
    )

    /** 공고 한 건 — `notice` 는 `raw_observation` 을 FK 로 참조하므로 그 행이 먼저다. */
    protected fun seedNotice(
        key: NoticeKey,
        deadline: Instant,
        money: NoticeMoney? = null,
    ) {
        dataSource().connection.use { connection ->
            insertObservation(connection, "obs-${key.noticeNumber}")
            connection
                .prepareStatement(
                    "INSERT INTO notice (notice_number, notice_round, status, observation_key, deadline_at, " +
                        "created_at, updated_at, base_amount_won, base_amount_currency, base_amount_vat, " +
                        "base_amount_provenance, base_amount_provenance_detail, floor_rate_fraction, " +
                        "floor_rate_origin_kind, floor_rate_origin_detail) " +
                        "VALUES (?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    var index = 1
                    statement.setString(index++, key.noticeNumber)
                    statement.setString(index++, key.noticeRound.value)
                    statement.setString(index++, "obs-${key.noticeNumber}")
                    statement.setTimestamp(index++, Timestamp.from(deadline))
                    statement.setTimestamp(index++, Timestamp.from(now))
                    statement.setTimestamp(index++, Timestamp.from(now))
                    statement.setObject(index++, money?.baseWon?.let(BigDecimal::valueOf))
                    statement.setString(index++, money?.let { Currency.KRW.name })
                    statement.setString(index++, money?.let { VatTreatment.INCLUSIVE.name })
                    statement.setString(index++, money?.baseProvenance?.let(ProvenanceCodec::kindNameOf))
                    statement.setString(index++, money?.baseProvenance?.let(ProvenanceCodec::detailOf))
                    statement.setBigDecimal(index++, money?.floorFraction)
                    statement.setString(index++, money?.floorOrigin?.let(FloorRateOriginCodec::kindOf))
                    statement.setString(index, money?.floorOrigin?.let(FloorRateOriginCodec::detailOf))
                    statement.executeUpdate()
                }
        }
    }

    /** 정정 공고 — 공고의 마감만 바꾼다(`null` 이면 마감 미정). 판 번호는 V2 트리거가 올린다. */
    protected fun correctNoticeDeadline(
        key: NoticeKey,
        deadline: Instant?,
    ) {
        val value = deadline?.let { "'$it'" } ?: "NULL"
        adminExecute("UPDATE notice SET deadline_at = $value WHERE notice_number = '${key.noticeNumber}'")
    }

    /** 공고를 지운다 — 6B-3 파기 뒤와 같은 상태(건 · 기록은 FK 가 없어 남는다). */
    protected fun purgeNotice(key: NoticeKey) {
        adminExecute("DELETE FROM notice WHERE notice_number = '${key.noticeNumber}'")
    }

    /** 이름 붙은 CHECK 의 `IN (…)` 어휘 — `pg_get_constraintdef` 의 `'…'::text` 리터럴을 모은다. */
    protected fun checkVocabulary(constraintName: String): Set<String> =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?")
                .use { statement ->
                    statement.setString(1, constraintName)
                    statement.executeQuery().use { rs ->
                        check(rs.next()) { "제약이 없다: $constraintName" }
                        CHECK_LITERAL
                            .findAll(rs.getString(1))
                            .map { it.groupValues[1] }
                            .toSet()
                    }
                }
        }

    protected fun adminExecute(sql: String) {
        dataSource().connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun insertObservation(
        connection: Connection,
        observationKey: String,
    ) {
        connection
            .prepareStatement(
                "INSERT INTO raw_observation (observation_key, source_endpoint, payload, " +
                    "payload_fields, observed_at, release_sha, inserted_at) " +
                    "VALUES (?, 'NOTICE_LIST', '{}', '{}'::jsonb, ?, 'test', ?)",
            ).use { statement ->
                statement.setString(1, observationKey)
                statement.setTimestamp(2, Timestamp.from(now))
                statement.setTimestamp(3, Timestamp.from(now))
                statement.executeUpdate()
            }
    }
}

private val CHECK_LITERAL = Regex("""'([A-Z_]+)'::text""")

/** 커넥션을 빌려줄 때마다 역할을 바꾼다 — 비풀링이라 반납 뒤 역할이 남지 않는다. */
private class RoleDataSource(
    private val base: DataSource,
    private val role: String,
) : DataSource by base {
    override fun getConnection(): Connection =
        base.connection.apply { createStatement().use { it.execute("SET ROLE $role") } }
}
