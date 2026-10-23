package bidvector.adapters.notificationinbox

import bidvector.adapters.persistence.PersistenceTestSupport
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection

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
     */
    @Test
    fun `앱 역할은 upsert 문장을 그대로 돌릴 수 있다`() {
        appStatementFails(
            "INSERT INTO notification_inbox (account_id, kind, notice_number, notice_round, " +
                " detail_code, occurred_at, updated_at) " +
                "VALUES ($accountId, 'NOTICE_REVISED', 'N-PRIV', '000', NULL, now(), now()) " +
                "ON CONFLICT (account_id, kind, notice_number, notice_round) DO UPDATE SET " +
                " occurred_at = EXCLUDED.occurred_at, updated_at = EXCLUDED.updated_at, " +
                " detail_code = EXCLUDED.detail_code, " +
                " read_at = CASE WHEN notification_inbox.detail_code IS DISTINCT FROM " +
                "  EXCLUDED.detail_code THEN NULL ELSE notification_inbox.read_at END",
        ) shouldBe false
    }

    private fun appUpdateFails(assignment: String): Boolean =
        appStatementFails("UPDATE notification_inbox SET $assignment")

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
