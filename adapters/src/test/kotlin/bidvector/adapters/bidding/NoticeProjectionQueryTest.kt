package bidvector.adapters.bidding

import bidvector.bidding.NoticeKey
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.bidding.NoticeProjection
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * M14/14E — 공고 읽기 투영의 **실 DB 축**. 이중체는 포트의 약속을 흉내 낼 뿐이고, 그 약속이
 * 실제로 성립하는지는 질의 문장이 정한다(「키가 없으면 행이 없다」는 결국 SQL 의 성질이다).
 *
 * 재는 것 셋:
 *  ⓐ **행 없음 ↔ 칸 빔** — 저장되지 않은 키는 결과에 **없고**, 저장됐는데 제목 열이 NULL 인
 *    키는 결과에 **있다**. 값 셋만 뽑는 문장이면 둘이 같아진다(그래서 키 열을 함께 뽑는다).
 *  ⓑ **묶음 조회** — 키 여럿을 한 번에 넘기면 있는 것만 돌아온다. 건 수가 질의 수를 늘리지
 *    않는다는 성질의 저장 층 쪽 절반이다.
 *  ⓒ **빈 집합** — 질의를 아예 보내지 않는다(빈 배열을 `unnest` 에 넘기지 않는다).
 */
class NoticeProjectionQueryTest : BidCaseDbFixture() {
    private val query = JdbcNoticeProjectionQuery(dataSource())

    private val titled = NoticeKey("R14E-PROJ-1", NoticeRound.of("000"))
    private val untitled = NoticeKey("R14E-PROJ-2", NoticeRound.of("000"))
    private val absent = NoticeKey("R14E-PROJ-3", NoticeRound.of("000"))

    private val deadline: Instant = Instant.parse("2026-10-22T04:00:00Z")

    @Test
    fun `행 없음과 제목 칸 빔이 다른 결과로 선다`() {
        seedNotice(titled, deadline)
        setNoticeDisplay(titled, "세종 청사 공조설비 교체", "행정중심복합도시건설청", "조달청")
        seedNotice(untitled, deadline)
        // 제목은 NULL 그대로 두고 기관 하나만 채운다 — 「행은 있다」가 제목에 기대지 않음을 보인다.
        setNoticeDisplay(untitled, null, "행정안전부", null)

        val found = query.projections(setOf(titled, untitled, absent))

        // ⓐ 저장되지 않은 키는 **키 자체가 없다**.
        found.keys shouldContainExactlyInAnyOrder listOf(titled, untitled)
        found[titled] shouldBe NoticeProjection("세종 청사 공조설비 교체", "행정중심복합도시건설청", "조달청")
        // 행은 **있고** 제목 칸만 비었다 — 위 줄과 달리 키가 결과에 있다.
        found[untitled] shouldBe NoticeProjection(null, "행정안전부", null)
        found.containsKey(absent) shouldBe false
    }

    @Test
    fun `빈 키 집합은 질의 없이 빈 결과다`() {
        query.projections(emptySet()) shouldBe emptyMap()
    }

    /**
     * 표시 열 셋만 세운다 — `seedNotice` 는 금액·마감 축의 자리라 이 열들을 받지 않는다.
     * V2 의 정정 트리거가 판 번호를 올리는 것은 이 축과 무관하다.
     */
    private fun setNoticeDisplay(
        key: NoticeKey,
        title: String?,
        demandAgency: String?,
        noticeAgency: String?,
    ) {
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "UPDATE notice SET notice_title = ?, demand_agency_name = ?, notice_agency_name = ? " +
                        "WHERE notice_number = ? AND notice_round = ?",
                ).use { statement ->
                    statement.setString(1, title)
                    statement.setString(2, demandAgency)
                    statement.setString(3, noticeAgency)
                    statement.setString(4, key.noticeNumber)
                    statement.setString(5, key.noticeRound.value)
                    statement.executeUpdate()
                }
        }
    }
}
