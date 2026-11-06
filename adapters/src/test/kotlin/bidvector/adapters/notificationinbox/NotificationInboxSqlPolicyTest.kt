package bidvector.adapters.notificationinbox

import bidvector.workflow.notificationinbox.NotificationBidKind
import bidvector.workflow.notificationinbox.NotificationKind
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.sql.Timestamp
import java.time.Instant

/**
 * M9/inbox-vocab — **읽음 해제를 결정하는 것은 bind 하나다**(D-M9-3 · 설계 검토 우회 5).
 *
 * 왜 이 게이트가 따로 필요한가: 정책을 SQL 쪽으로 옮겨 적어도 **거동 test 는 전부 초록이다**
 * (같은 답을 내므로). 잃는 것은 거동이 아니라 **종류를 더할 때 컴파일이 막아 준다**는 성질이고,
 * 그것은 평상 거동으로는 보이지 않는다.
 *
 * **앞 판은 그 축을 문자열로 쟀고, 그래서 열렸다**(cr M-2 · v M-1 실측). 두 꼴이 술어를
 * 지나갔다 — `kind LIKE 'NOTICE_REV%'`(리터럴이 축어가 아니다)와 `kind = 'NOTICE_' || 'REVISED'`
 * (리터럴이 연결로 쪼개졌다). 둘 다 bind 를 무시하는 SQL 이 되어 Kotlin 정책 표가 wire 와
 * 끊기는데도 게이트와 거동 test 가 모두 초록이었다. 문자열 열거를 넓히는 것은 같은 종류의
 * 술어를 한 겹 더 쌓는 일이라, 축을 **거동**으로 바꿨다.
 *
 * 그래서 이 class 의 **하중은 첫 test** 가 진다: 출하 문장을 **직접 실행**해 종류 전수 ×
 * bind{true,false} 로 「bind=true 면 시각 전진에
 * 해제, false 면 유지」를 잰다. 종류 리터럴이 어떤 꼴로 끼어도 그 등식이 깨진다 — 끼는 순간
 * 「그 종류만」 또는 「그 종류 빼고」가 되므로.
 *
 * 남긴 문자열 검사는 **진단**이다(하중이 아니다): 축어 리터럴을 적는 가장 흔한 꼴을 그 자리에서
 * 이름 붙여 준다. 단어 경계로 본다(cr L-5) — 형제 관례(`BiddingSqlOwnershipTest`)와 같은 꼴이고,
 * 장래 종류 이름이 다른 이름의 부분 문자열일 때 오탐으로 붉어지지 않는다.
 */
internal class NotificationInboxSqlPolicyTest : NotificationInboxTestSupport() {
    private val statements: Map<String, String> =
        NotificationInboxSql::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .associate { field ->
                field.isAccessible = true
                field.name to (field.get(null) as String)
            }

    /**
     * **하중이 이 test 다.** 출하 상수를 손 사본 없이 그대로 쏜다 — 사본을 두면 문장이 바뀌어도
     * 이 게이트가 옛 모양을 재게 된다(`NotificationInboxPrivilegeTest` 가 같은 이유로 출하 상수를
     * 쏜다).
     *
     * 걸음 셋: 적고 → 읽고 → **시각을 전진시켜** 같은 detail 로 다시 적는다. detail 이 그대로라
     * detail 갈래는 거짓이고, 남는 결정자는 시각 갈래뿐이다. 그 갈래가 bind 를 보는지 종류를
     * 보는지가 여기서 갈린다.
     */
    @Test
    fun `종류 전수 × bind 두 값에서 읽음 해제를 결정하는 것은 bind 하나다`() {
        val account = insertAccount("sql-policy")

        NotificationKind.entries.forEach { kind ->
            listOf(true, false).forEach { resetsOnNewerFact ->
                val notice = unknownNotice("N-${kind.name}-$resetsOnNewerFact")
                val detail = factOf(kind).detail?.name
                upsertDirectly(account.value, kind, notice.number.value, detail, OBSERVED_AT, resetsOnNewerFact)
                markReadDirectly(account.value, kind, notice.number.value)

                upsertDirectly(account.value, kind, notice.number.value, detail, LATER, resetsOnNewerFact)

                val row = rawRow(account, kind.name, notice.number.value)
                withClue("$kind bind=$resetsOnNewerFact") {
                    (row.readAt == null) shouldBe resetsOnNewerFact
                }
            }
        }
    }

    /**
     * **모집단에 그 문장이 실제로 있다**(cr M-2). `Modifier.isStatic` 은 `const val` 만 집으므로,
     * `UPSERT_ITEM` 을 `val` 로 바꾸는 평범한 리팩터가 그 문장을 모집단에서 **지운다** — 그때
     * 「비어 있지 않다」만 보면 남은 세 문장 때문에 초록이다. 이름을 들어 단언한다(형제 관례
     * `BiddingSqlOwnershipTest` 와 같은 축).
     */
    @Test
    fun `알림함 문장 전수가 모집단에 있다 — 반사가 조용히 빠뜨리지 않는다`() {
        statements.keys shouldContainAll
            listOf("UPSERT_ITEM", "SELECT_PAGE", "SELECT_COUNTS", "MARK_ITEM_READ")
    }

    /** 진단 — 축어 리터럴을 적은 자리를 이름 붙인다(하중은 위 거동 test 가 진다). */
    @Test
    fun `알림함 문장 전수에 종류 이름 축어가 없다 — 진단`() {
        leakedKindsIn(statements).shouldBeEmpty()
    }

    /**
     * 진단 — **투찰 종류 축어도 없다**(M9/paper-bid). 이 slice 가 걷어낸 것이 바로 그 꼴의
     * 리터럴(`bid_kind = 'REAL'`)이고, 그것이 알림함 문장으로 **옮겨 오는** 것이 가장 그럴듯한
     * 회귀다 — 그러면 한 축의 항목만 읽히거나 기록되고 거동 test 는 자기 축만 돌아 초록이다.
     *
     * 하중은 실 DB 거동(`NotificationInboxPersistenceTest` 의 종류 × 투찰 종류 교차)이 진다.
     */
    @Test
    fun `알림함 문장 전수에 투찰 종류 축어가 없다 — 진단`() {
        leakedBidKindsIn(statements).shouldBeEmpty()
    }

    /** 양성 대조 — 진단 술어가 실제로 투찰 종류 이름을 집는다. */
    @Test
    fun `투찰 종류를 담은 문장은 진단에 걸린다 — 양성 대조`() {
        val planted = "… WHERE bid_kind = '${NotificationBidKind.REAL.name}' …"

        leakedBidKindsIn(mapOf("PLANTED" to planted)) shouldContainExactly
            listOf("PLANTED" to NotificationBidKind.REAL)
    }

    /**
     * 양성 대조 — 진단 술어가 실제로 종류 이름을 집는다. 단어 경계 때문에 **부분 문자열로는
     * 걸리지 않는다**는 것도 같은 자리에서 보인다(`NOTICE_REVISEDX` 는 다른 이름이다).
     */
    @Test
    fun `종류 이름을 담은 문장은 진단에 걸리고 부분 문자열은 걸리지 않는다 — 양성·음성 대조`() {
        val planted = "… WHEN kind = '${NotificationKind.NOTICE_REVISED.name}' THEN NULL …"
        leakedKindsIn(mapOf("PLANTED" to planted)) shouldContainExactly
            listOf("PLANTED" to NotificationKind.NOTICE_REVISED)

        leakedKindsIn(mapOf("SUFFIXED" to "… kind = 'NOTICE_REVISEDX' …")).shouldBeEmpty()
    }

    private fun leakedKindsIn(statements: Map<String, String>): List<Pair<String, NotificationKind>> =
        statements.flatMap { (name, sql) ->
            NotificationKind.entries
                .filter { Regex("\\b${it.name}\\b").containsMatchIn(sql) }
                .map { name to it }
        }

    private fun leakedBidKindsIn(statements: Map<String, String>): List<Pair<String, NotificationBidKind>> =
        statements.flatMap { (name, sql) ->
            NotificationBidKind.entries
                .filter { Regex("\\b${it.name}\\b").containsMatchIn(sql) }
                .map { name to it }
        }

    /** 출하 문장을 그대로 쏜다 — 일곱째 bind 가 이 test 의 축이다. */
    private fun upsertDirectly(
        account: Long,
        kind: NotificationKind,
        noticeNumber: String,
        detail: String?,
        occurredAt: Instant,
        resetsOnNewerFact: Boolean,
    ) = dataSource().connection.use { connection ->
        connection.prepareStatement(NotificationInboxSql.UPSERT_ITEM).use { statement ->
            statement.setLong(1, account)
            statement.setString(2, noticeNumber)
            statement.setString(3, "000")
            // 투찰 종류는 **그 종류의 축에 맞는 값**이다 — 틀리면 짝 CHECK 가 INSERT 를 거부하고
            // 이 test 가 읽음 해제 축이 아니라 다른 것을 재게 된다.
            statement.setString(4, defaultBidKindFor(kind).name)
            statement.setString(5, kind.name)
            statement.setString(6, detail)
            statement.setTimestamp(7, Timestamp.from(occurredAt))
            statement.setBoolean(8, resetsOnNewerFact)
            statement.executeQuery().use { rows -> rows.next() }
        }
    }

    /** 읽음 처리도 출하 문장으로 — 손 UPDATE 를 두면 이 test 가 production 과 다른 열을 만진다. */
    private fun markReadDirectly(
        account: Long,
        kind: NotificationKind,
        noticeNumber: String,
    ) = dataSource().connection.use { connection ->
        connection.prepareStatement(NotificationInboxSql.MARK_ITEM_READ).use { statement ->
            statement.setTimestamp(1, Timestamp.from(READ_AT))
            statement.setLong(2, account)
            statement.setString(3, noticeNumber)
            statement.setString(4, "000")
            statement.setString(5, defaultBidKindFor(kind).name)
            statement.setString(6, kind.name)
            statement.executeUpdate() shouldBe 1
        }
    }
}
