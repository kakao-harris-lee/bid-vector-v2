package bidvector.app.evaluation

import bidvector.adapters.notification.NoticeMessageSettings
import bidvector.adapters.notification.NotificationChannelSettings
import bidvector.adapters.notification.TelegramBotCredential
import bidvector.adapters.notification.TelegramChatId
import bidvector.adapters.notification.TelegramSenderSettings
import bidvector.workflow.notification.RouteKey
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import javax.sql.DataSource

/**
 * 가짜 채널 설정 — 이 test 의 relay 는 발송 자리에 닿기 **전에** 끊기므로 값이 쓰이지 않는다
 * (실 Telegram 호출 0, D-9). `null` 을 넘길 수 없다: `Live` 모드 환경은 채널 설정 없이 조립되지
 * 않는다(그 불변식이 「보낼 수 있는 환경이 조용히 아무것도 안 하는」 상태를 막는다).
 */
internal fun relayChannelSettings(): NotificationChannelSettings =
    NotificationChannelSettings(
        telegram =
            TelegramSenderSettings(
                baseUrl = "http://127.0.0.1:1",
                botCredential = TelegramBotCredential.of("811122233:FAKEcommitZZQ"),
                routeKey = RouteKey("telegram-primary"),
                chatId = TelegramChatId.of("-1000000000002"),
                connectTimeout = Duration.ofSeconds(1),
                responseTimeout = Duration.ofSeconds(1),
            ),
        message = NoticeMessageSettings("https://bidvector.example"),
    )

/**
 * claim 이 커밋된 **뒤** 첫 연결 요청에서 끊기는 `DataSource`.
 *
 * 기준은 호출 **순번이 아니라 상태**다 — 순번에 걸면 조립이 연결을 몇 번 쓰는지가 바뀌는 순간
 * 주입이 일어나지 않고 test 가 「끊기지 않았다」로 붉어진다(선례: `CrashAfterDispatch` 가 같은
 * 이유로 순번에서 사건으로 옮겼다). 생성 시점의 `CLAIMED` 집합을 기준으로 두고, 그 집합이
 * **늘어난 뒤** 첫 연결에서 던진다.
 */
internal class FailAfterClaimDataSource(
    private val delegate: DataSource,
) : DataSource by delegate {
    private val baseline: Set<String> = claimedEntryIds()

    override fun getConnection(): Connection {
        if (claimedEntryIds() != baseline) {
            throw SQLException("relay 발송 전에 연결이 끊겼다(주입)", CONNECTION_FAILURE_SQL_STATE)
        }
        return delegate.connection
    }

    private fun claimedEntryIds(): Set<String> =
        delegate.connection.use { connection ->
            connection.prepareStatement(CLAIMED_ENTRY_IDS).use { statement ->
                statement.executeQuery().use { rows ->
                    buildSet { while (rows.next()) add(rows.getString(1)) }
                }
            }
        }

    private companion object {
        const val CLAIMED_ENTRY_IDS = "SELECT entry_id FROM outbox WHERE state = 'CLAIMED'"
        const val CONNECTION_FAILURE_SQL_STATE = "08006"
    }
}
