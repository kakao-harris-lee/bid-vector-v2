package bidvector.workflow.evaluation

import bidvector.decision.UnitScore
import bidvector.decision.priority.derive.DerivationAbsence
import bidvector.decision.priority.derive.DerivationOutcome
import bidvector.qualification.LicenseName
import bidvector.qualification.OperatorLicenses
import bidvector.strategy.CategoryCode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * [ProfileFacts] allow-list 구조(D-4B6A-1) + [OperatorProfilePort]·[WorkloadPort] fake 둘
 * (scope.md ⑤, ADR 0005 D-9 — 실 구현은 M6/후속).
 */
class ProfilePortsTest {
    @Test
    fun `ProfileFacts 는 업종 면허 지역 세 필드로만 생성된다 — allow-list 고정`() {
        val facts =
            ProfileFacts(
                businessTypes = setOf(CategoryCode("SW")),
                licenses = OperatorLicenses.Declared(listOf(LicenseName("정보통신공사업"))),
                regionTerms = listOf("서울"),
            )

        facts.businessTypes shouldBe setOf(CategoryCode("SW"))
        facts.licenses shouldBe OperatorLicenses.Declared(listOf(LicenseName("정보통신공사업")))
        facts.regionTerms shouldBe listOf("서울")
    }

    @Test
    fun `OperatorProfilePort fake — 미설정은 null`() {
        val port = FakeOperatorProfilePort(null)

        port.current() shouldBe null
    }

    @Test
    fun `OperatorProfilePort fake — 설정된 프로필을 그대로 낸다`() {
        val facts =
            ProfileFacts(
                businessTypes = setOf(CategoryCode("SW")),
                licenses = OperatorLicenses.NotDeclared,
                regionTerms = emptyList(),
            )
        val port = FakeOperatorProfilePort(facts)

        port.current() shouldBe facts
    }

    @Test
    fun `WorkloadPort fake — 항상 미가용(WorkloadNotCollected)`() {
        val port = AlwaysUnavailableWorkloadPort()

        val outcome = port.current()

        outcome shouldBe DerivationOutcome.Absent(DerivationAbsence.WorkloadNotCollected)
    }
}

private class FakeOperatorProfilePort(
    private val facts: ProfileFacts?,
) : OperatorProfilePort {
    override fun current(): ProfileFacts? = facts
}

private class AlwaysUnavailableWorkloadPort : WorkloadPort {
    override fun current(): DerivationOutcome<UnitScore> =
        DerivationOutcome.Absent(DerivationAbsence.WorkloadNotCollected)
}
