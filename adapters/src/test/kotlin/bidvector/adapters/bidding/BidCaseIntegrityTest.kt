package bidvector.adapters.bidding

import bidvector.bidding.BidCommandTag
import bidvector.bidding.BidKind
import bidvector.bidding.BidStateTag
import bidvector.bidding.OPEN_CASE_COMMAND
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


    // --- 계약 D-7B-4 ----------------------------------------------------------------

    /** 검토 r2 C2-N5 — 손으로 적은 열거가 도메인 enum 과 **등식**이다(한쪽에만 어휘가 늘면 붉다). 결정 ⑤ 의 새 명령 포함. */
    @Test
    fun `상태 · 명령 어휘 CHECK 는 도메인 열거 전수와 같다`() {
        val states = BidStateTag.entries.map { it.name }.toSet()
        checkVocabulary("bid_case_state_check") shouldBe states
        checkVocabulary("bid_case_transition_from_state_check") shouldBe states
        checkVocabulary("bid_case_transition_to_state_check") shouldBe states
        checkVocabulary("bid_case_transition_command_check") shouldBe
            BidCommandTag.entries.map { it.name }.toSet() + OPEN_CASE_COMMAND
    }

    /** 검토 r2 V2-W4 · C2-M1 잔여 — 기록이 가리키는 초안은 **같은 건 · 같은 계정**의 것이다(복합 FK). */
    @Test
    fun `기록의 초안은 같은 건 · 같은 계정의 것이어야 한다 — 없으면 통과`() {
        val mine = account("integ-f")
        val other = account("integ-g")
        val caseA = openCase(mine, "integ-f1")
        val caseB = openCase(mine, "integ-f2")
        val theirs = openCase(other, "integ-g1")
        val draftA = requireNotNull(insertDraft(caseA, mine.value))

        insertsRecord(caseA, mine.value, "{3}", draftId = draftA) shouldBe true
        insertsRecord(caseB, mine.value, "{3}", draftId = draftA) shouldBe false
        insertsRecord(theirs, other.value, "{3}", draftId = draftA) shouldBe false
        insertsRecord(caseB, mine.value, "{3}", draftId = null) shouldBe true
    }

    /** 검토 r2 C2-N18 — 자식 둘(초안 · 기록)의 복합 FK 도 행동으로 잰다(타임라인 쪽은 위 test). */
    @Test
    fun `초안 · 기록 행의 계정도 부모 건의 소유자와 같아야 한다`() {
        val mine = account("integ-h")
        val other = account("integ-i")
        val caseId = openCase(mine, "integ-h")

        (insertDraft(caseId, mine.value) != null) shouldBe true
        (insertDraft(caseId, other.value) != null) shouldBe false
        insertsRecord(caseId, mine.value, "{3}") shouldBe true
        insertsRecord(caseId, other.value, "{3}") shouldBe false
    }

    /** 검토 r2 C2-N13 — 메모 · 증빙 참조도 지울 수 없는 표에 남으므로 도메인 상한을 표가 한 번 더 진다. */
    @Test
    fun `메모 · 증빙 참조는 도메인 상한을 넘을 수 없다`() {
        val mine = account("integ-j")
        val caseId = openCase(mine, "integ-j")

        insertsRecord(caseId, mine.value, "{3}", memo = "m".repeat(MEMO_LIMIT)) shouldBe true
        insertsRecord(caseId, mine.value, "{3}", memo = "m".repeat(MEMO_LIMIT + 1)) shouldBe false
        insertsRecord(caseId, mine.value, "{3}", evidence = "e".repeat(EVIDENCE_LIMIT)) shouldBe true
        insertsRecord(caseId, mine.value, "{3}", evidence = "e".repeat(EVIDENCE_LIMIT + 1)) shouldBe false
    }

    /** 검토 r2 C2-N14 — 스냅샷의 기초금액 통화 · 과세가 빈 문자열이면 되읽을 때 `valueOf("")` 가 터진다. */
    @Test
    fun `스냅샷 기초금액의 통화 · 과세는 빈 문자열일 수 없다`() {
        val mine = account("integ-k")
        val caseId = openCase(mine, "integ-k")

        (insertDraft(caseId, mine.value, baseCurrency = "KRW", baseVat = "INCLUSIVE") != null) shouldBe true
        (insertDraft(caseId, mine.value, baseCurrency = "", baseVat = "INCLUSIVE") != null) shouldBe false
        (insertDraft(caseId, mine.value, baseCurrency = "KRW", baseVat = "") != null) shouldBe false
    }

    private fun openCase(
        account: bidvector.workflow.access.AccountId,
        suffix: String,
    ): Long =
        workflow()
            .open(account, notice(suffix), BidKind.REAL)
            .view.case.id.value

    private var nextDraftSeq = 0

    /** 초안 한 줄 — 성공하면 그 행의 id, 실패하면 `null`. 기초금액은 [baseCurrency] 가 있을 때만 싣는다. */
    private fun insertDraft(
        caseId: Long,
        accountId: Long,
        baseCurrency: String? = null,
        baseVat: String? = null,
    ): Long? {
        val base =
            if (baseCurrency == null) {
                "NULL, NULL, NULL, NULL"
            } else {
                "1000, '$baseCurrency', '$baseVat', 'UNDECLARED'"
            }
        val sql =
            "INSERT INTO bid_draft (case_id, account_id, draft_seq, amount_won, amount_currency, amount_vat, " +
                "amount_provenance, presented_at, snapshot_notice_revision, snapshot_taken_at, " +
                "snapshot_base_amount_won, snapshot_base_amount_currency, snapshot_base_amount_vat, " +
                "snapshot_base_amount_provenance) VALUES ($caseId, $accountId, ${nextDraftSeq++}, 1000, 'KRW', " +
                "'INCLUSIVE', 'OPERATOR_ENTERED', now(), 1, now(), $base) RETURNING id"
        return dataSource().connection.use { connection ->
            runCatching {
                connection.createStatement().use { statement ->
                    statement.executeQuery(sql).use { rs ->
                        rs.next()
                        rs.getLong(1)
                    }
                }
            }.getOrNull()
        }
    }

    private var nextVersion = 1

    /**
     * 타임라인 한 줄 — 성공 여부만 본다. autocommit 연결이라 실패한 문장은 그냥 실패하고 남는 것이 없다(검토 r2
     * C2-N18 — 앞 판 주석의 「롤백」은 사실이 아니었다). 판 번호는 매번 새 값이다(UNIQUE 를 피한다).
     */
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

    @Suppress("LongParameterList")
    private fun insertsRecord(
        caseId: Long,
        accountId: Long,
        drawNumbers: String,
        draftId: Long? = null,
        memo: String? = null,
        evidence: String? = null,
    ): Boolean {
        val retain = if (evidence == null) "NULL" else "'2029-10-21'"
        return attempt(
            "INSERT INTO bid_record (case_id, account_id, record_seq, amount_won, amount_currency, amount_vat, " +
                "amount_provenance, submitted_at, confirmed_at, retroactive, draw_numbers, draft_id, memo, " +
                "evidence_ref, evidence_retain_until) " +
                "VALUES ($caseId, $accountId, ${nextRecordSeq++}, 1000, 'KRW', 'INCLUSIVE', 'OPERATOR_ENTERED', " +
                "now(), now(), false, '$drawNumbers', ${draftId ?: "NULL"}, ${literal(memo)}, " +
                "${literal(evidence)}, $retain)",
        )
    }

    private fun literal(value: String?): String = value?.let { "'$it'" } ?: "NULL"

    private fun attempt(sql: String): Boolean =
        dataSource().connection.use { connection ->
            runCatching { connection.createStatement().use { it.executeUpdate(sql) } }.isSuccess
        }

    private companion object {
        /** `BidMemo` · `EvidenceRef` 의 상한(도메인 companion 이 private 이라 값을 옮겨 적는다 — 어긋나면 위 test 가 붉다). */
        const val MEMO_LIMIT = 2000
        const val EVIDENCE_LIMIT = 128
    }
}
