package bidvector.adapters.persistence

import bidvector.procurement.CallSpend
import bidvector.procurement.CollectedAxisStore
import bidvector.procurement.CollectionCallLedgerStore
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.SourceEndpoint
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/**
 * 호출 예산 원장의 영속 읽기(D-6G-29 ①) — 새 표를 만들지 않는다. 두 수집 갈래가 슬롯마다·상세 호출마다
 * `collection_run` 에 `pages_fetched` 를 남기므로, 그 합이 곧 「쓴 호출 수」다. **마이그레이션 없음**이
 * D-6G-1 의 목표였고 이 자리에서 지켜진다.
 *
 * `since` 는 승인이 시작된 시점이다 — 그 전의 수집 이력(다른 slice 의 실수집)을 이 예산에 계상하지
 * 않는다. `dayStart` 는 **KST 하루의 시작**이고 호출부가 준다(이 어댑터는 구역을 정하지 않는다).
 */
class JdbcCollectionCallLedgerStore(
    private val dataSource: DataSource,
) : CollectionCallLedgerStore {
    override fun spentSince(
        since: Instant,
        dayStart: Instant,
    ): CallSpend =
        dataSource.connection.use { connection ->
            connection.prepareStatement(SPENT_SQL).use { statement ->
                // 순서가 SQL 의 `?` 순서다 — FILTER(오늘치)가 먼저, WHERE(총계)가 뒤.
                statement.setTimestamp(1, Timestamp.from(dayStart))
                statement.setTimestamp(2, Timestamp.from(since))
                statement.executeQuery().use { rows ->
                    rows.next()
                    // 오늘치는 총계의 부분집합이다 — `dayStart` 가 `since` 보다 이르면 SQL 이 같은 창을
                    // 두 번 세므로 호출부가 아니라 여기서 좁힌다.
                    val total = rows.getInt("total_pages")
                    CallSpend(total = total, today = minOf(rows.getInt("today_pages"), total))
                }
            }
        }
}

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
private const val SPENT_SQL =
    """
    SELECT COALESCE(SUM(pages_fetched), 0) AS total_pages,
           COALESCE(SUM(pages_fetched) FILTER (WHERE started_at >= ?), 0) AS today_pages
      FROM collection_run
     WHERE started_at >= ?
    """

private const val COLLECTED_SQL =
    """
    SELECT DISTINCT payload_fields ->> 'bidNtceNo' AS notice_number,
                    payload_fields ->> 'bidNtceOrd' AS notice_round
      FROM raw_observation
     WHERE source_endpoint = ?
    """
