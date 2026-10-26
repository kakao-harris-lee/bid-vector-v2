package bidvector.adapters.profile

import bidvector.adapters.persistence.Sql
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.evaluation.OperatorProfileStorePort
import bidvector.workflow.evaluation.ProfileFacts
import javax.sql.DataSource

/**
 * [OperatorProfilePort]의 첫 production 구현(`OPEN-4B6-PROFILE-SOURCE` 종결) — 저장소에
 * 이 port의 실 구현이 없어 `OpportunityAnalysis`가 프로필을 요구하는 판정 축은 조립될 수
 * 없었다(port KDoc이 「실 구현은 M6」이라 적어 뒀던 공백).
 *
 * **D-6F6-1 — 이 slice 는 표 + 어댑터(읽기·저장)까지다.** 편집 HTTP endpoint 는 6A 로
 * 이관한다 — 이 클래스가 양방향(조회 [current]·저장 [save])을 내면 6A 는 HTTP 만 얹는다.
 *
 * **D-6F6-2 — 도메인 타입을 직접 만들지 않는다.** [ProfileFacts]·`OperatorLicenses` 모두
 * `internal constructor`로 닫힌 유일 통로([bidvector.strategy.validate]처럼)가 없는 평범한
 * public 값 타입이다 — 이 클래스는 [OperatorProfileRow]를 거쳐 그 public 생성자만 그대로
 * 부른다(`OperatorProfileRow.toProfileFacts`·`ProfileFacts.toRow`).
 *
 * **D-6F6-3 — 프로필은 싱글턴이다.** `id=1` 고정 조회·upsert — V12의 `CHECK (id = 1)`이
 * 표 층에서, 이 클래스는 SQL 층에서 각각 같은 불변식을 진다(심층 방어, `JdbcStrategyRepository`
 * 와 같은 관례).
 */
class JdbcOperatorProfileRepository(
    private val dataSource: DataSource,
) : OperatorProfilePort,
    OperatorProfileStorePort {
    /** 행이 없으면 「프로필 미설정」이다 — 빈 프로필을 지어내지 않는다(D-6F6-3). */
    override fun current(): ProfileFacts? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(Sql.SELECT_PROFILE).use { statement ->
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.toOperatorProfileRow().toProfileFacts() else null
                }
            }
        }

    /**
     * 저장 진입점 — [OperatorProfileStorePort] 의 구현이다(M7/7C④ 2b 가 그 port 를 세웠다. 앞 판은
     * 어느 port 에도 속하지 않는 메서드였고 KDoc 이 「편집 HTTP 는 6A 소관」이라 적어 뒀다 — 그 자리를
     * 2b 가 받았다). `ProfileFacts`의 필드를 그대로 행으로 옮긴다(자기 값을 지어 쓰지 않는다).
     * **누가 이 메서드를 부르는가**의 인가는 HTTP 층이 진다 — 자격 사실은 계정 단위가 아니라 싱글턴
     * 이므로(V12) 이 서명에 계정이 없다.
     */
    override fun save(facts: ProfileFacts) {
        val row = facts.toRow()
        dataSource.connection.use { connection ->
            connection.prepareStatement(Sql.UPSERT_PROFILE).use { statement ->
                statement.bindOperatorProfileRow(1, row)
                statement.executeUpdate()
            }
        }
    }
}
