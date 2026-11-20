package bidvector.adapters.bidding

import bidvector.adapters.persistence.ConnectionSource
import bidvector.adapters.persistence.DataSourceConnectionSource
import bidvector.bidding.NoticeKey
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.bidding.NoticeProjection
import bidvector.workflow.bidding.NoticeProjectionQueryPort
import javax.sql.DataSource

/**
 * 입찰함 카드의 공고 표시 칸 조회(M14/14E) — **라이브 값**이다(초안 스냅샷의 사본이 아니다).
 *
 * 공고가 정정돼 제목이 바뀌면 카드의 제목도 바뀌는 것이 맞다 — 카드는 「지금 그 공고가 무엇인가」를
 * 보이는 자리이고, 「그때 그 금액을 정한 근거」는 초안이 따로 얼려 들고 있다. `JdbcNoticeDeadlineQuery`
 * 가 같은 축을 같은 사유로 이미 택했다.
 *
 * 건이 공고를 FK 로 참조하지 않으므로(V21 머리말 — 6B-3 파기와 충돌한다) 파기된 공고의 키는 결과에
 * **없다**. 행이 있는데 제목 칸이 NULL 이면 키는 **있고** 값의 `title` 이 `null` 이다 — 둘을 가른다.
 *
 * 한 번의 질의로 묶는다(목록 화면이 건마다 한 번씩 묻지 않게). 계정 축이 없는 사유는 포트 KDoc 에
 * 적혀 있다 — 공고는 사용자 소유 데이터가 아니다.
 */
class JdbcNoticeProjectionQuery(
    private val connections: ConnectionSource,
) : NoticeProjectionQueryPort {
    /** 조립이 쓰는 생성자 — `DataSource` 하나를 받는다(`ConnectionSource` 는 adapters 안의 타입이다). */
    constructor(dataSource: DataSource) : this(DataSourceConnectionSource(dataSource))

    override fun projections(notices: Set<NoticeKey>): Map<NoticeKey, NoticeProjection> {
        if (notices.isEmpty()) return emptyMap()
        val keys = notices.toList()
        return connections.withConnection { connection ->
            connection.prepareStatement(BiddingSql.SELECT_NOTICE_PROJECTION).use { statement ->
                statement.setArray(
                    1,
                    connection.createArrayOf("text", keys.map { it.noticeNumber }.toTypedArray()),
                )
                statement.setArray(
                    2,
                    connection.createArrayOf("text", keys.map { it.noticeRound.value }.toTypedArray()),
                )
                statement.executeQuery().use { rs ->
                    buildMap {
                        while (rs.next()) {
                            val key =
                                NoticeKey(
                                    rs.getString("notice_number"),
                                    NoticeRound.of(rs.getString("notice_round")),
                                )
                            put(
                                key,
                                NoticeProjection(
                                    title = rs.getString("notice_title"),
                                    demandAgencyName = rs.getString("demand_agency_name"),
                                    noticeAgencyName = rs.getString("notice_agency_name"),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
