package bidvector.adapters.notificationinbox

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.notificationinbox.NotificationKind
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp

/**
 * M7/7C③ — V23 의 **열 단위 GRANT** 가 실제로 무엇을 막는지 **앱 역할로 직접 쏘아** 잰다
 * (7D 의 `AccessPrivilegeTest` 와 같은 분담 · 같은 사유).
 *
 * 왜 권한 행렬(스키마 래칫 축9)만으로 모자라나: `has_table_privilege(…, 'UPDATE')` 는 **표 단위**
 * 부여만 본다. 열 단위로만 주면 그 축이 `false` 로 나오고, 그래서 그 행렬은 「어느 열을 갱신할
 * 수 있는가」를 아예 재지 못한다 — GRANT 를 `created_at` 쪽으로 넓혀도 행렬은 그대로 초록이다.
 * 이 class 가 그 창을 막는다(설계 검토 우회 ⑫).
 */
internal class NotificationInboxPrivilegeTest : PersistenceTestSupport() {
    private var accountId: Long = 0

    @BeforeEach
    fun seedRow() {
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, " +
                            " failed_attempts) VALUES ('priv-probe', 'h', now(), now(), 0) RETURNING id",
                    ).use { rows ->
                        rows.next()
                        accountId = rows.getLong("id")
                    }
                statement.execute(
                    "INSERT INTO notification_inbox (account_id, kind, notice_number, notice_round, " +
                        " occurred_at, updated_at) " +
                        "VALUES ($accountId, 'NOTICE_REVISED', 'N-PRIV', '000', now(), now())",
                )
            }
        }
    }

    /**
     * **적재 시각을 밀어 파기를 피하는 길이 권한 층에서 막힌다.** 이 열이 보존 cutoff 의 기준이라
     * 앱 역할이 바꿀 수 있으면 「90일이면 사라진다」가 앱 프로세스의 선의에 의존한다.
     */
    @Test
    fun `앱 역할은 적재 시각을 바꿀 수 없다`() {
        appUpdateFails("created_at = now()") shouldBe true
    }

    /** 소유 계정을 바꿀 수 있으면 **남의 알림함으로 항목을 옮길** 수 있다. */
    @Test
    fun `앱 역할은 소유 계정을 바꿀 수 없다`() {
        appUpdateFails("account_id = account_id") shouldBe true
    }

    /**
     * 종류·공고 키를 바꿀 수 있으면 **중복 억제를 우회**할 수 있다(한 항목을 다른 키로 옮겨
     * 같은 사실을 두 항목으로 만든다).
     */
    @Test
    fun `앱 역할은 종류와 공고 키를 바꿀 수 없다`() {
        appUpdateFails("kind = 'NEW_CANDIDATE'") shouldBe true
        appUpdateFails("notice_number = 'N-OTHER'") shouldBe true
        appUpdateFails("notice_round = '001'") shouldBe true
    }

    /** 요청 경로가 실제로 필요한 네 열 — 음성 대조(위 금지가 전부를 막는 것이 아님을 보인다). */
    @Test
    fun `앱 역할은 detail·사실 시각·갱신 시각·읽음 시각만 갱신할 수 있다`() {
        appUpdateFails("read_at = now()") shouldBe false
        appUpdateFails("occurred_at = now()") shouldBe false
        appUpdateFails("updated_at = now()") shouldBe false
        appUpdateFails("detail_code = NULL") shouldBe false
    }

    /** **앱 프로세스가 장악돼도 알림을 지울 수 없다** — 파기는 소유자 연결의 일이다. */
    @Test
    fun `앱 역할은 알림 항목을 지울 수 없다`() {
        appStatementFails("DELETE FROM notification_inbox") shouldBe true
        appStatementFails("TRUNCATE TABLE notification_inbox") shouldBe true
    }

    @Test
    fun `앱 역할은 항목을 읽고 등록할 수 있다 — 요청 경로와 쓰기 포트가 그것을 한다`() {
        appStatementFails("SELECT count(*) FROM notification_inbox") shouldBe false
        appStatementFails(
            "INSERT INTO notification_inbox (account_id, kind, notice_number, notice_round, " +
                " occurred_at, updated_at) " +
                "VALUES ($accountId, 'NEW_CANDIDATE', 'N-INSERT', '000', now(), now())",
        ) shouldBe false
    }

    /**
     * upsert 가 **실제로** 앱 역할로 도는지 — `ON CONFLICT DO UPDATE` 는 갱신하는 열에 대한
     * UPDATE 권한을 요구한다. 문장 단위로 재야 「네 열이면 충분하다」가 증명된다(열마다 따로
     * 재는 것으로는 그 조합이 성립하는지 알 수 없다).
     *
     * **출하 상수를 그대로 쏜다**(r1 검토 CR M-1 · verifier F6). 앞 판은 같은 SQL 을 손으로
     * 베껴 두었는데, 그러면 `DO UPDATE SET` 에 GRANT 밖 열이 더해져도 **거동 test 는 admin
     * 연결이라 초록이고 이 권한 test 는 옛 사본이라 초록**이다 — 두 자리가 같이 눈을 감는다.
     */
    @Test
    fun `앱 역할은 출하 upsert 상수를 그대로 돌릴 수 있다`() {
        appPreparedFails(NotificationInboxSql.UPSERT_ITEM) { statement ->
            statement.setLong(1, accountId)
            statement.setString(2, NotificationKind.NOTICE_REVISED.name)
            statement.setString(3, "N-PRIV")
            statement.setString(4, "000")
            statement.setString(5, null)
            statement.setTimestamp(6, Timestamp.from(NotificationInboxTestSupport.OBSERVED_AT))
        } shouldBe false
    }

    /** 읽음 처리 두 문장도 **출하 상수로** — 같은 이유(사본이 옛 모양으로 굳는다). */
    @Test
    fun `앱 역할은 출하 읽음 처리 상수 둘을 그대로 돌릴 수 있다`() {
        appPreparedFails(NotificationInboxSql.MARK_ITEM_READ) { statement ->
            statement.setTimestamp(1, Timestamp.from(NotificationInboxTestSupport.OBSERVED_AT))
            statement.setLong(2, accountId)
            statement.setString(3, NotificationKind.NOTICE_REVISED.name)
            statement.setString(4, "N-PRIV")
            statement.setString(5, "000")
        } shouldBe false

        appPreparedFails(NotificationInboxSql.MARK_NOTICE_READ) { statement ->
            statement.setTimestamp(1, Timestamp.from(NotificationInboxTestSupport.OBSERVED_AT))
            statement.setLong(2, accountId)
            statement.setString(3, "N-PRIV")
            statement.setString(4, "000")
        } shouldBe false
    }

    /**
     * **적재 시각을 INSERT 로 지정하는 길이 막혀 있다**(r1 검토 verifier F5). UPDATE 쪽은 열
     * 단위 GRANT 가 이미 막았지만, 표 단위 INSERT 였을 때는 **처음 넣을 때** 먼 미래를 적어
     * 그 행이 보존 cutoff 를 영영 지나지 않게 할 수 있었다(이후 upsert 는 `created_at` 을
     * 건드리지 않는다). 지금은 INSERT 도 일곱 열뿐이다.
     *
     * 음성 대조가 바로 위 test 들이다 — 같은 역할로 **그 일곱 열만 쓰는** 출하 upsert 는 돈다.
     * 그래서 이 실패는 「권한이 전혀 없다」가 아니라 **그 열이 범위 밖**이라는 뜻이다.
     */
    @Test
    fun `앱 역할은 적재 시각을 지정해 넣을 수 없다`() {
        appStatementFails(
            "INSERT INTO notification_inbox (account_id, kind, notice_number, notice_round, " +
                " occurred_at, updated_at, created_at) " +
                "VALUES ($accountId, 'NEW_CANDIDATE', 'N-CREATED', '000', now(), now(), " +
                " now() + interval '100 years')",
        ) shouldBe true

        // 읽음 시각도 같다 — 읽음은 처리 경로의 UPDATE 로만 움직인다.
        appStatementFails(
            "INSERT INTO notification_inbox (account_id, kind, notice_number, notice_round, " +
                " occurred_at, updated_at, read_at) " +
                "VALUES ($accountId, 'NEW_CANDIDATE', 'N-READ', '000', now(), now(), now())",
        ) shouldBe true
    }

    private fun appUpdateFails(assignment: String): Boolean =
        appStatementFails("UPDATE notification_inbox SET $assignment")

    /** 출하 상수를 **매개변수까지 묶어** 앱 역할로 쏜다. */
    private fun appPreparedFails(
        sql: String,
        bind: (PreparedStatement) -> Unit,
    ): Boolean =
        runCatching {
            appConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    bind(statement)
                    statement.execute()
                }
                connection.rollback()
            }
        }.isFailure

    private fun appStatementFails(sql: String): Boolean =
        runCatching {
            appConnection().use { connection ->
                connection.execute(sql)
                // 되돌린다 — 음성 대조의 쓰기가 다음 단언에 새어 나가지 않게.
                connection.rollback()
            }
        }.isFailure

    private fun Connection.execute(sql: String) = createStatement().use { it.execute(sql) }
}
