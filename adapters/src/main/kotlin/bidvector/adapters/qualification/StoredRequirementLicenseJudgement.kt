package bidvector.adapters.qualification

import bidvector.procurement.Notice
import bidvector.qualification.LicenseEligibility
import bidvector.qualification.LicenseJudgement
import bidvector.qualification.LicenseQualificationPolicyData
import bidvector.qualification.OperatorLicenses
import bidvector.sharedkernel.Resolution
import bidvector.workflow.evaluation.LicenseJudgementPort
import bidvector.workflow.evaluation.OperatorProfilePort

/**
 * [LicenseJudgementPort] 의 production 구현(M7/7C ②) — **저장된 요건만 읽고** 판정은
 * [LicenseEligibility.judge] 하나가 낸다. 이 클래스는 `LicenseVerdict` 를 조립하지 않는다
 * ([StoredRequirementLicenseGate] 와 같은 규율).
 *
 * **[StoredRequirementLicenseGate] 가 이 클래스로 위임한다**(거동 불변) — 그 게이트는 같은 세
 * 입력으로 같은 판정을 내고 `verdict` 만 꺼내던 자리였다. 봉투를 내는 자리와 verdict 를 내는
 * 자리를 따로 구현하면 「같은 입력에 다른 판정」이 설 여지가 생기므로, 판정 호출은 이
 * 클래스 하나로 모으고 게이트는 그 결과를 좁혀 낸다.
 *
 * 프로필 미설정(`current()` 가 `null`)은 [OperatorLicenses.NotDeclared] 이고 커널이 그 값을
 * `Uncertain(OperatorLicensesNotDeclared)` 로 옮긴다 — `Uncertain ≠ Ineligible`(U-5).
 */
class StoredRequirementLicenseJudgement(
    private val store: JdbcRequirementStore,
    private val operatorProfilePort: OperatorProfilePort,
    private val policy: Resolution.Resolved<LicenseQualificationPolicyData>,
) : LicenseJudgementPort {
    override fun judgementFor(notice: Notice): LicenseJudgement {
        val collection = store.find(notice.id)
        val operatorLicenses = operatorProfilePort.current()?.licenses ?: OperatorLicenses.NotDeclared
        return LicenseEligibility.judge(collection, operatorLicenses, policy)
    }
}
