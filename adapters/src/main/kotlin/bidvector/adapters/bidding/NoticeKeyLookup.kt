package bidvector.adapters.bidding

import bidvector.adapters.persistence.ConnectionSource
import bidvector.bidding.NoticeKey
import bidvector.sharedkernel.NoticeRound
import java.sql.ResultSet

/**
 * 공고 키 묶음으로 `notice` 를 읽는 **한 자리**(M14/14E 가 둘째 질의를 들이며 뽑아냈다).
 *
 * 두 질의(마감 · 표시 칸)가 같은 표를 같은 키로 묶어 읽는다. 그 조립을 복사하면 **「키가 있으면
 * 공고 행이 있다」는 약속이 두 벌**이 되고, 한쪽만 고쳐도 아무것도 붉지 않는다 — 그 부재를
 * `cpdCheck` 가 실제로 잡았다(이 함수가 생긴 사유다).
 *
 * 약속은 여기 **한 번** 적힌다:
 *  · 빈 키 집합이면 **질의를 보내지 않는다**(빈 배열을 `unnest` 에 넘기지 않는다).
 *  · 결과 map 의 키는 **그 행이 실제로 있었다**는 뜻이다. 값이 전부 NULL 인 행도 키는 선다 —
 *    그래서 [read] 는 값 열만 읽고 키 열은 건드리지 않는다.
 *
 * [sql] 은 `notice_number` · `notice_round` 를 **반드시 함께 뽑아야 한다**(키를 복원하는 자리가
 * 여기다). 값 열만 뽑는 문장을 넘기면 `ResultSet` 이 그 열을 모른다고 던진다 — 조용히 틀리지
 * 않는다.
 */
internal fun <T> ConnectionSource.readByNoticeKeys(
    sql: String,
    notices: Set<NoticeKey>,
    read: (ResultSet) -> T,
): Map<NoticeKey, T> {
    if (notices.isEmpty()) return emptyMap()
    val keys = notices.toList()
    return withConnection { connection ->
        connection.prepareStatement(sql).use { statement ->
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
                        put(key, read(rs))
                    }
                }
            }
        }
    }
}
