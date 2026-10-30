package bidvector.adapters.result

import bidvector.adapters.notice.beginReadOnlySearchTransaction
import bidvector.settlement.OperatorTradeName
import bidvector.workflow.access.AccountId
import bidvector.workflow.result.OperatorTradeNamePort
import javax.sql.DataSource

/**
 * 운영자 상호 하나를 읽는다(M7/7C⑤-b) — **그 열만** 읽는다.
 *
 * 프로필 저장소(`JdbcBusinessProfileStore`)를 빌리지 않는 이유는 그쪽이 절 전체를 내기 때문이다.
 * 담을 자리가 없는 값을 읽을 이유가 없다는 이 slice 의 규율을 입력 쪽에도 건다 — 읽는 열이
 * 하나라 그 사실을 질의가 진술한다.
 *
 * **원문 그대로 낸다** — 앞뒤 공백도 자르지 않는다. 자르는 것도 정규화이고, 정규화는 대사 시점의
 * 커널이 **한 번만** 한다(이중 정규화 금지, `TradeName` KDoc 과 같은 규율).
 *
 * 행이 없거나 열이 `NULL` 이면 [OperatorTradeName.NotDeclared] 다. 공백뿐인 값은 V22 의 CHECK 가
 * 막지만, 그 전제에 기대지 않고 여기서도 「없음」으로 접는다 — [OperatorTradeName.Declared] 는
 * 공백뿐인 값으로 설 수 없으므로 접지 않으면 값 생성이 터진다.
 */
class JdbcOperatorTradeNameView(
    private val dataSource: DataSource,
) : OperatorTradeNamePort {
    override fun tradeNameOf(account: AccountId): OperatorTradeName =
        dataSource.readOnly { connection ->
            connection.prepareStatement(MyBidResultSql.SELECT_OPERATOR_TRADE_NAME).use { statement ->
                statement.setLong(1, account.value)
                statement.executeQuery().use { rows ->
                    if (rows.next()) tradeNameOf(rows.getString("trade_name")) else OperatorTradeName.NotDeclared
                }
            }
        }

    private fun tradeNameOf(raw: String?): OperatorTradeName =
        if (raw.isNullOrBlank()) OperatorTradeName.NotDeclared else OperatorTradeName.Declared(raw)
}
