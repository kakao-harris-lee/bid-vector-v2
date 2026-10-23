package bidvector.adapters.bidding

import bidvector.bidding.BidKind
import bidvector.bidding.SkipReason
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * **V21 의 구조 제약이 실제로 막는다**(계약 D-7B-3 · 검토 C-M1 · C-M2 · C-M5 · C-L4) — 소유자 연결로 직접
 * 넣어 본다(권한이 아니라 **제약**이 막는지를 잰다).
 *
 * 각 거부 단언 옆에 **같은 모양의 정상 행이 들어간다**는 양성 대조를 둔다 — 「무엇이든 거부된다」(열 이름
 * 오타 · 다른 제약)와 「그 제약이 막는다」가 같은 초록이 되지 않게.
 */
class BidCaseIntegrityTest : BidCaseDbFixture() {
    @Test
    fun `자식 행의 계정은 부모 건의 소유자와 같아야 한다 — 복합 FK`() {
        val mine = account("integ-a")
        val other = account("integ-b")
        val caseId =
            workflow()
                .open(mine, notice("integ-a"), BidKind.REAL)
                .view.case.id.value

        insertsTransition(caseId, mine.value, "WATCHING", "DEFER") shouldBe true
        insertsTransition(caseId, other.value, "WATCHING", "DEFER") shouldBe false
    }

    @Test
    fun `타임라인의 상태 어휘는 건과 같은 열거다 — 보류는 들어오지 않는다`() {
        val mine = account("integ-c")
        val caseId =
            workflow()
                .open(mine, notice("integ-c"), BidKind.REAL)
                .view.case.id.value

        insertsTransition(caseId, mine.value, "DEFERRED", "DEFER") shouldBe true
        insertsTransition(caseId, mine.value, "ON_HOLD", "DEFER") shouldBe false
        insertsTransition(caseId, mine.value, "DEFERRED", "HOLD") shouldBe false
    }

    @Test
    fun `사유는 상한을 넘을 수 없다 — 지울 수 없는 표에 남는 자유 텍스트`() {
        val mine = account("integ-d")
        val caseId =
            workflow()
                .open(mine, notice("integ-d"), BidKind.REAL)
                .view.case.id.value

        insertsTransition(caseId, mine.value, "SKIPPED", "SKIP", "r".repeat(SkipReason.MAX_LENGTH)) shouldBe true
        insertsTransition(caseId, mine.value, "SKIPPED", "SKIP", "r".repeat(SkipReason.MAX_LENGTH + 1)) shouldBe false
    }

    @Test
    fun `추첨번호는 양수뿐이다`() {
        val mine = account("integ-e")
        val caseId =
            workflow()
                .open(mine, notice("integ-e"), BidKind.REAL)
                .view.case.id.value

        insertsRecord(caseId, mine.value, "{3,11}") shouldBe true
        insertsRecord(caseId, mine.value, "{0,11}") shouldBe false
    }

    private var nextVersion = 1

    /** 타임라인 한 줄 — 성공 여부만 본다(실패는 롤백한다). 판 번호는 매번 새 값이다(UNIQUE 를 피한다). */
    private fun insertsTransition(
        caseId: Long,
        accountId: Long,
        toState: String,
        command: String,
        reason: String? = if (command == "SKIP") "사유" else null,
    ): Boolean =
        attempt(
            "INSERT INTO bid_case_transition (case_id, account_id, case_version, from_state, to_state, command, " +
                "actor_kind, actor_detail, reason, retroactive, occurred_at) " +
                "VALUES ($caseId, $accountId, ${nextVersion++}, 'WATCHING', '$toState', '$command', " +
                "'USER', NULL, ${reason?.let { "'$it'" } ?: "NULL"}, false, now())",
        )

    private var nextRecordSeq = 0

    private fun insertsRecord(
        caseId: Long,
        accountId: Long,
        drawNumbers: String,
    ): Boolean =
        attempt(
            "INSERT INTO bid_record (case_id, account_id, record_seq, amount_won, amount_currency, amount_vat, " +
                "amount_provenance, submitted_at, confirmed_at, retroactive, draw_numbers) " +
                "VALUES ($caseId, $accountId, ${nextRecordSeq++}, 1000, 'KRW', 'INCLUSIVE', 'OPERATOR_ENTERED', " +
                "now(), now(), false, '$drawNumbers')",
        )

    private fun attempt(sql: String): Boolean =
        dataSource().connection.use { connection ->
            runCatching { connection.createStatement().use { it.executeUpdate(sql) } }.isSuccess
        }
}
