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
 * **[StoredRequirementLicenseGate] 와 이 클래스는 각자 커널의 판정 함수를 부른다**(`cr r1 M1` —
 * 앞 문면은 「게이트가 이 클래스로 위임한다」고 적었으나 그 위임은 되돌렸다). 한때 게이트가 이
 * 클래스에 위임하게 바꿨는데, 자격 어댑터 의존 게이트가 **그 게이트 클래스의 컴파일된 코드가
 * 판정 타입을 참조한다**는 사실을 들고 있어 붉어졌다 — 그 앵커는 「어댑터가 verdict 를 스스로
 * 조립하지 않는다」를 증명하는 자리라 중복 제거를 위해 바꿀 것이 아니다.
 *
 * 그래서 **세 줄이 두 자리에 있고, 둘이 같은 판정을 내는지는 실 DB test 의 verdict 등식이
 * 감시한다**(`StoredRequirementLicenseGateTest`). 중복을 지우는 대신 **갈라지면 붉어지게** 둔 것이고,
 * 그 등식이 없으면 「같은 입력에 다른 판정」이 설 여지가 생긴다.
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
