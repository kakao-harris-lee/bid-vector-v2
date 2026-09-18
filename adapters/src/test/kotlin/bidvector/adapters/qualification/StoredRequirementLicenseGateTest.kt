package bidvector.adapters.qualification

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.qualification.LicenseAliasTable
import bidvector.qualification.LicenseName
import bidvector.qualification.LicenseQualificationPolicyData
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.LmtGrpNo
import bidvector.qualification.LmtSno
import bidvector.qualification.OperatorLicenses
import bidvector.qualification.RequirementCollection
import bidvector.qualification.RequirementGroupId
import bidvector.qualification.RequirementRow
import bidvector.qualification.RequirementSourceField
import bidvector.qualification.UncertainReason
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.evaluation.ProfileFacts
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * [StoredRequirementLicenseGate] — `LicenseGatePort`의 첫 production 구현(D-6F5-2).
 * **항진명제 함정(6F-2·6F-6 HIGH, 두 번 연속) 회피** — 이 test는 게이트가 실제로 내야 할
 * `LicenseVerdict`를 이 test 자신이 도메인 규칙(§3.2.5, 그룹 간 OR·그룹 내 AND)으로 손수
 * 판단해 리터럴로 적는다. `LicenseEligibility.judge`를 다시 불러 비교하지 않는다 — 그러면
 * 커널이 잘못돼도 test가 같이 잘못돼 항상 초록이다.
 */
class StoredRequirementLicenseGateTest : PersistenceTestSupport() {
    private val now = Instant.parse("2026-09-19T00:00:00Z")
    private val emptyPolicy =
        Resolution.Resolved(
            LicenseQualificationPolicyData(
                aliasTable = LicenseAliasTable(emptyList()),
                regionalConditions = emptyList(),
            ),
            PolicyVersion(EffectiveFrom.Initial, "test"),
        )

    private fun gate(operatorProfilePort: OperatorProfilePort = OperatorProfilePort { null }) =
        StoredRequirementLicenseGate(JdbcRequirementStore(dataSource()), operatorProfilePort, emptyPolicy)

    private fun declaredLicenses(vararg names: String) =
        OperatorProfilePort {
            ProfileFacts(
                businessTypes = emptySet(),
                licenses = OperatorLicenses.Declared(names.map(::LicenseName)),
                regionTerms = emptyList(),
            )
        }

    private fun insertNotice(number: String): NoticeId {
        val id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                now,
            )
        val key = appendRawObservation(observation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = null,
                openingScheduledAt = null,
                raw = observation,
            )
        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted
        return id
    }

    private fun findNotice(id: NoticeId) = JdbcNoticeRepository(dataSource()).find(id)!!

    @Test
    fun `저장된 요건이 없으면 Uncertain RequirementDataAbsent 다`() {
        val id = insertNotice("GATE-ABSENT")

        gate().verdictFor(findNotice(id)) shouldBe LicenseVerdict.Uncertain(UncertainReason.RequirementDataAbsent)
    }

    @Test
    fun `요건 수집이 실패로 저장돼 있으면 Uncertain CollectionFailed 다`() {
        val id = insertNotice("GATE-FAILED")
        JdbcRequirementStore(dataSource()).save(id, RequirementCollection.CollectionFailed)

        gate().verdictFor(findNotice(id)) shouldBe LicenseVerdict.Uncertain(UncertainReason.CollectionFailed)
    }

    @Test
    fun `보유 면허가 요구를 충족하면 Eligible 이다`() {
        val id = insertNotice("GATE-ELIGIBLE")
        val row =
            RequirementRow.Parsed(
                groupNo = null,
                serialNo = LmtSno("01"),
                sourceField = RequirementSourceField.LcnsLmtNm,
                licenseNames = listOf(LicenseName("전기공사업")),
            )
        JdbcRequirementStore(dataSource()).save(id, RequirementCollection.Collected(listOf(row)))

        val verdict = gate(declaredLicenses("전기공사업")).verdictFor(findNotice(id))

        verdict shouldBe LicenseVerdict.Eligible(setOf(RequirementGroupId.Ungrouped))
    }

    @Test
    fun `보유 면허가 요구를 충족하지 못하면 Ineligible 이고 부족분을 담는다`() {
        val id = insertNotice("GATE-INELIGIBLE")
        val row =
            RequirementRow.Parsed(
                groupNo = null,
                serialNo = LmtSno("01"),
                sourceField = RequirementSourceField.LcnsLmtNm,
                licenseNames = listOf(LicenseName("전기공사업")),
            )
        JdbcRequirementStore(dataSource()).save(id, RequirementCollection.Collected(listOf(row)))

        val verdict = gate(declaredLicenses("정보통신공사업")).verdictFor(findNotice(id))

        verdict shouldBe
            LicenseVerdict.Ineligible(mapOf(RequirementGroupId.Ungrouped to setOf(LicenseName("전기공사업"))))
    }

    @Test
    fun `요건은 있는데 운영자 프로필이 미설정이면 Uncertain OperatorLicensesNotDeclared 다`() {
        val id = insertNotice("GATE-NO-PROFILE")
        val row =
            RequirementRow.Parsed(
                groupNo = null,
                serialNo = LmtSno("01"),
                sourceField = RequirementSourceField.LcnsLmtNm,
                licenseNames = listOf(LicenseName("전기공사업")),
            )
        JdbcRequirementStore(dataSource()).save(id, RequirementCollection.Collected(listOf(row)))

        val verdict = gate(OperatorProfilePort { null }).verdictFor(findNotice(id))

        verdict shouldBe LicenseVerdict.Uncertain(UncertainReason.OperatorLicensesNotDeclared)
    }

    /** groupNo 결측 두 행이 U-8에 따라 하나의 `Ungrouped` 그룹으로 AND 폴딩된다(회귀 방지). */
    @Test
    fun `같은 그룹의 요구 면허를 일부만 보유하면 Ineligible 이다 — AND 폴딩`() {
        val id = insertNotice("GATE-PARTIAL")
        val rows =
            listOf(
                RequirementRow.Parsed(
                    groupNo = LmtGrpNo("001"),
                    serialNo = LmtSno("01"),
                    sourceField = RequirementSourceField.LcnsLmtNm,
                    licenseNames = listOf(LicenseName("전기공사업")),
                ),
                RequirementRow.Parsed(
                    groupNo = LmtGrpNo("001"),
                    serialNo = LmtSno("02"),
                    sourceField = RequirementSourceField.LcnsLmtNm,
                    licenseNames = listOf(LicenseName("정보통신공사업")),
                ),
            )
        JdbcRequirementStore(dataSource()).save(id, RequirementCollection.Collected(rows))

        val verdict = gate(declaredLicenses("전기공사업")).verdictFor(findNotice(id))

        verdict shouldBe
            LicenseVerdict.Ineligible(
                mapOf(RequirementGroupId.Numbered(LmtGrpNo("001")) to setOf(LicenseName("정보통신공사업"))),
            )
    }
}
