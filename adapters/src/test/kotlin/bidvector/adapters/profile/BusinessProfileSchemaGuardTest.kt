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

    /** 앱 역할로 전환한 커넥션을 내는 `DataSource` — 어댑터를 요청 경로의 권한으로 돌린다. */
    private val appRole: DataSource =
        object : DataSource by dataSource() {
            override fun getConnection(): Connection =
                dataSource().connection.apply { createStatement().use { it.execute("SET ROLE bidvector_app") } }
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

    @Test
    fun `CHECK — 상호는 빈 문자열을 거부하고 상한이 정책과 같다(코드포인트)`() {
        val owner = account()
        val insert =
            "INSERT INTO account_business_profile (account_id, trade_name, nara_registration, " +
                "nara_registered_fields, updated_at) VALUES (?, ?, 'UNANSWERED', '{}', now())"
        val astral = "𠮷"

        asApp(insert, owner.value, astral.repeat(ProfileInputPolicy.TRADE_NAME_MAX_CODE_POINTS))
        shouldThrow<SQLException> { asApp(insert, account().value, "") }
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

    @Test
    fun `CHECK — 시평액 금액 · 연도 · 면허명 경계가 정책과 같다`() {
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
        shouldThrow<SQLException> { asApp(insert, owner.value, "", 1L, 2026.toShort()) }
        shouldThrow<SQLException> {
            val tooLong = "가".repeat(ProfileInputPolicy.LICENSE_NAME_MAX_CODE_POINTS + 1)
            asApp(insert, owner.value, tooLong, 1L, 2026.toShort())
        }
    }
}
