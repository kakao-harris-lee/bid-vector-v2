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
        val notDeclared =
            ProfileFacts(setOf(CategoryCode("건설업")), OperatorLicenses.NotDeclared, emptyList())
        val declaredEmpty =
            ProfileFacts(setOf(CategoryCode("건설업")), OperatorLicenses.Declared(emptyList()), emptyList())

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
            ProfileFacts(
                setOf(CategoryCode("건설업")),
                OperatorLicenses.Declared(listOf(LicenseName("건설업"))),
                listOf("서울"),
            )
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
        val insertSecondRow =
            "INSERT INTO operator_profile (id, business_types, licenses_declared, license_names, region_terms) " +
                "VALUES (2, '{}', false, '{}', '{}')"
        appConnection().use { connection ->
            connection.autoCommit = true
            connection.prepareStatement(insertSecondRow).use { statement ->
                shouldThrow<SQLException> { statement.executeUpdate() }
            }
        }
    }

    /**
     * `OPEN-6F6-CATEGORY-CODE-NORMALIZATION`(D-6F6-4, D-6F6-10 수정 라운드 1) — 이 표가 싣는
     * `bidvector.strategy.CategoryCode`는 `bidvector.procurement.CategoryCode.of()`와 달리
     * 정규화가 없는 평범한 `data class`다. 이 test는 그 공백을 **닫지 않고 보이게** 고정한다.
     *
     * **verifier r1 HIGH-1 시정** — 원판은 `repository().current()!!.businessTypes shouldBe
     * facts.businessTypes`로, 양변이 같은 `CategoryCode(...)` 생성자를 지난 값이라 그 생성자
     * 안에 멱등 정규화가 들어오면 양변이 함께 접혀 단언이 계속 통과하는 항진명제였다(6F-2
     * HIGH-1과 같은 결함 클래스 — 함수와 그 함수 본문 재계산값의 비교). 이 판은 복원된
     * `.value`(String, 재구성이 아니라 단순 필드 접근)를 리터럴 `String` 기대값과 직접
     * 대조한다 — 어느 쪽도 `CategoryCode(...)`를 다시 통과하지 않는다. `CategoryCode`
     * 생성자에 trim+lowercase가 들어오면 `spaced`·`plain` 두 리터럴이 생성 시점부터 이미
     * 같은 정규화 값이 되어 `businessTypes`(Set)가 1원소로 접히고, 아래 **원소 수 2** 단언이
     * 먼저 붉어진다 — `strategy/Text.kt`는 여전히 무편집(6F-4 소관), 이 test는 그 타입이
     * 바뀌는 순간을 잡을 뿐이다.
     */
    @Test
    fun `업종 코드는 정규화 없이 원문 그대로 왕복된다 — OPEN-6F6-CATEGORY-CODE-NORMALIZATION`() {
        val spaced = " 정보통신공사업 "
        val plain = "정보통신공사업"
        val facts =
            ProfileFacts(
                businessTypes = setOf(CategoryCode(spaced), CategoryCode(plain)),
                licenses = OperatorLicenses.NotDeclared,
                regionTerms = emptyList(),
            )

        repository().save(facts)

        val restoredProfile = repository().current()!!
        val restoredValues = restoredProfile.businessTypes.map(CategoryCode::value).toSet()

        // 원소 수 — 정규화가 들어오면 두 리터럴이 생성 시점에 이미 하나로 접혀 여기서 붉어진다.
        restoredValues.size shouldBe 2
        // 원문 리터럴 보존 — strip·lower 없이 공백이 붙은 원문이 그대로 남는다(재구성 비교 아님).
        restoredValues shouldBe setOf(spaced, plain)
    }
}
