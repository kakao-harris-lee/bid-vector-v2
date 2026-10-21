package bidvector.adapters.bidding

import bidvector.adapters.persistence.ConnectionSource
import bidvector.bidding.NoticeKey
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.bidding.NoticeDeadlineQueryPort
import java.time.Instant

/**
 * 공고 마감 조회 — **라이브 값**이다(초안 스냅샷의 사본이 아니다).
 *
 * 투찰 예정 이전 상태에서는 공고가 정정되면 다음 확인 시점도 따라 움직이는 것이 맞다
 * (`nextCheckpoint` KDoc). 초안이 얼린 것은 「그때 그 금액을 정한 근거」이지 「앞으로 언제
 * 확인할 것인가」가 아니다.
 *
 * 건이 공고를 FK 로 참조하지 않으므로(V21 머리말 — 6B-3 파기 충돌) 파기된 공고의 키는 결과에
 * **없고**, 그러면 다음 확인 시점이 「없음」이 된다. 그것이 정직한 답이다 — 지어내지 않는다.
 *
 * 한 번의 질의로 묶는다(목록 화면이 건마다 한 번씩 묻지 않게). 계정 축이 없는 유일한 포트이고,
 * 그 예외의 사유는 포트 KDoc 에 적혀 있다 — 공고는 사용자 소유 데이터가 아니다.
 */
class JdbcNoticeDeadlineQuery(
    private val connections: ConnectionSource,
) : NoticeDeadlineQueryPort {
    override fun deadlines(notices: Set<NoticeKey>): Map<NoticeKey, Instant> {
        if (notices.isEmpty()) return emptyMap()
        val keys = notices.toList()
        return connections.withConnection { connection ->
            connection.prepareStatement(BiddingSql.SELECT_NOTICE_DEADLINE).use { statement ->
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
                            put(key, rs.getTimestamp("deadline_at").toInstant())
                        }
                    }
                }
            }
        }
    }
}
