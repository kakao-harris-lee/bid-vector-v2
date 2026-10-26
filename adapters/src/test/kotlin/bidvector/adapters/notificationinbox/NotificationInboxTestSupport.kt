package bidvector.adapters.notificationinbox

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeTitle
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.NotificationFact
import bidvector.workflow.notificationinbox.NotificationInboxRecord
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * 알림함 실 DB test 의 공유 하네스(M7/7C③). **공고는 production 경로로 심는다**
 * ([JdbcNoticeRepository]) — 손으로 짠 SQL 사본으로 심으면 이 표의 LEFT JOIN 이 실제 적재가
 * 만드는 행과 다른 모양을 볼 수 있다.
 *
 * 계정은 **소유자 연결로** 심는다. 앱 역할에는 `account` INSERT 권한이 없으므로(V20) 이것이
 * production 과 같은 분담이다 — 계정 생성은 운영자 명령의 일이다.
 */
internal abstract class NotificationInboxTestSupport : PersistenceTestSupport() {
    /** 쓰기 포트 — `record` 만 있다(읽기 메서드를 **갖고 있지 않다**). */
    protected fun inbox(): JdbcNotificationInbox = JdbcNotificationInbox(dataSource())

    /** 읽기·읽음 처리 포트 — 어댑터가 class 로도 갈려 있다(`typeShapeGate` 래칫). */
    protected fun store(): JdbcNotificationInboxStore = JdbcNotificationInboxStore(dataSource())

    protected fun insertAccount(loginName: String): AccountId =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, failed_attempts) " +
                        "VALUES (?, 'inbox-fixture-hash', now(), now(), 0) RETURNING id",
                ).use { statement ->
                    statement.setString(1, loginName)
                    statement.executeQuery().use { rows ->
                        rows.next()
                        AccountId(rows.getLong("id"))
                    }
                }
        }

    /**
     * 공고 하나. [title] 을 주지 않으면 **제목 칸이 NULL 인 행**이 선다 — 「공고 행이 없다」와
     * 「행은 있고 제목이 비었다」를 가르는 test 가 그 모양을 쓴다.
     */
    protected fun insertNotice(
        number: String,
        title: String? = null,
        deadline: Instant? = null,
    ): NoticeId {
        val id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(
                    RawKey("bidNtceNo") to id.number.value,
                    RawKey("bidNtceOrd") to id.round.value,
                ),
                SourceEndpoint.NOTICE_LIST,
                OBSERVED_AT,
            )
        val key = appendRawObservation(observation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = deadline,
                openingScheduledAt = null,
                raw = observation,
                title = title?.let(NoticeTitle::of),
                demandAgency = null,
                noticeAgency = null,
            )
        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted
        return id
    }

    /** 알림함에 심지 **않은** 공고 키 — FK 가 없으므로 이 키로도 항목이 선다(V23 주석 ③). */
    protected fun unknownNotice(number: String): NoticeId = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))

    protected fun record(
        account: AccountId,
        notice: NoticeId,
        fact: NotificationFact,
        occurredAt: Instant = OBSERVED_AT,
    ): NotificationInboxRecord =
        NotificationInboxRecord(
            account = account,
            notice = notice,
            fact = fact,
            occurredAt = occurredAt,
        )

    /** 행 하나의 시각·읽음 상태를 날것으로 읽는다 — 포트가 노출하지 않는 `created_at` 을 본다. */
    protected fun rawRow(
        account: AccountId,
        kind: String,
        noticeNumber: String,
    ): RawInboxRow =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT occurred_at, created_at, updated_at, read_at, detail_code " +
                        "FROM notification_inbox " +
                        "WHERE account_id = ? AND kind = ? AND notice_number = ?",
                ).use { statement ->
                    statement.setLong(1, account.value)
                    statement.setString(2, kind)
                    statement.setString(3, noticeNumber)
                    statement.executeQuery().use { rows ->
                        check(rows.next()) { "행이 없다: $kind $noticeNumber" }
                        RawInboxRow(
                            occurredAt = rows.getTimestamp("occurred_at").toInstant(),
                            createdAt = rows.getTimestamp("created_at").toInstant(),
                            updatedAt = rows.getTimestamp("updated_at").toInstant(),
                            readAt = rows.getTimestamp("read_at")?.toInstant(),
                            detailCode = rows.getString("detail_code"),
                        )
                    }
                }
        }

    protected fun rowCount(): Long =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM notification_inbox").use { rows ->
                    rows.next()
                    rows.getLong(1)
                }
            }
        }

    internal companion object {
        val OBSERVED_AT: Instant = Instant.parse("2026-10-23T00:00:00Z")
    }
}

internal data class RawInboxRow(
    val occurredAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val readAt: Instant?,
    val detailCode: String?,
)
