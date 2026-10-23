package bidvector.adapters.notificationinbox

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.notificationinbox.NotificationDetail
import bidvector.workflow.notificationinbox.NotificationKind
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.sql.Connection

/**
 * M7/7C③ — **PII 부재를 카탈로그 성질로 잰다**(계약 (1) 「구성」 · 설계 검토 우회 ④).
 *
 * 왜 이 모양인가: 「알림 본문에 PII 를 넣지 않는다」를 문장이나 문자열 스캔으로 지키면 스타일
 * 하나로 열린다(어휘를 바꿔 적으면 지나간다). 여기서는 **표의 TEXT 열 전수**가 ⓐ 닫힌 어휘
 * CHECK 에 갇혔거나 ⓑ 공고 키 두 열이라는 **등식**을 건다 — 자유 문자열 열을 더하면 등식이
 * 깨지고, 우회하려면 이 게이트 자체를 바꿔야 한다.
 *
 * 함께 어휘 등식 둘을 잰다(우회 ⑨): Kotlin enum 과 DB CHECK 가 **같은 집합**이어야 한다.
 * 한쪽만 늘리면 저장할 수 없는 어휘나 복원할 수 없는 행이 생기고, 둘 다 조용하다.
 */
internal class NotificationInboxPrivacyTest : PersistenceTestSupport() {
    /**
     * **이 표에 자유 문자열 열이 없다.** 공고 키 둘은 PII 가 아니다(공개 조달 식별자이고 형태
     * CHECK 가 걸려 있다). 나머지 TEXT 열은 전부 `IN (…)` 어휘에 갇혀 있어 임의 문면을 담을
     * 수 없다 — 즉 사업자번호·담당자 이름·연락처가 들어올 **칸이 없다**.
     */
    @Test
    fun `알림함의 TEXT 열은 전부 닫힌 어휘이거나 공고 키다 — 자유 문자열 열 0`() {
        val textColumns = textColumnsOf(TABLE)
        textColumns.shouldNotBeEmpty()

        val constrained = columnsWithVocabularyCheck(TABLE)
        val freeText = textColumns - constrained - NOTICE_KEY_COLUMNS

        freeText.shouldBeEmpty()
    }

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다(자유 TEXT 열이 있는 표는 걸린다). */
    @Test
    fun `자유 문자열 열을 가진 기존 표는 같은 술어에 걸린다 — 양성 대조`() {
        // `notice_audit.reason` 은 `<> ''` 뿐이라 어휘가 닫히지 않은 자유 문자열이다.
        val freeText = textColumnsOf("notice_audit") - columnsWithVocabularyCheck("notice_audit")

        freeText.shouldNotBeEmpty()
    }

    @Test
    fun `종류 어휘는 Kotlin enum 전수와 같다`() {
        checkVocabularyOf(TABLE, "kind") shouldContainExactlyInAnyOrder
            NotificationKind.entries.map { it.name }
    }

    @Test
    fun `detail 어휘는 Kotlin enum 전수와 같다`() {
        checkVocabularyOf(TABLE, "detail_code") shouldContainExactlyInAnyOrder
            NotificationDetail.entries.map { it.name }
    }

    /**
     * **종류 ↔ detail 짝이 표 층에서도 닫힌다**(심층 방어 · 우회 ⑩). Kotlin 쪽은 sealed 구성이라
     * 불법 쌍이 표현되지 않지만, 어댑터의 codec 이 어긋나게 쓰면 잡는 것은 이 CHECK 다.
     *
     * detail 을 갖는 종류 둘은 **반드시** 자기 어휘를 싣고, 나머지 여섯은 **반드시** NULL 이다.
     */
    @Test
    fun `detail 을 갖지 않는 종류에 detail 을 실으면 DB 가 거부한다`() {
        val account = insertFixtureAccount()
        val forbidden =
            NotificationKind.entries.filterNot { it in DETAIL_BEARING_KINDS }

        forbidden.forEach { kind ->
            insertFails(account, kind.name, NotificationDetail.AWARDED.name) shouldBe true
        }
    }

    @Test
    fun `detail 을 갖는 종류에 detail 이 없으면 DB 가 거부한다`() {
        val account = insertFixtureAccount()

        DETAIL_BEARING_KINDS.forEach { kind ->
            insertFails(account, kind.name, null) shouldBe true
        }
    }

    @Test
    fun `개찰 완료에 낙찰 어휘를 실으면 DB 가 거부한다 — 어휘가 맞아도 짝이 틀리면 막힌다`() {
        val account = insertFixtureAccount()

        insertFails(
            account,
            NotificationKind.OPENING_COMPLETED.name,
            NotificationDetail.AWARDED.name,
        ) shouldBe true
        insertFails(
            account,
            NotificationKind.AWARD_DECIDED.name,
            NotificationDetail.PROVISIONAL_FIRST.name,
        ) shouldBe true
    }

    /** 음성 대조 — 올바른 짝은 실제로 들어간다(위 셋이 전부를 막는 술어가 아님을 보인다). */
    @Test
    fun `올바른 짝은 들어간다 — 음성 대조`() {
        val account = insertFixtureAccount()

        insertFails(account, NotificationKind.NOTICE_REVISED.name, null) shouldBe false
        insertFails(
            account,
            NotificationKind.OPENING_COMPLETED.name,
            NotificationDetail.PROVISIONAL_FIRST.name,
        ) shouldBe false
        insertFails(
            account,
            NotificationKind.AWARD_DECIDED.name,
            NotificationDetail.NOT_AWARDED.name,
        ) shouldBe false
    }

    @Test
    fun `읽은 시각이 적재보다 앞서면 DB 가 거부한다`() {
        val account = insertFixtureAccount()

        val failed =
            runCatching {
                dataSource().connection.use { connection ->
                    connection
                        .prepareStatement(
                            "INSERT INTO notification_inbox (account_id, kind, notice_number, " +
                                " notice_round, occurred_at, created_at, updated_at, read_at) " +
                                "VALUES (?, 'NOTICE_REVISED', 'N-BACKDATE', '000', now(), now(), now(), " +
                                " now() - interval '1 day')",
                        ).use { statement ->
                            statement.setLong(1, account)
                            statement.executeUpdate()
                        }
                }
            }.isFailure

        failed shouldBe true
    }

    private fun insertFixtureAccount(): Long =
        dataSource().connection.use { connection ->
            connection
                .createStatement()
                .use { statement ->
                    statement
                        .executeQuery(
                            "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, " +
                                " failed_attempts) VALUES ('privacy-probe', 'h', now(), now(), 0) RETURNING id",
                        ).use { rows ->
                            rows.next()
                            rows.getLong("id")
                        }
                }
        }

    /** 삽입이 거부됐는가 — 거부는 성공으로도 실패로도 읽히지 않게 **boolean 으로** 돌려준다. */
    private fun insertFails(
        account: Long,
        kind: String,
        detail: String?,
    ): Boolean =
        runCatching {
            dataSource().connection.use { connection ->
                connection
                    .prepareStatement(
                        "INSERT INTO notification_inbox (account_id, kind, notice_number, notice_round, " +
                            " detail_code, occurred_at, updated_at) " +
                            "VALUES (?, ?, ?, '000', ?, now(), now())",
                    ).use { statement ->
                        statement.setLong(1, account)
                        statement.setString(2, kind)
                        // 키가 겹치지 않게 공고 번호를 짝마다 다르게 둔다(PK 위반을 짝 CHECK 위반으로
                        // 착각하지 않도록 — 그 둘을 가르지 않으면 이 test 가 공허해진다).
                        statement.setString(3, "N-$kind-$detail")
                        statement.setString(4, detail)
                        statement.executeUpdate()
                    }
            }
        }.isFailure

    private fun textColumnsOf(table: String): Set<String> =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_schema = 'public' AND table_name = ? AND data_type = 'text'",
                ).use { statement ->
                    statement.setString(1, table)
                    statement.executeQuery().use { rows ->
                        val columns = mutableSetOf<String>()
                        while (rows.next()) columns += rows.getString(1)
                        columns
                    }
                }
        }

    /**
     * `CHECK (… IN (…))` 로 **어휘가 닫힌** 열. PostgreSQL 은 `IN` 목록을 `= ANY (ARRAY[…])` 로
     * 적으므로 그 형태를 찾는다. `<> ''` 처럼 비어 있지 않음만 요구하는 CHECK 는 어휘를 닫지
     * 않으므로 여기 들지 않는다 — 그것이 이 술어의 요점이다.
     */
    private fun columnsWithVocabularyCheck(table: String): Set<String> {
        val definitions = checkDefinitionsOf(table)
        return textColumnsOf(table)
            .filter { column -> definitions.any { it.mentionsVocabularyOf(column) } }
            .toSet()
    }

    private fun checkDefinitionsOf(table: String): List<String> =
        dataSource().connection.use { connection -> connection.readCheckDefinitions(table) }

    private fun Connection.readCheckDefinitions(table: String): List<String> =
        prepareStatement(
            "SELECT pg_get_constraintdef(oid) AS def FROM pg_constraint " +
                "WHERE contype = 'c' AND conrelid = ?::regclass",
        ).use { statement ->
            statement.setString(1, table)
            statement.executeQuery().use { rows ->
                val definitions = mutableListOf<String>()
                while (rows.next()) definitions += rows.getString("def")
                definitions
            }
        }

    /** 열 하나를 가두는 `CHECK (… IN (…))` 의 어휘 — 상수 목록을 제약 정의에서 읽는다. */
    private fun checkVocabularyOf(
        table: String,
        column: String,
    ): List<String> {
        val vocabulary =
            checkDefinitionsOf(table).single { definition ->
                // 짝 CHECK 도 이 열을 언급하므로 **단일 열 어휘 제약만** 고른다(짝 쪽에는 ` OR ` 가 있다).
                definition.mentionsVocabularyOf(column) && !definition.contains(" OR ")
            }
        return VOCABULARY_LITERAL.findAll(vocabulary).map { it.groupValues[1] }.toList()
    }

    /**
     * 이 제약 정의가 [column] 의 **어휘를 닫는가**. PostgreSQL 은 `IN (…)` 을 `= ANY (ARRAY[…])`
     * 로 되돌려 주고, **열의 자료형에 따라 좌변 표기가 갈린다** — 이미 `text` 인 열은 `kind = ANY`
     * 로, 캐스트가 필요한 열은 `(kind)::text = ANY` 로 나온다. 한 쪽만 보면 제약이 있는데도
     * 「어휘가 닫히지 않았다」로 읽혀 이 class 의 등식이 **거짓으로 통과**한다(기존 스키마 래칫
     * 의 같은 술어가 두 형태를 함께 보는 이유다 — 그 선례를 그대로 쓴다).
     *
     * `<> ''` 처럼 비어 있지 않음만 요구하는 CHECK 는 어휘를 닫지 않으므로 여기 들지 않는다 —
     * 그것이 이 술어의 요점이다.
     */
    private fun String.mentionsVocabularyOf(column: String): Boolean =
        contains("$column = ANY") || contains("($column)::text = ANY")

    private companion object {
        const val TABLE = "notification_inbox"
        val NOTICE_KEY_COLUMNS = setOf("notice_number", "notice_round")
        val DETAIL_BEARING_KINDS =
            setOf(NotificationKind.OPENING_COMPLETED, NotificationKind.AWARD_DECIDED)
        val VOCABULARY_LITERAL = Regex("'([^']*)'")
    }
}
