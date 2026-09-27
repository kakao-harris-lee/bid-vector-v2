package bidvector.adapters.persistence

import bidvector.procurement.CollectedAxisStore
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.SourceEndpoint
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * 이미 받은 (공고, 축)(D-6G-29 ③) — 원문 관측의 존재가 곧 「받았다」다. 이 갈래는 canonical 승격을
 * 하지 않으므로 다른 증거가 없다.
 *
 * **대조는 SQL 이 아니라 Kotlin 에서 한다.** `raw_observation` 의 공고번호는 온 그대로의 원문이고
 * 물어보는 쪽은 canonical([NoticeNumber.of]: ASCII 대문자화 · 공백 → `-`)이다. 둘을 SQL 에서 그대로
 * 맞대면 소문자가 든 번호에서 조용히 빗나가 「안 받았다」고 답한다 — 이어 돌기가 실패하는 것이
 * 아니라 **표본 전체를 다시 불러** 승인 상한을 그만큼 태운다(실측: 소문자 번호 test 가 RED 였다).
 * 정규화 규칙을 SQL 에 한 벌 더 쓰지 않는다(COL-05 「규칙은 여기 하나뿐」) — 원문을 읽어 와
 * 도메인 규칙으로 접는다.
 *
 * 훑는 범위는 **축 하나**다. 이 네 상세 축에 쓰는 것은 6G 수집 갈래뿐이고, 그것도 표본에 뽑힌
 * 공고에만 나간다 — 행 수는 표본 크기로 묶인다.
 */
class JdbcCollectedAxisStore(
    private val dataSource: DataSource,
) : CollectedAxisStore {
    override fun alreadyCollected(
        endpoint: SourceEndpoint,
        noticeIds: Collection<NoticeId>,
    ): Set<NoticeId> {
        if (noticeIds.isEmpty()) return emptySet()
        val wanted = noticeIds.associateBy { it.number.value to it.round.value }
        return dataSource.connection.use { connection ->
            connection.prepareStatement(COLLECTED_SQL).use { statement ->
                statement.setString(1, endpoint.name)
                statement.executeQuery().use { rows -> matched(rows, wanted) }
            }
        }
    }

    private fun matched(
        rows: ResultSet,
        wanted: Map<Pair<String, String>, NoticeId>,
    ): Set<NoticeId> {
        val out = mutableSetOf<NoticeId>()
        while (rows.next()) {
            val number = rows.getString("notice_number")?.let { NoticeNumber.of(it).value }
            val round = rows.getString("notice_round")
            if (number != null && round != null) wanted[number to round]?.let(out::add)
        }
        return out
    }
}

/**
 * 두 창을 한 번에 센다 — 총계(`since` 이후)와 오늘치(`dayStart` 이후). `started_at` 을 축으로 쓴다:
 * 호출이 실제로 나간 시각이고, `reference_date`(조회 대상 날짜)와 다르다.
 */

private const val COLLECTED_SQL =
    """
    SELECT DISTINCT payload_fields ->> 'bidNtceNo' AS notice_number,
                    payload_fields ->> 'bidNtceOrd' AS notice_round
      FROM raw_observation
     WHERE source_endpoint = ?
    """
