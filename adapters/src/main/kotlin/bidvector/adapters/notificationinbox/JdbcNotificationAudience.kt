package bidvector.adapters.notificationinbox

import bidvector.adapters.notice.beginReadOnlySearchTransaction
import bidvector.workflow.access.AccountId
import bidvector.workflow.notificationinbox.NotificationAudience
import bidvector.workflow.notificationinbox.NotificationAudiencePort
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

/**
 * 청중 질의 하나(M9/ops-code · NOTI-01) — **별 홀더**다.
 *
 * `NotificationInboxSql` 에 넣지 않은 이유: 그 object 의 문장 **모집단**을 `NotificationInboxSqlPolicyTest`
 * 가 리플렉션으로 뽑아 종류 전수·권한 축을 잰다. 청중 질의는 알림함 표를 건드리지 않으므로 그
 * 모집단에 들어가면 등식이 재는 대상이 바뀐다 — 게이트가 무엇을 재는지 흐려지는 쪽이 아니라
 * 홀더를 가르는 쪽을 택했다.
 */
internal object NotificationAudienceSql {
    /**
     * **열이 `id` 하나다.** 로그인 식별자·해시·잠금 시각을 함께 읽으면 알림 생산 경로가 자격
     * 정보를 손에 쥔다 — 필요한 것은 「누구의 알림함인가」 하나다.
     *
     * `ORDER BY id` 는 **결정적 순서**를 위한 것이다. 순서가 없으면 알림함 기록의 순서가 실행마다
     * 달라지고, 그 비결정성이 test 의 단언을 흔든다(기록 자체는 멱등이라 결과는 같다).
     */
    const val SELECT_ACCOUNT_IDS: String = "SELECT id FROM account ORDER BY id"
}

/**
 * 체계의 알림이 갈 계정 전수(M9/ops-code · NOTI-01).
 *
 * **읽기 전용 트랜잭션에서 돈다**(`JdbcNotificationInboxStore` 와 같은 helper) — 그 안에서는 어떤
 * DML 도 DB 가 거부하므로, 이 경로에 쓰기를 넣으려면 이름이나 술어가 아니라 **트랜잭션 설정**을
 * 바꿔야 한다.
 *
 * **`SQLException` 을 [NotificationAudience.Unavailable] 로 접는다**(cr r1 MEDIUM · v M-2).
 * 앞 판은 전파했고, 그 선택의 근거는 「받을 사람이 없다」와 「누가 받는지 못 읽었다」를 같은 값으로
 * 만들지 않겠다는 것이었다 — 그 근거는 지금도 옳지만 **예외가 그것을 가르는 유일한 수단이 아니다.**
 * 두 사실을 타입이 가르므로(빈 `Accounts` vs `Unavailable`) 조용한 0 건은 여전히 불가능하고, 대신
 * 평가 커밋 run 은 멈추지 않는다: 이 포트는 그 run 안에서 불리고, 앞 판에서는 저장 층 장애 하나가
 * 채널 요청 전에 run 을 끊어 **그 판정의 outbox 흔적까지** 함께 잃었다. 알림함 쓰기 포트가
 * `Failed` 로 접는 것과 같은 갈래이고 접는 자리도 같다.
 *
 * 좁은 타입만 잡는다 — 그 밖의 오류는 그대로 올라간다(쓰기 포트와 같은 규율).
 */
class JdbcNotificationAudience(
    private val dataSource: DataSource,
) : NotificationAudiencePort {
    override fun accounts(): NotificationAudience =
        try {
            dataSource.connection.use { connection ->
                connection.beginReadOnlySearchTransaction()
                try {
                    NotificationAudience.Accounts(connection.readAccountIds())
                } finally {
                    // 읽기만 한 트랜잭션이라 되돌릴 것이 없다 — 열어 둔 채 반납하지 않으려고 닫는다.
                    connection.rollback()
                }
            }
        } catch (
            @Suppress("SwallowedException") failure: SQLException,
        ) {
            // 조용히 삼키지 않는다 — 값이 그 축이고, 표지는 조립이 로그 한 줄로 적는다.
            NotificationAudience.Unavailable
        }
}

private fun Connection.readAccountIds(): List<AccountId> =
    prepareStatement(NotificationAudienceSql.SELECT_ACCOUNT_IDS).use { statement ->
        statement.executeQuery().use { rows ->
            val accounts = mutableListOf<AccountId>()
            while (rows.next()) accounts += AccountId(rows.getLong("id"))
            accounts
        }
    }
