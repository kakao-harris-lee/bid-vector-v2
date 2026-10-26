package bidvector.adapters.profile

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.qualification.LicenseName
import bidvector.workflow.access.AccountId
import bidvector.workflow.profile.BusinessDetails
import bidvector.workflow.profile.ConstructionCapacity
import bidvector.workflow.profile.HeadquartersRegion
import bidvector.workflow.profile.NaraReadiness
import bidvector.workflow.profile.NaraRegistrationField
import bidvector.workflow.profile.StorageWriteOutcome
import bidvector.workflow.profile.TradeName
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

/**
 * [JdbcBusinessProfileStore] — **실 DB 위의 왕복 · 소유 경계 · 절 독립**(scope test 「프로필 왕복」 ·
 * 「부분 갱신이 다른 필드를 지우지 않음」). 소유 경계는 fake 로 증명되지 않는다: SQL 에서
 * `account_id = ?` 를 빼면 이 test 만 붉어진다.
 *
 * 값은 전부 합성이다(evidence 실값 0).
 */
class JdbcBusinessProfileStoreTest : PersistenceTestSupport() {
    private val now = Instant.parse("2026-10-22T06:00:00Z")
    private val store = JdbcBusinessProfileStore(dataSource())

    private val registered =
        BusinessDetails(
            tradeName = TradeName(" (주)합성상호 "),
            headquartersRegion = HeadquartersRegion.GYEONGNAM,
            naraReadiness =
                NaraReadiness.Registered(
                    setOf(NaraRegistrationField.CONSTRUCTION, NaraRegistrationField.SERVICE),
                    LocalDate.of(2027, 3, 31),
                ),
        )

    private fun account(login: String): AccountId =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, " +
                        "failed_attempts) VALUES (?, 'x', ?, ?, 0) RETURNING id",
                ).use { statement ->
                    statement.setString(1, login)
                    statement.setTimestamp(2, Timestamp.from(now))
                    statement.setTimestamp(3, Timestamp.from(now))
                    statement.executeQuery().use { rs ->
                        rs.next()
                        AccountId(rs.getLong(1))
                    }
                }
        }

    private fun capacity(
        license: String,
        amount: Long = 1_234_000_000L,
        year: Int = 2026,
    ) = ConstructionCapacity(LicenseName(license), amount, year)

    @Test
    fun `행이 없는 계정은 필드마다 미설정이다 — 빈 값을 지어내지 않는다`() {
        val owner = account("owner")

        store.details(owner) shouldBe BusinessDetails.EMPTY
        store.capacities(owner).shouldBeEmpty()
    }

    @Test
    fun `왕복 — 등록 · 분야 · 만료일 · 상호 원문(앞뒤 공백 포함)이 그대로 복원된다`() {
        val owner = account("owner")

        store.replaceDetails(owner, registered, now)

        val restored = store.details(owner)
        restored shouldBe registered
        restored.tradeName?.raw shouldBe " (주)합성상호 "
    }

    @Test
    fun `왕복 — 미응답과 미등록은 서로 다르게 복원된다`() {
        val owner = account("owner")

        store.replaceDetails(owner, BusinessDetails.EMPTY.copy(naraReadiness = NaraReadiness.NotRegistered), now)
        store.details(owner).naraReadiness shouldBe NaraReadiness.NotRegistered

        store.replaceDetails(owner, BusinessDetails.EMPTY, now)
        store.details(owner).naraReadiness shouldBe NaraReadiness.Unanswered
    }

    @Test
    fun `절 교체는 null 로 되돌릴 수 있다 — 상호를 지우면 미설정이다`() {
        val owner = account("owner")
        store.replaceDetails(owner, registered, now)

        store.replaceDetails(owner, registered.copy(tradeName = null), now)

        store.details(owner).tradeName shouldBe null
    }

    @Test
    fun `소유 경계 — 다른 계정의 사업 정보와 시평액이 보이지 않는다`() {
        val mine = account("mine")
        val theirs = account("theirs")
        store.replaceDetails(theirs, registered, now)
        store.upsertCapacity(theirs, capacity("합성토목"), now)

        store.details(mine) shouldBe BusinessDetails.EMPTY
        store.capacities(mine).shouldBeEmpty()
    }

    @Test
    fun `소유 경계 — 같은 면허명이라도 다른 계정의 행을 지우거나 바꾸지 않는다`() {
        val mine = account("mine")
        val theirs = account("theirs")
        store.upsertCapacity(mine, capacity("합성토목", amount = 1_000L), now)
        store.upsertCapacity(theirs, capacity("합성토목", amount = 2_000L), now)

        store.upsertCapacity(mine, capacity("합성토목", amount = 3_000L), now)
        store.removeCapacity(mine, LicenseName("합성토목")) shouldBe true

        store.capacities(mine).shouldBeEmpty()
        store.capacities(theirs) shouldBe listOf(capacity("합성토목", amount = 2_000L))
    }

    @Test
    fun `시평액 upsert — 같은 면허명은 갱신, 다른 면허명은 추가 · 면허명 순`() {
        val owner = account("owner")
        store.upsertCapacity(owner, capacity("합성토목", year = 2025), now)
        store.upsertCapacity(owner, capacity("합성건축"), now)

        store.upsertCapacity(owner, capacity("합성토목", amount = 9_000L, year = 2026), now)

        store.capacities(owner) shouldBe listOf(capacity("합성건축"), capacity("합성토목", amount = 9_000L, year = 2026))
    }

    @Test
    fun `없는 면허명 삭제는 false 다`() {
        store.removeCapacity(account("owner"), LicenseName("합성없음")) shouldBe false
    }

    /** 부분 갱신 — 한 절의 쓰기가 다른 절(시평액 · V12 자격 사실)을 지우거나 만들지 않는다. */
    @Test
    fun `사업 정보 교체가 시평액을 지우지 않고 V12 행을 만들지 않는다`() {
        val owner = account("owner")
        store.upsertCapacity(owner, capacity("합성토목"), now)

        store.replaceDetails(owner, registered, now)
        store.replaceDetails(owner, BusinessDetails.EMPTY, now)

        store.capacities(owner) shouldBe listOf(capacity("합성토목"))
        JdbcOperatorProfileRepository(dataSource()).current() shouldBe null
    }

    @Test
    fun `시평액 쓰기가 사업 정보를 만들거나 바꾸지 않는다`() {
        val owner = account("owner")
        store.replaceDetails(owner, registered, now)

        store.upsertCapacity(owner, capacity("합성토목"), now)
        store.removeCapacity(owner, LicenseName("합성토목"))

        store.details(owner) shouldBe registered
    }

    /**
     * **제약 위반은 예외가 아니라 값이다**(2b 표적 1 · D-7C4-8 Q2). 없는 계정으로 쓰면 외래 키가
     * 거부하고 드라이버 예외의 `detail` 에는 거부된 **행의 값**이 실린다 — 그 예외가 어댑터 밖으로
     * 나가면 그 값이 스택트레이스로 로그에 남는다. 밖으로 나오는 것은 SQLSTATE 다섯 글자뿐이다.
     *
     * 실 DB 가 필요한 이유: 이 번역은 **드라이버가 매기는 SQLSTATE** 위에 서 있고, fake 로는 그 값이
     * 실제로 class 23 인지 증명되지 않는다.
     */
    @Test
    fun `없는 계정으로 쓰면 제약 거부를 값으로 낸다 — 예외 문면이 밖으로 나오지 않는다`() {
        val absent = AccountId(999_999_999L)

        store.replaceDetails(absent, registered, now) shouldBe
            StorageWriteOutcome.RejectedByStorage(FOREIGN_KEY_VIOLATION)
        store.upsertCapacity(absent, capacity("합성토목"), now) shouldBe
            StorageWriteOutcome.RejectedByStorage(FOREIGN_KEY_VIOLATION)
    }

    @Test
    fun `정상 쓰기는 썼다고 낸다`() {
        val owner = account("owner")

        store.replaceDetails(owner, registered, now) shouldBe StorageWriteOutcome.Written
        store.upsertCapacity(owner, capacity("합성토목"), now) shouldBe StorageWriteOutcome.Written
    }
}

/** SQL 표준 SQLSTATE — 외래 키 위반. 두 표 모두 `account (id)` 를 참조한다(V22). */
private const val FOREIGN_KEY_VIOLATION = "23503"
