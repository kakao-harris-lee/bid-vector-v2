package bidvector.adapters.qualification

import bidvector.procurement.Notice
import bidvector.qualification.LicenseEligibility
import bidvector.qualification.LicenseQualificationPolicyData
import bidvector.qualification.LicenseVerdict
import bidvector.qualification.OperatorLicenses
import bidvector.sharedkernel.Resolution
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.OperatorProfilePort

/**
 * [LicenseGatePort]의 첫 production 구현(D-6F5-2, D-6F5-3) — **저장된 요건만 읽는다.** LLM
 * 재추출을 부르지 않는다(scope.md 착수 조사 ①②③, D-6F5-1의 분할 근거). 판정은
 * [LicenseEligibility.judge] 하나가 낸다 — 이 클래스는 `LicenseVerdict`를 직접 조립하지
 * 않는다(scope.md 우회 2, `QualificationAdapterDependencyTest`의 참조 단언이 이 호출
 * 자리를 잠근다).
 *
 * **D-6F5-3 — [OperatorLicenses]는 [OperatorProfilePort] 주입으로 받는다.** 그 port의 실
 * 구현(6F-6, PR #38)에 직접 의존하지 않는다 — 진행 중 다른 slice의 미병합 브랜치에 이
 * slice가 얹히지 않는다. 프로필이 미설정(`current()`가 `null`)이면
 * [OperatorLicenses.NotDeclared]로 낸다 — 커널이 그 값을
 * `Uncertain(OperatorLicensesNotDeclared)`로 옮긴다(U-5, `Uncertain ≠ Ineligible`).
 *
 * **D-6F5-5 — 정책은 생성자 주입이다.** [policy]는 이미 해소된 [Resolution.Resolved]다 —
 * 이 클래스는 `bidvector.qualification.LICENSE_QUALIFICATION_POLICY`를 참조하지 않는다
 * (어댑터가 정책 파일의 두 번째 독자가 되지 않는다, 6F-1 D-6F1-6과 같은 규율).
 */
class StoredRequirementLicenseGate(
    private val store: JdbcRequirementStore,
    private val operatorProfilePort: OperatorProfilePort,
    private val policy: Resolution.Resolved<LicenseQualificationPolicyData>,
) : LicenseGatePort {
    override fun verdictFor(notice: Notice): LicenseVerdict {
        val collection = store.find(notice.id)
        val operatorLicenses = operatorProfilePort.current()?.licenses ?: OperatorLicenses.NotDeclared
        return LicenseEligibility.judge(collection, operatorLicenses, policy).verdict
    }
}
