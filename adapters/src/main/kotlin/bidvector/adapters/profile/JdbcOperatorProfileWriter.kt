package bidvector.adapters.profile

import bidvector.adapters.persistence.Sql
import bidvector.workflow.access.AccountId
import bidvector.workflow.evaluation.OperatorProfileStorePort
import bidvector.workflow.evaluation.ProfileFacts
import javax.sql.DataSource

/**
 * V12 자격 사실의 **저장** 어댑터(M7/7C④ 2b) — [JdbcOperatorProfileRepository](읽기)와 **다른
 * 클래스**다.
 *
 * 왜 갈랐나: 한 클래스가 조회 port 와 저장 port 를 함께 구현하면 상속 모양 래칫(D-3, 구현
 * 인터페이스 1)이 막는다. 그 래칫을 올리는 대신 축을 가르는 쪽을 골랐다 — 읽기 자리에 쓰기
 * 능력이 서명으로 들어오지 않게 하는 규율(V22 의 reader/store 분리)과 같은 방향이고, 조립이
 * 어느 쪽을 주는지가 타입으로 보인다.
 *
 * **M15/15A(V28) — 계정을 받는다.** 앞 판의 KDoc 이 예고한 자리이고, 쓰기의 소유 경계는 이제
 * 호출자 전제가 아니라 **이 서명과 upsert 의 충돌 키**(`account_id`)가 함께 진다. 「누가 이
 * 메서드를 부르는가」의 인가는 여전히 HTTP 층이다(`/api/profile` 은 계정 전용 경로다).
 *
 * `ProfileFacts` 의 필드를 그대로 행으로 옮긴다 — 자기 값을 지어 쓰지 않는다(D-6F6-2).
 * 문장 하나라 커넥션의 자동 커밋으로 원자다.
 */
class JdbcOperatorProfileWriter(
    private val dataSource: DataSource,
) : OperatorProfileStorePort {
    override fun save(
        account: AccountId,
        facts: ProfileFacts,
    ) {
        val row = facts.toRow()
        dataSource.connection.use { connection ->
            connection.prepareStatement(Sql.UPSERT_PROFILE).use { statement ->
                statement.setLong(1, account.value)
                statement.bindOperatorProfileRow(2, row)
                statement.executeUpdate()
            }
        }
    }
}
