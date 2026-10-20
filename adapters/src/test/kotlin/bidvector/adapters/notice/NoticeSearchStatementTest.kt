package bidvector.adapters.notice

import bidvector.procurement.ProvenanceKind
import bidvector.workflow.notice.NoticeSearchLimits
import bidvector.workflow.notice.NoticeSearchParameters
import bidvector.workflow.notice.NoticeSearchRequest
import bidvector.workflow.notice.parseNoticeSearchParameters
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * 질의 조립의 **구조** 게이트 — DB 없이 잰다.
 *
 * 설계 검토 (2) 의 우회 ②(`q=%` 로 전수 조회)·⑤(`'; DROP` 주입)를 닫는 자리다. 닫는 방식이
 * 「위험한 문자를 찾아 거부」가 아니라 **「문면이 값에 의존하지 않는다」**는 등식이라, 어떤
 * 입력을 고안해도 그 입력이 SQL 문면에 실릴 수 없다 — 우회하려면 조립 함수 자신을 바꿔야
 * 하고 그러면 이 등식이 깨진다.
 */
class NoticeSearchStatementTest {
    private fun criteriaOf(parameters: NoticeSearchParameters) =
        parseNoticeSearchParameters(parameters).shouldBeInstanceOf<NoticeSearchRequest.Accepted>().criteria

    private fun statementOf(parameters: NoticeSearchParameters) = noticeSearchStatement(criteriaOf(parameters))

    @Test
    fun `우회 ②⑤ — 문면은 조건의 유무만 보고 만들어진다(값이 달라도 같다)`() {
        val probes =
            listOf(
                "보통 공고명",
                "%",
                "_",
                "\\",
                "100%_할인",
                "'; DROP TABLE notice; --",
                "' OR 1=1 --",
                // `U+0000` 은 더 이상 이 자리에 올 수 없다 — 파싱이 제어 문자를 먼저 거부한다
                // (verifier r1 F-2). 대신 이스케이프 글자가 섞인 표본을 둔다.
                "!%_\\",
            )

        val clauses = probes.map { probe -> statementOf(NoticeSearchParameters(text = probe)).clause }.toSet()

        clauses.size shouldBe 1
    }

    @Test
    fun `우회 ⑤ — 입력 문자열은 문면이 아니라 값으로만 들어간다`() {
        val statement = statementOf(NoticeSearchParameters(text = "'; DROP TABLE notice; --"))

        statement.clause shouldNotContain "DROP"
        statement.clause shouldNotContain "--"
        statement.bindings shouldContainExactly
            listOf(
                NoticeSearchBinding.Text("%'; DROP TABLE notice; --%"),
                NoticeSearchBinding.Text("%'; DROP TABLE notice; --%"),
            )
    }

    @Test
    fun `우회 ② — 와일드카드와 이스케이프 문자는 글자로 바뀐다`() {
        noticeSearchContainsPattern("%") shouldBe "%!%%"
        noticeSearchContainsPattern("_") shouldBe "%!_%"
        noticeSearchContainsPattern("!") shouldBe "%!!%"
        noticeSearchContainsPattern("100%_할인") shouldBe "%100!%!_할인%"
        // 이스케이프 문자를 먼저 바꾸지 않으면 `!%` 가 `!!%`(리터럴 느낌표 + 와일드카드)가 된다.
        noticeSearchContainsPattern("!%") shouldBe "%!!!%%"
        // 역슬래시는 이제 보통 글자다 — 이스케이프되지 않고 그대로 지난다.
        noticeSearchContainsPattern("\\") shouldBe "%\\%"
    }

    @Test
    fun `문면이 ESCAPE 절을 명시하고 그 글자가 설정과 무관하다`() {
        val statement = statementOf(NoticeSearchParameters(text = "가", agencyText = "나"))

        // 역슬래시였으면 `standard_conforming_strings = off` 에서 문자열이 닫히지 않는다(L-4).
        statement.clause shouldNotContain "ESCAPE '\\'"
        statement.clause shouldContain "ILIKE ? ESCAPE '!'"
        statement.clause shouldContain "notice_title"
        statement.clause shouldContain "notice_number"
        statement.clause shouldContain "demand_agency_name"
        statement.clause shouldContain "notice_agency_name"
    }

    @Test
    fun `조건이 없으면 TRUE 하나다 — 빈 WHERE 가 생기지 않는다`() {
        val statement = statementOf(NoticeSearchParameters())

        statement.clause shouldBe "TRUE"
        statement.bindings shouldBe emptyList()
    }

    @Test
    fun `금액 조건은 기초금액 열만 보고 비교 성립 전제를 함께 건다`() {
        val statement = statementOf(NoticeSearchParameters(amountMinWon = "1000", amountMaxWon = "2000"))

        statement.clause shouldContain "base_amount_won >= ?"
        statement.clause shouldContain "base_amount_won <= ?"
        statement.clause shouldContain "base_amount_vat = ?"
        statement.clause shouldContain "base_amount_provenance <> ?"
        // legacy 검색이 비교하던 열(추정가격)은 문면에 없다 — capability map STR-16 「폐기」.
        statement.clause shouldNotContain "estimated_amount"
        statement.bindings shouldContainExactly
            listOf(
                NoticeSearchBinding.Text(NoticeSearchLimits.AMOUNT_VAT_TREATMENT.name),
                NoticeSearchBinding.Text(ProvenanceKind.UNDECLARED.name),
                NoticeSearchBinding.Amount(1000L),
                NoticeSearchBinding.Amount(2000L),
            )
    }

    @Test
    fun `금액 한계가 한쪽뿐이면 그 비교 하나 + 성립 전제다`() {
        val minOnly = statementOf(NoticeSearchParameters(amountMinWon = "1000"))
        val maxOnly = statementOf(NoticeSearchParameters(amountMaxWon = "2000"))

        minOnly.clause shouldContain "base_amount_won >= ?"
        minOnly.clause shouldNotContain "base_amount_won <= ?"
        maxOnly.clause shouldContain "base_amount_won <= ?"
        maxOnly.clause shouldNotContain "base_amount_won >= ?"
    }

    @Test
    fun `마감 범위는 양끝을 포함하는 두 비교다`() {
        val statement =
            statementOf(
                NoticeSearchParameters(deadlineFrom = "2026-10-20T00:00:00Z", deadlineTo = "2026-10-21T00:00:00Z"),
            )

        statement.clause shouldContain "deadline_at >= ?"
        statement.clause shouldContain "deadline_at <= ?"
        statement.bindings.size shouldBe 2
    }

    @Test
    fun `페이지 질의는 LIMIT·OFFSET 을 바인딩 자리로 두고 정렬이 결정적이다`() {
        val sql = NoticeSearchSql.selectPage("TRUE")

        sql shouldContain "LIMIT ? OFFSET ?"
        sql shouldContain "ORDER BY deadline_at ASC NULLS LAST, notice_number ASC, notice_round ASC"
        // 읽는 표는 `notice` 하나다 — join 이 없다(원문 관측·감사 표에 닿지 않는다).
        sql shouldContain "FROM notice"
        sql shouldNotContain "JOIN"
    }
}
