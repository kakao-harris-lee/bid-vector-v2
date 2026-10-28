package bidvector.adapters.notificationinbox

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.workflow.notificationinbox.NotificationDetail
import bidvector.workflow.notificationinbox.NotificationKind
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.sql.Connection

/**
 * M7/7C③ — **PII 부재를 카탈로그 성질로 잰다**(계약 (1) 「구성」 · 설계 검토 우회 ④).
 *
 * 왜 이 모양인가: 「알림 본문에 PII 를 넣지 않는다」를 문장이나 문자열 스캔으로 지키면 스타일
 * 하나로 열린다(어휘를 바꿔 적으면 지나간다). 여기서는 **표의 열 전수**가 ⓐ 닫힌 어휘 CHECK 에
 * 갇혔거나 ⓑ 공고 키 두 열이거나 ⓒ [STRUCTURAL_COLUMNS] 가 **(이름, 자료형) 쌍으로** 면제한
 * 열이라는 **등식**을 건다 — 열을 더하면 등식이 깨지고, 우회하려면 이 게이트 자체를 바꿔야 한다.
 *
 * **r1 검토(verifier F3)가 이 술어를 두 축에서 뚫었다 — 그 둘을 구조로 닫았다.**
 * ① 모집단이 `data_type = 'text'` 뿐이어서 `code TEXT` 는 걸렸어도 `memo VARCHAR(200)` 는
 * 모집단에 **들지도 않았다**. 이제 모집단은 **자료형 무관 전수**이고, 통과하는 열을
 * [STRUCTURAL_COLUMNS] 에 **열거**한다 — 등재되지 않은 열은 자료형이 무엇이든 「판정 대상」이다.
 * ② 어휘 판정이 제약 정의의 **부분 문자열**이어서 `detail_code = ANY` 가 이름이 겹치는 `code`
 * 열을 「어휘가 닫힌 열」로 셌다(`kind = ANY` 는 `ind` 를). 이제 `pg_constraint.conkey` 로
 * **제약이 실제로 가두는 열 번호**를 읽는다 — 이름이 겹쳐도 다른 열은 가둬지지 않는다.
 *
 * 함께 어휘 등식 둘을 잰다(우회 ⑨): Kotlin enum 과 DB CHECK 가 **같은 집합**이어야 한다.
 * 한쪽만 늘리면 저장할 수 없는 어휘나 복원할 수 없는 행이 생기고, 둘 다 조용하다.
 */
internal class NotificationInboxPrivacyTest : PersistenceTestSupport() {
    /**
     * **이 표에 자유 문자열 열이 없다.** 공고 키 둘은 PII 가 아니다 — 공개 조달 식별자다.
     * 그 둘의 제약은 **같지 않다**(r2 verifier L1 이 앞 판 문면을 바로잡았다): `notice_round` 는
     * 세 자리 형태 정규식이고 `notice_number` 는 **비어 있지 않음뿐**이다. 그래서 이름 면제의
     * 근거를 아래 「공고 키 두 열의 CHECK 정의가 고정돼 있다」가 따로 못박는다. 나머지 열은
     * 전부 `IN (…)` 어휘에 갇혔거나 [STRUCTURAL_COLUMNS] 가 이름까지 들어 면제한 시각·계정
     * 열이다 — 즉 **PII 가 들어올 칸이 이 표에 없다.**
     *
     * **「칸이 없다」를 자료형으로만 말할 수는 없다**(r3 verifier M1): 사업자번호와 연락처는
     * 정수에도 들어가므로, 면제가 자료형 집합이면 `BIGINT`·`NUMERIC` 열이 조용히 지난다. 그래서
     * 면제는 쌍이고, 그 축을 아래 「숫자 열도 등재된 쌍만 지난다」가 DB 없이 따로 잠근다.
     */
    @Test
    fun `알림함의 열 전수가 닫힌 어휘이거나 공고 키이거나 등재된 쌍이다 — 자유 문면 열 0`() {
        val columns = columnTypesOf(TABLE)
        columns.keys.shouldNotBeEmpty()

        freeFormColumns(columns, vocabularyClosedColumnsOf(TABLE)).shouldBeEmpty()
    }

    /**
     * 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다. **어느 열이 걸리는지까지 단언한다**:
     * 「비어 있지 않다」만 보면 면제 목록이 이 표 전용이라 다른 표에서는 거의 모든 열이 걸려
     * 통과가 공허해진다.
     */
    @Test
    fun `자유 문자열 열을 가진 기존 표는 같은 술어에 걸린다 — 양성 대조`() {
        // `notice_audit.reason` 은 `<> ''` 뿐이라 어휘가 닫히지 않은 자유 문자열이다.
        val freeForm = freeFormColumns(columnTypesOf("notice_audit"), vocabularyClosedColumnsOf("notice_audit"))

        freeForm shouldContain "reason"
    }

    /**
     * 양성·음성 대조 — **r3 verifier M1 이 뚫은 자료형 축을 술어 로직만으로 고정한다.**
     *
     * 앞 판은 면제가 자료형 집합(`bigint`·`numeric`·…)이어서, 숫자 PII 열을 더해도 위 등식이
     * 초록이었다. 지금은 쌍이므로 ⓐ 등재되지 않은 숫자 열은 걸리고, ⓑ 등재된 열은 지나가고,
     * ⓒ **등재된 열의 자료형이 바뀌면 면제가 사라진다**(이름만 맞혀서 지나가는 길도 닫는다).
     *
     * 어휘 인자는 `emptySet()` 이다 — 이 test 가 재는 축은 어휘가 아니라 쌍 등재이므로, 닫힌
     * 어휘를 끼우면 어느 축이 통과시켰는지 흐려진다(앞 판은 `setOf("kind")` 를 넘겼는데 어느
     * map 에도 `kind` 가 없어 **아무 일도 하지 않았다** — r4 CR LOW-4).
     */
    @Test
    fun `숫자 열도 등재된 쌍만 지난다 — 이름만·자료형만으로는 면제되지 않는다`() {
        freeFormColumns(mapOf("business_registration_no" to "bigint"), emptySet()) shouldBe
            setOf("business_registration_no")
        freeFormColumns(mapOf("contact_phone" to "numeric"), emptySet()) shouldBe setOf("contact_phone")
        freeFormColumns(mapOf("account_id" to "bigint"), emptySet()).shouldBeEmpty()
        freeFormColumns(mapOf("account_id" to "text"), emptySet()) shouldBe setOf("account_id")
    }

    /**
     * 양성 대조 — **r1 이 뚫은 두 꼴을 술어 로직만으로 고정한다**(실 DB 없이). 둘 다 앞 판
     * 술어를 지나갔다: `code` 는 `detail_code = ANY` 의 부분 문자열에 걸려 「닫힌 열」로 셌고,
     * `memo` 는 `varchar` 라 모집단에 들지 않았다. 지금 술어는 둘을 **자유 문면 열로** 센다.
     */
    @Test
    fun `이름이 겹치는 열과 varchar 열은 술어에 걸린다 — 양성 대조`() {
        val simulated =
            mapOf(
                "account_id" to "bigint",
                "kind" to "text",
                "detail_code" to "text",
                "notice_number" to "text",
                "notice_round" to "text",
                "occurred_at" to "timestamp with time zone",
                "code" to "text",
                "memo" to "character varying",
            )

        freeFormColumns(simulated, vocabularyClosed = setOf("kind", "detail_code")) shouldBe
            setOf("code", "memo")
    }

    /**
     * 양성 대조 — **r2 가 뚫은 두 꼴을 술어 로직만으로 고정한다**(verifier M1 · CR MEDIUM-1).
     * 두 문자열은 PostgreSQL 16.4 가 `pg_get_constraintdef` 로 돌려준 **그대로**다(직접 렌더링해
     * 확인). 앞 판 술어는 `= ANY (ARRAY[` **포함**만 봐서 둘을 「닫힘」으로 셌다.
     *
     * ⓑ 의 `"left"` 가 큰따옴표인 것은 PG 의 출력이 그렇기 때문이다 — `left` 는 예약어라
     * deparse 가 따옴표를 씌운다. 앞 판은 이 자리를 손으로 `left(note, 1)` 이라 적어, 거부
     * 결과는 같았지만 「실제 꼴을 잰다」는 주장이 ⓑ 에서만 성립하지 않았다(r3 CR LOW-1).
     */
    @Test
    fun `OR 꼬리와 함수로 감싼 어휘 CHECK 는 닫힘으로 세지 않는다 — 양성 대조`() {
        // ⓐ 닫힌 절 ∨ 열린 절 — 집합이 열린다.
        """CHECK (((memo = ANY (ARRAY['x'::text, 'y'::text])) OR (memo IS NOT NULL)))"""
            .closesVocabularyOf("memo") shouldBe false
        // ⓑ 닫히는 것은 열이 아니라 함수값이다.
        """CHECK (("left"(note, 1) = ANY (ARRAY['a'::text, 'b'::text])))"""
            .closesVocabularyOf("note") shouldBe false
    }

    /**
     * 음성 대조 — **진짜 어휘 CHECK 두 꼴이 여전히 닫힘으로 세어져야 한다.** 이것이 없으면
     * 「모든 정의를 열림으로 센다」는 퇴화가 위 양성 대조를 그대로 통과한다.
     *
     * 두 꼴의 출처를 가른다(둘 다 PG 16.4 실측): 캐스트 없는 쪽은 **`TEXT` 열**의 `IN` 이고
     * 이 표의 `kind`·`detail_code` 가 그 꼴이다. 캐스트 있는 쪽은 **`varchar` 열에 `::text` 를
     * 명시한** `IN` 이다 — 이 표에는 없고, 앞 판은 그 꼴에 `detail_code` 라는 이름을 붙여
     * 「이 표의 detail_code 가 캐스트 꼴로 돌아온다」로 읽힐 수 있었다. 그렇지 않다.
     */
    @Test
    fun `진짜 어휘 CHECK 두 꼴은 닫힘으로 센다 — 음성 대조`() {
        """CHECK ((kind = ANY (ARRAY['NEW_CANDIDATE'::text, 'AWARD_DECIDED'::text])))"""
            .closesVocabularyOf("kind") shouldBe true
        """CHECK (((legacy_code)::text = ANY (ARRAY['AWARDED'::text, 'NOT_AWARDED'::text])))"""
            .closesVocabularyOf("legacy_code") shouldBe true
    }

    /**
     * **공고 키 두 열의 면제를 이름이 아니라 CHECK 정의로 묶는다**(r2 verifier L1).
     *
     * [NOTICE_KEY_COLUMNS] 는 이름만 보므로, 그 열의 제약을 풀어도 PII 등식이 초록이었다 —
     * verifier 가 `notice_round` 의 형태 CHECK 를 `<> ''` 로 바꿔도 통과하는 것을 실측했다.
     * 그래서 면제의 **근거**를 여기서 고정한다: 둘은 공개 조달 식별자이고 임의 문면을 담지
     * 않는다는 주장이 제약으로 서 있어야 한다.
     *
     * 앞 판 KDoc 은 두 열 모두 「형태 CHECK 가 걸려 있다」고 적었는데 **사실이 아니었다** —
     * `notice_round` 만 세 자리 정규식이고 `notice_number` 는 비어 있지 않음뿐이다. 그 차이를
     * 숨기지 않고 각각 단언한다.
     */
    @Test
    fun `공고 키 두 열의 CHECK 정의가 고정돼 있다 — 이름 면제의 근거`() {
        val checks = singleColumnChecksOf(TABLE)

        checks.getValue("notice_round").single() shouldBe
            """CHECK ((notice_round ~ '^[0-9]{3}$'::text))"""
        checks.getValue("notice_number").single() shouldBe
            """CHECK ((notice_number <> ''::text))"""
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

    /** 열 이름 → `information_schema` 자료형. **자료형으로 걸러내지 않는다** — 모집단은 전수다. */
    private fun columnTypesOf(table: String): Map<String, String> =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT column_name, data_type FROM information_schema.columns " +
                        "WHERE table_schema = 'public' AND table_name = ?",
                ).use { statement ->
                    statement.setString(1, table)
                    statement.executeQuery().use { rows ->
                        val columns = mutableMapOf<String, String>()
                        while (rows.next()) columns[rows.getString(1)] = rows.getString(2)
                        columns
                    }
                }
        }

    /**
     * 임의 문면을 담을 수 있는 열 — 즉 **이 게이트가 거부해야 하는 열**. 통과 조건 셋 중
     * 하나도 만족하지 못한 열이다: ⓐ 공고 키 ⓑ 단일 열 어휘 CHECK 에 갇힌 열 ⓒ
     * [STRUCTURAL_COLUMNS] 가 **(이름, 자료형) 쌍으로** 면제한 열.
     *
     * ⓒ 가 자료형 집합이 아니라 쌍인 이유는 그 상수의 KDoc 에 있다 — 자료형만 보면 숫자 PII 가
     * 지나간다.
     *
     * 순수 함수다(DB 를 읽지 않는다) — 그래서 양성 대조가 실 스키마를 바꾸지 않고 술어 자체를
     * 잴 수 있다.
     */
    private fun freeFormColumns(
        columns: Map<String, String>,
        vocabularyClosed: Set<String>,
    ): Set<String> =
        columns
            .filterNot { (name, type) ->
                name in NOTICE_KEY_COLUMNS || name in vocabularyClosed || STRUCTURAL_COLUMNS[name] == type
            }.keys

    /**
     * **단일 열** 어휘 CHECK 가 가두는 열 — `pg_constraint.conkey` 로 제약이 실제로 가두는 열
     * 번호를 읽어 이름으로 되돌린다(이름 부분 문자열 판정을 쓰지 않는 이유는 클래스 KDoc ②).
     * `cardinality(conkey) = 1` 이 짝 CHECK 를 자동으로 제외한다 — 짝 CHECK 는 두 열을 든다.
     *
     * 어휘를 **닫는** 제약만 센다: PostgreSQL 이 `IN (…)` 을 되돌려 주는 `= ANY (ARRAY[…])`
     * 형태여야 하고, `<> ''`·형태 정규식(`~`)처럼 집합을 열어 두는 CHECK 는 들지 않는다.
     * 판정은 포함이 아니라 **정의 전문 일치**다([closesVocabularyOf]).
     */
    private fun vocabularyClosedColumnsOf(table: String): Set<String> =
        singleColumnChecksOf(table)
            .filter { (column, definitions) -> definitions.any { it.closesVocabularyOf(column) } }
            .keys

    /** 열 이름 → 그 열 **하나만** 가두는 CHECK 정의들. */
    private fun singleColumnChecksOf(table: String): Map<String, List<String>> =
        dataSource().connection.use { connection -> connection.readSingleColumnChecks(table) }

    private fun Connection.readSingleColumnChecks(table: String): Map<String, List<String>> =
        prepareStatement(
            "SELECT a.attname AS column_name, pg_get_constraintdef(c.oid) AS def " +
                "FROM pg_constraint c " +
                "JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey) " +
                "WHERE c.contype = 'c' AND c.conrelid = ?::regclass AND cardinality(c.conkey) = 1",
        ).use { statement ->
            statement.setString(1, table)
            statement.executeQuery().use { rows ->
                val byColumn = mutableMapOf<String, MutableList<String>>()
                while (rows.next()) {
                    byColumn.getOrPut(rows.getString("column_name")) { mutableListOf() } += rows.getString("def")
                }
                byColumn
            }
        }

    /**
     * 이 제약 정의가 [column] 의 값 집합을 **닫는가** — **정의 전문을 한 형태에 못박는다.**
     *
     * r2 검토(verifier M1 · CR MEDIUM-1)가 앞 판 `contains("= ANY (ARRAY[")` 를 두 꼴로 뚫었다.
     * ⓐ `CHECK ((memo = ANY (ARRAY['x'::text, 'y'::text])) OR memo IS NOT NULL)` — 닫힌 절을
     * **열린 절과 논리합**하면 집합이 열리는데 그 문자열은 그대로 남는다. ⓑ
     * `CHECK ((left(note, 1) = ANY (ARRAY['a'::text, 'b'::text])))` — 닫히는 것이 **열이 아니라
     * 함수값**이고 열 자체는 임의 문면을 담는다. 둘 다 `conkey` 가 그 열 하나라 열 귀속 축으로는
     * 걸러지지 않는다 — 그 축은 r1 에서 닫았고 **이 축은 그때 열려 있었다.**
     *
     * 그래서 포함이 아니라 **일치**로 본다: 좌변이 그 열(또는 그 열의 `text` 캐스트) **자체**이고,
     * 우변이 문자열 리터럴 배열이며, **그 밖의 절이 하나도 없는** 정의만 닫혔다고 센다. `OR`·
     * `NOT`·함수 감싸기·추가 술어가 섞이면 앵커가 어긋나 **기본값(열림)** 으로 떨어진다.
     *
     * **닫혀 있는데도 열림으로 떨어지는 꼴 셋 — 의도된 거부다**(r3 CR MEDIUM-1, PostgreSQL 16.4
     * 에서 직접 렌더링해 확인). 앵커를 넓히지 않는 쪽을 골랐다: 넓히는 만큼 양성 대조를 같은 수로
     * 더해야 하고, 그러지 않으면 「무엇이 열렸는지」를 다시 잃는다. 이 세 꼴로 열을 더하면 등식이
     * 붉어지는데 그 열은 **사실 닫혀 있다** — 그때 할 일은 게이트를 넓히는 것이 아니라 그 열을
     * 아래 ⓐ~ⓒ 중 어느 꼴로 쓸지 정하고, 넓힌다면 대조를 함께 세우는 것이다.
     *
     * ⓐ **길이 제한 `varchar` 의 `IN`** — `VARCHAR(20) CHECK (c IN ('a','b'))` 는
     *   `CHECK (((c)::text = ANY ((ARRAY['a'::character varying, 'b'::character varying])::text[])))`
     *   로 돌아온다. 배열 전체에 `::text[]` 캐스트가 붙어 리터럴 꼴이 다르다.
     * ⓑ **nullable 어휘** — `TEXT CHECK (c IS NULL OR c IN ('a','b'))` 는
     *   `CHECK (((c IS NULL) OR (c = ANY (ARRAY['a'::text, 'b'::text]))))` 다. **이것이 이 저장소의
     *   지배적 관례**이고(`CleanMigrationCheckTest` 가 축어로 고정한 `business_division`·
     *   `source_field` 가 그 꼴이다), 그래서 셋 중 다음 사람이 가장 밟기 쉬운 자리다. `NULL` 은
     *   값이 아니므로 집합은 실제로 닫혀 있다.
     * ⓒ **원소 하나** — `TEXT CHECK (c IN ('a'))` 는 PG 가 `CHECK ((c = 'a'::text))` 로 **접는다**.
     *   `ANY (ARRAY[…])` 가 아예 나오지 않는다(r2 verifier X2 가 이 자리에서 「우연히 RED」를
     *   만들었다 — 그래서 실 스키마 변이는 값을 **둘 이상**으로 둔다).
     */
    private fun String.closesVocabularyOf(column: String): Boolean {
        val col = Regex.escape(column)
        val literal = "'[^']*'::text"
        return Regex(
            "^CHECK \\(\\((?:$col|\\($col\\)::text) = ANY \\(ARRAY\\[$literal(?:, $literal)*\\]\\)\\)\\)$",
        ).matches(this)
    }

    /**
     * 열 하나를 가두는 `CHECK (… IN (…))` 의 어휘 — 상수 목록을 제약 정의에서 읽는다.
     * 제약을 고르는 축은 **`conkey` 가 그 열 하나인 것**이다(이름 부분 문자열이 아니다).
     */
    private fun checkVocabularyOf(
        table: String,
        column: String,
    ): List<String> {
        val vocabulary =
            singleColumnChecksOf(table)
                .getValue(column)
                .single { it.closesVocabularyOf(column) }
        return VOCABULARY_LITERAL.findAll(vocabulary).map { it.groupValues[1] }.toList()
    }

    private companion object {
        const val TABLE = "notification_inbox"
        val NOTICE_KEY_COLUMNS = setOf("notice_number", "notice_round")
        val DETAIL_BEARING_KINDS =
            setOf(NotificationKind.OPENING_COMPLETED, NotificationKind.AWARD_DECIDED)
        val VOCABULARY_LITERAL = Regex("'([^']*)'")

        /**
         * 임의 문면을 담지 않는다고 **이 표에서 판정된** 열 — 통과 축 ⓒ. 키가 **(열 이름,
         * 자료형) 쌍**이고 기본은 거부다: 새 열은 자료형이 무엇이든 여기 등재되기 전까지
         * 「판정 대상」이며, 등재된 열의 자료형이 바뀌면 그 면제도 함께 사라진다.
         *
         * **앞 판은 자료형만 보았고 그래서 숫자 PII 를 통과시켰다**(r3 verifier M1 실측):
         * `bigint`·`numeric`·`integer` 가 면제 자료형이라 `business_registration_no BIGINT` 나
         * `contact_phone NUMERIC` 을 더해도 아래 등식이 초록이었다. 사업자번호(열 자리 수)와
         * 연락처는 **정수 하나에 들어간다** — 즉 「PII 가 들어올 칸이 없다」가 문자열 칸에만
         * 참이었다. 쌍으로 묶으면 그 둘이 이름으로 걸린다.
         *
         * 쌍이 드는 다섯은 **이 표가 실제로 가진 비문자열 열 전수**다: 계정 축 하나와 시각 넷.
         *
         * **이 쌍이 고정하지 않는 축 하나** — 열의 *제약*이다(r4 verifier 참고). `account_id` 의
         * FK 를 떼면 그 열에 임의 정수가 들어갈 수 있는데 이 등식은 초록이고, 형제 표에 자유
         * 문면 열을 더해도 이 test 는 표 하나만 보므로 모른다. 그 축은 같은 `check` 안의
         * `CleanMigration*` 스냅샷 래칫(FK·열·표 목록·권한)이 맡는다 — verifier 가 두 경우를
         * 모두 넣어 래칫이 RED 인 것을 실측했다. 여기서 그 축을 다시 재지 않는 이유다.
         */
        val STRUCTURAL_COLUMNS =
            mapOf(
                "account_id" to "bigint",
                "occurred_at" to "timestamp with time zone",
                "created_at" to "timestamp with time zone",
                "updated_at" to "timestamp with time zone",
                "read_at" to "timestamp with time zone",
            )
    }
}
