package bidvector.adapters.profile

import bidvector.adapters.persistence.Sql
import bidvector.sharedkernel.AccountId
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.evaluation.ProfileFacts
import javax.sql.DataSource

/**
 * [OperatorProfilePort]의 첫 production 구현(`OPEN-4B6-PROFILE-SOURCE` 종결) — 저장소에
 * 이 port의 실 구현이 없어 `OpportunityAnalysis`가 프로필을 요구하는 판정 축은 조립될 수
 * 없었다(port KDoc이 「실 구현은 M6」이라 적어 뒀던 공백).
 *
 * **읽기 전용이다**(M7/7C④ 2b). 앞 판은 이 클래스가 조회와 저장을 함께 냈고(D-6F6-1), 2b 가
 * 저장을 port 로 세우면서 같은 클래스가 인터페이스 둘을 구현하게 됐다 — 모양 래칫(D-3)이 그것을
 * 막는다. 축을 가르는 쪽을 골랐다: 저장은 [JdbcOperatorProfileWriter] 이고, 읽기 자리에 쓰기
 * 능력이 서명으로 들어오지 않는다(V22 의 reader/store 분리와 같은 규율).
 *
 * **D-6F6-2 — 도메인 타입을 직접 만들지 않는다.** [ProfileFacts]·`OperatorLicenses` 모두
 * `internal constructor`로 닫힌 유일 통로([bidvector.strategy.validate]처럼)가 없는 평범한
 * public 값 타입이다 — 이 클래스는 [OperatorProfileRow]를 거쳐 그 public 생성자만 그대로
 * 부른다(`OperatorProfileRow.toProfileFacts`·`ProfileFacts.toRow`).
 *
 * **M15/15A(V28) — 프로필은 계정 소유다.** 앞 판의 `id=1` 고정 조회는 V28 과 함께 사라졌다 —
 * 표 층의 PK 가 `account_id` 이고 이 클래스의 술어가 같은 열이다(`JdbcStrategyRepository` 와
 * 같은 관례).
 */
class JdbcOperatorProfileRepository(
    private val dataSource: DataSource,
) : OperatorProfilePort {
    /**
     * 행이 없으면 「프로필 미설정」이다 — 빈 프로필을 지어내지 않는다(D-6F6-3).
     *
     * **M15/15A(V28)** — 그 판정이 **계정마다** 선다. 술어가 `account_id = ?` 하나라 다른 계정의
     * 행을 빌려 올 구조가 없다.
     */
    override fun current(account: AccountId): ProfileFacts? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(Sql.SELECT_PROFILE).use { statement ->
                statement.setLong(1, account.value)
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.toOperatorProfileRow().toProfileFacts() else null
                }
            }
        }
}
