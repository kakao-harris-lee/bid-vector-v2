package bidvector.adapters.profile

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.qualification.LicenseName
import bidvector.qualification.OperatorLicenses
import bidvector.strategy.CategoryCode
import bidvector.workflow.evaluation.ProfileFacts
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * [JdbcOperatorProfileRepository] — 왕복·미설정·세 상태 구분·갱신·싱글턴(scope.md in_scope,
 * D-6F6-2·D-6F6-3, 우회 (1)(7)). 위협 모델 방어 ①(「미선언」과 「빈 프로필」이 섞이지
 * 않는다)의 유일한 실행 증거다.
 */
class JdbcOperatorProfileRepositoryTest : PersistenceTestSupport() {
    private fun repository() = JdbcOperatorProfileRepository(dataSource())

    @Test
    fun `프로필 미설정 — 행이 없으면 current 는 null`() {
        repository().current() shouldBe null
    }

    @Test
    fun `왕복 — Declared 면허·업종·지역 어휘가 그대로 복원된다`() {
        val facts =
            ProfileFacts(
                businessTypes = setOf(CategoryCode("정보통신공사업"), CategoryCode("소프트웨어개발업")),
                licenses = OperatorLicenses.Declared(listOf(LicenseName("정보통신공사업"), LicenseName("소프트웨어사업자"))),
                regionTerms = listOf("서울", "경기"),
            )

        repository().save(facts)

        repository().current() shouldBe facts
    }

    /**
     * 위협 모델 방어 ① 핵심 — `NotDeclared`가 「면허 0개 보유」(`Declared(emptyList())`)로
     * 납작해지면 판정이 `Uncertain`에서 `Ineligible`로 바뀐다(scope.md). 이 test 가 그 둘을
     * 저장·복원 양쪽에서 가른다.
     */
    @Test
    fun `왕복 — NotDeclared 는 Declared 빈 목록과 다르게 복원된다`() {
        val notDeclared = ProfileFacts(setOf(CategoryCode("건설업")), OperatorLicenses.NotDeclared, emptyList())
        val declaredEmpty = ProfileFacts(setOf(CategoryCode("건설업")), OperatorLicenses.Declared(emptyList()), emptyList())

        repository().save(notDeclared)
        val afterNotDeclared = repository().current()

        repository().save(declaredEmpty)
        val afterDeclaredEmpty = repository().current()

        afterNotDeclared shouldBe notDeclared
        afterDeclaredEmpty shouldBe declaredEmpty
        afterNotDeclared shouldNotBe afterDeclaredEmpty
    }

    @Test
    fun `갱신 — 두 번째 save 가 첫 번째 값을 덮어쓴다(싱글턴 upsert)`() {
        val first =
            ProfileFacts(setOf(CategoryCode("건설업")), OperatorLicenses.Declared(listOf(LicenseName("건설업"))), listOf("서울"))
        val second =
            ProfileFacts(setOf(CategoryCode("전기공사업")), OperatorLicenses.NotDeclared, listOf("부산", "울산"))

        repository().save(first)
        repository().save(second)

        repository().current() shouldBe second
    }

    /**
     * D-6F6-3·우회 (7) — 표 자신의 `CHECK (id = 1)`이 두 번째 행을 거부한다. 애플리케이션
     * 경로([JdbcOperatorProfileRepository.save])는 늘 `id=1`만 쓰므로, 이 test 는 그 경로를
     * 우회하는 직접 SQL로 표 제약 자체를 잰다(`bidvector_app` 역할, `appConnection()`).
     */
    @Test
    fun `싱글턴 제약 — id 2 로 두 번째 행을 직접 SQL 로 넣으면 표 제약이 거부한다`() {
        appConnection().use { connection ->
            connection.autoCommit = true
            connection.prepareStatement(
                "INSERT INTO operator_profile (id, business_types, licenses_declared, license_names, region_terms) " +
                    "VALUES (2, '{}', false, '{}', '{}')",
            ).use { statement ->
                shouldThrow<SQLException> { statement.executeUpdate() }
            }
        }
    }
}
