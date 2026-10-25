package bidvector.adapters.bidding

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * **사용자 소유 표를 만지는 문장 전수가 계정으로 좁혀진다** — 문장 집합으로 잰다(계약 D-7B-3 · verifier
 * r1 L-4 · 변이 M3).
 *
 * 왜 행동 test 로 충분하지 않은가: 전이의 조건부 갱신(`UPDATE_CASE_STATE`)은 같은 트랜잭션의 선행
 * 소유 조회가 이미 막으므로, 그 문장에서 `account_id = ?` 를 빼도 남의 건 test 가 초록이다(M3 초록).
 * 그 필터는 심층 방어의 한 겹이고, 한 겹은 그 자리에서만 잴 수 있다.
 *
 * 판정은 **`BiddingSql` 의 문자열 상수 전수**다(이름을 손으로 적지 않는다 — 새 문장이 생겨도 이 test 의
 * 모집단에 자동으로 든다). 규칙:
 * - 사용자 소유 표(`bid_case` · `bid_case_transition` · `bid_draft` · `bid_record`)를 읽거나 고치는 문장은
 *   `account_id = ?` 를 갖는다.
 * - 그 표에 넣는 문장은 열 목록에 `account_id` 를 갖는다(값은 포트가 받은 계정이다).
 * - 공고 조회(`notice` 만 만지는 문장)는 계정 축이 없다 — 사용자 소유 데이터가 아니다.
 *
 * **이 술어는 문자열이다 — 정본은 행동 축이다**(검토 r2 C2-N17). `account_id = ? OR TRUE` 처럼 필터를 무력하게 하는
 * 변형은 이 test 를 지나간다. 소유 경계가 실제로 서는지는 실 DB 의 `BidCaseOwnershipTest`(남의 건은 읽히지도 쓰이지도
 * 않는다)가 재고, 이 test 는 「필터를 **빠뜨린** 새 문장」을 싸게 잡는 그물이다(변이 `OR TRUE` 를 변이 표에 둔다).
 */
class BiddingSqlOwnershipTest {
    private val statements: Map<String, String> =
        BiddingSql::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .associate { field ->
                field.isAccessible = true
                field.name to (field.get(null) as String)
            }

    @Test
    fun `사용자 소유 표를 만지는 문장은 전부 계정으로 좁혀진다`() {
        val owned = statements.filterValues { sql -> OWNED_TABLES.any { Regex("\\b$it\\b").containsMatchIn(sql) } }
        owned.keys.shouldNotBeEmpty()

        val unfiltered =
            owned.filterValues { sql ->
                if (sql.startsWith("INSERT")) {
                    !sql.substringBefore("VALUES").contains("account_id")
                } else {
                    !sql.contains("account_id = ?")
                }
            }

        unfiltered.keys shouldBe emptySet()
    }

    /** 양성 대조 — 모집단이 실제 문장을 담는다(반사가 아무것도 못 집어 등식이 빈 집합끼리 맞는 일이 없게). */
    @Test
    fun `모집단에 갱신 · 조회 · 삽입 문장이 모두 있다`() {
        statements.keys.containsAll(listOf("UPDATE_CASE_STATE", "SELECT_CASE_BY_ID", "INSERT_TRANSITION")) shouldBe true
    }

    private companion object {
        val OWNED_TABLES = listOf("bid_case", "bid_case_transition", "bid_draft", "bid_record")
    }
}
