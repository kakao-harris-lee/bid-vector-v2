package bidvector.adapters.bidding

import bidvector.bidding.BidCommand
import bidvector.bidding.BidKind
import bidvector.bidding.BidStateTag
import bidvector.bidding.tag
import bidvector.workflow.bidding.BidActor
import bidvector.workflow.bidding.BidCommandResult
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * **`bid_case` 의 열 단위 UPDATE 를 집합으로 잠그고, 출하 앱 역할의 양성 경로를 끝까지 돈다**
 * (계약 D-7B-3 · verifier r1 H2 · 검토 H-3).
 *
 * 앞 판에서는 이 test 가 **없었는데** 다른 test 의 주석이 이 이름을 가리켰다. 그래서 열 GRANT 가 한
 * 칸 바뀌어도(verifier 변이 M7: `updated_at` → `created_at`) 35 test 가 초록이었고, 같은 변이가
 * 출하 앱 역할에서는 모든 전이를 `permission denied` 로 깼다.
 *
 * 두 축이다.
 * 1. **등식** — 앱 역할이 UPDATE 할 수 있는 `bid_case` 열 = 전이가 바꾸는 일곱. 늘어도(예: `account_id`)
 *    줄어도(예: `updated_at`) 붉다. 판정은 `has_column_privilege` 로 **표의 모든 열**에 대해 묻는다(열
 *    목록을 손으로 적지 않는다).
 * 2. **양성 경로** — 출하 저장소 · 질의를 **앱 역할 연결**로 세워 담기 둘 · 초안 · 기록 둘 · 전이를
 *    끝까지 돈다. 거부만 재는 test 는 「연결이 죽었다」와 「권한이 좁다」를 가르지 못한다.
 */
class BidCaseColumnPrivilegeTest : BidCaseDbFixture() {
    @Test
    fun `앱 역할이 UPDATE 할 수 있는 bid_case 열은 전이가 바꾸는 일곱뿐이다`() {
        val updatable =
            dataSource().connection.use { connection ->
                connection
                    .prepareStatement(
                        "SELECT column_name FROM information_schema.columns " +
                            "WHERE table_schema = 'public' AND table_name = 'bid_case' " +
                            "AND has_column_privilege('bidvector_app', 'bid_case', column_name, 'UPDATE')",
                    ).use { statement ->
                        statement.executeQuery().use { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }
                    }
            }

        updatable shouldBe
            setOf("state", "case_version", "remind_at", "opening_at", "document_due_at", "skip_reason", "updated_at")
    }

    @Test
    fun `출하 저장소가 앱 역할 연결로 끝까지 돈다 — 담기 둘 · 초안 · 기록 둘 · 전이`() {
        val mine = account("priv-a")
        val key = notice("priv-a")
        seedNotice(key, now.plusSeconds(DEADLINE_AHEAD_SECONDS))
        val workflow = workflow(appRoleDataSource())

        val caseId =
            workflow
                .open(mine, key, BidKind.REAL)
                .view.case.id
        workflow.open(mine, key, BidKind.REAL).created shouldBe false
        workflow
            .draft(mine, caseId, amount(1_000_000L), now, BidActor.User(mine))
            .shouldBeInstanceOf<BidCommandResult.Processed>()
        repeat(2) {
            workflow
                .command(mine, caseId, BidCommand.RecordBid(submission(), null), BidActor.User(mine))
                .shouldBeInstanceOf<BidCommandResult.Processed>()
        }
        val openingAt = now.plusSeconds(DEADLINE_AHEAD_SECONDS)
        workflow
            .command(mine, caseId, BidCommand.AwaitOpening(openingAt), BidActor.User(mine))
            .shouldBeInstanceOf<BidCommandResult.Processed>()

        workflow
            .read(mine, caseId)!!
            .case.state.tag shouldBe BidStateTag.AWAITING_OPENING
        count("bid_case", caseId.value) shouldBe 1
        count("bid_draft", caseId.value) shouldBe 1
        // 「같은 공고에 기록이 여러 번 갱신돼도 건은 하나다」(§6.1) — 기록은 쌓이고 건은 하나다.
        count("bid_record", caseId.value) shouldBe 2
        workflow.timeline(mine, caseId)!!.map { it.command }.shouldNotBeEmpty() shouldBe
            listOf("ADD_TO_BID_BOX", "DRAFT_BID", "RECORD_BID", "RECORD_BID", "AWAIT_OPENING")
    }

    private fun count(
        table: String,
        caseId: Long,
    ): Int =
        dataSource().connection.use { connection ->
            val column = if (table == "bid_case") "id" else "case_id"
            connection.prepareStatement("SELECT count(*) FROM $table WHERE $column = ?").use { statement ->
                statement.setLong(1, caseId)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    private companion object {
        const val DEADLINE_AHEAD_SECONDS = 86_400L
    }
}
