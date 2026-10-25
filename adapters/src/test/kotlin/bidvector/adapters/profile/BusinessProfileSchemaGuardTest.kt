package bidvector.adapters.profile

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.qualification.LicenseName
import bidvector.workflow.access.AccountId
import bidvector.workflow.profile.BusinessDetails
import bidvector.workflow.profile.ConstructionCapacity
import bidvector.workflow.profile.HeadquartersRegion
import bidvector.workflow.profile.NaraReadiness
import bidvector.workflow.profile.NaraRegistrationField
import bidvector.workflow.profile.ProfileInputPolicy
import bidvector.workflow.profile.TradeName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/**
 * V22 의 **표 층 방어**를 앱 역할(`bidvector_app`)로 잰다 — 관리자 연결로 재면 GRANT 가 무엇을
 * 막는지 보이지 않는다.
 *
 * 셋이다. (1) 어댑터의 모든 쓰기가 앱 역할 권한만으로 성립한다(열 단위 UPDATE 로 upsert 가 되는가).
 * (2) 열 단위 UPDATE 집합이 정확히 V22 가 준 것이다 — **소유자 열(`account_id`)이 그 밖**이다.
 * (3) CHECK 가 [ProfileInputPolicy] 와 같은 경계에서 갈린다(앱 타입을 우회한 직접 SQL 로).
 */
class BusinessProfileSchemaGuardTest : PersistenceTestSupport() {
    private val now = Instant.parse("2026-10-22T06:00:00Z")

    /**
     * 앱 역할로 전환한 커넥션을 내는 `DataSource` — 어댑터를 요청 경로의 권한으로 돌린다.
     *
     * 두 인자 `getConnection` 은 **던진다**(r1 CR L-12). 위임하면 `SET ROLE` 없이 관리자 DataSource 로
     * 가서, 그 형태를 부르는 호출자가 생기는 날 **관리자 권한을 재면서 조용히 초록**이 된다.
     */
    private val appRole: DataSource =
        object : DataSource by dataSource() {
            override fun getConnection(): Connection =
                dataSource().connection.apply { createStatement().use { it.execute("SET ROLE bidvector_app") } }

            override fun getConnection(
                username: String?,
                password: String?,
            ): Connection = error("앱 역할 DataSource 는 두 인자 getConnection 을 내지 않는다(권한을 재는 자리다)")
        }

    private var accounts = 0

    /** 계정 하나 — 사업 정보는 계정당 한 행이라 경계 test 가 시도마다 새 계정을 쓴다. */
    private fun account(): AccountId =
        dataSource().connection.use { connection ->
            accounts += 1
            connection
                .prepareStatement(
                    "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, " +
                        "failed_attempts) VALUES (?, 'x', ?, ?, 0) RETURNING id",
                ).use { statement ->
                    statement.setString(1, "guard-$accounts")
                    statement.setTimestamp(2, Timestamp.from(now))
                    statement.setTimestamp(3, Timestamp.from(now))
                    statement.executeQuery().use { rs ->
                        rs.next()
                        AccountId(rs.getLong(1))
                    }
                }
        }

    private fun asApp(
        sql: String,
        vararg values: Any?,
    ) {
        appConnection().use { connection ->
            connection.autoCommit = true
            connection.prepareStatement(sql).use { statement ->
                values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
        }
    }

    @Test
    fun `앱 역할 권한만으로 어댑터의 모든 쓰기가 성립한다(upsert 의 갱신 · 삭제 포함)`() {
        val owner = account()
        val store = JdbcBusinessProfileStore(appRole)
        val details =
            BusinessDetails(
                TradeName("합성상호"),
                HeadquartersRegion.SEOUL,
                NaraReadiness.Registered(setOf(NaraRegistrationField.GOODS), LocalDate.of(2027, 1, 1)),
            )
        val capacity = ConstructionCapacity(LicenseName("합성토목"), 5L, 2026)

        store.replaceDetails(owner, BusinessDetails.EMPTY, now)
        store.replaceDetails(owner, details, now)
        store.upsertCapacity(owner, capacity, now)
        store.upsertCapacity(owner, capacity.copy(amountWon = 6L), now)

        store.details(owner) shouldBe details
        store.capacities(owner) shouldBe listOf(capacity.copy(amountWon = 6L))
        store.removeCapacity(owner, capacity.licenseName) shouldBe true
    }

    @Test
    fun `열 단위 UPDATE 집합이 V22 그대로다 — 소유자 열은 밖이다`() {
        val granted =
            dataSource().connection.use { connection ->
                connection
                    .prepareStatement(
                        "SELECT table_name, column_name FROM information_schema.column_privileges " +
                            "WHERE grantee = 'bidvector_app' AND privilege_type = 'UPDATE' " +
                            "AND table_name IN ('account_business_profile', 'account_construction_capacity')",
                    ).use { statement ->
                        statement.executeQuery().use { rs ->
                            buildSet { while (rs.next()) add("${rs.getString(1)}.${rs.getString(2)}") }
                        }
                    }
            }

        granted shouldBe
            setOf(
                "account_business_profile.trade_name",
                "account_business_profile.headquarters_region",
                "account_business_profile.nara_registration",
                "account_business_profile.nara_registered_fields",
                "account_business_profile.business_certificate_expires_on",
                "account_business_profile.updated_at",
                "account_construction_capacity.amount_won",
                "account_construction_capacity.assessment_year",
                "account_construction_capacity.updated_at",
            )
    }

    @Test
    fun `앱 역할은 소유자를 바꾸거나 사업 정보 행을 지울 수 없다`() {
        val owner = account()
        JdbcBusinessProfileStore(dataSource()).replaceDetails(owner, BusinessDetails.EMPTY, now)

        shouldThrow<SQLException> { asApp("UPDATE account_business_profile SET account_id = account_id") }
        shouldThrow<SQLException> { asApp("UPDATE account_construction_capacity SET account_id = account_id") }
        shouldThrow<SQLException> { asApp("DELETE FROM account_business_profile") }
    }

    /**
     * **공백뿐인 값도 표가 거부한다**(r1 CR M-3 · F4). 앱 타입은 `isNotBlank` 로 막지만, 빈 문자열만
     * 막는 CHECK 는 `'  '` 를 받아들였고 **그 행을 읽으면 앱이 던진다** — 앱으로 고칠 수도 없는 읽기
     * 실패였다(앱은 공백 상호를 쓸 수도 없다). 표 층도 같은 쪽으로 좁힌다.
     */
    @Test
    fun `CHECK — 상호는 빈 문자열과 공백뿐인 값을 거부하고 상한이 정책과 같다(코드포인트)`() {
        val owner = account()
        val insert =
            "INSERT INTO account_business_profile (account_id, trade_name, nara_registration, " +
                "nara_registered_fields, updated_at) VALUES (?, ?, 'UNANSWERED', '{}', now())"
        val astral = "𠮷"

        asApp(insert, owner.value, astral.repeat(ProfileInputPolicy.TRADE_NAME_MAX_CODE_POINTS))
        shouldThrow<SQLException> { asApp(insert, account().value, "") }
        shouldThrow<SQLException> { asApp(insert, account().value, "  ") }
        shouldThrow<SQLException> { asApp(insert, account().value, "\t\n") }
        shouldThrow<SQLException> {
            asApp(insert, account().value, "가".repeat(ProfileInputPolicy.TRADE_NAME_MAX_CODE_POINTS + 1))
        }
    }

    @Test
    fun `CHECK — 등록이 아니면 분야 · 만료일이 실릴 수 없고 어휘 밖 값은 거부된다`() {
        val owner = account()
        val insert =
            "INSERT INTO account_business_profile (account_id, headquarters_region, nara_registration, " +
                "nara_registered_fields, business_certificate_expires_on, updated_at) " +
                "VALUES (?, ?, ?, ?::text[], ?, now())"

        shouldThrow<SQLException> { asApp(insert, owner.value, null, "NOT_REGISTERED", "{GOODS}", null) }
        shouldThrow<SQLException> {
            asApp(insert, owner.value, null, "UNANSWERED", "{}", java.sql.Date.valueOf("2027-01-01"))
        }
        shouldThrow<SQLException> { asApp(insert, owner.value, "MARS", "UNANSWERED", "{}", null) }
        shouldThrow<SQLException> { asApp(insert, owner.value, null, "MAYBE", "{}", null) }
        shouldThrow<SQLException> { asApp(insert, owner.value, null, "REGISTERED", "{FOREIGN}", null) }
    }

    /**
     * **중복 원소도 표가 거부한다**(r1 CR L-8 · F4). 앱은 늘 정렬된 distinct 집합을 쓰고 읽기에서
     * `toSet()` 이 접으므로 중복은 **조용한 정규화**였다 — 직접 SQL 로만 생기는 틈이지만, 「표 층이
     * 같은 불변식을 한 겹 더 깐다」는 주장은 그 틈이 있으면 참이 아니다.
     *
     * **어휘 전수를 돈다**(D-7C4-3 팀장 요청). V22 의 그 CHECK 는 어휘를 두 자리에 든다 —
     * 「어휘 안인가」(부분집합)와 「값마다 한 번뿐인가」(값별 절). 한 값을 **한쪽에서만** 지우는 변이를
     * 잡으려면 중복 거부를 값마다 재야 한다: 값별 절이 하나 빠지면 그 값의 중복이 통과한다.
     */
    @Test
    fun `CHECK — 등록분야 배열은 어휘 전수에서 중복 원소를 거부한다`() {
        val insert =
            "INSERT INTO account_business_profile (account_id, nara_registration, " +
                "nara_registered_fields, updated_at) VALUES (?, 'REGISTERED', ?::text[], now())"

        asApp(insert, account().value, "{GOODS,SERVICE}")
        NaraRegistrationField.entries.forEach { field ->
            shouldThrow<SQLException> { asApp(insert, account().value, "{${field.name},${field.name}}") }
        }
        shouldThrow<SQLException> { asApp(insert, account().value, "{SERVICE,CONSTRUCTION,SERVICE}") }
    }

    /** 어휘 둘의 전수가 표에 들어간다 — 열거와 CHECK 가 갈리면 여기서 붉다. */
    @Test
    fun `CHECK — 소재지 17 · 등록분야 셋 전부를 표가 받는다`() {
        val store = JdbcBusinessProfileStore(appRole)
        val owner = account()
        HeadquartersRegion.entries.forEach { region ->
            val details =
                BusinessDetails(null, region, NaraReadiness.Registered(NaraRegistrationField.entries.toSet(), null))
            store.replaceDetails(owner, details, now)
            store.details(owner) shouldBe details
        }
    }

    /**
     * **면허명의 공백뿐인 값은 상호보다 나쁘다**(r2 CR R2-1 · F6). 면허명은 **PK 의 일부**라 `' '` 와
     * `'  '` 가 서로 다른 행이 되고, 읽기 실패가 필드 하나가 아니라 **그 계정의 시평액 목록 전체**를
     * 막는다 — `capacities()` 가 행을 돌다 던지면 목록이 통째로 오지 않는다. 그래서 상호와 같은
     * 술어(`[^[:space:]]`)로 좁히고 그 경계를 여기서 잰다.
     */
    @Test
    fun `CHECK — 시평액 금액 · 연도 · 면허명 경계가 정책과 같다(공백뿐인 면허명 포함)`() {
        val owner = account()
        val insert =
            "INSERT INTO account_construction_capacity (account_id, license_name, amount_won, assessment_year, " +
                "updated_at) VALUES (?, ?, ?, ?, now())"
        val max = ProfileInputPolicy.CAPACITY_AMOUNT_MAX_WON

        asApp(insert, owner.value, "합성상한", max, ProfileInputPolicy.ASSESSMENT_YEAR_MAX.toShort())
        asApp(insert, owner.value, "합성하한", 1L, ProfileInputPolicy.ASSESSMENT_YEAR_MIN.toShort())
        shouldThrow<SQLException> { asApp(insert, owner.value, "합성영", 0L, 2026.toShort()) }
        shouldThrow<SQLException> { asApp(insert, owner.value, "합성초과", max + 1, 2026.toShort()) }
        shouldThrow<SQLException> {
            asApp(insert, owner.value, "합성연도", 1L, (ProfileInputPolicy.ASSESSMENT_YEAR_MIN - 1).toShort())
        }
        // 연도 상한의 **밖**도 잰다(r1 CR L-7 — 앞 판은 `MAX` 수락만 재서 상한이 미측정이었다).
        shouldThrow<SQLException> {
            asApp(insert, owner.value, "합성연도상한", 1L, (ProfileInputPolicy.ASSESSMENT_YEAR_MAX + 1).toShort())
        }
        shouldThrow<SQLException> { asApp(insert, owner.value, "", 1L, 2026.toShort()) }
        shouldThrow<SQLException> { asApp(insert, owner.value, " ", 1L, 2026.toShort()) }
        shouldThrow<SQLException> { asApp(insert, owner.value, "  ", 1L, 2026.toShort()) }
        shouldThrow<SQLException> { asApp(insert, owner.value, "\t\n", 1L, 2026.toShort()) }
        shouldThrow<SQLException> {
            val tooLong = "가".repeat(ProfileInputPolicy.LICENSE_NAME_MAX_CODE_POINTS + 1)
            asApp(insert, owner.value, tooLong, 1L, 2026.toShort())
        }
    }
}
