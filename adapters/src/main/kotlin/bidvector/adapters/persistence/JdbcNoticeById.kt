package bidvector.adapters.persistence

import bidvector.procurement.Notice
import bidvector.procurement.NoticeId
import bidvector.workflow.evaluation.NoticeByIdPort
import javax.sql.DataSource

/**
 * [NoticeByIdPort] 의 production 구현(M7/7C ②) — 공고 **한 행**을 키로 읽는다. 쓰기 메서드가
 * 없고 트랜잭션 경계도 없다.
 *
 * **`NoticeRepository` 를 평가 경로에 넘기지 않기 위해 따로 선다.** 그 저장소는 읽기와 함께
 * `persist` 를 들고 있어서, 운영자 요청 평가가 그것을 쥐면 「평가가 공고를 고칠 수 있다」가
 * 타입으로 가능해진다. 저장소를 빈으로 올려 좁혀 받는 대신 읽기만 하는 구현을 두면 쓰기 능력이
 * 조립 그래프에 아예 들어오지 않는다.
 *
 * 질의·복원은 새로 적지 않는다 — [Sql.SELECT_NOTICE]·[queryNoticeScoped]·[reconstructNotice]
 * 를 그대로 쓴다(SQL 문면은 상수 하나이고 이 클래스는 그 두 번째 호출부다). 그래서 이 포트와
 * 후보 스캔([bidvector.adapters.evaluation.JdbcCandidateSource])이 같은 행에서 **같은
 * `Notice` 값**을 복원한다 — 단건 평가와 전건 dry-run 의 차분이 성립하는 전제다.
 */
class JdbcNoticeById(
    private val dataSource: DataSource,
) : NoticeByIdPort {
    override fun find(id: NoticeId): Notice? =
        dataSource.connection.use { connection ->
            connection.queryNoticeScoped(Sql.SELECT_NOTICE, id) { rs -> id.reconstructNotice(rs.toNoticeRow()) }
        }
}
